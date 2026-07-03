package com.kishku7.bankvault.api;

import com.kishku7.bankvault.vault.Bank;
import com.kishku7.bankvault.vault.BankManager;
import com.kishku7.bankvault.vault.StackStore;
import com.kishku7.bankvault.vault.VaultCapacity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable vault surface for automation clients (the M1 mod and friends).
 *
 * Every method returns ONE plain-ASCII line, pipe-delimited, no color codes:
 *   BV|&lt;op&gt;|OK|...     on success
 *   BV|&lt;op&gt;|ERR|reason on failure
 * Keys are the bank's native keys: plain item ids ("minecraft:cobblestone") or
 * special component-bearing keys ("minecraft:chainmail_chestplate#a1b2c3d4").
 * All methods expect the SERVER thread and the calling player's own bank.
 * Additive-only: no payloads or storage formats are touched.
 */
public final class VaultApi {

    /** Page size for list/find output -- keeps each response a single manageable line. */
    public static final int PAGE = 50;

    private VaultApi() {}

    // ---- queries ----

    public static String snapshot(ServerPlayer p) {
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return err("snapshot", "no-bank");
        StringBuilder sb = head("snapshot");
        sb.append("|total=").append(bank.totalItems())
          .append("|unique=").append(bank.uniqueItems())
          .append("|cap=").append(VaultCapacity.capacityFor(bank.upgradeCount))
          .append("|upgrades=").append(bank.upgradeCount).append('/').append(VaultCapacity.MAX_UPGRADES)
          .append("|members=");
        boolean first = true;
        for (Bank.Member m : bank.members) {
            if (!first) sb.append(',');
            sb.append(m.name).append(':').append(m.level);
            first = false;
        }
        return sb.toString();
    }

    public static String list(ServerPlayer p, int page) {
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return err("list", "no-bank");
        List<String> entries = new ArrayList<>(bank.items.size() + bank.special.size());
        for (Map.Entry<String, Long> e : bank.items.entrySet()) entries.add(e.getKey() + "=" + e.getValue());
        for (Map.Entry<String, Bank.Special> e : bank.special.entrySet()) entries.add(e.getKey() + "=" + e.getValue().count);
        int pages = Math.max(1, (entries.size() + PAGE - 1) / PAGE);
        int pg = Math.min(Math.max(1, page), pages);
        StringBuilder sb = head("list");
        sb.append("|page=").append(pg).append('/').append(pages).append('|');
        int from = (pg - 1) * PAGE, to = Math.min(entries.size(), from + PAGE);
        for (int i = from; i < to; i++) {
            if (i > from) sb.append(';');
            sb.append(entries.get(i));
        }
        return sb.toString();
    }

    public static String count(ServerPlayer p, String key) {
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return err("count", "no-bank");
        String k = key.trim();
        StringBuilder sb = head("count");
        if (k.indexOf('#') >= 0) {
            Bank.Special sp = bank.special.get(k);
            sb.append('|').append(k).append('=').append(sp == null ? 0 : sp.count);
            return sb.toString();
        }
        Long plain = bank.items.get(k);
        sb.append('|').append(k).append('=').append(plain == null ? 0 : plain.longValue());
        StringBuilder vars = new StringBuilder();
        for (Map.Entry<String, Bank.Special> e : bank.special.entrySet()) {
            if (e.getKey().startsWith(k + "#")) {
                if (vars.length() > 0) vars.append(';');
                vars.append(e.getKey()).append('=').append(e.getValue().count);
            }
        }
        if (vars.length() > 0) sb.append("|variants=").append(vars);
        return sb.toString();
    }

    public static String find(ServerPlayer p, String query) {
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return err("find", "no-bank");
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder sb = head("find");
        sb.append('|');
        int shown = 0, matched = 0;
        for (Map.Entry<String, Long> e : bank.items.entrySet()) {
            if (e.getKey().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                matched++;
                if (shown < PAGE) { if (shown > 0) sb.append(';'); sb.append(e.getKey()).append('=').append(e.getValue()); shown++; }
            }
        }
        for (Map.Entry<String, Bank.Special> e : bank.special.entrySet()) {
            if (e.getKey().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                matched++;
                if (shown < PAGE) { if (shown > 0) sb.append(';'); sb.append(e.getKey()).append('=').append(e.getValue().count); shown++; }
            }
        }
        if (matched > shown) sb.append("|more=").append(matched - shown);
        return sb.toString();
    }

    // ---- mutations ----

    public static String withdraw(ServerPlayer p, String key, int count) {
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return err("withdraw", "no-bank");
        if (bank.levelOf(p.getUUID()) < BankManager.MEMBER) return err("withdraw", "deposit-only-member");
        String k = key.trim();
        if (k.indexOf('#') >= 0) return withdrawSpecial(p, bank, k, count);
        Identifier id = parseId(k);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return err("withdraw", "unknown-item:" + k);
        String plainKey = id.toString();
        long taken = BankManager.withdraw(bank, plainKey, count);
        if (taken <= 0) {
            List<String> variants = new ArrayList<>();
            for (String vk : bank.special.keySet()) if (vk.startsWith(plainKey + "#")) variants.add(vk);
            if (variants.size() == 1) return withdrawSpecial(p, bank, variants.get(0), count);
            if (variants.size() > 1) {
                StringBuilder sb = head("withdraw");
                sb.append("|AMBIG|");
                for (int i = 0; i < variants.size(); i++) {
                    if (i > 0) sb.append(';');
                    sb.append(variants.get(i)).append('=').append(bank.special.get(variants.get(i)).count);
                }
                return sb.toString();
            }
            return err("withdraw", "empty:" + plainKey);
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        deliver(p, new ItemStack(item), taken);
        syncQuiet(p, bank);
        return head("withdraw").append('|').append(plainKey).append("|taken=").append(taken).toString();
    }

    private static String withdrawSpecial(ServerPlayer p, Bank bank, String key, int count) {
        Bank.Special sp = bank.special.get(key);
        if (sp == null) return err("withdraw", "no-such-key:" + key);
        ItemStack proto = StackStore.decode(sp.stack, p.level().registryAccess());
        if (proto == null || proto.isEmpty()) return err("withdraw", "decode-failed:" + key);
        long taken = BankManager.withdrawKey(bank, key, count);
        if (taken <= 0) return err("withdraw", "empty:" + key);
        deliver(p, proto, taken);
        syncQuiet(p, bank);
        return head("withdraw").append('|').append(key).append("|taken=").append(taken).toString();
    }

    /** Deposit from the player's inventory: the held stack ("hand"), everything ("all"), or by plain id. */
    public static String deposit(ServerPlayer p, String what, int count) {
        Bank bank = BankManager.lookup(p.getUUID());
        if (bank == null) return err("deposit", "no-bank");
        if (bank.levelOf(p.getUUID()) < 1) return err("deposit", "not-a-member");
        String w = what.trim();
        long moved = 0;
        if ("hand".equalsIgnoreCase(w)) {
            ItemStack hand = p.getMainHandItem();
            if (hand.isEmpty()) return err("deposit", "empty-hand");
            ItemStack in = hand;
            if (count > 0 && count < hand.getCount()) in = hand.copyWithCount(count);
            long accepted = BankManager.depositStack(bank, in.copy(), p.level().registryAccess());
            if (accepted <= 0) return err("deposit", "vault-full-or-rejected");
            hand.shrink((int) Math.min(accepted, hand.getCount()));
            moved = accepted;
        } else {
            Identifier id = parseId(w);
            if (id == null) return err("deposit", "bad-id:" + w);
            String plainKey = id.toString();
            long want = count <= 0 ? Long.MAX_VALUE : count;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize() && moved < want; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !StackStore.isPlain(s) || !plainKey.equals(StackStore.idOf(s))) continue;
                int take = (int) Math.min(s.getCount(), want - moved);
                long accepted = BankManager.deposit(bank, plainKey, take);
                if (accepted <= 0) break;
                s.shrink((int) accepted);
                moved += accepted;
            }
            if (moved <= 0) return err("deposit", "none-moved:" + plainKey);
        }
        syncQuiet(p, bank);
        return head("deposit").append('|').append(w).append("|moved=").append(moved).toString();
    }

    // ---- helpers ----

    private static void deliver(ServerPlayer p, ItemStack proto, long taken) {
        int max = proto.getMaxStackSize();
        long left = taken;
        while (left > 0) {
            int n = (int) Math.min(max, left);
            ItemStack out = proto.copy();
            out.setCount(n);
            if (!p.getInventory().add(out)) p.drop(out, false);
            left -= n;
        }
    }

    private static void syncQuiet(ServerPlayer p, Bank bank) {
        try {
            com.kishku7.bankvault.net.ModNetworking.sendSync(p, bank);
        } catch (Throwable ignored) {
            // sync is best-effort; the command result line is the contract
        }
    }

    private static Identifier parseId(String s) {
        String ns = "minecraft", path = s;
        int i = s.indexOf(':');
        if (i >= 0) { ns = s.substring(0, i); path = s.substring(i + 1); }
        try { return Identifier.fromNamespaceAndPath(ns, path); }
        catch (Exception e) { return null; }
    }

    private static StringBuilder head(String op) {
        return new StringBuilder("BV|").append(op).append("|OK");
    }

    private static String err(String op, String reason) {
        return "BV|" + op + "|ERR|" + reason;
    }
}
