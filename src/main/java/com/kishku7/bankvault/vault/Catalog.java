package com.kishku7.bankvault.vault;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kishku7.bankvault.BankVault;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item->tab catalog + data-driven smart-sort config. SETTINGS-DRIVEN: if
 * {@code config/bankvault/categories.json} exists it wins; otherwise the copy bundled in the jar
 * is used. Tabs, item assignment, sort lists, and per-tab sort step chains are all data -- item
 * updates or tab re-shuffles never require a mod rebuild. Items not present in the file land in
 * the "uncategorized" tab.
 */
public final class Catalog {

    public record Tab(String id, String name, String glyph, int order) {}

    private static final Gson GSON = new Gson();
    private static final List<String> DEFAULT = List.of("uncategorized");
    private static final String[] EMPTY = new String[0];

    private static List<Tab> tabs;
    private static Map<String, List<String>> itemTabs;
    private static Map<String, Integer> colors;
    private static Map<String, String[]> sortLists;
    private static Map<String, Map<String, List<String>>> tabSort;
    private static Map<String, Map<String, Map<String, Integer>>> tabOrder; // tab -> mode -> itemId -> rank

    private Catalog() {}

    /** Write any MISSING config file from the bundled defaults (per-file: delete one and only
     *  that one is restored on next init). Existing files are never touched. */
    public static synchronized void restoreMissingDefaults() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("bankvault");
        try { Files.createDirectories(dir); }
        catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] config dir create failed", e); return; }
        for (String f : List.of("categories.json", "sort_family.json", "sort_type.json")) {
            Path dst = dir.resolve(f);
            if (Files.exists(dst)) continue;
            try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/" + f)) {
                if (in == null) { BankVault.LOGGER.error("[Bank Vault] bundled default missing: {}", f); continue; }
                Files.copy(in, dst);
                BankVault.LOGGER.info("[Bank Vault] restored default config: {}", f);
            } catch (Exception e) {
                BankVault.LOGGER.error("[Bank Vault] default restore failed: {}", f, e);
            }
        }
    }

    public static synchronized void ensureLoaded() {
        if (tabs != null) return;
        tabs = new ArrayList<>();
        itemTabs = new HashMap<>();
        colors = new HashMap<>();
        sortLists = new HashMap<>();
        tabSort = new HashMap<>();
        tabOrder = new HashMap<>();
        // 1) config-dir override (live-editable, survives mod updates)
        Path cfg = FabricLoader.getInstance().getConfigDir().resolve("bankvault").resolve("categories.json");
        if (Files.exists(cfg)) {
            try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                parse(GSON.fromJson(r, JsonObject.class));
                BankVault.LOGGER.info("[Bank Vault] catalog from config: {} tabs, {} items", tabs.size(), itemTabs.size());
                loadOrderFile("family");
                loadOrderFile("type");
                return;
            } catch (Exception e) {
                BankVault.LOGGER.error("[Bank Vault] config catalog load failed, falling back to bundled", e);
                tabs.clear(); itemTabs.clear(); colors.clear(); sortLists.clear(); tabSort.clear(); tabOrder.clear();
            }
        }
        // 2) bundled default
        try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/categories.json")) {
            if (in == null) { BankVault.LOGGER.error("[Bank Vault] categories.json not found"); return; }
            parse(GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class));
            BankVault.LOGGER.info("[Bank Vault] catalog bundled: {} tabs, {} items, {} colors",
                    tabs.size(), itemTabs.size(), colors.size());
        } catch (Exception e) {
            BankVault.LOGGER.error("[Bank Vault] catalog load failed", e);
        }
        loadOrderFile("family");
        loadOrderFile("type");
    }

    /** sort_family.json / sort_type.json: {"<tab>": ["item_id", ...]} -- the array order IS the
     *  on-screen order for that tab+mode. Config dir wins; bundled copy is the fallback. */
    private static void loadOrderFile(String mode) {
        JsonObject root = null;
        Path f = FabricLoader.getInstance().getConfigDir().resolve("bankvault").resolve("sort_" + mode + ".json");
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { root = GSON.fromJson(r, JsonObject.class); }
            catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] sort_{}.json config load failed", mode, e); }
        }
        if (root == null) {
            try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/sort_" + mode + ".json")) {
                if (in != null) root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            } catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] sort_{}.json bundled load failed", mode, e); }
        }
        if (root == null) return;
        int n = 0;
        for (String tab : root.keySet()) {
            Map<String, Integer> rank = new HashMap<>();
            int[] i = {0};
            root.getAsJsonArray(tab).forEach(x -> rank.put(x.getAsString(), i[0]++));
            tabOrder.computeIfAbsent(tab, k -> new HashMap<>()).put(mode, rank);
            n += rank.size();
        }
        BankVault.LOGGER.info("[Bank Vault] sort_{}: {} tabs, {} ranked ids", mode, root.keySet().size(), n);
    }

    private static void parse(JsonObject root) {
        root.getAsJsonArray("tabs").forEach(e -> {
            JsonObject t = e.getAsJsonObject();
            tabs.add(new Tab(t.get("id").getAsString(), t.get("name").getAsString(),
                    t.get("glyph").getAsString(), t.get("order").getAsInt()));
        });
        tabs.sort(java.util.Comparator.comparingInt(Tab::order));
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("items").entrySet()) {
            List<String> list = new ArrayList<>();
            e.getValue().getAsJsonArray().forEach(t -> list.add(t.getAsString()));
            itemTabs.put(e.getKey(), list);
        }
        if (root.has("colors"))
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("colors").entrySet())
                colors.put(e.getKey(), e.getValue().getAsInt());
        if (root.has("sortLists"))
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("sortLists").entrySet()) {
                List<String> l = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                sortLists.put(e.getKey(), l.toArray(new String[0]));
            }
        if (root.has("tabOrder"))
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("tabOrder").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Map<String, Map<String, Integer>> modes = new HashMap<>();
                for (String mode : o.keySet()) {
                    Map<String, Integer> rank = new HashMap<>();
                    int[] n = {0};
                    o.getAsJsonArray(mode).forEach(x -> rank.put(x.getAsString(), n[0]++));
                    modes.put(mode, rank);
                }
                tabOrder.put(e.getKey(), modes);
            }
        if (root.has("tabSort"))
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("tabSort").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Map<String, List<String>> modes = new HashMap<>();
                for (String mode : o.keySet()) {
                    List<String> steps = new ArrayList<>();
                    o.getAsJsonArray(mode).forEach(x -> steps.add(x.getAsString()));
                    modes.put(mode, steps);
                }
                tabSort.put(e.getKey(), modes);
            }
    }

    /** Drop all cached catalog/sort data and re-read the JSON files (config dir first). */
    public static synchronized void reload() {
        tabs = null; itemTabs = null; colors = null; sortLists = null; tabSort = null; tabOrder = null;
        ensureLoaded();
    }

    public static List<Tab> tabs() { ensureLoaded(); return tabs; }

    public static List<String> tabsFor(String itemId) { ensureLoaded(); return itemTabs.getOrDefault(itemId, DEFAULT); }

    public static boolean inTab(String itemId, String tab) { return tabsFor(itemId).contains(tab); }

    /** All item ids the catalog knows (used by /bank fillall). */
    public static Set<String> itemIds() { ensureLoaded(); return itemTabs.keySet(); }

    /** Named ordered list from the sortLists config section (empty array if absent). */
    public static String[] sortList(String name) { ensureLoaded(); return sortLists.getOrDefault(name, EMPTY); }

    /** Sort step chain for a tab + mode ("family"/"type"); falls back to the "default" entry. */
    public static List<String> sortSteps(String tabId, String mode) {
        ensureLoaded();
        Map<String, List<String>> m = tabSort.get(tabId);
        if (m == null || !m.containsKey(mode)) m = tabSort.get("default");
        if (m == null) return List.of("name");
        return m.getOrDefault(mode, List.of("name"));
    }

    /** Explicit rank of an item in a tab's curated order ("family"/"type" mode); items not in
     *  the list (or tabs without one) rank last so later steps (name) take over. The order list
     *  IS the sort -- the mod applies no logic of its own. */
    public static int orderIndex(String tabId, String mode, String itemId) {
        ensureLoaded();
        Map<String, Map<String, Integer>> m = tabOrder.get(tabId);
        if (m == null) return Integer.MAX_VALUE;
        Map<String, Integer> rank = m.get(mode);
        if (rank == null) return Integer.MAX_VALUE;
        Integer r = rank.get(itemId);
        return r == null ? Integer.MAX_VALUE : r;
    }

    /** Dye/dye-source color index 0-15 (white..black), or -1 if not a colored dye item. */
    public static int colorOf(String itemId) { ensureLoaded(); return colors.getOrDefault(itemId, -1); }
}
