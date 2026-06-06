package com.kishku7.bankvault.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

    /** One button definition. Exactly one of {@code category} / {@code words} is non-null. */
    public record BtnDef(String label, String icon, String category, List<String> words) {
        /** Stable identity for selection state across re-inits and config reloads. */
        public String key() { return category != null ? "cat:" + category : "kw:" + label; }
    }

    private static final Gson GSON = new Gson();
    private static int buttonSize = 20;
    private static List<List<BtnDef>> rows;
    private static long mtime = -1;

    private ButtonLayout() {}

    public static synchronized int buttonSize() { ensureLoaded(); return buttonSize; }
    public static synchronized List<List<BtnDef>> rows() { ensureLoaded(); return rows; }

    private static void ensureLoaded() {
        Path cfg = FabricLoader.getInstance().getConfigDir().resolve("bankvault").resolve("buttons.json");
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
        List<List<BtnDef>> out = new ArrayList<>();
        JsonArray rr = root.getAsJsonArray("rows");
        if (rr != null) for (JsonElement re : rr) {
            List<BtnDef> row = new ArrayList<>();
            for (JsonElement e : re.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    row.add(new BtnDef(null, null, e.getAsString(), null));
                } else if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> words = new ArrayList<>();
                    if (o.has("words")) o.getAsJsonArray("words").forEach(x -> words.add(x.getAsString()));
                    String label = o.has("label") ? o.get("label").getAsString()
                            : (words.isEmpty() ? "?" : words.get(0));
                    String icon = o.has("icon") ? o.get("icon").getAsString() : "";
                    if (!words.isEmpty()) row.add(new BtnDef(label, icon, null, words));
                }
            }
            if (!row.isEmpty()) out.add(row);
        }
        rows = out;
    }
}
