package com.kishku7.bankvault.command;

import com.kishku7.bankvault.vault.Bank;
import com.kishku7.bankvault.vault.BankManager;
import com.kishku7.bankvault.vault.Catalog;
import com.kishku7.bankvault.vault.StackStore;
import com.kishku7.bankvault.vault.VaultCapacity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.RegistryAccess;
/* [[[cog
import compat_core
compat_core.emit_bc_import_components(cog, ver)
]]] */
import net.minecraft.core.component.DataComponents;
/* [[[end]]] */
import net.minecraft.core.registries.BuiltInRegistries;
/* [[[cog
import compat_core
compat_core.emit_bc_import_registries(cog, ver)
]]] */
import net.minecraft.core.registries.Registries;
/* [[[end]]] */
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
/* [[[cog
import compat_core
compat_core.emit_bc_import_brew(cog, ver)
]]] */
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
/* [[[end]]] */

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /bank} -- manage your bank vault group and stored items. */
public final class BankCommand {

    private BankCommand() {}

    private interface MemberOp { String apply(ServerPlayer actor, UUID targetId, String targetName); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bank")
                .executes(c -> info(c.getSource()))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(BankCommand::players)
                                .executes(c -> invite(c.getSource(), StringArgumentType.getString(c, "player"), 2))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                        .executes(c -> invite(c.getSource(), StringArgumentType.getString(c, "player"),
                                                IntegerArgumentType.getInteger(c, "level"))))))
                .then(Commands.literal("accept").executes(c -> accept(c.getSource(), null))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(BankCommand::inviters)
                                .executes(c -> accept(c.getSource(), StringArgumentType.getString(c, "player")))))
                .then(Commands.literal("decline").executes(c -> simple(c.getSource(), BankManager::decline))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(BankCommand::inviters)
                                .executes(c -> simple(c.getSource(),
                                        p -> BankManager.decline(p, StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("leave").executes(c -> simple(c.getSource(), BankManager::leave)))
                .then(Commands.literal("disband").executes(c -> simple(c.getSource(), BankManager::disband)))
                .then(Commands.literal("upgrade")
                        .executes(c -> upgrade(c.getSource())))
                .then(Commands.literal("fillall").requires(s ->
/* [[[cog
import compat_core
compat_core.emit_perm_gamemaster26(cog, ver)
]]] */
s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
/* [[[end]]] */
                        )
                        .executes(c -> fillAll(c.getSource())))
                .then(Commands.literal("clearall").requires(s ->
/* [[[cog
import compat_core
compat_core.emit_perm_gamemaster26(cog, ver)
]]] */
s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
/* [[[end]]] */
                        )
                        .executes(c -> clearAll(c.getSource())))
                .then(Commands.literal("reload").requires(s ->
/* [[[cog
import compat_core
compat_core.emit_perm_gamemaster26(cog, ver)
]]] */
s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
/* [[[end]]] */
                        )
                        .executes(c -> reloadCatalog(c.getSource())))
                // Hidden machine-readable surface for automation clients (M1). Not shown in help;
                // plain-ASCII single-line replies via VaultApi ("BV|op|OK|..." / "BV|op|ERR|...").
                .then(Commands.literal("api")
                        .then(Commands.literal("snapshot")
                                .executes(c -> api(c.getSource(), p -> com.kishku7.bankvault.api.VaultApi.snapshot(p))))
                        .then(Commands.literal("list").executes(c -> api(c.getSource(), p -> com.kishku7.bankvault.api.VaultApi.list(p, 1)))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> api(c.getSource(), p -> com.kishku7.bankvault.api.VaultApi.list(p, IntegerArgumentType.getInteger(c, "page"))))))
                        .then(Commands.literal("count")
                                .then(Commands.argument("key", StringArgumentType.greedyString())
                                        .executes(c -> api(c.getSource(), p -> com.kishku7.bankvault.api.VaultApi.count(p, StringArgumentType.getString(c, "key"))))))
                        .then(Commands.literal("find")
                                .then(Commands.argument("query", StringArgumentType.greedyString())
                                        .executes(c -> api(c.getSource(), p -> com.kishku7.bankvault.api.VaultApi.find(p, StringArgumentType.getString(c, "query"))))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                                .executes(c -> api(c.getSource(), p -> com.kishku7.bankvault.api.VaultApi.withdraw(p, StringArgumentType.getString(c, "key"), IntegerArgumentType.getInteger(c, "count")))))))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("what", StringArgumentType.greedyString())
                                                .executes(c -> api(c.getSource(), p -> com.kishku7.bankvault.api.VaultApi.deposit(p, StringArgumentType.getString(c, "what"), IntegerArgumentType.getInteger(c, "count"))))))))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(c -> withdraw(c.getSource(),
                                                IntegerArgumentType.getInteger(c, "count"),
                                                StringArgumentType.getString(c, "item"))))))
                .then(Commands.literal("setlevel")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(BankCommand::players)
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                        .executes(c -> withTarget(c.getSource(), StringArgumentType.getString(c, "player"),
                                                (a, id, n) -> BankManager.setLevel(a, id, n, IntegerArgumentType.getInteger(c, "level")))))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(BankCommand::players)
                                .executes(c -> withTarget(c.getSource(), StringArgumentType.getString(c, "player"), BankManager::kick))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(BankCommand::players)
                                .executes(c -> withTarget(c.getSource(), StringArgumentType.getString(c, "player"), BankManager::transfer)))));
    }

    private interface SimpleOp { String apply(ServerPlayer p); }

    private interface ApiOp { String apply(ServerPlayer p); }

    /** Runs an automation op and replies with its single machine-readable line (no color codes). */
    private static int api(CommandSourceStack src, ApiOp op) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        String line = op.apply(p);
        p.sendSystemMessage(Component.literal(line));
        return line.contains("|OK") ? 1 : 0;
    }

    private static int simple(CommandSourceStack src, SimpleOp op) {
        ServerPlayer p = src.getPlayer();
        return p == null ? 0 : send(p, op.apply(p));
    }

    private static int info(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        Bank bank = BankManager.getOrCreate(p);
        StringBuilder sb = new StringBuilder("\u00a76[Bank Vault]\u00a7r ");
        sb.append(String.format("%,d/%,d items \u00b7 %d unique \u00b7 upgrades %d/64. Members: ",
                bank.totalItems(), VaultCapacity.capacityFor(bank.upgradeCount), bank.uniqueItems(), bank.upgradeCount));
        for (Bank.Member m : bank.members) sb.append(m.name).append("(").append(BankManager.levelName(m.level)).append(") ");
        return send(p, sb.toString().trim());
    }

    private static int list(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        Bank bank = BankManager.getOrCreate(p);
        if (bank.items.isEmpty()) return send(p, "\u00a76[Bank Vault]\u00a7r (empty)");
        send(p, "\u00a76[Bank Vault]\u00a7r contents:");
        int shown = 0;
        for (Map.Entry<String, Long> e : bank.items.entrySet()) {
            if (shown++ >= 40) { send(p, "\u00a77\u2026and " + (bank.items.size() - 40) + " more."); break; }
            send(p, String.format("\u00a77- \u00a7f%s \u00a77x \u00a7e%,d", e.getKey(), e.getValue()));
        }
        return 1;
    }

    private static int invite(CommandSourceStack src, String name, int level) {
        ServerPlayer actor = src.getPlayer();
        if (actor == null) return 0;
        UUID id = resolveId(src, name);
        if (id == null) return send(actor, "\u00a7cPlayer not found: " + name);
        send(actor, BankManager.invite(actor, id, name, level));
        // rc.3: BankManager.invite notifies the invitee directly (covers GUI invites too).
        return 1;
    }

    private static int accept(CommandSourceStack src, String inviterName) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        BankManager.AcceptResult r = BankManager.accept(p, inviterName);
        send(p, r.message());
        int ex = r.excessChests();
        while (ex > 0) {
            int n = Math.min(64, ex);
            ItemStack chests = new ItemStack(Items.CHEST, n);
            if (!p.getInventory().add(chests)) p.drop(chests, false);
            ex -= n;
        }
        return r.ok() ? 1 : 0;
    }

    private static int upgrade(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return send(p, "\u00a7cYou don't belong to a bank.");
        if (bank.levelOf(p.getUUID()) < BankManager.MASTER) return send(p, "\u00a7cOnly Bank Masters or the Owner can add upgrades.");
        ItemStack hand = p.getMainHandItem();
        if (hand.getItem() != Items.CHEST) return send(p, "\u00a7cHold a stack of chests, then run \u00a7e/bank upgrade\u00a7c.");
        int room = VaultCapacity.MAX_UPGRADES - bank.upgradeCount;
        if (room <= 0) return send(p, "\u00a7cThis bank is already at the 64-upgrade cap.");
        int add = Math.min(room, hand.getCount());
        hand.shrink(add);
        bank.upgradeCount += add;
        BankManager.save(bank);
        return send(p, String.format("\u00a7aAdded %d upgrade(s) \u2014 now %d/64, capacity %,d items.",
                add, bank.upgradeCount, VaultCapacity.capacityFor(bank.upgradeCount)));
    }

    private static int withdraw(CommandSourceStack src, int count, String itemArg) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return send(p, "\u00a7cYou don't belong to a bank.");
        if (bank.levelOf(p.getUUID()) < BankManager.MEMBER) return send(p, "\u00a7cDeposit-only members can't withdraw.");
        String arg = itemArg.trim();
        // Component-bearing stacks live under "id#hash" special keys (enchanted/trimmed gear).
        // Accept the key directly, and fall back to a UNIQUE special variant when the plain id
        // has no plain-bucket stock. Additive fix 2026-07-02 (bot + human ergonomics); the old
        // behavior for these inputs was only ever a failure message.
        if (arg.contains("#")) {
            return withdrawSpecial(p, bank, arg, count);
        }
        Identifier id = parseId(arg);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return send(p, "\u00a7cUnknown item: " + itemArg);
        String key = id.toString();
        long taken = BankManager.withdraw(bank, key, count);
        if (taken <= 0) {
            java.util.List<String> variants = new java.util.ArrayList<>();
            for (String k : bank.special.keySet()) {
                if (k.startsWith(key + "#")) variants.add(k);
            }
            if (variants.size() == 1) return withdrawSpecial(p, bank, variants.get(0), count);
            if (variants.size() > 1) {
                StringBuilder b = new StringBuilder("\u00a7eSeveral variants in the bank -- withdraw by key:");
                for (String k : variants) b.append("\n\u00a77  ").append(k).append(" x").append(bank.special.get(k).count);
                return send(p, b.toString());
            }
            return send(p, "\u00a7cNone of that item in the bank.");
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        int max = new ItemStack(item).getMaxStackSize();
        long left = taken;
        while (left > 0) {
            int n = (int) Math.min(max, left);
            ItemStack stack = new ItemStack(item, n);
            if (!p.getInventory().add(stack)) p.drop(stack, false);
            left -= n;
        }
        return send(p, String.format("\u00a7aWithdrew %,d %s.", taken, key));
    }

    /** Withdraw an exact special ("id#hash") stack with its components intact. */
    private static int withdrawSpecial(ServerPlayer p, Bank bank, String key, int count) {
        Bank.Special sp = bank.special.get(key);
        if (sp == null) return send(p, "\u00a7cNo such stack in the bank: " + key);
        ItemStack proto = StackStore.decode(sp.stack, p.level().registryAccess());
        if (proto == null || proto.isEmpty()) return send(p, "\u00a7cCannot reconstruct " + key + " -- not withdrawn.");
        long taken = BankManager.withdrawKey(bank, key, count);
        if (taken <= 0) return send(p, "\u00a7cNone of that item in the bank.");
        int max = proto.getMaxStackSize();
        long left = taken;
        while (left > 0) {
            int n = (int) Math.min(max, left);
            ItemStack out = proto.copy();
            out.setCount(n);
            if (!p.getInventory().add(out)) p.drop(out, false);
            left -= n;
        }
        return send(p, String.format("\u00a7aWithdrew %,d %s.", taken, key));
    }

    private static int withTarget(CommandSourceStack src, String name, MemberOp op) {
        ServerPlayer actor = src.getPlayer();
        if (actor == null) return 0;
        UUID id = resolveId(src, name);
        if (id == null) return send(actor, "\u00a7cPlayer not found: " + name);
        return send(actor, op.apply(actor, id, name));
    }

    private static UUID resolveId(CommandSourceStack src, String name) {
        ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static Identifier parseId(String s) {
        String ns = "minecraft", path = s;
        int i = s.indexOf(':');
        if (i >= 0) { ns = s.substring(0, i); path = s.substring(i + 1); }
        try { return Identifier.fromNamespaceAndPath(ns, path); }
        catch (Exception e) { return null; }
    }

    private static int send(ServerPlayer p, String msg) {
        if (msg != null && !msg.isEmpty()) p.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> inviters(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        ServerPlayer p = ctx.getSource().getPlayer();
        List<String> names = p == null ? List.of()
                : BankManager.pendingInvites(p.getUUID()).stream().map(i -> i.inviterName).distinct().toList();
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> players(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        List<String> names = ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                .map(pl -> pl.getGameProfile().name()).toList();
        return SharedSuggestionProvider.suggest(names, builder);
    }

    /** Op-only test utility: deposits 1 of every cataloged item plus every NBT variant the
     *  registries define -- enchanted books (every enchantment x every level), potions in all
     *  four carriers (potion/splash/lingering/tipped arrow) for every potion, and ominous
     *  bottles I-V. Registry-driven: new game content needs no mod change. Bumps the bank to
     *  max upgrades first so everything fits. */
    private static int fillAll(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        Bank bank = BankManager.getOrCreate(p);
        bank.upgradeCount = VaultCapacity.MAX_UPGRADES;
        BankManager.save(bank);
        RegistryAccess ra = src.getServer().registryAccess();
        int plain = 0, books = 0, potions = 0, other = 0;
        com.kishku7.bankvault.vault.Keywords.pollConfig();
        // carrier items are only obtainable WITH potion contents -- plain forms are uncraftable
        java.util.Set<String> componentOnly = java.util.Set.of("minecraft:potion", "minecraft:splash_potion",
                "minecraft:lingering_potion", "minecraft:tipped_arrow");
        // rc.5 (Kishku7): GENERIC -- walk the live item registry, not a curated list, so every
        // loaded mod's items are included (mods we don't yet know about included).
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier rid = BuiltInRegistries.ITEM.getKey(item);
            if (componentOnly.contains(rid.toString())) continue;
            if (com.kishku7.bankvault.vault.Keywords.itemHasAny(rid.toString(), java.util.List.of("creativeOnly", "technical"))) continue; // skip creative-only/technical ids (barrier, spawn eggs, test/void/command blocks, etc.)
            ItemStack s = new ItemStack(item);
            if (s.isEmpty() || BankManager.hasExact(bank, s, ra)) continue;
            if (BankManager.depositStack(bank, s, ra) > 0) plain++;
        }
        /* [[[cog
        import compat_core
        compat_core.emit_fillall_variants(cog, ver)
        ]]] */
        var enchants = ra.lookupOrThrow(Registries.ENCHANTMENT);
        for (var holder : enchants.listElements().toList()) {
            int max = holder.value().getMaxLevel();
            for (int lvl = 1; lvl <= max; lvl++) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mut.set(holder, lvl);
                book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
                if (!BankManager.hasExact(bank, book, ra) && BankManager.depositStack(bank, book, ra) > 0) books++;
            }
        }
        var pots = ra.lookupOrThrow(Registries.POTION);
        for (var holder : pots.listElements().toList()) {
            boolean hasEffects = !holder.value().getEffects().isEmpty();
            for (Item base : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
                if (base == Items.TIPPED_ARROW && !hasEffects) continue; // no-effect tipped arrows aren't survival
                ItemStack s = PotionContents.createItemStack(base, holder);
                if (!BankManager.hasExact(bank, s, ra) && BankManager.depositStack(bank, s, ra) > 0) potions++;
            }
        }
        for (int amp = 0; amp < 5; amp++) {
            ItemStack s = new ItemStack(Items.OMINOUS_BOTTLE);
s.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, new net.minecraft.world.item.component.OminousBottleAmplifier(amp));
            if (!BankManager.hasExact(bank, s, ra) && BankManager.depositStack(bank, s, ra) > 0) other++;
        }
        /* [[[end]]] */
        final int fp = plain, fb = books, fpo = potions, fo = other;
        p.sendSystemMessage(Component.literal("\u00A76[Bank Vault]\u00A7r added (missing only): " + fp + " items, "
                + fb + " enchanted books, " + fpo + " potions/arrows, " + fo + " ominous bottles. Upgrades set to max."));
        return 1;
    }

    /** Op-only test utility (rc.5, Kishku7): wipe EVERYTHING out of your bank -- plain and special
     *  (NBT) stacks both. Capacity/upgrades are untouched. */
    private static int clearAll(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        Bank bank = BankManager.getOrCreate(p);
        long total = bank.totalItems();
        int unique = bank.uniqueItems();
        bank.items.clear();
        bank.special.clear();
        BankManager.save(bank);
        com.kishku7.bankvault.net.ModNetworking.sendSync(p, bank);
        p.sendSystemMessage(Component.literal(String.format("\u00a76[Bank Vault]\u00a7r cleared %,d items (%d unique).", total, unique)));
        return 1;
    }

    /** Re-reads categories.json + sort_family.json + sort_type.json from disk and drops the old
     *  cache. In singleplayer the client shares the JVM, so tabs/sorting update on next screen
     *  rebuild -- no relog. (On a dedicated server this only reloads the server side.) */
    private static int reloadCatalog(CommandSourceStack src) {
        Catalog.reload();
        int tabs = Catalog.tabs().size();
        int items = Catalog.itemIds().size();
        src.sendSystemMessage(Component.literal("\u00A76[Bank Vault]\u00A7r catalog reloaded: " + tabs + " tabs, " + items + " items."));
        return 1;
    }
}
