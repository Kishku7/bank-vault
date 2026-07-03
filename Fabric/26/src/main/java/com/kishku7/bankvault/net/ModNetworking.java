package com.kishku7.bankvault.net;

import com.kishku7.bankvault.inventory.BankVaultMenu;
import com.kishku7.bankvault.vault.Bank;
import com.kishku7.bankvault.vault.BankManager;
import com.kishku7.bankvault.vault.StackStore;
import com.kishku7.bankvault.vault.UserSettings;
import com.kishku7.bankvault.vault.VaultCapacity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class ModNetworking {

    private ModNetworking() {}

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(VaultSyncPayload.TYPE, VaultSyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WithdrawPayload.TYPE, WithdrawPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpgradePayload.TYPE, UpgradePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DepositPayload.TYPE, DepositPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GridViewPayload.TYPE, GridViewPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DepositAllPayload.TYPE, DepositAllPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SharingStatePayload.TYPE, SharingStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShareActionPayload.TYPE, ShareActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UiStatePayload.TYPE, UiStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UiStateSyncPayload.TYPE, UiStateSyncPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WithdrawPayload.TYPE, (payload, context) -> onWithdraw(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UpgradePayload.TYPE, (payload, context) -> onUpgrade(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(DepositPayload.TYPE, (payload, context) -> onDeposit(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(GridViewPayload.TYPE, (payload, context) -> onGridView(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(DepositAllPayload.TYPE, (payload, context) -> onDepositAll(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ShareActionPayload.TYPE, (payload, context) -> onShareAction(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UiStatePayload.TYPE, (payload, context) -> onUiState(payload, context.player()));
    }


    private static void sendTo(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendSync(ServerPlayer player, Bank bank) {
        RegistryAccess ra = player.level().registryAccess();
        List<VaultSyncPayload.Entry> entries = new ArrayList<>();
        for (var e : bank.items.entrySet()) {
            Identifier id = StackStore.parseId(e.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (st.isEmpty()) continue;
            entries.add(new VaultSyncPayload.Entry(e.getKey(), st, e.getValue()));
        }
        for (var e : bank.special.entrySet()) {
            ItemStack st = StackStore.decode(e.getValue().stack, ra);
            if (!st.isEmpty()) entries.add(new VaultSyncPayload.Entry(e.getKey(), st, e.getValue().count));
        }
        sendTo(player, new VaultSyncPayload(entries, bank.upgradeCount,
                VaultCapacity.capacityFor(bank.upgradeCount), bank.levelOf(player.getUUID())));
        sendSharing(player);
    }

    /** Push the Sharing-corner state (membership + pending invite) to one player. */
    public static void sendSharing(ServerPlayer player) {
        Bank bank = BankManager.lookup(player.getUUID());
        List<SharingStatePayload.Member> ms = new ArrayList<>();
        if (bank != null) for (Bank.Member m : bank.members)
            ms.add(new SharingStatePayload.Member(m.uuid, m.name, m.level));
        List<SharingStatePayload.InviteEntry> is = new ArrayList<>();
        for (BankManager.Invite inv : BankManager.pendingInvites(player.getUUID()))
            is.add(new SharingStatePayload.InviteEntry(inv.inviterName, inv.level));
        sendTo(player, new SharingStatePayload(ms, is));
    }

    /** v1.2 last-use memory: persist the interaction in the server-side bucket files. */
    private static void onUiState(UiStatePayload payload, ServerPlayer player) {
        UserSettings.update(player, payload.lastTab(), payload.tab(), payload.sort(),
                payload.sections());
    }


    /** Push the player's remembered UI state (last tab + per-tab sorts); must be sent BEFORE
     *  the menu-open packet so the screen finds it at init. */
    public static void sendUiState(ServerPlayer player) {
        UserSettings.Rec rec = UserSettings.get(player.getUUID());
        List<UiStateSyncPayload.TabSort> ts = new ArrayList<>();
        List<UiStateSyncPayload.TabPins> tp = new ArrayList<>();
        String lastTab = "";
        boolean sections = false;
        if (rec != null) {
            lastTab = rec.lastTab == null ? "" : rec.lastTab;
            sections = rec.showSections;
            for (var e : rec.sorts.entrySet()) ts.add(new UiStateSyncPayload.TabSort(e.getKey(), e.getValue()));
            for (var e : rec.pins.entrySet()) tp.add(new UiStateSyncPayload.TabPins(e.getKey(), e.getValue()));
        }
        sendTo(player, new UiStateSyncPayload(lastTab, ts, sections, tp));
    }

    /** Re-sync every online member of a bank (membership or contents changed). */
    private static void refreshGroup(net.minecraft.server.MinecraftServer server, Bank bank) {
        for (Bank.Member m : bank.members) {
            try {
                ServerPlayer p = server.getPlayerList().getPlayer(java.util.UUID.fromString(m.uuid));
                if (p != null) sendSync(p, bank);   // sendSync also pushes sharing state
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static void onShareAction(ShareActionPayload payload, ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) return;
        String msg = null;
        switch (payload.op()) {
            case ShareActionPayload.INVITE -> {
                ServerPlayer target = server.getPlayerList().getPlayerByName(payload.target());
                if (target == null) msg = "\u00a7cPlayer '" + payload.target() + "' is not online.";
                else if (target.getUUID().equals(player.getUUID())) msg = "\u00a7cYou can't invite yourself.";
                else {
                    msg = BankManager.invite(player, target.getUUID(), target.getGameProfile().name(),
                            Math.max(BankManager.DEPOSIT, payload.level()));
                    sendSharing(target);                       // light their Accept button up live
                }
            }
            case ShareActionPayload.ACCEPT -> {
                // rc.3: target carries the inviter name; empty = most recent invite
                BankManager.AcceptResult r = BankManager.accept(player,
                        payload.target().isEmpty() ? null : payload.target());
                msg = r.message();
                if (r.ok()) {
                    int excess = r.excessChests();
                    while (excess > 0) {                       // refund surplus upgrade chests
                        int n = Math.min(64, excess);
                        ItemStack c = new ItemStack(Items.CHEST, n);
                        if (!player.getInventory().add(c)) player.drop(c, false);
                        excess -= n;
                    }
                    Bank b = BankManager.lookup(player.getUUID());
                    if (b != null) refreshGroup(server, b);
                }
            }
            case ShareActionPayload.DECLINE -> msg = BankManager.decline(player,
                    payload.target().isEmpty() ? null : payload.target());
            case ShareActionPayload.KICK, ShareActionPayload.LEVEL_UP, ShareActionPayload.LEVEL_DOWN -> {
                Bank bank = BankManager.lookup(player.getUUID());
                java.util.UUID t;
                try { t = java.util.UUID.fromString(payload.target()); }
                catch (IllegalArgumentException e) { t = null; }
                Bank.Member m = (bank == null || t == null) ? null : bank.member(t);
                if (m == null) msg = "\u00a7cThat player isn't in your bank.";
                else if (payload.op() == ShareActionPayload.KICK) {
                    msg = BankManager.kick(player, t, m.name);
                    ServerPlayer kicked = server.getPlayerList().getPlayer(t);
                    if (kicked != null) sendSharing(kicked);
                    refreshGroup(server, bank);
                } else {
                    int delta = payload.op() == ShareActionPayload.LEVEL_UP ? 1 : -1;
                    msg = BankManager.setLevel(player, t, m.name, m.level + delta);
                    refreshGroup(server, bank);
                }
            }
            case ShareActionPayload.LEAVE -> {
                Bank old = BankManager.lookup(player.getUUID());
                msg = BankManager.leave(player);
                if (old != null) refreshGroup(server, old);
                BankManager.getOrCreate(player);               // fresh solo bank right away
            }
            case ShareActionPayload.REFRESH -> { /* rc.4 heartbeat: the tail below re-syncs */ }
            default -> { return; }
        }
        if (msg != null && !msg.isEmpty()) player.sendSystemMessage(Component.literal(msg));
        Bank now = BankManager.lookup(player.getUUID());
        if (now != null) sendSync(player, now); else sendSharing(player);
    }

    /** The client tells us which bank key sits in each visible grid cell so a real-Slot click on the
     *  vault view (handled in BankVaultMenu.clicked) knows what to withdraw. */
    private static void onGridView(GridViewPayload payload, ServerPlayer player) {
        if (player.containerMenu instanceof BankVaultMenu menu) {
            menu.setViewKeys(payload.keys());
            menu.setCurrentTab(payload.tab());
        }
    }

    private static void onWithdraw(WithdrawPayload payload, ServerPlayer player) {
        Bank bank = BankManager.lookup(player.getUUID());
        if (bank == null || bank.levelOf(player.getUUID()) < BankManager.MEMBER) return;
        String key = payload.itemId();
        RegistryAccess ra = player.level().registryAccess();
        ItemStack proto;
        if (key.contains("#")) {
            Bank.Special sp = bank.special.get(key);
            if (sp == null) return;
            proto = StackStore.decode(sp.stack, ra);
        } else {
            Identifier id = StackStore.parseId(key);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
            proto = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        }
        if (proto.isEmpty()) return;
        long taken = BankManager.withdrawKey(bank, key, Math.max(1, payload.amount()));
        int max = proto.getMaxStackSize();
        long left = taken;
        while (left > 0) {
            int n = (int) Math.min(max, left);
            ItemStack g = proto.copyWithCount(n);
            if (!player.getInventory().add(g)) player.drop(g, false);
            left -= n;
        }
        sendSync(player, bank);
    }

    private static void onDeposit(DepositPayload payload, ServerPlayer player) {
        Bank bank = BankManager.lookup(player.getUUID());
        if (bank == null || bank.levelOf(player.getUUID()) < BankManager.DEPOSIT) return;
        Inventory inv = player.getInventory();
        int slot = payload.slot();
        if (slot < 0 || slot >= inv.getContainerSize()) return;
        ItemStack s = inv.getItem(slot);
        if (s.isEmpty()) return;
        long accepted = BankManager.depositStack(bank, s, player.level().registryAccess());
        if (accepted > 0) s.shrink((int) accepted);
        sendSync(player, bank);
    }

    /** Bulk deposit (v1.1 "Deposit:" buttons). Vanilla Inventory indices: 0..8 hotbar, 9..35 main
     *  rows. Strictly those ranges -- armor (36..39), offhand (40), trinket and crafting slots are
     *  untouchable here by construction. Partial deposits stop when the vault fills; the remainder
     *  stays where it was. */
    private static void onDepositAll(DepositAllPayload payload, ServerPlayer player) {
        Bank bank = BankManager.lookup(player.getUUID());
        if (bank == null || bank.levelOf(player.getUUID()) < BankManager.DEPOSIT) return;
        Inventory inv = player.getInventory();
        RegistryAccess ra = player.level().registryAccess();
        boolean rejected = false;
        int first = payload.includeHotbar() ? 0 : 9;                 // main rows always; hotbar only on "All"
        for (int i = first; i <= 35; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            long accepted = BankManager.depositStack(bank, s, ra);
            if (accepted > 0) s.shrink((int) accepted);
            if (!s.isEmpty()) rejected = true;                       // vault filled mid-stack
        }
        if (rejected) player.sendSystemMessage(Component.literal("\u26a0 Vault is full."));
        sendSync(player, bank);
        if (player.containerMenu instanceof BankVaultMenu menu) menu.broadcastChanges();
    }

    private static void onUpgrade(UpgradePayload payload, ServerPlayer player) {
        Bank bank = BankManager.lookup(player.getUUID());
        if (bank == null) return;
        if (bank.levelOf(player.getUUID()) < BankManager.MASTER) {
            player.sendSystemMessage(Component.literal("§cOnly Bank Masters or the Owner can change upgrades."));
            sendSync(player, bank);
            return;
        }
        if (payload.add()) {
            ItemStack hand = player.getMainHandItem();
            if (hand.getItem() == Items.CHEST && bank.upgradeCount < VaultCapacity.MAX_UPGRADES) {
                hand.shrink(1);
                bank.upgradeCount++;
                BankManager.save(bank);
            }
        } else if (bank.upgradeCount > 0) {
            bank.upgradeCount--;
            BankManager.save(bank);
            ItemStack c = new ItemStack(Items.CHEST, 1);
            if (!player.getInventory().add(c)) player.drop(c, false);
        }
        sendSync(player, bank);
    }
}
