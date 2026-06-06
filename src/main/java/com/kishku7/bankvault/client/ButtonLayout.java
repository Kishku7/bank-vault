package com.kishku7.bankvault.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

/** v1.2 category-button layout -- settings-driven like the catalog. The config file
 *  {@code config/bankvault/buttons.json} wins; the bundled copy is the fallback. Layout is
 *  "numbered rows": each row is a list of category ids and the number of entries in a row IS
 *  that row's column count -- ragged rows are fine (7 buttons on row 1, 2 on row 2). The file
 *  is re-read automatically whenever its modification time changes (live-editable). */
public final class ButtonLayout {

    private static final Gson GSON = new Gson();
    private static int buttonSize = 20;
    private static List<List<String>> rows;
    private static long mtime = -1;

    private ButtonLayout() {}

    public static synchronized int buttonSize() { ensureLoaded(); return buttonSize; }
    public static synchronized List<List<String>> rows() { ensureLoaded(); return rows; }

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
        List<List<String>> out = new ArrayList<>();
        JsonArray rr = root.getAsJsonArray("rows");
        if (rr != null) rr.forEach(e -> {
            List<String> row = new ArrayList<>();
            e.getAsJsonArray().forEach(x -> row.add(x.getAsString()));
            if (!row.isEmpty()) out.add(row);
        });
        rows = out;
    }
}
