package com.kishku7.bankvault.vault;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kishku7.bankvault.BankVault;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

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

    public record Tab(String id, String name, String glyph, int order, String icon) {}

    private static final Gson GSON = new Gson();
    private static final List<String> DEFAULT = List.of("uncategorized");
    private static final String[] EMPTY = new String[0];

    private static List<Tab> tabs;
    private static Map<String, List<String>> itemTabs;
    private static Map<String, Integer> colors;
    private static Map<String, String[]> sortLists;
    private static Map<String, Map<String, List<String>>> tabSort;
    private static Map<String, Map<String, Map<String, Integer>>> tabOrder; // tab -> mode -> itemId -> rank
    private static Map<String, Map<String, List<String>>> tabOrderList;     // tab -> mode -> ordered ids
    private static Map<String, Map<String, Map<String, String>>> groupLabels; // tab -> mode -> itemId -> section label
    private static Set<String> tabsWithItems;

    private Catalog() {}

    /** Write any MISSING config file from the bundled defaults (per-file: delete one and only
     *  that one is restored on next init). Existing files are never touched. */

    /** Config data version (Dave, 1.2.1 spec). Bump whenever the bundled data files change in
     *  a way upgrades must pick up, and record the delta in MIGRATION_LOG. Rule 1: a config
     *  file with no "version" field is pre-1.2.1 and needs an upgrade. */
    public static final String DATA_VERSION = "1.2.1";

    /** Rule 3: the tracked deltas between data versions, newest last. */
    private static final List<String> MIGRATION_LOG = List.of(
            "1.2.1: first versioned data. Pre-1.2.1 configs lack the 1.2 line's kw:/dyn: ranked"
                    + " lists, section-group labels, buttons.json and keywords.json; generated"
                    + " files are replaced with the bundled spec, user-editable files gain"
                    + " missing entries (user edits kept).");

    /** Generated, spec-owned files: on upgrade these are REPLACED with the bundled spec. */
    private static final List<String> GENERATED_FILES = List.of(
            "categories.json", "sort_family.json", "sort_type.json", "sort_groups.json");
    /** User-editable files: on upgrade, missing elements are ADDED; user edits always win. */
    private static final List<String> USER_FILES = List.of("buttons.json", "keywords.json");

    /** Rules 2/4/5 (Dave, 1.2.1): bring an older config up to spec, then stamp it with the
     *  new version. Generated files are replaced wholesale; user-editable files deep-gain
     *  missing elements from the bundled spec. Runs before missing-file restore. */
    private static void migrateConfig(Path dir) {
        for (String f : GENERATED_FILES) {
            Path dst = dir.resolve(f);
            if (!Files.exists(dst) || DATA_VERSION.equals(readDataVersion(dst))) continue;
            try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/" + f)) {
                if (in == null) continue;
                Files.copy(in, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                BankVault.LOGGER.info("[Bank Vault] config upgraded (replaced): {} -> {}", f, DATA_VERSION);
            } catch (Exception e) {
                BankVault.LOGGER.error("[Bank Vault] config upgrade failed: {}", f, e);
            }
        }
        for (String f : USER_FILES) {
            Path dst = dir.resolve(f);
            if (!Files.exists(dst) || DATA_VERSION.equals(readDataVersion(dst))) continue;
            try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/" + f)) {
                if (in == null) continue;
                JsonObject bundled = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
                JsonObject cfg = null;
                try (Reader r = Files.newBufferedReader(dst, StandardCharsets.UTF_8)) {
                    cfg = GSON.fromJson(r, JsonObject.class);
                } catch (Exception ignored) {}
                if (cfg == null) cfg = new JsonObject();
                mergeMissing(bundled, cfg);
                cfg.addProperty("version", DATA_VERSION);
                Files.writeString(dst, GSON.toJson(cfg), StandardCharsets.UTF_8);
                BankVault.LOGGER.info("[Bank Vault] config upgraded (merged, user edits kept): {} -> {}", f, DATA_VERSION);
            } catch (Exception e) {
                BankVault.LOGGER.error("[Bank Vault] config upgrade failed: {}", f, e);
            }
        }
    }

    /** Rule 1: missing "version" = pre-1.2.1. */
    private static String readDataVersion(Path f) {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            if (o != null && o.has("version")) return o.get("version").getAsString();
        } catch (Exception ignored) {}
        return "pre-1.2.1";
    }

    /** Recursive: keys the config lacks are added from the bundled spec; existing keys (user
     *  edits) always win. Objects recurse; arrays and scalars are atomic. */
    private static void mergeMissing(JsonObject bundled, JsonObject cfg) {
        for (Map.Entry<String, JsonElement> e : bundled.entrySet()) {
            if (!cfg.has(e.getKey())) cfg.add(e.getKey(), e.getValue());
            else if (e.getValue().isJsonObject() && cfg.get(e.getKey()).isJsonObject())
                mergeMissing(e.getValue().getAsJsonObject(), cfg.get(e.getKey()).getAsJsonObject());
        }
    }

    public static synchronized void restoreMissingDefaults() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("bankvault");
        try { Files.createDirectories(dir); }
        catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] config dir create failed", e); return; }
        migrateConfig(dir);   // 1.2.1: version-stamped configs upgrade in place (Dave)
        for (String f : List.of("categories.json", "sort_family.json", "sort_type.json", "sort_groups.json", "buttons.json", "keywords.json")) {
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
        tabOrderList = new HashMap<>();
        groupLabels = new HashMap<>();
        // 1) config-dir override (live-editable, survives mod updates)
        Path cfg = FMLPaths.CONFIGDIR.get().resolve("bankvault").resolve("categories.json");
        if (Files.exists(cfg)) {
            try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                parse(GSON.fromJson(r, JsonObject.class));
                fillMissingIcons();
                BankVault.LOGGER.info("[Bank Vault] catalog from config: {} tabs, {} items", tabs.size(), itemTabs.size());
                loadOrderFile("family");
                loadOrderFile("type");
                loadGroupsFile();
                return;
            } catch (Exception e) {
                BankVault.LOGGER.error("[Bank Vault] config catalog load failed, falling back to bundled", e);
                tabs.clear(); itemTabs.clear(); colors.clear(); sortLists.clear(); tabSort.clear(); tabOrder.clear(); tabOrderList.clear(); groupLabels.clear();
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
        loadGroupsFile();
    }

    /** sort_family.json / sort_type.json: {"<tab>": ["item_id", ...]} -- the array order IS the
     *  on-screen order for that tab+mode. Config dir wins; bundled copy is the fallback. */
    private static void loadOrderFile(String mode) {
        JsonObject root = null;
        Path f = FMLPaths.CONFIGDIR.get().resolve("bankvault").resolve("sort_" + mode + ".json");
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
            if ("version".equals(tab)) continue;   // 1.2.1 data-version stamp
            Map<String, Integer> rank = new HashMap<>();
            List<String> ordered = new ArrayList<>();
            int[] i = {0};
            root.getAsJsonArray(tab).forEach(x -> { rank.put(x.getAsString(), i[0]++); ordered.add(x.getAsString()); });
            tabOrder.computeIfAbsent(tab, k -> new HashMap<>()).put(mode, rank);
            tabOrderList.computeIfAbsent(tab, k -> new HashMap<>()).put(mode, ordered);
            n += rank.size();
        }
        BankVault.LOGGER.info("[Bank Vault] sort_{}: {} tabs, {} ranked ids", mode, tabOrder.size(), n);
    }

    /** sort_groups.json (v1.2 sections): {"<tab>": {"family": [["Label", count], ...], "type":
     *  [...]}} -- spans partition that tab's ranked list, in order. Resolved here into a direct
     *  itemId -> label map per tab+mode. Config dir wins; bundled is the fallback. */
    private static void loadGroupsFile() {
        JsonObject root = null;
        Path f = FMLPaths.CONFIGDIR.get().resolve("bankvault").resolve("sort_groups.json");
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { root = GSON.fromJson(r, JsonObject.class); }
            catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] sort_groups.json config load failed", e); }
        }
        if (root == null) {
            try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/sort_groups.json")) {
                if (in != null) root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            } catch (Exception e) { BankVault.LOGGER.error("[Bank Vault] sort_groups.json bundled load failed", e); }
        }
        if (root == null) return;
        int tabsN = 0;
        for (String tab : root.keySet()) {
            if ("version".equals(tab)) continue;   // 1.2.1 data-version stamp
            Map<String, List<String>> modes = tabOrderList.get(tab);
            if (modes == null) continue;
            JsonObject o = root.getAsJsonObject(tab);
            Map<String, Map<String, String>> byMode = new HashMap<>();
            for (String mode : o.keySet()) {
                List<String> ordered = modes.get(mode);
                if (ordered == null) continue;
                Map<String, String> labels = new HashMap<>();
                int idx = 0;
                for (JsonElement se : o.getAsJsonArray(mode)) {
                    String label = se.getAsJsonArray().get(0).getAsString();
                    int count = se.getAsJsonArray().get(1).getAsInt();
                    for (int k = 0; k < count && idx < ordered.size(); k++, idx++) labels.put(ordered.get(idx), label);
                }
                byMode.put(mode, labels);
            }
            groupLabels.put(tab, byMode);
            tabsN++;
        }
        BankVault.LOGGER.info("[Bank Vault] sort_groups: {} tabs with section labels", tabsN);
    }

    private static void parse(JsonObject root) {
        root.getAsJsonArray("tabs").forEach(e -> {
            JsonObject t = e.getAsJsonObject();
            tabs.add(new Tab(t.get("id").getAsString(), t.get("name").getAsString(),
                    t.get("glyph").getAsString(), t.get("order").getAsInt(),
                    t.has("icon") ? t.get("icon").getAsString() : ""));
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

    /** v1.2: a config-dir categories.json written before v1.2 has no "icon" fields. Backfill
     *  any missing icon from the bundled defaults (matched by tab id) so buttons never render
     *  blank on upgraded installs. User-set icons are never overwritten. */
    private static void fillMissingIcons() {
        if (tabs.stream().noneMatch(t -> t.icon() == null || t.icon().isEmpty())) return;
        Map<String, String> bundled = new HashMap<>();
        try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/categories.json")) {
            if (in == null) return;
            JsonObject root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            root.getAsJsonArray("tabs").forEach(e -> {
                JsonObject t = e.getAsJsonObject();
                if (t.has("icon")) bundled.put(t.get("id").getAsString(), t.get("icon").getAsString());
            });
        } catch (Exception e) {
            BankVault.LOGGER.error("[Bank Vault] bundled icon backfill failed", e);
            return;
        }
        int filled = 0;
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            if ((t.icon() == null || t.icon().isEmpty()) && bundled.containsKey(t.id())) {
                tabs.set(i, new Tab(t.id(), t.name(), t.glyph(), t.order(), bundled.get(t.id())));
                filled++;
            }
        }
        if (filled > 0) BankVault.LOGGER.info("[Bank Vault] backfilled {} tab icons from bundled defaults", filled);
    }

    /** Drop all cached catalog/sort data and re-read the JSON files (config dir first). */
    public static synchronized void reload() {
        tabs = null; itemTabs = null; colors = null; sortLists = null; tabSort = null; tabOrder = null; tabOrderList = null; groupLabels = null; tabsWithItems = null;
        ensureLoaded();
    }

    public static List<Tab> tabs() { ensureLoaded(); return tabs; }

    /** Tab by id, or null when the id is not configured. */
    public static Tab tab(String id) {
        ensureLoaded();
        for (Tab t : tabs) if (t.id().equals(id)) return t;
        return null;
    }

    /** True when at least one catalog item is assigned to this tab (smart-sort records exist). */
    public static synchronized boolean hasItems(String tabId) {
        ensureLoaded();
        if (tabsWithItems == null) {
            Set<String> s = new java.util.HashSet<>();
            for (List<String> l : itemTabs.values()) s.addAll(l);
            tabsWithItems = s;
        }
        return tabsWithItems.contains(tabId);
    }

    /** Tabs an item belongs to; items unknown to the catalog OR mapped to an EMPTY list land
     *  in "uncategorized" (beta.1 fix: config catalogs can carry "id": [] entries -- get(0) on
     *  the raw list crashed every rebuild on categorical tabs, freezing the grid). */
    public static List<String> tabsFor(String itemId) {
        ensureLoaded();
        List<String> l = itemTabs.getOrDefault(itemId, DEFAULT);
        return l.isEmpty() ? DEFAULT : l;
    }

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

    /** True when an explicit per-tab sort config exists for this key+mode -- either a tabSort
     *  step chain or a ranked list loaded from sort_family/sort_type. Keyword buttons use this
     *  to choose between their own curated sort and the categorical fallback. */
    public static boolean hasSortConfig(String key, String mode) {
        ensureLoaded();
        Map<String, List<String>> m = tabSort.get(key);
        if (m != null && m.containsKey(mode)) return true;
        Map<String, Map<String, Integer>> o = tabOrder.get(key);
        return o != null && o.containsKey(mode);
    }

    /** Step chain for key+mode with NO "default" fallback; null when absent (A-Z stays a plain
     *  name sort unless a tab opts in with an "alpha" chain). */
    public static List<String> sortStepsExact(String key, String mode) {
        ensureLoaded();
        Map<String, List<String>> m = tabSort.get(key);
        return m == null ? null : m.get(mode);
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

    /** Section label for an item in a tab's curated order (v1.2 sections), or null when the
     *  tab/mode has no group data. */
    public static String groupLabel(String tabId, String mode, String itemId) {
        ensureLoaded();
        Map<String, Map<String, String>> m = groupLabels.get(tabId);
        if (m == null) return null;
        Map<String, String> labels = m.get(mode);
        return labels == null ? null : labels.get(itemId);
    }

    /** Dye/dye-source color index 0-15 (white..black), or -1 if not a colored dye item. */
    public static int colorOf(String itemId) { ensureLoaded(); return colors.getOrDefault(itemId, -1); }
}
