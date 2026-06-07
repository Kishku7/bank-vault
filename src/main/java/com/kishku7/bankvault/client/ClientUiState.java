package com.kishku7.bankvault.client;

import com.kishku7.bankvault.net.UiStateSyncPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client cache of the player's remembered vault UI state (v1.2 last-use memory). Filled by
 *  {@link UiStateSyncPayload} just before the screen opens; kept current locally as the player
 *  clicks so re-inits inside one session never go stale. */
public final class ClientUiState {

    private static String lastTab = "";
    private static final Map<String, String> sorts = new HashMap<>();

    private ClientUiState() {}

    public static synchronized void set(String tab, List<UiStateSyncPayload.TabSort> list) {
        lastTab = tab == null ? "" : tab;
        sorts.clear();
        if (list != null) for (UiStateSyncPayload.TabSort t : list) sorts.put(t.tab(), t.sort());
    }

    public static synchronized String lastTab() { return lastTab; }

    /** Remembered sort string for a tab, or null when the player never sorted it. */
    public static synchronized String sortFor(String tab) { return sorts.get(tab); }

    /** Mirror a local interaction so subsequent screen inits restore the newest state. */
    public static synchronized void remember(String tab, String sort) {
        lastTab = tab == null ? "" : tab;
        if (tab != null && !tab.isEmpty() && sort != null && !sort.isEmpty()) sorts.put(tab, sort);
    }
}
