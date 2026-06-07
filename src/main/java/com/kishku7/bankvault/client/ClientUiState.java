package com.kishku7.bankvault.client;

import com.kishku7.bankvault.net.UiStateSyncPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client cache of the player's remembered vault UI state (v1.2 last-use memory). Filled by
 *  {@link UiStateSyncPayload} just before the screen opens; kept current locally as the player
 *  clicks so re-inits inside one session never go stale. */
public final class ClientUiState {

    private static String lastTab = "";
    private static final Map<String, String> sorts = new HashMap<>();
    private static final Map<String, List<String>> pins = new HashMap<>();
    private static boolean showSections = false;

    private ClientUiState() {}

    public static synchronized void set(String tab, List<UiStateSyncPayload.TabSort> sortList,
                                        boolean sections, List<UiStateSyncPayload.TabPins> pinList) {
        lastTab = tab == null ? "" : tab;
        sorts.clear();
        if (sortList != null) for (UiStateSyncPayload.TabSort t : sortList) sorts.put(t.tab(), t.sort());
        pins.clear();
        if (pinList != null) for (UiStateSyncPayload.TabPins t : pinList) pins.put(t.tab(), new ArrayList<>(t.ids()));
        showSections = sections;
    }

    public static synchronized String lastTab() { return lastTab; }

    /** Remembered sort string for a tab, or null when the player never sorted it. */
    public static synchronized String sortFor(String tab) { return sorts.get(tab); }

    public static synchronized boolean showSections() { return showSections; }

    public static synchronized void rememberSections(boolean v) { showSections = v; }

    /** Per-tab user pins, in pin order (never null). */
    public static synchronized List<String> pinsFor(String tab) {
        List<String> l = pins.get(tab);
        return l == null ? List.of() : List.copyOf(l);
    }

    /** Mirror a local pin toggle (the server applies the same flip via PinPayload). */
    public static synchronized void togglePinLocal(String tab, String itemId) {
        List<String> l = pins.computeIfAbsent(tab, k -> new ArrayList<>());
        if (!l.remove(itemId)) l.add(itemId);
    }

    /** Mirror a local interaction so subsequent screen inits restore the newest state. */
    public static synchronized void remember(String tab, String sort) {
        lastTab = tab == null ? "" : tab;
        if (tab != null && !tab.isEmpty() && sort != null && !sort.isEmpty()) sorts.put(tab, sort);
    }
}
