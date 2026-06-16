package com.kishku7.bankvault.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.util.List;

/** v1.2 category/keyword button layout -- settings-driven like the catalog. The config file
 *  {@code config/bankvault/buttons.json} wins; the bundled copy is the fallback. Layout is
 *  "numbered rows": each row is a list of button entries and the number of entries in a row IS
 *  that row's column count -- ragged rows are fine. Re-read automatically when the file's
 *  modification time changes (live-editable).
 *
 *  Two entry forms (schema v2):
 *    "wood"                                  -- a category id from categories.json (v1 form)
 *    {"label": "Wood", "icon": "minecraft:oak_log", "words": ["wood"]}
 *                                            -- a keyword button matching ANY of the words
 *                                               from keywords.json (multi-word friendly) */
public final class ButtonLayout {

    /** One button definition. Exactly one of {@code category} / {@code words} / {@code dynamic}
     *  is non-null. {@code dynamic} buttons resolve membership at runtime (e.g. "trinkets"). */
    public record BtnDef(String label, String icon, String category, List<String> words, String dynamic, List<String> pins) {
        /** Stable identity for selection state across re-inits and config reloads. */
        public String key() {
            if (category != null) return "cat:" + category;
            if (dynamic != null) return "dyn:" + dynamic;
            return "kw:" + label;
        }
    }

    /** One layout row: a section header (section != null), a plain gap (empty buttons),
     *  or a row of buttons. */
    public record Row(String section, List<BtnDef> buttons) {}

    private static final Gson GSON = new Gson();
    private static int buttonSize = 20;
    private static List<Row> rows;
    private static long mtime = -1;

    private ButtonLayout() {}

    public static synchronized int buttonSize() { ensureLoaded(); return buttonSize; }
    public static synchronized List<Row> rows() { ensureLoaded(); return rows; }

    /** Fast path: load once if never loaded; no disk access afterwards. */
    private static void ensureLoaded() {
        if (rows == null) pollConfig();
    }

    /** Check the config file's mtime and (re)load when it changed. Call once per screen-open. */
    public static synchronized void pollConfig() {
        Path cfg = FMLPaths.CONFIGDIR.get().resolve("bankvault").resolve("buttons.json");
        long m = -1;
        try { if (Files.exists(cfg)) m = Files.getLastModifiedTime(cfg).toMillis(); } catch (Exception ignored) {}
        if (rows != null && m == mtime) return;
        mtime = m;
        rows = new ArrayList<>();
        buttonSize = 20;
        if (m >= 0) {
            try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                parse(GSON.fromJson(r, JsonObject.class));
                BankVault.LOGGER.info("[Bank Vault] button layout from config: {} rows", rows.size());
                return;
            } catch (Exception e) {
                BankVault.LOGGER.error("[Bank Vault] buttons.json config load failed, falling back to bundled", e);
                rows = new ArrayList<>();
            }
        }
        try (InputStream in = ButtonLayout.class.getResourceAsStream("/data/bankvault/buttons.json")) {
            if (in == null) { BankVault.LOGGER.error("[Bank Vault] bundled buttons.json missing"); return; }
            parse(GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class));
            BankVault.LOGGER.info("[Bank Vault] button layout bundled: {} rows", rows.size());
        } catch (Exception e) {
            BankVault.LOGGER.error("[Bank Vault] buttons.json load failed", e);
        }
    }

    private static void parse(JsonObject root) {
        if (root.has("buttonSize")) buttonSize = Math.max(18, Math.min(32, root.get("buttonSize").getAsInt()));
        List<Row> out = new ArrayList<>();
        JsonArray rr = root.getAsJsonArray("rows");
        if (rr != null) for (JsonElement re : rr) {
            if (re.isJsonPrimitive() && "gap".equals(re.getAsString())) { out.add(new Row(null, new ArrayList<>())); continue; }
            if (re.isJsonObject() && re.getAsJsonObject().has("section")) {
                out.add(new Row(re.getAsJsonObject().get("section").getAsString(), new ArrayList<>()));
                continue;
            }
            List<BtnDef> row = new ArrayList<>();
            for (JsonElement e : re.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    row.add(new BtnDef(null, null, e.getAsString(), null, null, null));
                } else if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> words = new ArrayList<>();
                    if (o.has("words")) o.getAsJsonArray("words").forEach(x -> words.add(x.getAsString()));
                    String dynamic = o.has("dynamic") ? o.get("dynamic").getAsString() : null;
                    String label = o.has("label") ? o.get("label").getAsString()
                            : (dynamic != null ? dynamic : words.isEmpty() ? "?" : words.get(0));
                    String icon = o.has("icon") ? o.get("icon").getAsString() : "";
                    List<String> pins = new ArrayList<>();
                    if (o.has("pins")) o.getAsJsonArray("pins").forEach(x -> pins.add(x.getAsString()));
                    if (!words.isEmpty() || dynamic != null)
                        row.add(new BtnDef(label, icon, null, words.isEmpty() ? null : words, dynamic,
                                pins.isEmpty() ? null : pins));
                }
            }
            if (!row.isEmpty()) out.add(new Row(null, row));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).section() == null
                && out.get(out.size() - 1).buttons().isEmpty()) out.remove(out.size() - 1);
        rows = out;
    }
}
