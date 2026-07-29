package com.kishku7.bankvault.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.platform.Platform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads/saves banks, the player->bank index, and pending invites. The JSON files under
 * {@code config/bankvault/} are the source of truth (Ender-Chest model -- independent of the structure).
 */
public final class BankManager {

    public static final int OWNER = 4, MASTER = 3, MEMBER = 2, DEPOSIT = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path root, banksDir, indexFile, invitesFile;
    private static Map<String, String> index;        // playerUUID -> bankId
    private static Map<String, List<Invite>> invites;  // inviteeUUID -> invites, oldest..newest (rc.3: multi-invite)

    private BankManager() {}

    public static class Invite {
        public String bankId, inviterName, inviterUuid;
        public int level;
        public long sentAt;
    }

    public record AcceptResult(boolean ok, String message, int excessChests) {}

    private static synchronized void ensure() {
        if (root != null) return;
        root = Platform.configDir().resolve("bankvault");
        banksDir = root.resolve("banks");
        indexFile = root.resolve("index.json");
        invitesFile = root.resolve("invites.json");
        try { Files.createDirectories(banksDir); } catch (IOException e) { BankVault.LOGGER.error("[Bank Vault] mkdir failed", e); }
        index = loadMap(indexFile, new TypeToken<Map<String, String>>() {}.getType());
        invites = loadInvites();
    }

    private static <T> Map<String, T> loadMap(Path file, java.lang.reflect.Type type) {
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Map<String, T> m = GSON.fromJson(r, type);
                if (m != null) return m;
            } catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] load failed {}", file, e); }
        }
        return new HashMap<>();
    }

    /** rc.3: invites are now a LIST per invitee. Pre-rc.3 files stored a single object -- migrate. */
    private static Map<String, List<Invite>> loadInvites() {
        Map<String, List<Invite>> out = new HashMap<>();
        if (!Files.exists(invitesFile)) return out;
        try (Reader r = Files.newBufferedReader(invitesFile)) {
            com.google.gson.JsonObject rootObj = GSON.fromJson(r, com.google.gson.JsonObject.class);
            if (rootObj == null) return out;
            for (Map.Entry<String, com.google.gson.JsonElement> e : rootObj.entrySet()) {
                List<Invite> list = new ArrayList<>();
                if (e.getValue().isJsonArray()) {
                    for (com.google.gson.JsonElement el : e.getValue().getAsJsonArray())
                        list.add(GSON.fromJson(el, Invite.class));
                } else if (e.getValue().isJsonObject()) {
                    list.add(GSON.fromJson(e.getValue(), Invite.class));   // old single-invite format
                }
                if (!list.isEmpty()) out.put(e.getKey(), list);
            }
        } catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] load failed {}", invitesFile, e); }
        return out;
    }

    private static void writeJson(Path file, Object obj) {
        try (Writer w = Files.newBufferedWriter(file)) { GSON.toJson(obj, w); }
        catch (IOException e) { BankVault.LOGGER.error("[Bank Vault] save failed {}", file, e); }
    }

    private static Bank loadBank(String bankId) {
        Path p = banksDir.resolve(bankId + ".json");
        if (!Files.exists(p)) return null;
        try (Reader r = Files.newBufferedReader(p)) { return GSON.fromJson(r, Bank.class); }
        catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] bank load failed {}", bankId, e); return null; }
    }

    public static synchronized void save(Bank bank) {
        ensure();
        writeJson(banksDir.resolve(bank.bankId + ".json"), bank);
    }

    private static void deleteBank(String bankId) {
        try { Files.deleteIfExists(banksDir.resolve(bankId + ".json")); }
        catch (IOException e) { BankVault.LOGGER.error("[Bank Vault] bank delete failed {}", bankId, e); }
    }

    // -- core lookup ------------------------------------------------------------

    public static synchronized Bank getOrCreate(ServerPlayer player) {
        ensure();
        String pid = player.getUUID().toString();
        String bid = index.get(pid);
        Bank bank = bid != null ? loadBank(bid) : null;
        if (bank == null) {
            bank = new Bank();
            bank.bankId = UUID.randomUUID().toString();
            bank.createdAt = System.currentTimeMillis();
            bank.members.add(new Bank.Member(pid, player.getGameProfile().name(), OWNER, bank.createdAt));
            index.put(pid, bank.bankId);
            save(bank);
            writeJson(indexFile, index);
            BankVault.LOGGER.info("[Bank Vault] created bank {} for {}", bank.bankId, player.getGameProfile().name());
        }
        return bank;
    }

    /** True if the bank already holds this exact stack (plain id, or byte-identical prototype). */
    public static synchronized boolean hasExact(Bank bank, ItemStack stack, RegistryAccess ra) {
        ensure();
        if (StackStore.isPlain(stack)) return bank.items.containsKey(StackStore.idOf(stack));
        com.google.gson.JsonElement js = StackStore.encode(stack, ra);
        if (js == null) return false;
        String base = StackStore.specialKey(stack, js);
        String key = base;
        Bank.Special sp = bank.special.get(key);
        int probe = 0;
        while (sp != null) {
            if (js.equals(sp.stack)) return true;
            key = base + "~" + (++probe);
            sp = bank.special.get(key);
        }
        return false;
    }

    public static synchronized Bank lookup(UUID playerId) {
        ensure();
        String bid = index.get(playerId.toString());
        return bid == null ? null : loadBank(bid);
    }

    // -- item storage ---------------------------------------------------------

    public static synchronized long deposit(Bank bank, String itemId, long count) {
        ensure();
        long room = VaultCapacity.capacityFor(bank.upgradeCount) - bank.totalItems();
        if (room <= 0 || count <= 0) return 0;
        long add = Math.min(room, count);
        bank.items.merge(itemId, add, Long::sum);
        save(bank);
        return add;
    }

    public static synchronized long withdraw(Bank bank, String itemId, long count) {
        ensure();
        Long have = bank.items.get(itemId);
        if (have == null || count <= 0) return 0;
        long take = Math.min(have, count);
        if (have - take <= 0) bank.items.remove(itemId);
        else bank.items.put(itemId, have - take);
        save(bank);
        return take;
    }


    /** Deposit a stack (NBT-aware). Returns the amount actually stored. */
    public static synchronized long depositStack(Bank bank, ItemStack stack, RegistryAccess ra) {
        ensure();
        long room = VaultCapacity.capacityFor(bank.upgradeCount) - bank.totalItems();
        if (room <= 0 || stack.isEmpty()) return 0;
        long add = Math.min(room, stack.getCount());
        if (StackStore.isPlain(stack)) {
            bank.items.merge(StackStore.idOf(stack), add, Long::sum);
        } else {
            com.google.gson.JsonElement js = StackStore.encode(stack, ra);
            if (js == null) {
                // NEVER flatten a component-bearing stack into the plain bucket -- that strips
                // its NBT on withdrawal. Reject instead; the stack stays where it was.
                BankVault.LOGGER.warn("[Bank Vault] refusing deposit: cannot serialize {}", StackStore.idOf(stack));
                return 0;
            }
            // Exact-match merge: counts combine ONLY when the stored prototype is byte-identical.
            // On a key collision with different JSON, linear-probe to "key~1", "key~2", ...
            String base = StackStore.specialKey(stack, js);
            String key = base;
            Bank.Special sp = bank.special.get(key);
            int probe = 0;
            while (sp != null && !js.equals(sp.stack)) {
                key = base + "~" + (++probe);
                sp = bank.special.get(key);
            }
            if (sp == null) bank.special.put(key, new Bank.Special(js, add)); else sp.count += add;
        }
        save(bank);
        return add;
    }

    /** Withdraw by key (plain id, or "id#hash" for an NBT stack). Returns the amount removed. */
    public static synchronized long withdrawKey(Bank bank, String key, long count) {
        ensure();
        if (key.contains("#")) {
            Bank.Special sp = bank.special.get(key);
            if (sp == null || count <= 0) return 0;
            long take = Math.min(sp.count, count);
            sp.count -= take;
            if (sp.count <= 0) bank.special.remove(key);
            save(bank);
            return take;
        }
        return withdraw(bank, key, count);
    }

    // -- groups / sharing -------------------------------------------------------

    /** Returns a feedback message. Inviter must be Master+; level is clamped to what they may grant. */
    public static synchronized String invite(ServerPlayer inviter, UUID targetId, String targetName, int level) {
        ensure();
        Bank bank = getOrCreate(inviter);
        int my = bank.levelOf(inviter.getUUID());
        if (my < MASTER) return "\u00a7cYou must be a Bank Master or Owner to invite.";
        int maxGrant = (my == OWNER) ? MASTER : MEMBER;
        if (level < DEPOSIT) level = DEPOSIT;
        if (level > maxGrant) level = maxGrant;
        if (bank.member(targetId) != null) return "\u00a7cThat player is already in your bank.";
        Invite inv = new Invite();
        inv.bankId = bank.bankId; inv.level = level;
        inv.inviterUuid = inviter.getUUID().toString();
        inv.inviterName = inviter.getGameProfile().name();
        inv.sentAt = System.currentTimeMillis();
        List<Invite> list = invites.computeIfAbsent(targetId.toString(), k -> new ArrayList<>());
        list.removeIf(i -> inv.inviterUuid.equals(i.inviterUuid));   // re-invite: replace + becomes newest
        list.add(inv);                                               // newest = end of list
        writeJson(invitesFile, invites);

        // rc.3: the invitee hears about it in chat, with instructions (GUI + /bank invite both land here)
        MinecraftServer server = inviter.level().getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayer(targetId);
        if (target != null) {
            String how = list.size() > 1
                    ? "\u00a7e/bank accept " + inv.inviterName + "\u00a7r (\u00a7e/bank accept\u00a7r takes the newest)"
                    : "\u00a7e/bank accept\u00a7r";
            // rc.4 (Kishku7): no rank disclosure to the invitee -- only Masters+ see rank info
            target.sendSystemMessage(Component.literal(
                    "\u00a76[Bank Vault]\u00a7r " + inv.inviterName + " invited you to share their bank vault! "
                    + how + ", \u00a7e/bank decline\u00a7r, or open any Bank Vault to respond."));
        }
        return "\u00a7aInvited " + targetName + " as " + levelName(level) + ". They run \u00a7e/bank accept\u00a7a.";
    }

    /** Most recent pending invite, or null. */
    public static synchronized Invite pendingInvite(UUID invitee) {
        ensure();
        List<Invite> l = invites.get(invitee.toString());
        return (l == null || l.isEmpty()) ? null : l.get(l.size() - 1);
    }

    /** All pending invites, oldest..newest (immutable copy). */
    public static synchronized List<Invite> pendingInvites(UUID invitee) {
        ensure();
        List<Invite> l = invites.get(invitee.toString());
        return l == null ? List.of() : List.copyOf(l);
    }

    /** Accept the most recent pending invite. */
    public static synchronized AcceptResult accept(ServerPlayer invitee) { return accept(invitee, null); }

    /** Merges the invitee's (solo) bank into the inviter's group bank.
     *  rc.3: inviterName selects a specific invite; null = the MOST RECENT one. */
    public static synchronized AcceptResult accept(ServerPlayer invitee, String inviterName) {
        ensure();
        String key = invitee.getUUID().toString();
        List<Invite> pending = invites.get(key);
        if (pending == null || pending.isEmpty())
            return new AcceptResult(false, "\u00a7cYou have no pending invite.", 0);
        Invite inv = null;
        if (inviterName == null) {
            inv = pending.get(pending.size() - 1);                      // newest
        } else {
            for (int i = pending.size() - 1; i >= 0; i--)               // newest match wins
                if (pending.get(i).inviterName.equalsIgnoreCase(inviterName)) { inv = pending.get(i); break; }
            if (inv == null) return new AcceptResult(false, "\u00a7cNo pending invite from " + inviterName + ".", 0);
        }
        Bank group = loadBank(inv.bankId);
        if (group == null) {
            pending.remove(inv);
            if (pending.isEmpty()) invites.remove(key);
            writeJson(invitesFile, invites);
            return new AcceptResult(false, "\u00a7cThat bank no longer exists.", 0);
        }

        Bank own = lookup(invitee.getUUID());
        if (own != null && own.members.size() > 1)
            return new AcceptResult(false, "\u00a7cLeave your current group first (\u00a7e/bank leave\u00a7c).", 0);

        int excess = 0;
        if (own != null && !own.bankId.equals(group.bankId)) {
            for (Map.Entry<String, Long> e : own.items.entrySet())
                group.items.merge(e.getKey(), e.getValue(), Long::sum);
            // v1.1 fix: special (NBT) stacks were silently dropped on merge. Same probe scheme as
            // depositStack: merge on byte-identical stored JSON, else walk key, key~1, key~2...
            for (Map.Entry<String, Bank.Special> e : own.special.entrySet()) {
                String base = e.getKey().contains("~") ? e.getKey().substring(0, e.getKey().indexOf('~')) : e.getKey();
                String cand = base;
                for (int n = 1; ; cand = base + "~" + n++) {
                    Bank.Special exist = group.special.get(cand);
                    if (exist == null) { group.special.put(cand, e.getValue()); break; }
                    if (exist.stack.equals(e.getValue().stack)) { exist.count += e.getValue().count; break; }
                }
            }
            int combined = group.upgradeCount + own.upgradeCount;
            group.upgradeCount = Math.min(VaultCapacity.MAX_UPGRADES, combined);
            excess = Math.max(0, combined - VaultCapacity.MAX_UPGRADES);
            deleteBank(own.bankId);
        }
        group.members.add(new Bank.Member(invitee.getUUID().toString(),
                invitee.getGameProfile().name(), inv.level, System.currentTimeMillis()));
        index.put(invitee.getUUID().toString(), group.bankId);
        pending.remove(inv);                                            // others stay pending
        if (pending.isEmpty()) invites.remove(key);
        save(group); writeJson(indexFile, index); writeJson(invitesFile, invites);
        // rc.4 (Kishku7): rank named only when the new member is Master+ -- others just join
        return new AcceptResult(true,
                "\u00a7aJoined " + inv.inviterName + "'s bank."
                        + (inv.level >= MASTER ? " You are a " + levelName(inv.level) + "." : "")
                        + (excess > 0 ? " \u00a77(" + excess + " surplus chests returned.)" : ""), excess);
    }

    /** Decline the most recent pending invite. */
    public static synchronized String decline(ServerPlayer invitee) { return decline(invitee, null); }

    /** rc.3: inviterName declines a specific invite; null = the MOST RECENT one. */
    public static synchronized String decline(ServerPlayer invitee, String inviterName) {
        ensure();
        String key = invitee.getUUID().toString();
        List<Invite> pending = invites.get(key);
        if (pending == null || pending.isEmpty()) return "\u00a7cYou have no pending invite.";
        Invite inv = null;
        if (inviterName == null) {
            inv = pending.get(pending.size() - 1);
        } else {
            for (int i = pending.size() - 1; i >= 0; i--)
                if (pending.get(i).inviterName.equalsIgnoreCase(inviterName)) { inv = pending.get(i); break; }
            if (inv == null) return "\u00a7cNo pending invite from " + inviterName + ".";
        }
        pending.remove(inv);
        if (pending.isEmpty()) invites.remove(key);
        writeJson(invitesFile, invites);
        return "\u00a77Invite from " + inv.inviterName + " declined.";
    }

    /** Member leaves; owner triggers succession; last member out deletes the bank. */
    public static synchronized String leave(ServerPlayer player) {
        ensure();
        Bank bank = lookup(player.getUUID());
        if (bank == null) return "\u00a7cYou don't belong to a bank.";
        Bank.Member me = bank.member(player.getUUID());
        boolean wasOwner = me != null && me.level == OWNER;
        bank.members.removeIf(m -> m.uuid.equals(player.getUUID().toString()));
        index.remove(player.getUUID().toString());

        if (bank.members.isEmpty()) {
            deleteBank(bank.bankId);
        } else {
            Bank.Member heir = null;
            if (wasOwner) {
                heir = succession(bank);
                if (heir != null) heir.level = OWNER;
            }
            save(bank);
            // rc.4 (Kishku7): Owners/Masters are told when someone leaves the share
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                String note = "\u00a76[Bank Vault]\u00a77 " + player.getGameProfile().name() + " left the bank."
                        + (heir != null ? " Ownership passed to " + heir.name + "." : "");
                for (Bank.Member m : bank.members) {
                    if (m.level < MASTER) continue;
                    try {
                        ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(m.uuid));
                        if (online != null) online.sendSystemMessage(Component.literal(note));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        writeJson(indexFile, index);
        return "\u00a77You left the bank." + (wasOwner && !bank.members.isEmpty() ? " Ownership passed on." : "");
    }

    private static Bank.Member succession(Bank bank) {
        Bank.Member best = null;
        for (Bank.Member m : bank.members) {  // members are kept in join order
            if (best == null || m.level > best.level) best = m;
        }
        return best;
    }

    public static synchronized String setLevel(ServerPlayer actor, UUID targetId, String targetName, int newLevel) {
        ensure();
        Bank bank = lookup(actor.getUUID());
        if (bank == null) return "\u00a7cYou don't belong to a bank.";
        int my = bank.levelOf(actor.getUUID());
        if (my < MASTER) return "\u00a7cYou lack permission.";
        if (targetId.equals(actor.getUUID())) return "\u00a7cYou can't change your own level.";
        Bank.Member t = bank.member(targetId);
        if (t == null) return "\u00a7cThat player isn't in your bank.";
        if (t.level >= my) return "\u00a7cYou can't modify someone at your level or above.";
        int maxGrant = (my == OWNER) ? MASTER : MEMBER;
        if (newLevel < DEPOSIT) newLevel = DEPOSIT;
        if (newLevel > maxGrant) newLevel = maxGrant;
        int oldLevel = t.level;
        t.level = newLevel;
        save(bank);

        // rc.3 (Kishku7): promotions are announced to every online member; demotions only to
        // online Owners/Masters.
        MinecraftServer server = actor.level().getServer();
        if (server != null && newLevel != oldLevel) {
            boolean up = newLevel > oldLevel;
            String note = up
                    ? "\u00a76[Bank Vault]\u00a7a " + targetName + " was promoted to " + levelName(newLevel)
                      + " by " + actor.getGameProfile().name() + "."
                    : "\u00a76[Bank Vault]\u00a7c " + targetName + " was lowered to " + levelName(newLevel)
                      + " by " + actor.getGameProfile().name() + ".";
            for (Bank.Member m : bank.members) {
                if (!up && m.level < MASTER) continue;
                try {
                    ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(m.uuid));
                    if (online != null) online.sendSystemMessage(Component.literal(note));
                } catch (IllegalArgumentException ignored) {}
            }
            return "";   // broadcast already covers the actor (Master+) -- no duplicate line
        }
        return "\u00a7a" + targetName + " is now " + levelName(newLevel) + ".";
    }

    public static synchronized String kick(ServerPlayer actor, UUID targetId, String targetName) {
        ensure();
        Bank bank = lookup(actor.getUUID());
        if (bank == null) return "\u00a7cYou don't belong to a bank.";
        int my = bank.levelOf(actor.getUUID());
        if (my < MASTER) return "\u00a7cYou lack permission.";
        Bank.Member t = bank.member(targetId);
        if (t == null) return "\u00a7cThat player isn't in your bank.";
        if (t.level >= my) return "\u00a7cYou can't kick someone at your level or above.";
        bank.members.removeIf(m -> m.uuid.equals(targetId.toString()));
        index.remove(targetId.toString());
        save(bank); writeJson(indexFile, index);
        return "\u00a7a" + targetName + " was removed from the bank.";
    }

    public static synchronized String transfer(ServerPlayer owner, UUID targetId, String targetName) {
        ensure();
        Bank bank = lookup(owner.getUUID());
        if (bank == null) return "\u00a7cYou don't belong to a bank.";
        if (bank.levelOf(owner.getUUID()) != OWNER) return "\u00a7cOnly the Owner can transfer ownership.";
        Bank.Member t = bank.member(targetId);
        if (t == null) return "\u00a7cThat player isn't in your bank.";
        t.level = OWNER;
        bank.member(owner.getUUID()).level = MASTER;
        save(bank);
        return "\u00a7aOwnership transferred to " + targetName + ". You are now a Bank Master.";
    }

    public static synchronized String disband(ServerPlayer owner) {
        ensure();
        Bank bank = lookup(owner.getUUID());
        if (bank == null) return "\u00a7cYou don't belong to a bank.";
        if (bank.levelOf(owner.getUUID()) != OWNER) return "\u00a7cOnly the Owner can disband.";
        for (Bank.Member m : bank.members) index.remove(m.uuid);
        deleteBank(bank.bankId);
        writeJson(indexFile, index);
        return "\u00a7cBank disbanded \u2014 all stored items are gone.";
    }

    public static String levelName(int level) {
        return switch (level) {
            case OWNER -> "Owner";
            case MASTER -> "Bank Master";
            case MEMBER -> "Full Member";
            default -> "Deposit Only";
        };
    }
}
