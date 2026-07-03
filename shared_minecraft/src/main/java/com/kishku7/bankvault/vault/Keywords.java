package com.kishku7.bankvault.vault;

import com.google.gson.Gson;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.platform.Platform;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** v1.2 item keyword table -- the descriptive layer alongside the category catalog.
 *  Every item carries 4-9 camelCase words (wood, weapon, hand2hand, earlyGame, mobDrop...).
 *  Generated per docs/keywords-rules.md. The config file {@code config/bankvault/keywords.json}
 *  wins; the bundled copy is the fallback. Re-read automatically when the config file's
 *  modification time changes (live-editable, same pattern as ButtonLayout). */
public final class Keywords {

    private static final Gson GSON = new Gson();
    private static Map<String, List<String>> itemWords;
    private static Map<String, Integer> wordCounts;
    private static long mtime = -1;

    private Keywords() {}

    /** Check the config file's mtime and (re)load when it changed. Call this ONCE per
     *  screen-open -- never from per-frame/per-item paths (a disk stat per call was the
     *  alpha.2 hover-lag bug). */
    public static synchronized void pollConfig() {
        Path cfg = Platform.configDir().resolve("bankvault").resolve("keywords.json");
        long m = -1;
        try { if (Files.exists(cfg)) m = Files.getLastModifiedTime(cfg).toMillis(); } catch (Exception ignored) {}
        if (itemWords != null && m == mtime) return;
        mtime = m;
        itemWords = new HashMap<>();
        wordCounts = new HashMap<>();
        loadedWordCounts = null;   // rc.6: recompute registry-filtered counts after reload
        if (m >= 0) {
            try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                parse(GSON.fromJson(r, JsonObject.class));
                BankVault.LOGGER.info("[Bank Vault] keywords from config: {} items, {} words",
                        itemWords.size(), wordCounts.size());
                return;
            } catch (Exception e) {
                BankVault.LOGGER.error("[Bank Vault] keywords.json config load failed, falling back to bundled", e);
                itemWords = new HashMap<>(); wordCounts = new HashMap<>();
            }
        }
        try (InputStream in = Keywords.class.getResourceAsStream("/data/bankvault/keywords.json")) {
            if (in == null) { BankVault.LOGGER.error("[Bank Vault] bundled keywords.json missing"); return; }
            parse(GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class));
            BankVault.LOGGER.info("[Bank Vault] keywords bundled: {} items, {} words",
                    itemWords.size(), wordCounts.size());
        } catch (Exception e) {
            BankVault.LOGGER.error("[Bank Vault] keywords.json load failed", e);
        }
    }

    private static void parse(JsonObject root) {
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("items").entrySet()) {
            List<String> ws = new ArrayList<>();
            e.getValue().getAsJsonArray().forEach(x -> ws.add(x.getAsString()));
            itemWords.put(e.getKey(), ws);
            for (String w : ws) wordCounts.merge(w, 1, Integer::sum);
        }
    }

    /** Fast path: load once if never loaded; no disk access afterwards. */
    private static void ensureLoaded() {
        if (itemWords == null) pollConfig();
    }

    /** Words for an item id (empty list when unknown). */
    public static synchronized List<String> wordsFor(String itemId) {
        ensureLoaded();
        return itemWords.getOrDefault(itemId, List.of());
    }

    /** True when the item carries at least one of the given words. */
    public static synchronized boolean itemHasAny(String itemId, List<String> words) {
        ensureLoaded();
        List<String> ws = itemWords.get(itemId);
        if (ws == null) return false;
        for (String w : words) if (ws.contains(w)) return true;
        return false;
    }

    /** Word counts restricted to items whose mod is actually LOADED (registry-present).
     *  Computed lazily on first use -- registries are final by screen time, NOT at parse
     *  time (mod init order is arbitrary) -- and cached until the next config (re)load.
     *  rc.6 (Kishku7): the Aether button must not appear on installs without an Aether mod;
     *  generic for any modded keyword button, known or future. */
    private static Map<String, Integer> loadedWordCounts;

    private static Map<String, Integer> loadedCounts() {
        if (loadedWordCounts == null) {
            Map<String, Integer> m = new HashMap<>();
            for (Map.Entry<String, List<String>> e : itemWords.entrySet()) {
                Identifier id = Identifier.tryParse(e.getKey());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
                for (String w : e.getValue()) m.merge(w, 1, Integer::sum);
            }
            loadedWordCounts = m;
        }
        return loadedWordCounts;
    }

    /** True when at least one REGISTERED item carries one of the given words
     *  (hide-empty rule for keyword buttons -- absent mods leave their buttons hidden). */
    public static synchronized boolean anyItemHas(List<String> words) {
        ensureLoaded();
        Map<String, Integer> counts = loadedCounts();
        for (String w : words) if (counts.getOrDefault(w, 0) > 0) return true;
        return false;
    }

    /** Total catalogued items carrying any of the words (tooltip support). rc.6 (Kishku7):
     *  UNIVERSAL registry filter -- vanilla or modded, an item only counts when the running
     *  game actually registers it. */
    public static synchronized Set<String> itemsWithAny(List<String> words) {
        ensureLoaded();
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, List<String>> e : itemWords.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            for (String w : words)
                if (e.getValue().contains(w)) { out.add(e.getKey()); break; }
        }
        return out;
    }
}
