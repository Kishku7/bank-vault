package com.kishku7.bankvault.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kishku7.bankvault.BankVault;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player UI memory (v1.2, Dave): last tab examined + last sort method per tab, persisted
 * server-side under {@code config/bankvault/user_settings/}. 27 bucket files -- {@code a.json}
 * .. {@code z.json} plus {@code other.json} -- chosen by the FIRST LETTER of the player's name
 * (non a-z falls into "other"). Records are keyed by UUID inside each bucket, so name changes
 * never lose data: on the next interaction the record MOVES to the new initial's bucket but the
 * UUID key (and everything stored under it) is preserved. All buckets load on server startup;
 * missing files are created empty. Writes are per-bucket and atomic (tmp + move).
 *
 * Record shape: { "<uuid>": { "name": "...", "lastTab": "kw:Wood",
 *                             "sorts": { "kw:Wood": "family", ... } } }
 * Sort values: "family" | "type" | "alpha" | "count_asc" | "count_desc".
 */
public final class UserSettings {

    /** One player's stored UI state. Public fields -- Gson-mapped 1:1 to the bucket JSON. */
    public static final class Rec {
        public String name = "";
        public String lastTab = "";
        public Map<String, String> sorts = new LinkedHashMap<>();
        public boolean showSections = false;
        public Map<String, List<String>> pins = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type BUCKET_TYPE = new TypeToken<LinkedHashMap<String, Rec>>() {}.getType();

    private static final Map<String, Map<String, Rec>> buckets = new HashMap<>(); // bucket -> uuid -> rec
    private static final Map<String, String> uuidBucket = new HashMap<>();        // uuid -> bucket
    private static boolean loaded = false;

    private UserSettings() {}

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("bankvault").resolve("user_settings");
    }

    private static List<String> allBuckets() {
        List<String> out = new ArrayList<>(27);
        for (char c = 'a'; c <= 'z'; c++) out.add(String.valueOf(c));
        out.add("other");
        return out;
    }

    /** Bucket for a player name: lowercase first letter when a-z, else "other". */
    private static String bucketFor(String name) {
        if (name == null || name.isEmpty()) return "other";
        char c = Character.toLowerCase(name.charAt(0));
        return (c >= 'a' && c <= 'z') ? String.valueOf(c) : "other";
    }

    /** Read all 27 bucket files (creating any that are missing) and build the UUID index.
     *  Called on server startup; safe to call again -- state is rebuilt from disk. */
    public static synchronized void loadAll() {
        buckets.clear();
        uuidBucket.clear();
        Path d = dir();
        try { Files.createDirectories(d); }
        catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] user_settings dir create failed", e); return; }
        int players = 0;
        for (String b : allBuckets()) {
            Path f = d.resolve(b + ".json");
            Map<String, Rec> m = null;
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    m = GSON.fromJson(r, BUCKET_TYPE);
                } catch (Exception e) {
                    BankVault.LOGGER.error("[Bank Vault] user_settings/{}.json load failed -- starting empty", b, e);
                }
            } else {
                try { Files.writeString(f, "{}", StandardCharsets.UTF_8); }
                catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] user_settings/{}.json create failed", b, e); }
            }
            if (m == null) m = new LinkedHashMap<>();
            for (Map.Entry<String, Rec> e : m.entrySet()) {
                if (e.getValue() == null) continue;
                if (e.getValue().sorts == null) e.getValue().sorts = new LinkedHashMap<>();
                if (e.getValue().pins == null) e.getValue().pins = new LinkedHashMap<>();
                uuidBucket.put(e.getKey(), b);
                players++;
            }
            buckets.put(b, m);
        }
        loaded = true;
        BankVault.LOGGER.info("[Bank Vault] user_settings loaded: 27 buckets, {} players", players);
    }

    /** Stored record for a player, or null when they have never interacted with a vault. */
    public static synchronized Rec get(UUID id) {
        if (!loaded) loadAll();
        String b = uuidBucket.get(id.toString());
        return b == null ? null : buckets.get(b).get(id.toString());
    }

    /** Locate-or-create the player's record, moving it between buckets after a rename
     *  (UUID key preserved). Returns the record; caller mutates then calls save(bucketOf). */
    private static Rec recFor(ServerPlayer player, boolean[] dirty) {
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().name();
        String b = bucketFor(name);
        String old = uuidBucket.get(uuid);
        Rec r = old == null ? null : buckets.get(old).get(uuid);
        if (r == null) { r = new Rec(); dirty[0] = true; }
        if (old != null && !old.equals(b)) {            // renamed across initials: move buckets
            buckets.get(old).remove(uuid);
            save(old);
            dirty[0] = true;
        }
        if (!name.equals(r.name)) { r.name = name; dirty[0] = true; }
        buckets.computeIfAbsent(b, k -> new LinkedHashMap<>()).put(uuid, r);
        uuidBucket.put(uuid, b);
        return r;
    }

    private static String bucketOf(ServerPlayer player) { return bucketFor(player.getGameProfile().name()); }

    /** Record a UI interaction: {@code lastTab} always updates; when {@code tab} and
     *  {@code sort} are both non-empty the per-tab sort memory updates too; {@code sections}
     *  ("on"/"off", empty = no change) flips the section-titles checkbox. Creates the record
     *  on a player's first interaction. */
    public static synchronized void update(ServerPlayer player, String lastTab, String tab, String sort,
                                           String sections) {
        if (!loaded) loadAll();
        boolean[] dirty = {false};
        Rec r = recFor(player, dirty);
        if (lastTab != null && !lastTab.isEmpty() && !lastTab.equals(r.lastTab)) { r.lastTab = lastTab; dirty[0] = true; }
        if (tab != null && !tab.isEmpty() && sort != null && !sort.isEmpty()
                && !sort.equals(r.sorts.get(tab))) { r.sorts.put(tab, sort); dirty[0] = true; }
        if (sections != null && !sections.isEmpty()) {
            boolean v = "on".equals(sections);
            if (v != r.showSections) { r.showSections = v; dirty[0] = true; }
        }
        if (dirty[0]) save(bucketOf(player));
    }

    /** Toggle a per-tab user pin (v1.2 Pin hot area). Capped at 54 pins per tab. */
    public static synchronized void togglePin(ServerPlayer player, String tab, String itemId) {
        if (!loaded) loadAll();
        if (tab == null || tab.isEmpty() || itemId == null || itemId.isEmpty()
                || tab.length() > 80 || itemId.length() > 256) return;
        boolean[] dirty = {false};
        Rec r = recFor(player, dirty);
        List<String> l = r.pins.computeIfAbsent(tab, k -> new ArrayList<>());
        if (!l.remove(itemId)) {
            if (l.size() >= 54) return;
            l.add(itemId);
        }
        if (l.isEmpty()) r.pins.remove(tab);
        save(bucketOf(player));
    }

    /** Atomic per-bucket write: serialize to a tmp file, then move over the live one. */
    private static void save(String bucket) {
        Path d = dir();
        Path f = d.resolve(bucket + ".json");
        Path tmp = d.resolve(bucket + ".json.tmp");
        try {
            Files.createDirectories(d);
            Files.writeString(tmp, GSON.toJson(buckets.getOrDefault(bucket, new LinkedHashMap<>()), BUCKET_TYPE),
                    StandardCharsets.UTF_8);
            try { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception atomicUnsupported) { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) {
            BankVault.LOGGER.error("[Bank Vault] user_settings/{}.json save failed", bucket, e);
        }
    }
}
