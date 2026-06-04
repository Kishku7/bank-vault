package com.kishku7.bankvault.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kishku7.bankvault.BankVault;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loads/saves banks, the player->bank index, and pending invites. The JSON files under
 * {@code config/bankvault/} are the source of truth (Ender-Chest model — independent of the structure).
 */
public final class BankManager {

    public static final int OWNER = 4, MASTER = 3, MEMBER = 2, DEPOSIT = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path root, banksDir, indexFile, invitesFile;
    private static Map<String, String> index;        // playerUUID -> bankId
    private static Map<String, Invite> invites;       // inviteeUUID -> invite

    private BankManager() {}

    public static class Invite {
        public String bankId, inviterName, inviterUuid;
        public int level;
        public long sentAt;
    }

    public record AcceptResult(boolean ok, String message, int excessChests) {}

    private static synchronized void ensure() {
        if (root != null) return;
        root = FabricLoader.getInstance().getConfigDir().resolve("bankvault");
        banksDir = root.resolve("banks");
        indexFile = root.resolve("index.json");
        invitesFile = root.resolve("invites.json");
        try { Files.createDirectories(banksDir); } catch (IOException e) { BankVault.LOGGER.error("[Bank Vault] mkdir failed", e); }
        index = loadMap(indexFile, new TypeToken<Map<String, String>>() {}.getType());
        invites = loadMap(invitesFile, new TypeToken<Map<String, Invite>>() {}.getType());
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

    // ── core lookup ────────────────────────────────────────────────────────────

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

    public static synchronized Bank lookup(UUID playerId) {
        ensure();
        String bid = index.get(playerId.toString());
        return bid == null ? null : loadBank(bid);
    }

    // ── item storage ─────────────────────────────────────────────────────────

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

    // ── groups / sharing ───────────────────────────────────────────────────────

    /** Returns a feedback message. Inviter must be Master+; level is clamped to what they may grant. */
    public static synchronized String invite(ServerPlayer inviter, UUID targetId, String targetName, int level) {
        ensure();
        Bank bank = getOrCreate(inviter);
        int my = bank.levelOf(inviter.getUUID());
        if (my < MASTER) return "§cYou must be a Bank Master or Owner to invite.";
        int maxGrant = (my == OWNER) ? MASTER : MEMBER;
        if (level < DEPOSIT) level = DEPOSIT;
        if (level > maxGrant) level = maxGrant;
        if (bank.member(targetId) != null) return "§cThat player is already in your bank.";
        Invite inv = new Invite();
        inv.bankId = bank.bankId; inv.level = level;
        inv.inviterUuid = inviter.getUUID().toString();
        inv.inviterName = inviter.getGameProfile().name();
        inv.sentAt = System.currentTimeMillis();
        invites.put(targetId.toString(), inv);
        writeJson(invitesFile, invites);
        return "§aInvited " + targetName + " as " + levelName(level) + ". They run §e/bank accept§a.";
    }

    public static synchronized Invite pendingInvite(UUID invitee) {
        ensure();
        return invites.get(invitee.toString());
    }

    /** Merges the invitee's (solo) bank into the inviter's group bank. */
    public static synchronized AcceptResult accept(ServerPlayer invitee) {
        ensure();
        Invite inv = invites.get(invitee.getUUID().toString());
        if (inv == null) return new AcceptResult(false, "§cYou have no pending invite.", 0);
        Bank group = loadBank(inv.bankId);
        if (group == null) { invites.remove(invitee.getUUID().toString()); writeJson(invitesFile, invites);
            return new AcceptResult(false, "§cThat bank no longer exists.", 0); }

        Bank own = lookup(invitee.getUUID());
        if (own != null && own.members.size() > 1)
            return new AcceptResult(false, "§cLeave your current group first (§e/bank leave§c).", 0);

        int excess = 0;
        if (own != null && !own.bankId.equals(group.bankId)) {
            for (Map.Entry<String, Long> e : own.items.entrySet())
                group.items.merge(e.getKey(), e.getValue(), Long::sum);
            int combined = group.upgradeCount + own.upgradeCount;
            group.upgradeCount = Math.min(VaultCapacity.MAX_UPGRADES, combined);
            excess = Math.max(0, combined - VaultCapacity.MAX_UPGRADES);
            deleteBank(own.bankId);
        }
        group.members.add(new Bank.Member(invitee.getUUID().toString(),
                invitee.getGameProfile().name(), inv.level, System.currentTimeMillis()));
        index.put(invitee.getUUID().toString(), group.bankId);
        invites.remove(invitee.getUUID().toString());
        save(group); writeJson(indexFile, index); writeJson(invitesFile, invites);
        return new AcceptResult(true,
                "§aJoined " + inv.inviterName + "'s bank as " + levelName(inv.level) + "."
                        + (excess > 0 ? " §7(" + excess + " surplus chests returned.)" : ""), excess);
    }

    public static synchronized String decline(ServerPlayer invitee) {
        ensure();
        if (invites.remove(invitee.getUUID().toString()) == null) return "§cYou have no pending invite.";
        writeJson(invitesFile, invites);
        return "§7Invite declined.";
    }

    /** Member leaves; owner triggers succession; last member out deletes the bank. */
    public static synchronized String leave(ServerPlayer player) {
        ensure();
        Bank bank = lookup(player.getUUID());
        if (bank == null) return "§cYou don't belong to a bank.";
        Bank.Member me = bank.member(player.getUUID());
        boolean wasOwner = me != null && me.level == OWNER;
        bank.members.removeIf(m -> m.uuid.equals(player.getUUID().toString()));
        index.remove(player.getUUID().toString());

        if (bank.members.isEmpty()) {
            deleteBank(bank.bankId);
        } else {
            if (wasOwner) {
                Bank.Member heir = succession(bank);
                if (heir != null) heir.level = OWNER;
            }
            save(bank);
        }
        writeJson(indexFile, index);
        return "§7You left the bank." + (wasOwner && !bank.members.isEmpty() ? " Ownership passed on." : "");
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
        if (bank == null) return "§cYou don't belong to a bank.";
        int my = bank.levelOf(actor.getUUID());
        if (my < MASTER) return "§cYou lack permission.";
        if (targetId.equals(actor.getUUID())) return "§cYou can't change your own level.";
        Bank.Member t = bank.member(targetId);
        if (t == null) return "§cThat player isn't in your bank.";
        if (t.level >= my) return "§cYou can't modify someone at your level or above.";
        int maxGrant = (my == OWNER) ? MASTER : MEMBER;
        if (newLevel < DEPOSIT) newLevel = DEPOSIT;
        if (newLevel > maxGrant) newLevel = maxGrant;
        t.level = newLevel;
        save(bank);
        return "§a" + targetName + " is now " + levelName(newLevel) + ".";
    }

    public static synchronized String kick(ServerPlayer actor, UUID targetId, String targetName) {
        ensure();
        Bank bank = lookup(actor.getUUID());
        if (bank == null) return "§cYou don't belong to a bank.";
        int my = bank.levelOf(actor.getUUID());
        if (my < MASTER) return "§cYou lack permission.";
        Bank.Member t = bank.member(targetId);
        if (t == null) return "§cThat player isn't in your bank.";
        if (t.level >= my) return "§cYou can't kick someone at your level or above.";
        bank.members.removeIf(m -> m.uuid.equals(targetId.toString()));
        index.remove(targetId.toString());
        save(bank); writeJson(indexFile, index);
        return "§a" + targetName + " was removed from the bank.";
    }

    public static synchronized String transfer(ServerPlayer owner, UUID targetId, String targetName) {
        ensure();
        Bank bank = lookup(owner.getUUID());
        if (bank == null) return "§cYou don't belong to a bank.";
        if (bank.levelOf(owner.getUUID()) != OWNER) return "§cOnly the Owner can transfer ownership.";
        Bank.Member t = bank.member(targetId);
        if (t == null) return "§cThat player isn't in your bank.";
        t.level = OWNER;
        bank.member(owner.getUUID()).level = MASTER;
        save(bank);
        return "§aOwnership transferred to " + targetName + ". You are now a Bank Master.";
    }

    public static synchronized String disband(ServerPlayer owner) {
        ensure();
        Bank bank = lookup(owner.getUUID());
        if (bank == null) return "§cYou don't belong to a bank.";
        if (bank.levelOf(owner.getUUID()) != OWNER) return "§cOnly the Owner can disband.";
        for (Bank.Member m : bank.members) index.remove(m.uuid);
        deleteBank(bank.bankId);
        writeJson(indexFile, index);
        return "§cBank disbanded — all stored items are gone.";
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
