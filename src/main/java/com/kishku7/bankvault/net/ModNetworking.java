package com.kishku7.bankvault.net;

import com.kishku7.bankvault.inventory.BankVaultMenu;
import com.kishku7.bankvault.vault.Bank;
import com.kishku7.bankvault.vault.BankManager;
import com.kishku7.bankvault.vault.StackStore;
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
        ServerPlayNetworking.registerGlobalReceiver(WithdrawPayload.TYPE, ModNetworking::onWithdraw);
        ServerPlayNetworking.registerGlobalReceiver(UpgradePayload.TYPE, ModNetworking::onUpgrade);
        ServerPlayNetworking.registerGlobalReceiver(DepositPayload.TYPE, ModNetworking::onDeposit);
        ServerPlayNetworking.registerGlobalReceiver(GridViewPayload.TYPE, ModNetworking::onGridView);
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
        ServerPlayNetworking.send(player, new VaultSyncPayload(entries, bank.upgradeCount,
                VaultCapacity.capacityFor(bank.upgradeCount), bank.levelOf(player.getUUID())));
    }

    /** The client tells us which bank key sits in each visible grid cell so a real-Slot click on the
     *  vault view (handled in BankVaultMenu.clicked) knows what to withdraw. */
    private static void onGridView(GridViewPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player.containerMenu instanceof BankVaultMenu menu) {
            menu.setViewKeys(payload.keys());
        }
    }

    private static void onWithdraw(WithdrawPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
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

    private static void onDeposit(DepositPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
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

    private static void onUpgrade(UpgradePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
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
