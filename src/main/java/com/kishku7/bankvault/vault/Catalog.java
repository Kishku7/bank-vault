package com.kishku7.bankvault.vault;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kishku7.bankvault.BankVault;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the bundled item->tabs catalog (+ dye color metadata). Items may belong to several tabs. */
public final class Catalog {

    public record Tab(String id, String name, String glyph, int order) {}

    private static final Gson GSON = new Gson();
    private static final List<String> DEFAULT = List.of("uncategorized");

    private static List<Tab> tabs;
    private static Map<String, List<String>> itemTabs;
    private static Map<String, Integer> colors;

    private Catalog() {}

    public static synchronized void ensureLoaded() {
        if (tabs != null) return;
        tabs = new ArrayList<>();
        itemTabs = new HashMap<>();
        colors = new HashMap<>();
        try (InputStream in = Catalog.class.getResourceAsStream("/data/bankvault/categories.json")) {
            if (in == null) { BankVault.LOGGER.error("[Bank Vault] categories.json not found"); return; }
            JsonObject root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            root.getAsJsonArray("tabs").forEach(e -> {
                JsonObject t = e.getAsJsonObject();
                tabs.add(new Tab(t.get("id").getAsString(), t.get("name").getAsString(),
                        t.get("glyph").getAsString(), t.get("order").getAsInt()));
            });
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("items").entrySet()) {
                List<String> list = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(t -> list.add(t.getAsString()));
                itemTabs.put(e.getKey(), list);
            }
            if (root.has("colors"))
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("colors").entrySet())
                    colors.put(e.getKey(), e.getValue().getAsInt());
            BankVault.LOGGER.info("[Bank Vault] catalog: {} tabs, {} items, {} colors",
                    tabs.size(), itemTabs.size(), colors.size());
        } catch (Exception e) {
            BankVault.LOGGER.error("[Bank Vault] catalog load failed", e);
        }
    }

    public static List<Tab> tabs() { ensureLoaded(); return tabs; }

    public static List<String> tabsFor(String itemId) { ensureLoaded(); return itemTabs.getOrDefault(itemId, DEFAULT); }

    public static boolean inTab(String itemId, String tab) { return tabsFor(itemId).contains(tab); }

    /** Dye/dye-source color index 0–15 (white..black), or -1 if not a colored dye item. */
    public static int colorOf(String itemId) { ensureLoaded(); return colors.getOrDefault(itemId, -1); }
}
