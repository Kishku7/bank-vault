package com.kishku7.bankvault.client;

import com.kishku7.bankvault.inventory.BankVaultMenu;
import com.kishku7.bankvault.inventory.ContainerExtractor;
import com.kishku7.bankvault.net.GridViewPayload;
import com.kishku7.bankvault.net.UpgradePayload;
import com.kishku7.bankvault.net.VaultSyncPayload;
import com.kishku7.bankvault.net.VaultSyncPayload.Entry;
import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.inventory.TrinketCompat;
import com.kishku7.bankvault.vault.Catalog;
import com.kishku7.bankvault.vault.Keywords;
import com.kishku7.bankvault.client.ClientNet;
import com.kishku7.bankvault.net.ShareActionPayload;
import com.kishku7.bankvault.net.SharingStatePayload;
import com.kishku7.bankvault.net.UiStatePayload;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
/* [[[cog
import compat_core
compat_core.emit_input_imports(cog, ver)
]]] */
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
/* [[[end]]] */
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
/* [[[cog
import compat_core
compat_core.emit_click_import(cog, ver)
]]] */
import net.minecraft.world.inventory.ContainerInput;
/* [[[end]]] */
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Creative-style bank vault browser. Left: full-height tab rail. Middle: the vault grid (real-slot
 *  backed, stretches to the panel bottom) with sort buttons above and a global search bar below.
 *  Right: a full replication of the native survival inventory -- armor, player preview, offhand,
 *  2x2 crafting, 3x9 storage + hotbar -- plus the Quick Unload cluster. */
public class BankVaultScreen extends AbstractContainerScreen<BankVaultMenu> {

    private static final int OVERLAY=0xBE060709, FACE=0xFF34343A, LIGHT=0xFF63636D, DARK=0xFF131316,
            WELL=0xFF191920, SLOT_BG=0xFF2A2A31, ACCENT=0xFFE0A92E, TEXT=0xFFD6D6DB, TITLE=0xFFF1F1F4,
            SUBTLE=0xFF8B8B93, CLOSE=0xFF6B2B27, CLOSE_HOV=0xFF8A3531, GHOST=0xB01C1C24;

    private static final String[] COLORS={"white","orange","magenta","light_blue","yellow","lime","pink","gray",
            "light_gray","cyan","purple","blue","brown","green","red","black"};
    private static final String[] WOODS={"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry",
            "pale_oak","bamboo","crimson","warped"};
    private static final String[] FORMS={"_fence_gate","_hanging_sign","_pressure_plate","_glass_pane",
            "_concrete_powder","_glazed_terracotta","_stained_glass_pane","_shulker_box","_trapdoor","_fence",
            "_stairs","_slab","_wall","_door","_button","_sign","_planks","_log","_wood","_leaves","_sapling",
            "_carpet","_wool","_concrete","_terracotta","_bricks","_brick","_ingot","_nugget","_block","_ore",
            "_bed","_candle","_banner","_boat","_dye","_seeds","_bulb"};

    private enum SortMode { SMART_FAMILY("Smart(F)"), SMART_TYPE("Smart(T)"), ALPHA("A–Z"), COUNT("Count"); final String label; SortMode(String l){label=l;} }

    private List<Entry> entries = new ArrayList<>();
    private int upgradeCount;
    private long capacity;
    private int permLevel;

    private String selectedKey = null; // resolved to the first configured button on init
    private boolean showSections = ClientUiState.showSections();   // v1.2 section titles checkbox
    /** v1.2 sections: one virtual grid row -- a header (items empty) or up to {@code cols} items.
     *  With the checkbox off this is just the flat view chunked into rows. */
    private record VRow(String header, List<Entry> items) {}
    private final List<VRow> vrows = new ArrayList<>();
    private int secBoxX, secBoxW, pinBoxX, pinBoxW, ctrlY, titlesX; // Pin box + Titles checkbox (search row)
    private boolean ctrlVisible;
    private SortMode sortMode = SortMode.SMART_FAMILY;
    private boolean countDesc = true;
    private int scrollRow = 0, btnScroll = 0;
    private final List<Entry> view = new ArrayList<>();
    private final Map<String, String> nameCache = new HashMap<>();

    // layout (absolute unless noted Rel = panel-relative for real slots)
    private int px, py, pw, ph;
    private int railX, railW, railTop, railBottom;
    private int btnTop, btnBottom, btnSize = 20, btnGap = 2;
    private record BtnCell(ButtonLayout.BtnDef def, String section, int x, int y) {}
    private final List<BtnCell> btnCells = new ArrayList<>();
    private int btnMaxScroll, btnContentH;
    private static final int BTN_SECTION_GAP = 6, BTN_SECTION_HDR = 12;   // full-size header font
    private record SectionMark(String text, int y) {}
    private final List<SectionMark> btnSections = new ArrayList<>();
    private final Map<String, ItemStack> iconCache = new HashMap<>();
    private final Map<String, Long> btnTotals = new HashMap<>();   // per-sync cache (hover-lag fix)
    private final Map<String, Boolean> trinketCache = new HashMap<>();   // per-stack-key trinket test cache
    private int gridX, gridY, gridBottom, slot = 18, cols, rows;
    private int sbarX, sbarTop, sbarBottom;
    private int rpX, rpY;                       // right panel (native inventory) top-left, absolute
    private static final int RP_W = 176, RP_H = 166;
    private int quY;                            // quick-unload cluster row, absolute
    private int upgX, upgY, upgChestX, upgSize = 16;
    private int closeX, closeY, closeSize = 14;
    private final int[] sbX = new int[4]; private final int[] sbW = new int[4]; private int sbY, sbH = 14;
    private int depInvX, depInvW, depAllX, depAllW, depY;   // v1.1 Deposit: [Inventory] [All]
    // -- Sharing corner (v1.1) --
    private List<SharingStatePayload.Member> shMembers = new ArrayList<>();
    private List<SharingStatePayload.InviteEntry> shInvites = new ArrayList<>();
    private int shSelInvite = -1;   // index into shInvites (display selection); -1 = none -> most recent
    private int shTop, shBtnY, shListY, shListH, shMgmtY;
    private int shBtn1X, shBtn1W, shBtn2X, shBtn2W;
    private int shMg1X, shMg1W, shMg2X, shMg2W, shMg3X, shMg3W, shMgmtMode;
    private int shScroll = 0; private static final int SH_ROW_H = 12, SH_BTN_H = 13;
    private String shSelected = null;
    private boolean shInputActive = false; private String shInputText = "";
    private int srchY, goX, goW2;
    private int searchBoxX, searchBoxW;

    // scrollbar thumb drag -- dragThumbY stores the raw pixel Y so the thumb moves pixel-for-pixel
    private boolean draggingThumb;
    private int dragOffsetY;
    private int dragThumbY;

    private String searchText = "";
    private boolean searchFocused = false;

    // deposit-denied feedback: grid flashes red 3x; header total goes dark red until room exists
    private long flashEnd = 0;
    private boolean depositDenied = false;
    private boolean unloadBlocked = false;   // a container is parked in Quick Unload with no room

    private static final int MARGIN = 14, DESIGN_W = 600, MIN_W = 380, MIN_H = 300, SB_W = 11;
    private static final float TAB_SCALE = 1.0f, LBL_SCALE = 1.0f;   // integer scale = crisp at 2x

    public BankVaultScreen(BankVaultMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    public void updateData(VaultSyncPayload data) {
        this.entries = new ArrayList<>(data.entries());
        this.upgradeCount = data.upgradeCount();
        this.capacity = data.capacity();
        this.permLevel = data.permLevel();
        if (railW > 0) recomputeButtons();
        resolveSelection();
        rebuild();
    }

    private int shRefreshTicks = 0;

    @Override
    protected void containerTick() {
        super.containerTick();
        // rc.4 (Dave): the host's member list must update live when someone accepts an invite.
        // Pushes cover the instant case; this heartbeat guarantees freshness even if one is missed.
        if (++shRefreshTicks >= 60) {   // every 3s
            shRefreshTicks = 0;
            ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.REFRESH, "", 0));
        }
    }

    public void updateSharing(SharingStatePayload data) {
        this.shMembers = new ArrayList<>(data.members());
        this.shInvites = new ArrayList<>(data.invites());
        if (shSelInvite >= shInvites.size()) shSelInvite = -1;
        if (shSelected != null) {
            boolean still = false;
            for (SharingStatePayload.Member m : shMembers) if (m.uuid().equals(shSelected)) { still = true; break; }
            if (!still) shSelected = null;
        }
    }

    /** rc.3: remainder of the alphabetically-first online player name that extends what's typed
     *  (case-insensitive prefix match, self excluded). Empty when nothing matches or the typed
     *  text already equals an online name exactly. */
    private String shCompletion() {
        if (shInputText.isEmpty() || this.minecraft == null
                || this.minecraft.getConnection() == null || this.minecraft.player == null) return "";
        String typed = shInputText.toLowerCase(java.util.Locale.ROOT);
        String me = this.minecraft.player.getGameProfile().name();
        String best = null;
        for (var info : this.minecraft.getConnection().getOnlinePlayers()) {
            String n = info.getProfile().name();
            if (n.equals(me)) continue;
            String ln = n.toLowerCase(java.util.Locale.ROOT);
            if (ln.equals(typed)) return "";                     // exact name typed: no ghost
            if (ln.startsWith(typed) && (best == null || n.compareToIgnoreCase(best) < 0)) best = n;
        }
        return best == null ? "" : best.substring(shInputText.length());
    }

    private SharingStatePayload.Member selectedMember() {
        if (shSelected == null) return null;
        for (SharingStatePayload.Member m : shMembers) if (m.uuid().equals(shSelected)) return m;
        return null;
    }

    private static String levelTag(int level) {
        return switch (level) { case 4 -> "Owner"; case 3 -> "Master"; case 2 -> "Member"; default -> "Deposit"; };
    }

    private String trimTo(String s, int w) {
        while (s.length() > 1 && this.font.width(s) > w) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void shButton(Gfx g, int x, int y, int w, String label, boolean enabled, int mx, int my) {
        boolean hov = enabled && inside(mx, my, x, y, w, SH_BTN_H);
        g.fill(x, y, x + w, y + SH_BTN_H, hov ? 0xFF3A3A42 : WELL);
        g.fill(x, y, x + w, y + 1, enabled ? ACCENT : SUBTLE);
        String l = trimTo(label, w - 6);
        g.text(this.font, l, x + (w - this.font.width(l)) / 2, y + 3, enabled ? TEXT : SUBTLE);
    }

    @Override
    protected void init() {
        super.init();
        ButtonLayout.pollConfig();
        Keywords.pollConfig();
        // Fill the screen: full vertical space at every GUI scale (no design-height cap).
        pw = Math.max(MIN_W, Math.min(DESIGN_W, this.width - 2 * MARGIN));
        // Height: just enough to fit the tab rail (or the right panel cluster), capped to the screen.
        // selection resolves against the button cells after recomputeButtons() below
        int tabsNeeded = 20;   // matches recomputeButtons: headers cost HDR + 3 pre-gap
        for (ButtonLayout.Row lr : ButtonLayout.rows())
            tabsNeeded += lr.section() != null ? BTN_SECTION_HDR + 3
                    : lr.buttons().isEmpty() ? BTN_SECTION_GAP : ButtonLayout.buttonSize() + 2;
        int tRowsPre = (this.menu.trinketSlotCount + 8) / 9;
        int clusterNeeded = RP_H + 6 + 18 + 14 + (tRowsPre > 0 ? tRowsPre * 18 + 4 : 0) + 81;   // +81: Sharing corner (v1.1)
        int gridNeeded = sbH + 6 + 6 * 18 + 6 + sbH;             // sort row + 6 grid rows min + search row
        int contentNeeded = Math.max(Math.max(tabsNeeded, clusterNeeded), gridNeeded);
        ph = Math.max(MIN_H, Math.min(this.height - 2 * MARGIN, 32 + contentNeeded + 8));
        this.leftPos = (this.width - pw) / 2;
        this.topPos = (this.height - ph) / 2;
        // Vanilla uses imageWidth/imageHeight to decide what counts as "outside the window"
        // (hasClickedOutside). Wrong values -> releases throw items on the ground.
        this.imageWidth = pw;
        this.imageHeight = ph;
        px = leftPos; py = topPos;

        // --- single-line header ---
        int wellTop = py + 6, wellH = 22;
        upgChestX = px + 8 + (int)(this.font.width("Upgrades") * LBL_SCALE) + 6;
        upgX = upgChestX + 18;
        upgY = wellTop + (wellH - upgSize) / 2;
        closeX = px + pw - 8 - closeSize; closeY = wellTop + (wellH - closeSize) / 2;
        int contentTop = py + 32;

        // --- right panel (native inventory) ---
        rpX = px + pw - 8 - RP_W;
        rpY = contentTop;                                        // top-aligned player area
        quY = rpY + RP_H + 6;

        // Sharing corner (v1.1): below the quick-unload cluster and trinket rows, to the panel bottom.
        int tRowsSh = (this.menu.trinketSlotCount + 8) / 9;
        shTop = quY + 34 + tRowsSh * 18 + (tRowsSh > 0 ? 4 : 0);
        shBtn1X = rpX + 8; shBtn1W = (RP_W - 16 - 4) / 2;
        shBtn2X = shBtn1X + shBtn1W + 4; shBtn2W = shBtn1W;
        shBtnY = shTop + 11;
        shMgmtY = py + ph - 8 - SH_BTN_H;
        shListY = shBtnY + SH_BTN_H + 4;
        shListH = Math.max(0, shMgmtY - 4 - shListY);
        if (shBtnY + SH_BTN_H > py + ph - 8) shTop = 0;   // no room at this scale: hide the corner

        // --- full-height left rail ---
        railX = px + 8;
        btnSize = ButtonLayout.buttonSize();
        int maxCols = 1;
        for (ButtonLayout.Row r : ButtonLayout.rows()) maxCols = Math.max(maxCols, r.buttons().size());
        railW = Math.max(30, Math.min(170, maxCols * (btnSize + btnGap) - btnGap + 10));   // 170: 8 cols at size 18 (Dave r26); grid drops to 11 cols
        railTop = contentTop; railBottom = py + ph - 8;
        btnTop = railTop + 5; btnBottom = railBottom - 5;

        // --- middle column: sort row on top, grid stretching down, search bar at the bottom ---
        gridX = railX + railW + 8;
        sbY = contentTop;
        SortMode[] modes = SortMode.values();
        int bx = gridX;
        for (int i = 0; i < 4; i++) {
            sbW[i] = this.font.width(modes[i] == SortMode.COUNT ? "Count ↓" : modes[i].label) + 10;
            sbX[i] = bx;
            bx += sbW[i] + 4;
        }
        int gridAvailRight = rpX - 8 - SB_W - 4;
        cols = Math.max(1, Math.min(BankVaultMenu.VIEW_COLS, (gridAvailRight - gridX) / slot));
        sbarX = gridX + cols * slot + 4;
        srchY = py + ph - 8 - sbH;
        gridY = sbY + sbH + 6;
        gridBottom = srchY - 6;
        rows = Math.max(1, Math.min(BankVaultMenu.VIEW_ROWS, (gridBottom - gridY) / slot));
        sbarTop = gridY; sbarBottom = gridBottom;

        // search bar spans the grid + scrollbar width, go button at the right end
        goW2 = this.font.width("↵") + 10;
        goX = sbarX + SB_W - goW2;
        searchBoxX = gridX;
        // v1.2: "Pin" drop box + "Titles" checkbox live on the search row, left of the go
        // button -- the sort row is always too tight for them (alpha.15 hid them there).
        ctrlY = srchY;
        secBoxW = 12;
        pinBoxW = this.font.width("Pin") + 8;
        secBoxX = goX - 6 - secBoxW;
        titlesX = secBoxX - 2 - this.font.width("Titles");
        pinBoxX = titlesX - 8 - pinBoxW;
        ctrlVisible = pinBoxX >= searchBoxX + 64;                  // keep a usable search box
        searchBoxW = Math.max(60, (ctrlVisible ? pinBoxX : goX) - 4 - searchBoxX);

        positionRealSlots();
        recomputeButtons();
        resolveSelection();
        rebuild();
    }

    /**
     * Resolve the selected category once buttons exist. Buttons are built from the vault contents,
     * which arrive in VaultSyncPayload AFTER the menu opens -- so at init() btnCells is usually
     * empty and the remembered tab can't be matched yet. This runs again from updateData() when the
     * contents land, so the last-tab restore (and the first-button fallback) actually take effect.
     * No-op while buttons are empty; never overrides a still-valid current selection.
     */
    private void resolveSelection() {
        if (btnCells.isEmpty()) return;
        if (selectedKey == null && !ClientUiState.lastTab().isEmpty()
                && btnCells.stream().anyMatch(b -> b.def().key().equals(ClientUiState.lastTab()))) {
            selectedKey = ClientUiState.lastTab();             // v1.2: restore last tab examined
            applySortString(ClientUiState.sortFor(selectedKey));
        }
        if (btnCells.stream().noneMatch(b -> b.def().key().equals(selectedKey)))
            selectedKey = btnCells.get(0).def().key();
    }

    /** Position every real slot (panel-relative coordinates; Slot.x/y are mutable via accesswidener). */
    private void positionRealSlots() {
        List<Slot> slots = this.menu.slots;
        int rx = rpX - px, ry = rpY - py;
        for (int i = 0; i < 27 && i < slots.size(); i++) {           // main inventory 3x9
            Slot s = slots.get(i);
            s.x = rx + 8 + (i % 9) * 18;
            s.y = ry + 84 + (i / 9) * 18;
        }
        for (int i = 0; i < 9 && 27 + i < slots.size(); i++) {       // hotbar
            Slot s = slots.get(27 + i);
            s.x = rx + 8 + i * 18;
            s.y = ry + 142;
        }
        if (slots.size() > BankVaultMenu.UNLOAD_SLOT) {              // quick-unload cluster
            Slot u = slots.get(BankVaultMenu.UNLOAD_SLOT);
            u.x = rx + 109; u.y = quY - py + 1;
        }
        if (slots.size() > BankVaultMenu.GRAB_SLOT) {
            Slot gg = slots.get(BankVaultMenu.GRAB_SLOT);
            gg.x = rx + 135; gg.y = quY - py + 1;
        }
        if (slots.size() > BankVaultMenu.UPGRADE_SLOT) {
            Slot up = slots.get(BankVaultMenu.UPGRADE_SLOT);
            up.x = upgX - px; up.y = upgY - py;
        }
        for (int i = 0; i < 4 && BankVaultMenu.ARMOR_FIRST + i < slots.size(); i++) {  // armor column
            Slot s = slots.get(BankVaultMenu.ARMOR_FIRST + i);
            s.x = rx + 8; s.y = ry + 8 + i * 18;
        }
        if (slots.size() > BankVaultMenu.OFFHAND_SLOT) {             // offhand, under the preview
            Slot s = slots.get(BankVaultMenu.OFFHAND_SLOT);
            s.x = rx + 43; s.y = ry + 62;
        }
        for (int i = 0; i < 9 && BankVaultMenu.CRAFT_FIRST + i < slots.size(); i++) {  // full 3x3 crafting
            Slot s = slots.get(BankVaultMenu.CRAFT_FIRST + i);
            s.x = rx + 72 + (i % 3) * 18; s.y = ry + 8 + (i / 3) * 18;
        }
        if (slots.size() > BankVaultMenu.CRAFT_RESULT) {             // crafting result
            Slot s = slots.get(BankVaultMenu.CRAFT_RESULT);
            s.x = rx + 148; s.y = ry + 26;
        }
        for (int i = 0; i < this.menu.trinketSlotCount; i++) {       // trinkets/backpack row(s), 9 wide
            int si = BankVaultMenu.TRINKET_FIRST + i;
            if (si >= slots.size()) break;
            Slot s = slots.get(si);
            s.x = rx + 8 + (i % 9) * 18;
            s.y = (quY - py) + 34 + (i / 9) * 18;
        }
    }

    private void clampBtnScroll() { btnScroll = Math.max(0, Math.min(btnScroll, btnMaxScroll)); }

    /** Build the button cells from the layout rows (v1.2 schema v2: category ids or
     *  keyword-word buttons). Hidden when the backing data has no records; Uncategorized
     *  shows only while the vault actually holds uncategorized items. */
    private void recomputeButtons() {
        btnCells.clear();
        iconCache.clear();
        btnTotals.clear();
        trinketCache.clear();
        int step = btnSize + btnGap;
        int y = 0;
        btnSections.clear();
        String curSection = "";
        for (ButtonLayout.Row lr : ButtonLayout.rows()) {
            if (lr.section() != null) {                              // labeled section header
                curSection = lr.section();
                if (y > 0) y += 3;
                btnSections.add(new SectionMark(lr.section(), y));
                y += BTN_SECTION_HDR;
                continue;
            }
            List<ButtonLayout.BtnDef> row = lr.buttons();
            if (row.isEmpty()) { if (y > 0) y += BTN_SECTION_GAP; continue; }   // "gap" section break
            int col = 0;
            for (ButtonLayout.BtnDef d : row) {
                if (!buttonVisible(d)) continue;
                btnCells.add(new BtnCell(d, curSection, railX + 5 + col * step, y));
                col++;
            }
            if (col > 0) y += step;
        }
        btnContentH = y;
        int viewH = Math.max(1, btnBottom - btnTop);
        btnMaxScroll = Math.max(0, (int) Math.ceil((btnContentH - viewH) / (double) step));
        clampBtnScroll();
    }

    private boolean buttonVisible(ButtonLayout.BtnDef d) {
        if ("everything".equals(d.dynamic())) return !entries.isEmpty();
        // rc.7 (Dave): visibility follows the VAULT CONTENTS -- a button shows only while the
        // bank holds at least one item under it. Depositing reveals its buttons; emptying a
        // category hides them again. This also subsumes the absent-mod concern: items from
        // mods that are not loaded can never be deposited, so their buttons can never appear.
        return btnTotal(d) > 0;
    }

    /** The button definition currently selected, or null. */
    private ButtonLayout.BtnDef selectedDef() {
        for (BtnCell bc : btnCells) if (bc.def().key().equals(selectedKey)) return bc.def();
        return null;
    }

    /** Does this vault entry belong under the given button? A button may carry BOTH a word
     *  list and a dynamic matcher -- membership is the union of the two. */
    private boolean matchesDef(ButtonLayout.BtnDef d, Entry e) {
        if (d.category() != null) {
            // rc.3 (Dave): "uncategorized" = matched by NO other button. The old categories.json
            // -only test surfaced items that already have keyword-button homes.
            if ("uncategorized".equals(d.category())) return isUncategorized(e);
            return Catalog.inTab(idOf(e), d.category());
        }
        if (d.words() != null && Keywords.itemHasAny(idOf(e), d.words())) return true;
        if ("everything".equals(d.dynamic())) return true;   // rc.3: Everything holds it all
        if ("trinkets".equals(d.dynamic())) return isTrinketCached(e);
        return false;
    }

    /** True when no button in the layout (besides Everything / Uncategorized itself) claims this
     *  entry. Walks ButtonLayout.rows() directly so button visibility cannot skew the answer. */
    private boolean isUncategorized(Entry e) {
        String id = idOf(e);
        for (ButtonLayout.Row lr : ButtonLayout.rows()) {
            if (lr.section() != null || lr.buttons() == null) continue;
            for (ButtonLayout.BtnDef d : lr.buttons()) {
                if (d.category() != null) {
                    if (!"uncategorized".equals(d.category()) && Catalog.inTab(id, d.category())) return false;
                    continue;
                }
                if (d.words() != null && Keywords.itemHasAny(id, d.words())) return false;
                if ("trinkets".equals(d.dynamic()) && isTrinketCached(e)) return false;
            }
        }
        return true;
    }

    /** Dynamic trinket membership: ask the trinkets API whether any of the player's trinket
     *  slots accepts this stack. Cached per stack key; guarded so a missing/shifted trinkets
     *  mod can never crash the vault. */
    private boolean isTrinketCached(Entry e) {
        if (!BankVault.TRINKETS || this.minecraft == null || this.minecraft.player == null) return false;
        return trinketCache.computeIfAbsent(e.key(), k -> {
            // Dave: backpacks are NOT trinkets even though they occupy a trinket slot.
            if (Keywords.wordsFor(idOf(e)).contains("backpack")) return false;
            try { return TrinketCompat.isTrinket(e.stack(), this.minecraft.player); }
            catch (Throwable t) { return false; }
        });
    }

    /** Sort identity for the selected button: category id for category buttons, the stable
     *  button key ("kw:<label>" / "dyn:<id>") for keyword/dynamic buttons -- the same key the
     *  per-button entries in tabSort and the sort_family/sort_type files use. */
    private String sortKey() {
        ButtonLayout.BtnDef d = selectedDef();
        if (d == null) return "default";
        if (d.category() != null) return d.category();
        return d.key();
    }

    /** Serialize the active sort for persistence (v1.2 last-use memory). */
    private String sortString() {
        switch (sortMode) {
            case SMART_TYPE: return "type";
            case ALPHA: return "alpha";
            case COUNT: return countDesc ? "count_desc" : "count_asc";
            default: return "family";
        }
    }

    /** Apply a persisted sort string; null/unknown falls back to Smart(F). */
    private void applySortString(String sv) {
        if (sv == null) sv = "";
        switch (sv) {
            case "type": sortMode = SortMode.SMART_TYPE; break;
            case "alpha": sortMode = SortMode.ALPHA; break;
            case "count_desc": sortMode = SortMode.COUNT; countDesc = true; break;
            case "count_asc": sortMode = SortMode.COUNT; countDesc = false; break;
            default: sortMode = SortMode.SMART_FAMILY; break;
        }
    }

    /** Mirror the interaction locally and persist it server-side (v1.2 last-use memory). */
    private void sendUiState() {
        if (selectedKey == null) return;
        String sv = sortString();
        ClientUiState.remember(selectedKey, sv);
        ClientNet.sendToServer(new UiStatePayload(selectedKey, selectedKey, sv, ""));
    }

    private String btnLabel(ButtonLayout.BtnDef d) {
        if (d.category() != null) {
            Catalog.Tab t = Catalog.tab(d.category());
            return t != null ? t.name() : d.category();
        }
        return d.label();
    }

    private long btnTotal(ButtonLayout.BtnDef d) {
        return btnTotals.computeIfAbsent(d.key(), k -> {
            long t = 0;
            for (Entry e : entries) if (matchesDef(d, e)) t += e.count();
            return t;
        });
    }

    private BtnCell buttonAt(int mx, int my) {
        if (my < btnTop || my >= btnBottom) return null;
        int step = btnSize + btnGap;
        for (BtnCell bc : btnCells) {
            int by = btnTop + bc.y() - btnScroll * step;
            if (inside(mx, my, bc.x(), by, btnSize, btnSize)) return bc;
        }
        return null;
    }

    private ItemStack iconFor(ButtonLayout.BtnDef d) {
        return iconCache.computeIfAbsent(d.key(), k -> {
            String s = d.icon();
            if (d.category() != null) {
                Catalog.Tab t = Catalog.tab(d.category());
                s = t != null ? t.icon() : "";
            }
            if (s != null && !s.isEmpty()) {
                Identifier iid = Identifier.tryParse(s);
                if (iid != null && BuiltInRegistries.ITEM.containsKey(iid))
                    return new ItemStack(BuiltInRegistries.ITEM.getValue(iid));
            }
            return new ItemStack(Items.CHEST);
        });
    }

    private int totalRows() { return vrows.size(); }
    private int maxRow() { return Math.max(0, totalRows() - rows); }
    private void scrollBy(int d) { scrollRow = Math.max(0, Math.min(scrollRow + d, maxRow())); sendGridView(); }

    private void rebuild() {
        view.clear();
        String q = searchText == null ? "" : searchText.trim();
        boolean searching = !q.isEmpty();
        ButtonLayout.BtnDef selDef = selectedDef();
        for (Entry e : entries) {
            // search is GLOBAL: with text in the box, results come from the whole vault, not the tab
            if (!searching && selDef != null && !matchesDef(selDef, e)) continue;
            if (searching && !nameOf(e).contains(q)) continue;
            view.add(e);
        }
        view.sort(comparator());
        buildVRows(foundUnderGrouping());
        scrollRow = Math.max(0, Math.min(scrollRow, maxRow()));
        sendGridView();
    }

    /** v1.2 sections: chunk the sorted view into virtual rows. Checkbox off = flat rows (old
     *  behavior); on = header rows + ragged, top-left-aligned item rows per section. */
    private void buildVRows(boolean foundUnder) {
        vrows.clear();
        if (cols <= 0 || view.isEmpty()) return;
        if (!showSections) {
            for (int i = 0; i < view.size(); i += cols)
                vrows.add(new VRow(null, List.copyOf(view.subList(i, Math.min(i + cols, view.size())))));
            return;
        }
        Map<String, String> lbl = new HashMap<>();
        for (Entry e : view) lbl.put(e.key(), sectionLabel(e));
        if (foundUnder) {
            // Found-under labels don't follow the comparator: stable re-group, Pinned first
            view.sort(Comparator
                    .comparingInt((Entry e) -> "Pinned".equals(lbl.get(e.key())) ? 0 : 1)
                    .thenComparing(e -> lbl.get(e.key())));
        }
        String cur = null;
        List<Entry> run = new ArrayList<>();
        for (Entry e : view) {
            String l = lbl.get(e.key());
            if (!l.equals(cur)) {
                if (!run.isEmpty()) { vrows.add(new VRow(null, List.copyOf(run))); run.clear(); }
                vrows.add(new VRow(l, List.of()));
                cur = l;
            }
            run.add(e);
            if (run.size() == cols) { vrows.add(new VRow(null, List.copyOf(run))); run.clear(); }
        }
        if (!run.isEmpty()) vrows.add(new VRow(null, List.copyOf(run)));
    }

    /** Entry shown in visible cell (r,c), or null (empty cell / header row / out of range). */
    private Entry cellEntry(int r, int c) {
        int vi = scrollRow + r;
        if (vi < 0 || vi >= vrows.size() || c < 0 || c >= cols) return null;
        VRow vr = vrows.get(vi);
        if (vr.header() != null) return null;
        return c < vr.items().size() ? vr.items().get(c) : null;
    }

    /** Section header occupying visible row r, or null. */
    private String headerAt(int r) {
        int vi = scrollRow + r;
        return (vi >= 0 && vi < vrows.size()) ? vrows.get(vi).header() : null;
    }

    // -- sorting --

    private Comparator<Entry> comparator() {
        Comparator<Entry> base = baseComparator();
        ButtonLayout.BtnDef d = selectedDef();
        List<String> bp = (d == null || d.pins() == null) ? List.of() : d.pins();
        List<String> up = selectedKey == null ? List.of() : ClientUiState.pinsFor(selectedKey);
        if (bp.isEmpty() && up.isEmpty()) return base;
        // v1.2 (Dave): pins ALWAYS lead, regardless of sort mode -- user pins, then button pins
        Comparator<Entry> userPinned = Comparator.comparingInt(e -> {
            int i = up.indexOf(idOf(e));
            return i < 0 ? Integer.MAX_VALUE : i;
        });
        Comparator<Entry> pinned = Comparator.comparingInt(e -> {
            int i = bp.indexOf(idOf(e));
            return i < 0 ? Integer.MAX_VALUE : i;
        });
        return userPinned.thenComparing(pinned).thenComparing(base);
    }

    private boolean isPinned(String id) {
        if (selectedKey != null && ClientUiState.pinsFor(selectedKey).contains(id)) return true;
        ButtonLayout.BtnDef d = selectedDef();
        return d != null && d.pins() != null && d.pins().contains(id);
    }

    /** Section title for one entry under the ACTIVE sort (v1.2 sections checkbox). Labels are
     *  designed to form contiguous runs in comparator order; search mode re-groups explicitly. */
    private String sectionLabel(Entry e) {
        String id = idOf(e);
        if (isPinned(id)) return "Pinned";
        if (foundUnderGrouping()) {
            for (BtnCell bc : btnCells) {
                if ("everything".equals(bc.def().dynamic())) continue;   // it matches all by design
                if (matchesDef(bc.def(), e))
                    return everythingSelected()
                            ? btnLabel(bc.def()) + " (" + bc.section() + ")"   // rc.4 (Dave): A-Z by family name
                            : bc.section() + " \u2192 " + btnLabel(bc.def());
            }
            return "Elsewhere";
        }
        switch (sortMode) {
            case COUNT: return countBucket(e.count());
            case ALPHA: return alphaLetter(e);
            default: {
                String sk = sortKey();
                String mode = sortMode == SortMode.SMART_TYPE ? "type" : "family";
                String l = Catalog.groupLabel(sk, mode, id);
                if (l != null) return l;
                List<String> steps = Catalog.sortStepsExact(sk, mode);
                if (steps != null && steps.contains("potion")) {
                    String pk = potionEffectKey(e);
                    return pk.equals("~") ? "Other" : prettyWords(pk);
                }
                ButtonLayout.BtnDef d = selectedDef();
                if (d != null && d.category() == null && !Catalog.hasSortConfig(sk, mode)) {
                    Catalog.Tab t = Catalog.tab(Catalog.tabsFor(id).get(0));
                    return t != null ? t.name() : "Other";          // categorical fallback grouping
                }
                return d != null ? btnLabel(d) : "Items";           // single-section tab
            }
        }
    }

    /** Found-under grouping (Section -> Button) applies during global search AND on the
     *  Everything tab's Smart modes (rc.3, Dave: categorize Everything programmatically --
     *  no generated ranked lists; A-Z and Count keep their natural letter/range sections). */
    private boolean foundUnderGrouping() {
        if (!searchText.trim().isEmpty()) return true;
        return everythingSelected()
                && (sortMode == SortMode.SMART_FAMILY || sortMode == SortMode.SMART_TYPE);
    }

    private boolean everythingSelected() {
        ButtonLayout.BtnDef d = selectedDef();
        return d != null && "everything".equals(d.dynamic());
    }

    /** Count-mode section buckets (Dave's ranges). */
    private static String countBucket(long c) {
        if (c <= 100) return "1-100";
        if (c <= 1_000) return "101-1,000";
        if (c <= 10_000) return "1,001-10,000";
        if (c <= 100_000) return "10,001-100,000";
        return "100,000+";
    }

    /** A-Z section letter: taken from the word the tab actually sorts by (last word when the
     *  tab's alpha chain leads with "lastword"), absent letters simply never appear. */
    private String alphaLetter(Entry e) {
        String n = nameOf(e);
        List<String> steps = Catalog.sortStepsExact(sortKey(), "alpha");
        String word = (steps != null && !steps.isEmpty() && steps.get(0).equals("lastword")) ? lastWord(n) : n;
        char c = word.isEmpty() ? '#' : word.charAt(0);
        return Character.isLetter(c) ? String.valueOf(Character.toUpperCase(c)) : "#";
    }

    private static String prettyWords(String sv) {
        StringBuilder b = new StringBuilder();
        for (String w : sv.split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.length() == 0 ? "Other" : b.toString();
    }

    private Comparator<Entry> baseComparator() {
        switch (sortMode) {
            case COUNT: return (a, b) -> countDesc ? Long.compare(b.count(), a.count()) : Long.compare(a.count(), b.count());
            case ALPHA: return alpha();
            case SMART_TYPE: return everythingSelected() ? smartFamily() : smartType();   // rc.4: Everything mirrors family
            default: return smartFamily();
        }
    }
    /** SETTINGS-DRIVEN smart sort: interprets the step chain from categories.json tabSort.
     *  Steps: "name", "form", "oxidation", "color", "firstword", "lastword",
     *  "prefix:<list>", "tier:<list>" (ordered infix), "suffix:<list>". */
    /** v1.2 (Dave): keyword/dynamic buttons sort CATEGORICALLY -- items group by their
     *  primary catalog category (in category order), each group walking that category's
     *  curated order, then display name. Reads as: all the wood things together, all the
     *  redstone things together, in the same order the old tabs used. */
    private Comparator<Entry> categorical(String mode) {
        Comparator<Entry> byTab = Comparator.comparingInt(e -> primaryTabOrder(idOf(e)));
        Comparator<Entry> byCurated = Comparator.comparingInt(e -> {
            String id = idOf(e);
            return Catalog.orderIndex(Catalog.tabsFor(id).get(0), mode, id);
        });
        return byTab.thenComparing(byCurated).thenComparing(Comparator.comparing(this::nameOf));
    }

    private int primaryTabOrder(String itemId) {
        Catalog.Tab t = Catalog.tab(Catalog.tabsFor(itemId).get(0));
        return t == null ? Integer.MAX_VALUE : t.order();
    }

    private Comparator<Entry> smartFamily() { return smart("family"); }
    private Comparator<Entry> smartType() { return smart("type"); }
    private Comparator<Entry> smart(String mode) {
        ButtonLayout.BtnDef sd = selectedDef();
        String sk = sortKey();
        // v1.2: keyword/dynamic buttons with their own curated config use it; the categorical
        // grouping stays as the fallback for buttons that have none.
        if (sd != null && sd.category() == null && !Catalog.hasSortConfig(sk, mode)) return categorical(mode);
        return chain(sk, mode, Catalog.sortSteps(sk, mode));
    }

    /** v1.2: A-Z is the plain display-name sort unless the tab opts in with an "alpha" step
     *  chain in tabSort (e.g. ["lastword", "firstword"] -- group by the noun, then the variant). */
    private Comparator<Entry> alpha() {
        String sk = sortKey();
        List<String> steps = Catalog.sortStepsExact(sk, "alpha");
        if (steps == null || steps.isEmpty()) return Comparator.comparing(this::nameOf);
        return chain(sk, "alpha", steps);
    }

    /** Interpret a sort step chain (shared by Smart family/type and the per-tab alpha sort). */
    private Comparator<Entry> chain(String sk, String mode, List<String> steps) {
        Comparator<Entry> cmp = null;
        for (String step : steps) {
            Comparator<Entry> c;
            if (step.equals("list")) c = Comparator.comparingInt(e -> Catalog.orderIndex(sk, mode, idOf(e)));
            else if (step.equals("name")) c = Comparator.comparing(this::nameOf);
            else if (step.equals("form")) { String[] arr = Catalog.sortList("forms").length > 0 ? Catalog.sortList("forms") : FORMS; c = Comparator.comparingInt(e -> suffixIndex(idOf(e), arr)); }
            else if (step.equals("oxidation")) c = Comparator.comparingInt(e -> oxidation(idOf(e)));
            else if (step.equals("color")) c = Comparator.comparingInt(e -> { int k = Catalog.colorOf(idOf(e)); return k < 0 ? 99 : k; });
            else if (step.equals("firstword")) c = Comparator.comparing(e -> firstWord(nameOf(e)));
            else if (step.equals("lastword")) c = Comparator.comparing(e -> lastWord(nameOf(e)));
            else if (step.equals("enchant")) c = Comparator.comparing(BankVaultScreen::enchantKey);
            else if (step.equals("potion")) c = Comparator.comparing(BankVaultScreen::potionEffectKey);
            else if (step.equals("potionpower")) c = Comparator.comparingInt(BankVaultScreen::potionPower);
            else if (step.startsWith("prefix:")) { String[] arr = Catalog.sortList(step.substring(7)); c = Comparator.comparingInt(e -> prefixIndex(idOf(e), arr)); }
            else if (step.startsWith("tier:")) { String[] arr = Catalog.sortList(step.substring(5)); c = Comparator.comparingInt(e -> infixIndex(idOf(e), arr)); }
            else if (step.startsWith("suffix:")) { String[] arr = Catalog.sortList(step.substring(7)); c = Comparator.comparingInt(e -> suffixIndex(idOf(e), arr)); }
            else continue;
            cmp = cmp == null ? c : cmp.thenComparing(c);
        }
        return cmp == null ? Comparator.comparing(this::nameOf) : cmp;
    }
    private static String idOf(Entry e) { return BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString(); }
    private String nameOf(Entry e) { return nameCache.computeIfAbsent(e.key(), k -> e.stack().getHoverName().getString().toLowerCase(Locale.ROOT)); }
    private static String path(String id) { int i = id.indexOf(':'); return i >= 0 ? id.substring(i + 1) : id; }
    private static String lastWord(String n) { String[] t = n.trim().split("\\s+"); return t.length == 0 ? n : t[t.length - 1]; }
    private static String firstWord(String n) { String[] t = n.trim().split("\\s+"); return t.length == 0 ? n : t[0]; }
    private String formKey(String id) { String p = path(id); String[] forms = Catalog.sortList("forms"); if (forms.length == 0) forms = FORMS; for (String f : forms) if (p.endsWith(f)) return f; return "~" + p; }
    private static int infixIndex(String id, String[] arr) { String p = path(id); for (int i = 0; i < arr.length; i++) if (p.contains(arr[i])) return i; return arr.length; }
    private static int suffixIndex(String id, String[] arr) { String p = path(id); for (int i = 0; i < arr.length; i++) if (p.endsWith(arr[i])) return i; return arr.length; }
    /** Effect-base key for potion-carrying stacks ("night_vision" for normal/long/strong alike);
     *  non-potion items sort after all potions via the tilde prefix. */
    private static String potionEffectKey(Entry e) {
        var pc = e.stack().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (pc == null || pc.potion().isEmpty()) return "~";
        String p = pc.potion().get().getRegisteredName();
        p = p.substring(p.indexOf(':') + 1);
        if (p.startsWith("long_")) p = p.substring(5);
        if (p.startsWith("strong_")) p = p.substring(7);
        return p;
    }

    /** 0 = plain, 1 = long, 2 = strong -- "logically sub-sorted by effect power". */
    private static int potionPower(Entry e) {
        var pc = e.stack().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (pc == null || pc.potion().isEmpty()) return 0;
        String p = pc.potion().get().getRegisteredName();
        if (p.contains(":long_")) return 1;
        if (p.contains(":strong_")) return 2;
        return 0;
    }

    /** Sort key for enchanted books (all share one hover name): first stored enchantment id, then level. */
    private static String enchantKey(Entry e) {
        var stored = e.stack().get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return "~";
        var en = stored.entrySet().iterator().next();
        return en.getKey().getRegisteredName() + String.format("%02d", en.getIntValue());
    }
    private static int prefixIndex(String id, String[] arr) { String p = path(id); for (int i = 0; i < arr.length; i++) if (p.startsWith(arr[i] + "_") || p.equals(arr[i])) return i; return arr.length; }
    private int oxidation(String id) { String p = path(id); int b = p.contains("oxidized") ? 3 : p.contains("weathered") ? 2 : p.contains("exposed") ? 1 : 0; return b + (p.startsWith("waxed_") ? 4 : 0); }

    // -- render --

    private void textScaled(Gfx g, String s, int x, int y, int color, float sc) {
        g.pushMatrix();
        g.translate(x, y);
        g.scale(sc, sc);
        g.text(this.font, s, 0, 0, color);
        g.popMatrix();
    }

    /* [[[cog
    import compat_core
    compat_core.emit_screen_entries(cog, ver)
    ]]] */
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
        Gfx g = Gfx.of(gg);
        g.fill(0, 0, this.width, this.height, OVERLAY);
        drawCustom(g, mouseX, mouseY);
        this.hoveredSlot = null;
        for (Slot s : this.menu.slots) if (s.isActive() && isHovering(s.x, s.y, 16, 16, mouseX, mouseY)) { this.hoveredSlot = s; break; }
        g.pushMatrix();
        g.translate(this.leftPos, this.topPos);
        this.extractSlots(gg, mouseX, mouseY);
        g.popMatrix();
        this.extractCarriedItem(gg, mouseX, mouseY);
        this.extractTooltip(gg, mouseX, mouseY);
        renderHoverTooltips(g, mouseX, mouseY);
    }
    /* [[[end]]] */

    /** Grid-item + section-button hover tooltips (shared by every era's render entry). */
    private void renderHoverTooltips(Gfx g, int mouseX, int mouseY) {
        Entry h = gridItemAt(mouseX, mouseY);
        if (h != null) {
            if (!searchText.trim().isEmpty()) {   // searching: tell them WHERE it lives (decision 2026-06)
                List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(h.stack()));
                boolean first = true;
                for (BtnCell bc2 : btnCells) {
                    if (!matchesDef(bc2.def(), h)) continue;
                    if (first) {
                        lines.add(Component.literal("Found under:")
                                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
                        first = false;
                    }
                    lines.add(Component.literal(bc2.section() + " \u2192 " + btnLabel(bc2.def()))
                            .withStyle(net.minecraft.ChatFormatting.BOLD));
                }
                g.setTooltipForNextFrame(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
            } else {
                g.setTooltipForNextFrame(this.font, h.stack(), mouseX, mouseY);
            }
        }
        BtnCell bt = buttonAt(mouseX, mouseY);
        if (bt != null) g.setTooltipForNextFrame(this.font,
                Component.literal(btnLabel(bt.def()) + " \u2014 " + abbrev(btnTotal(bt.def()))), mouseX, mouseY);
    }

    private void drawCustom(Gfx g, int mouseX, int mouseY) {
        panel(g, px, py, pw, ph, true);

        // header
        g.fill(px + 6, py + 6, px + pw - 6, py + 28, WELL);
        textScaled(g, "Upgrades", px + 8, py + 12, TEXT, LBL_SCALE);
        g.item(new ItemStack(Items.CHEST), upgChestX, upgY);
        g.fill(upgX, upgY, upgX + upgSize, upgY + upgSize, SLOT_BG);
        g.item(new ItemStack(Items.CHEST), upgX, upgY);
        g.fill(upgX, upgY, upgX + upgSize, upgY + upgSize, GHOST);
        g.text(this.font, upgradeCount + "/64", upgX + upgSize + 4, py + 11, upgradeCount >= 64 ? ACCENT : TEXT);
        String title = "Bank Vault";
        g.text(this.font, title, px + (pw - this.font.width(title)) / 2, py + 11, TITLE);
        long total = 0; for (Entry e : entries) total += e.count();
        String stats = String.format("%,d / %,d", total, capacity);
        boolean vaultFull = total >= capacity;
        if (!vaultFull) depositDenied = false;            // issue resolved -> clear the latch
        int statsColor = vaultFull ? (depositDenied ? 0xFF9B2520 : 0xFFF5D83A) : TEXT;
        g.text(this.font, stats, closeX - 8 - this.font.width(stats), py + 11, statsColor);
        boolean closeHov = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        g.fill(closeX, closeY, closeX + closeSize, closeY + closeSize, closeHov ? CLOSE_HOV : CLOSE);
        g.text(this.font, "✕", closeX + (closeSize - this.font.width("✕")) / 2, closeY + 3, TITLE);

        // left rail + category buttons (v1.2: buttons instead of tabs)
        panel(g, railX, railTop, railW, railBottom - railTop, false);
        int bStep = btnSize + btnGap;
        g.enableScissor(railX + 1, btnTop, railX + railW - 1, btnBottom);
        for (SectionMark sm : btnSections) {
            int sy = btnTop + sm.y() - btnScroll * bStep;
            if (sy + 8 < btnTop || sy > btnBottom) continue;
            // Dave (2026-06-07): 0.75x was unreadable at 2x -- same font/size as the grid
            // section titles (full-size, see FONT RULE: never fractional-scale UI text), white.
            textScaled(g, sm.text(), railX + 5, sy + 2, 0xFFFFFFFF, 1.0f);
        }
        for (BtnCell cell : btnCells) {
            int bx = cell.x(), by = btnTop + cell.y() - btnScroll * bStep;
            if (by + btnSize < btnTop || by > btnBottom) continue;
            boolean sel = cell.def().key().equals(selectedKey);
            boolean hov = inside(mouseX, mouseY, bx, by, btnSize, btnSize);
            g.fill(bx, by, bx + btnSize, by + btnSize, sel ? 0xFF4A3A12 : (hov ? 0xFF3A3A42 : WELL));
            if (sel) {
                g.fill(bx, by, bx + btnSize, by + 1, ACCENT);
                g.fill(bx, by + btnSize - 1, bx + btnSize, by + btnSize, ACCENT);
                g.fill(bx, by, bx + 1, by + btnSize, ACCENT);
                g.fill(bx + btnSize - 1, by, bx + btnSize, by + btnSize, ACCENT);
            }
            String ic = cell.def().icon();
            if (ic != null && ic.startsWith("texture:")) {   // baked composite icons (v1.2)
                Identifier tid = Identifier.tryParse(ic.substring(8));
                if (tid != null) g.blitGuiTextured(tid,
                        bx + (btnSize - 16) / 2, by + (btnSize - 16) / 2, 0f, 0f, 16, 16, 16, 16);
            } else {
                g.item(iconFor(cell.def()), bx + (btnSize - 16) / 2, by + (btnSize - 16) / 2);
            }
        }
        g.disableScissor();
        if (btnScroll > 0) g.text(this.font, "\u25b2", railX + railW - 12, railTop + 2, SUBTLE);
        if (btnScroll < btnMaxScroll) g.text(this.font, "\u25bc", railX + railW - 12, railBottom - 10, SUBTLE);

        // sort buttons (top of the grid column)
        SortMode[] modes = SortMode.values();
        for (int i = 0; i < 4; i++) {
            boolean active = sortMode == modes[i];
            boolean hov = inside(mouseX, mouseY, sbX[i], sbY, sbW[i], sbH);
            g.fill(sbX[i], sbY, sbX[i] + sbW[i], sbY + sbH, active ? 0xFF4A3A12 : (hov ? 0xFF3A3A42 : WELL));
            if (active) g.fill(sbX[i], sbY, sbX[i] + sbW[i], sbY + 1, ACCENT);
            String lbl = modes[i] == SortMode.COUNT ? ("Count " + (countDesc ? "↓" : "↑")) : modes[i].label;
            g.text(this.font, lbl, sbX[i] + (sbW[i] - this.font.width(lbl)) / 2, sbY + 3, active ? ACCENT : TEXT);
        }

        // v1.2: Pin drop box + "Titles" checkbox on the search row (left of the go button)
        if (ctrlVisible) {
            boolean carrying = !this.menu.getCarried().isEmpty();
            boolean hovP = inside(mouseX, mouseY, pinBoxX, ctrlY, pinBoxW, sbH);
            g.fill(pinBoxX, ctrlY, pinBoxX + pinBoxW, ctrlY + sbH, (hovP && carrying) ? 0xFF4A3A12 : WELL);
            int pb = carrying ? ACCENT : SUBTLE;
            g.fill(pinBoxX, ctrlY, pinBoxX + pinBoxW, ctrlY + 1, pb);
            g.fill(pinBoxX, ctrlY + sbH - 1, pinBoxX + pinBoxW, ctrlY + sbH, pb);
            g.fill(pinBoxX, ctrlY, pinBoxX + 1, ctrlY + sbH, pb);
            g.fill(pinBoxX + pinBoxW - 1, ctrlY, pinBoxX + pinBoxW, ctrlY + sbH, pb);
            g.text(this.font, "Pin", pinBoxX + (pinBoxW - this.font.width("Pin")) / 2, ctrlY + 3,
                    carrying ? ACCENT : TEXT);
            if (hovP && !carrying)
                g.setTooltipForNextFrame(this.font,
                        java.util.List.of(Component.literal("Drop a stack here to pin or unpin it for this tab (deposits it into the vault)")),
                        java.util.Optional.empty(), mouseX, mouseY);

            g.text(this.font, "Titles", titlesX, ctrlY + 3, showSections ? ACCENT : TEXT);
            int cy = ctrlY + (sbH - secBoxW) / 2;
            boolean hovS = inside(mouseX, mouseY, titlesX, ctrlY, secBoxX + secBoxW - titlesX, sbH);
            g.fill(secBoxX, cy, secBoxX + secBoxW, cy + secBoxW, hovS ? 0xFF3A3A42 : WELL);
            int cb2 = showSections ? ACCENT : SUBTLE;
            g.fill(secBoxX, cy, secBoxX + secBoxW, cy + 1, cb2);
            g.fill(secBoxX, cy + secBoxW - 1, secBoxX + secBoxW, cy + secBoxW, cb2);
            g.fill(secBoxX, cy, secBoxX + 1, cy + secBoxW, cb2);
            g.fill(secBoxX + secBoxW - 1, cy, secBoxX + secBoxW, cy + secBoxW, cb2);
            if (showSections) g.fill(secBoxX + 3, cy + 3, secBoxX + secBoxW - 3, cy + secBoxW - 3, ACCENT);
        }

        // vault grid (stretches to the search bar at the bottom)
        g.enableScissor(gridX, gridY, gridX + cols * slot, gridY + rows * slot);
        long flashLeft = flashEnd - System.currentTimeMillis();
        boolean flashOn = flashLeft > 0 && (flashLeft / 150) % 2 == 0;   // 3 blinks over ~900ms
        for (int r = 0; r < rows; r++) {
            String hdr = headerAt(r);
            int rowY = gridY + r * slot;
            if (hdr != null) {                                   // v1.2 section title row
                textScaled(g, hdr, gridX + 2, rowY + (slot - 8) / 2, ACCENT, 1.0f);
                int hw = this.font.width(hdr);
                if (gridX + hw + 8 < gridX + cols * slot - 4)
                    g.fill(gridX + hw + 8, rowY + slot / 2, gridX + cols * slot - 4, rowY + slot / 2 + 1, 0xFF3A3A42);
                continue;
            }
            for (int c = 0; c < cols; c++) {
                Entry e = cellEntry(r, c);
                if (e == null) continue;
                int sx = gridX + c * slot, sy = rowY;
                boolean hov = inside(mouseX, mouseY, sx, sy, slot, slot);
                g.fill(sx, sy, sx + slot - 1, sy + slot - 1, flashOn ? 0xFFA02820 : (hov ? 0xFF4A4A55 : SLOT_BG));
                g.item(e.stack(), sx + 1, sy + 1);
                g.itemDecorations(this.font, e.stack(), sx + 1, sy + 1, null);
                countText(g, abbrev(e.count()), sx, sy);
            }
        }
        g.disableScissor();

        // scrollbar
        drawScrollbar(g, mouseX, mouseY);

        // global search bar (bottom of the grid column)
        boolean sfFocused = searchFocused;
        g.fill(searchBoxX, srchY, searchBoxX + searchBoxW, srchY + sbH,
               sfFocused ? 0xFF1A1A28 : (inside(mouseX, mouseY, searchBoxX, srchY, searchBoxW, sbH) ? 0xFF252530 : SLOT_BG));
        int borderColor = sfFocused ? ACCENT : SUBTLE;
        g.fill(searchBoxX,                  srchY,           searchBoxX + searchBoxW, srchY + 1,       borderColor);
        g.fill(searchBoxX,                  srchY + sbH - 1, searchBoxX + searchBoxW, srchY + sbH,     borderColor);
        g.fill(searchBoxX,                  srchY,           searchBoxX + 1,          srchY + sbH,     borderColor);
        g.fill(searchBoxX + searchBoxW - 1, srchY,           searchBoxX + searchBoxW, srchY + sbH,     borderColor);
        boolean showHint = searchText.isEmpty() && !sfFocused;
        String displayStr = showHint ? "search entire vault..." : searchText;
        int displayColor = showHint ? SUBTLE : TEXT;
        while (displayStr.length() > 1 && this.font.width(displayStr) > searchBoxW - 6) {
            displayStr = displayStr.substring(1);
        }
        int textDrawX = searchBoxX + 3;
        int textDrawY = srchY + (sbH - 8) / 2;
        g.text(this.font, displayStr, textDrawX, textDrawY, displayColor);
        if (sfFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = textDrawX + this.font.width(displayStr);
            g.fill(cx, textDrawY - 1, cx + 1, textDrawY + 9, TEXT);
        }
        boolean goHov = inside(mouseX, mouseY, goX, srchY, goW2, sbH);
        g.fill(goX, srchY, goX + goW2, srchY + sbH, goHov ? 0xFF3A3A42 : WELL);
        String goIco = "↵";
        g.text(this.font, goIco, goX + (goW2 - this.font.width(goIco)) / 2, srchY + 3, TEXT);

        // --- right panel: native survival inventory replication ---
        panel(g, rpX, rpY, RP_W, RP_H, true);
        // player preview well (narrower -- the 3x3 crafting grid needs the width)
        g.fill(rpX + 28, rpY + 8, rpX + 68, rpY + 58, WELL);
        if (this.minecraft != null && this.minecraft.player != null) {
            g.entityInInventoryFollowsMouse(rpX + 28, rpY + 8, rpX + 68, rpY + 58,
                    20, 0.0625f, mouseX, mouseY, this.minecraft.player);
        }
        g.text(this.font, "→", rpX + 130, rpY + 30, SUBTLE);
        // vanilla-style slot boxes (exact vanilla palette + per-slot grid lines) for the whole
        // player area: armor, offhand, crafting, result, inventory, hotbar -- and trinket rows.
        for (int i = 0; i < this.menu.slots.size() && i < BankVaultMenu.VIEW_FIRST; i++) {
            if (i == BankVaultMenu.UPGRADE_SLOT || i == BankVaultMenu.UNLOAD_SLOT || i == BankVaultMenu.GRAB_SLOT) continue;
            Slot s = this.menu.slots.get(i);
            vanillaSlot(g, px + s.x, py + s.y);
        }
        for (int i = 0; i < this.menu.trinketSlotCount; i++) {
            int si = BankVaultMenu.TRINKET_FIRST + i;
            if (si >= this.menu.slots.size()) break;
            Slot s = this.menu.slots.get(si);
            vanillaSlot(g, px + s.x, py + s.y);
        }

        // Deposit buttons (v1.1): "Deposit:" + [Inventory] [All], right-aligned in the band
        // between the 3x3 crafting grid and the main inventory. All = 27 main + 9 hotbar;
        // Inventory = 27 main only. Strictly those slot ranges (server enforces too).
        {
            // rc.2 (Dave): half-size buttons -- short labels, tight padding, 11px tall
            depAllW = this.font.width("All") + 6;
            depInvW = this.font.width("Inv") + 6;
            depY = rpY + 68;
            depAllX = rpX + RP_W - 8 - depAllW;
            depInvX = depAllX - 3 - depInvW;
            String dlbl = "Deposit:";
            textScaled(g, dlbl, depInvX - 5 - (int) (this.font.width(dlbl) * LBL_SCALE), depY + 2, SUBTLE, LBL_SCALE);
            boolean ihov = inside(mouseX, mouseY, depInvX, depY, depInvW, 11);
            boolean ahov = inside(mouseX, mouseY, depAllX, depY, depAllW, 11);
            g.fill(depInvX, depY, depInvX + depInvW, depY + 11, ihov ? 0xFF3A3A42 : WELL);
            g.fill(depInvX, depY, depInvX + depInvW, depY + 1, SUBTLE);
            g.fill(depAllX, depY, depAllX + depAllW, depY + 11, ahov ? 0xFF3A3A42 : WELL);
            g.fill(depAllX, depY, depAllX + depAllW, depY + 1, SUBTLE);
            g.text(this.font, "Inv", depInvX + 3, depY + 2, TEXT);
            g.text(this.font, "All", depAllX + 3, depY + 2, TEXT);
        }

        // quick-unload cluster (below the inventory panel)
        textScaled(g, "Quick Unload", rpX + 8, quY + 5, SUBTLE, LBL_SCALE);
        panel(g, rpX + 108, quY, 18, 18, false);
        g.fill(rpX + 109, quY + 1, rpX + 125, quY + 17, 0xFF8B8B8B);   // vanilla slot face
        g.item(new ItemStack(Items.SHULKER_BOX), rpX + 109, quY + 1);
        g.fill(rpX + 109, quY + 1, rpX + 125, quY + 17, GHOST);
        panel(g, rpX + 134, quY, 18, 18, false);
        g.fill(rpX + 135, quY + 1, rpX + 151, quY + 17, 0xFF8B8B8B);   // vanilla slot face

        // Quick Unload container parked because the vault lacks room: flash once + "Bank Full" label
        ItemStack parked = this.menu.slots.size() > BankVaultMenu.UNLOAD_SLOT
                ? this.menu.slots.get(BankVaultMenu.UNLOAD_SLOT).getItem() : ItemStack.EMPTY;
        boolean blockedNow = false;
        if (!parked.isEmpty() && ContainerExtractor.isContainer(parked)) {
            long held = 0;
            List<ItemStack> all = ContainerExtractor.extractAll(parked);
            if (all != null) for (ItemStack s : all) held += s.getCount();
            blockedNow = held > Math.max(0, capacity - total);
        }
        if (blockedNow && !unloadBlocked) { flashEnd = System.currentTimeMillis() + 900; depositDenied = true; }
        unloadBlocked = blockedNow;
        if (unloadBlocked) {
            String bf = "Bank Full";
            textScaled(g, bf, rpX + 108 + 22 - (int)(this.font.width(bf) * LBL_SCALE) / 2, quY + 20, 0xFFE0524A, LBL_SCALE);
        }

        // --- Sharing corner (v1.1): buttons + member/invite list + management row ---
        shMgmtMode = 0;
        if (shTop > 0) {
            boolean inGroup = shMembers.size() > 1;
            boolean canInvite = permLevel >= 3;                  // Master+ may invite
            boolean hasInvite = !shInvites.isEmpty();
            textScaled(g, "Sharing", rpX + 8, shTop, SUBTLE, LBL_SCALE);
            String b1 = canInvite ? "Share Bank" : (inGroup ? "Leave Bank" : null);
            String b2 = inGroup ? (canInvite ? "Leave Bank" : null)
                                : ("Accept Invite" + (hasInvite ? " (" + shInvites.size() + ")" : ""));
            boolean b2On = inGroup ? canInvite : hasInvite;      // host w/ members never sees Accept
            if (b1 != null) shButton(g, shBtn1X, shBtnY, shBtn1W, b1, true, mouseX, mouseY);
            if (b2 != null) shButton(g, shBtn2X, shBtnY, shBtn2W, b2, b2On, mouseX, mouseY);

            if (shListH >= SH_ROW_H) {
                g.fill(rpX + 8, shListY, rpX + RP_W - 8, shListY + shListH, WELL);
                int yy = shListY + 2;
                if (shInputActive) {                              // inline invite-name entry
                    g.fill(rpX + 9, yy - 1, rpX + RP_W - 9, yy + 10, 0xFF1A1A28);
                    g.fill(rpX + 9, yy - 1, rpX + RP_W - 9, yy, ACCENT);          // focused border cue
                    boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                    if (shInputText.isEmpty()) {
                        // rc.2 (Dave): make "type here" unmistakable -- cursor blinks from the start
                        if (blink) g.fill(rpX + 12, yy - 1, rpX + 13, yy + 9, TEXT);
                        g.text(this.font, trimTo("type a name; Tab fills, Enter sends", RP_W - 28), rpX + 15, yy, SUBTLE);
                    } else {
                        // rc.3 (Dave): inline autocomplete -- grey remainder of the nearest online
                        // name; narrows as more letters are typed; Enter sends the completed name.
                        g.text(this.font, trimTo(shInputText, RP_W - 24), rpX + 12, yy, TEXT);
                        int cx2 = rpX + 12 + this.font.width(shInputText);
                        String ghost = shCompletion();
                        if (!ghost.isEmpty()) g.text(this.font, trimTo(ghost, RP_W - 24 - this.font.width(shInputText)), cx2 + 1, yy, SUBTLE);
                        if (blink) g.fill(cx2, yy - 1, cx2 + 1, yy + 9, TEXT);
                    }
                    yy += 13;
                }
                if (inGroup) {
                    int visRows = Math.max(0, (shListY + shListH - yy) / SH_ROW_H);
                    shScroll = Math.max(0, Math.min(shScroll, Math.max(0, shMembers.size() - visRows)));
                    String me = this.minecraft != null && this.minecraft.player != null
                            ? this.minecraft.player.getUUID().toString() : "";
                    for (int i = shScroll; i < shMembers.size() && yy + SH_ROW_H <= shListY + shListH; i++, yy += SH_ROW_H) {
                        SharingStatePayload.Member m = shMembers.get(i);
                        boolean self = m.uuid().equals(me);
                        boolean sel = m.uuid().equals(shSelected);
                        boolean hov = inside(mouseX, mouseY, rpX + 8, yy, RP_W - 16, SH_ROW_H);
                        if (sel) g.fill(rpX + 8, yy, rpX + RP_W - 8, yy + SH_ROW_H, 0xFF4A3A12);
                        else if (hov && !self) g.fill(rpX + 8, yy, rpX + RP_W - 8, yy + SH_ROW_H, 0xFF3A3A42);
                        // rc.4 (Dave): rank badges are Masters+ knowledge only
                        String tag = permLevel >= 3 ? levelTag(m.level()) : "";
                        g.text(this.font, trimTo((self ? "* " : "") + m.name(), RP_W - 20 - this.font.width(tag)),
                                rpX + 10, yy + 2, sel ? ACCENT : TEXT);
                        if (!tag.isEmpty()) g.text(this.font, tag, rpX + RP_W - 10 - this.font.width(tag), yy + 2, SUBTLE);
                    }
                } else if (hasInvite && !shInputActive) {
                    // newest first; click selects which invite Accept / Reject acts on
                    for (int d = 0; d < shInvites.size() && yy + SH_ROW_H <= shListY + shListH; d++, yy += SH_ROW_H) {
                        int idx = shInvites.size() - 1 - d;
                        SharingStatePayload.InviteEntry ie = shInvites.get(idx);
                        boolean sel = (shSelInvite == idx) || (shSelInvite < 0 && d == 0);
                        boolean hov = inside(mouseX, mouseY, rpX + 8, yy, RP_W - 16, SH_ROW_H);
                        if (sel) g.fill(rpX + 8, yy, rpX + RP_W - 8, yy + SH_ROW_H, 0xFF4A3A12);
                        else if (hov) g.fill(rpX + 8, yy, rpX + RP_W - 8, yy + SH_ROW_H, 0xFF3A3A42);
                        // rc.4 (Dave): the invitee is not told the offered rank
                        g.text(this.font, trimTo("From " + ie.from(), RP_W - 20), rpX + 10, yy + 2, sel ? ACCENT : TEXT);
                    }
                }
            }

            if (inGroup) {                                        // management row (selection-driven)
                SharingStatePayload.Member sel = selectedMember();
                if (sel != null && permLevel >= 3 && sel.level() < permLevel) {
                    shMgmtMode = 1;
                    shMg1W = this.font.width("Remove") + 8; shMg1X = rpX + 8;
                    shMg2W = this.font.width("+1") + 8;     shMg2X = shMg1X + shMg1W + 4;
                    shMg3W = this.font.width("-1") + 8;     shMg3X = shMg2X + shMg2W + 4;
                    shButton(g, shMg1X, shMgmtY, shMg1W, "Remove", true, mouseX, mouseY);
                    shButton(g, shMg2X, shMgmtY, shMg2W, "+1", sel.level() < 3, mouseX, mouseY);
                    shButton(g, shMg3X, shMgmtY, shMg3W, "-1", sel.level() > 1, mouseX, mouseY);
                }
            } else if (hasInvite) {
                shMgmtMode = 2;
                shMg1W = this.font.width("Reject Invite") + 8; shMg1X = rpX + 8;
                shButton(g, shMg1X, shMgmtY, shMg1W, "Reject Invite", true, mouseX, mouseY);
            }
        }
    }

    private void drawScrollbar(Gfx g, int mouseX, int mouseY) {
        g.fill(sbarX, sbarTop, sbarX + SB_W, sbarBottom, WELL);
        boolean upHov = inside(mouseX, mouseY, sbarX, sbarTop, SB_W, SB_W);
        boolean dnHov = inside(mouseX, mouseY, sbarX, sbarBottom - SB_W, SB_W, SB_W);
        g.fill(sbarX, sbarTop, sbarX + SB_W, sbarTop + SB_W, upHov ? 0xFF3A3A42 : SLOT_BG);
        g.fill(sbarX, sbarBottom - SB_W, sbarX + SB_W, sbarBottom, dnHov ? 0xFF3A3A42 : SLOT_BG);
        int arrW = this.font.width("▲");
        int arrOx = (SB_W - arrW) / 2;
        int arrOy = (SB_W - 8) / 2;
        g.text(this.font, "▲", sbarX + arrOx, sbarTop + arrOy, SUBTLE);
        g.text(this.font, "▼", sbarX + arrOx, sbarBottom - SB_W + arrOy, SUBTLE);
        int[] t = thumb();
        boolean thumbHov = draggingThumb || inside(mouseX, mouseY, t[0], t[1], t[2], t[3]);
        if (maxRow() > 0) {
            panel(g, t[0], t[1], t[2], t[3], !draggingThumb);
            g.pushMatrix();
            g.translate(t[0], t[1]);
            g.scale((float) SB_W / 16f, (float) SB_W / 16f);
            g.item(new ItemStack(Items.CHISELED_STONE_BRICKS), 0, 0);
            g.popMatrix();
            g.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], thumbHov ? 0x60FFFFFF : 0x80FFFFFF);
        } else {
            g.fill(t[0] + 1, t[1], t[0] + t[2] - 1, t[1] + t[3], SLOT_BG);
        }
    }

    /** Returns {x,y,w,h} for the scrollbar thumb. */
    private int[] thumb() {
        int trackTop = sbarTop + SB_W, trackBot = sbarBottom - SB_W;
        int mr = maxRow();
        int h = SB_W;
        int y;
        if (draggingThumb) {
            y = dragThumbY;
        } else {
            int trackH = Math.max(SB_W, trackBot - trackTop);
            y = (mr <= 0) ? trackTop : trackTop + (int) Math.round((double) (trackH - h) * scrollRow / mr);
        }
        return new int[]{ sbarX, y, SB_W, h };
    }

    // -- input --

    /* [[[cog
    import compat_core
    compat_core.emit_input_head(cog, ver, "key")
    ]]] */
    @Override
    public boolean keyPressed(KeyEvent event0) { return keyPressedImpl(Ev.key(event0)); }

    private boolean keyPressedImpl(Ev event) {
    /* [[[end]]] */
        if (shInputActive) {
            if (event.input() == 258) { // TAB -- rc.4 (Dave): accept the autocomplete
                String ghost = shCompletion();
                if (!ghost.isEmpty()) shInputText = shInputText + ghost;
                return true;
            }
            if (event.input() == 259) { // BACKSPACE
                if (!shInputText.isEmpty()) shInputText = shInputText.substring(0, shInputText.length() - 1);
                return true;
            }
            if (event.isEscape()) { shInputActive = false; return true; }
            if (event.isConfirmation()) {
                String name = (shInputText + shCompletion()).trim();   // rc.3: Enter takes the autocomplete
                shInputActive = false;
                if (!name.isEmpty())
                    ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.INVITE, name, 1));
                return true;
            }
            return true; // consume all other keys while the name field is focused
        }
        if (searchFocused) {
            if (event.input() == 259) { // BACKSPACE
                if (!searchText.isEmpty()) { searchText = searchText.substring(0, searchText.length() - 1); rebuild(); }
                return true;
            }
            if (event.isEscape()) { searchFocused = false; return true; }
            if (event.isConfirmation()) { searchFocused = false; return true; }
            return true; // consume all other keys while focused
        }
        /* [[[cog
        import compat_core
        compat_core.emit_input_tail(cog, ver, "key")
        ]]] */
return super.keyPressed((net.minecraft.client.input.KeyEvent) event.raw());
        /* [[[end]]] */
    }

    /* [[[cog
    import compat_core
    compat_core.emit_input_head(cog, ver, "chr")
    ]]] */
    @Override
    public boolean charTyped(CharacterEvent event0) { return charTypedImpl(Ev.chr(event0)); }

    private boolean charTypedImpl(Ev event) {
    /* [[[end]]] */
        if (shInputActive) {
            if (event.isAllowedChatCharacter() && shInputText.length() < 16) {
                shInputText = shInputText + event.codepointAsString();
            }
            return true;
        }
        if (searchFocused) {
            if (event.isAllowedChatCharacter() && searchText.length() < 32) {
                searchText = searchText + event.codepointAsString();
                rebuild();
            }
            return true;
        }
        /* [[[cog
        import compat_core
        compat_core.emit_input_tail(cog, ver, "chr")
        ]]] */
return super.charTyped((net.minecraft.client.input.CharacterEvent) event.raw());
        /* [[[end]]] */
    }

    /* [[[cog
    import compat_core
    compat_core.emit_input_head(cog, ver, "click")
    ]]] */
    @Override
    public boolean mouseClicked(MouseButtonEvent event0, boolean doubleClick) { return mouseClickedImpl(Ev.mouse(event0), doubleClick); }

    private boolean mouseClickedImpl(Ev event, boolean doubleClick) {
    /* [[[end]]] */
        int mx = (int) event.x(), my = (int) event.y(), button = event.button();

        if (!inside(mx, my, searchBoxX, srchY, searchBoxW, sbH)) searchFocused = false;

        if (inside(mx, my, closeX, closeY, closeSize, closeSize)) { this.onClose(); return true; }

        // scrollbar
        if (inside(mx, my, sbarX, sbarTop, SB_W, sbarBottom - sbarTop)) {
            if (inside(mx, my, sbarX, sbarTop, SB_W, SB_W)) { scrollBy(-1); return true; }
            if (inside(mx, my, sbarX, sbarBottom - SB_W, SB_W, SB_W)) { scrollBy(1); return true; }
            int[] t = thumb();
            if (inside(mx, my, t[0], t[1], t[2], t[3])) {
                draggingThumb = true;
                dragOffsetY = my - t[1];
                dragThumbY = t[1];
                return true;
            }
            scrollBy(my < t[1] ? -rows : rows); return true;
        }

        // search field focus + go button
        if (inside(mx, my, searchBoxX, srchY, searchBoxW, sbH)) { searchFocused = true; return true; }
        if (inside(mx, my, goX, srchY, goW2, sbH)) { searchFocused = false; rebuild(); return true; }

        // v1.2: section-titles checkbox + Pin drop box (handled BEFORE anything that could
        // treat the click as an item drop -- the cursor stack is never touched here)
        if (ctrlVisible && inside(mx, my, titlesX, ctrlY, secBoxX + secBoxW - titlesX, sbH)) {
            showSections = !showSections;
            ClientUiState.rememberSections(showSections);
            ClientNet.sendToServer(new UiStatePayload(selectedKey == null ? "" : selectedKey, "", "",
                    showSections ? "on" : "off"));
            rebuild();
            return true;
        }
        if (ctrlVisible && inside(mx, my, pinBoxX, ctrlY, pinBoxW, sbH)) {
            ItemStack carried = this.menu.getCarried();
            if (!carried.isEmpty() && selectedKey != null) pinClick();
            return true;   // swallow the click either way: items can never drop here
        }

        SortMode[] modes = SortMode.values();
        for (int i = 0; i < 4; i++) if (inside(mx, my, sbX[i], sbY, sbW[i], sbH)) {
            if (modes[i] == SortMode.COUNT && sortMode == SortMode.COUNT) countDesc = !countDesc; else sortMode = modes[i];
            sendUiState();                                         // v1.2: persist per-tab sort
            rebuild(); return true;
        }
        BtnCell bc = buttonAt(mx, my);
        if (bc != null) {
            selectedKey = bc.def().key();
            scrollRow = 0;
            applySortString(ClientUiState.sortFor(selectedKey));   // v1.2: each tab remembers its sort
            sendUiState();
            rebuild();
            return true;
        }
        if (button == 1 && inside(mx, my, upgX, upgY, upgSize, upgSize) && permLevel >= 3
                && this.menu.getCarried().isEmpty()) { ClientNet.sendToServer(new UpgradePayload(false)); return true; }

        // Deposit buttons (v1.1): Inventory = main 27, All = main 27 + hotbar 9. Deposit perm required.
        if (button == 0 && permLevel >= 1 && this.menu.getCarried().isEmpty()) {
            if (inside(mx, my, depInvX, depY, depInvW, 11)) {
                ClientNet.sendToServer(new com.kishku7.bankvault.net.DepositAllPayload(false)); return true;
            }
            if (inside(mx, my, depAllX, depY, depAllW, 11)) {
                ClientNet.sendToServer(new com.kishku7.bankvault.net.DepositAllPayload(true)); return true;
            }
        }

        // Sharing corner (v1.1)
        if (shTop > 0 && button == 0) {
            boolean inGroup = shMembers.size() > 1;
            boolean canInvite = permLevel >= 3;
            boolean hasInvite = !shInvites.isEmpty();
            if (inside(mx, my, shBtn1X, shBtnY, shBtn1W, SH_BTN_H)) {
                if (canInvite) { shInputActive = true; shInputText = ""; searchFocused = false; }
                else if (inGroup) ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.LEAVE, "", 0));
                return true;
            }
            if (inside(mx, my, shBtn2X, shBtnY, shBtn2W, SH_BTN_H)) {
                if (inGroup) {
                    if (canInvite) ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.LEAVE, "", 0));
                } else if (hasInvite) {
                    // rc.2 (Dave): informed consent -- close the vault, confirm the merge, then accept.
                    // rc.3: acts on the SELECTED invite (default = most recent), named in the dialog.
                    SharingStatePayload.InviteEntry chosen = shSelInvite >= 0 && shSelInvite < shInvites.size()
                            ? shInvites.get(shSelInvite) : shInvites.get(shInvites.size() - 1);
                    var mc = this.minecraft;
                    this.onClose();
                    if (mc != null) {
                        com.kishku7.bankvault.BvCompat.setScreen(mc, new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                            if (yes) ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.ACCEPT, chosen.from(), 0));
                            com.kishku7.bankvault.BvCompat.setScreen(mc, null);
                        },
                        Component.literal("Accept " + chosen.from() + "'s Bank Invite?"),
                        Component.literal("If you accept this invite, all of your bank vault items will be merged into the shared bank. Do you agree?")));
                    }
                }
                return true;
            }
            if (shInputActive && inside(mx, my, rpX + 9, shListY + 1, RP_W - 18, 11)) {
                return true;   // rc.2 (Dave): clicking the name box must NOT cancel the invite entry
            }
            if (!inGroup && !shInvites.isEmpty() && !shInputActive && shListH >= SH_ROW_H
                    && inside(mx, my, rpX + 8, shListY, RP_W - 16, shListH)) {
                int row = (my - (shListY + 2)) / SH_ROW_H;
                if (my >= shListY + 2 && row >= 0 && row < shInvites.size()) {
                    int idx = shInvites.size() - 1 - row;            // rows render newest-first
                    shSelInvite = (shSelInvite == idx) ? -1 : idx;
                }
                return true;
            }
            if (inGroup && shListH >= SH_ROW_H && inside(mx, my, rpX + 8, shListY, RP_W - 16, shListH)) {
                int yy0 = shListY + 2 + (shInputActive ? 13 : 0);
                int row = (my - yy0) / SH_ROW_H;
                int idx = shScroll + row;
                if (my >= yy0 && row >= 0 && idx < shMembers.size()) {
                    SharingStatePayload.Member m = shMembers.get(idx);
                    String me = this.minecraft != null && this.minecraft.player != null
                            ? this.minecraft.player.getUUID().toString() : "";
                    shSelected = m.uuid().equals(me) ? null : (m.uuid().equals(shSelected) ? null : m.uuid());
                }
                return true;
            }
            if (shMgmtMode == 1 && shSelected != null) {
                if (inside(mx, my, shMg1X, shMgmtY, shMg1W, SH_BTN_H)) {
                    ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.KICK, shSelected, 0));
                    shSelected = null; return true;
                }
                if (inside(mx, my, shMg2X, shMgmtY, shMg2W, SH_BTN_H)) {
                    ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.LEVEL_UP, shSelected, 0)); return true;
                }
                if (inside(mx, my, shMg3X, shMgmtY, shMg3W, SH_BTN_H)) {
                    ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.LEVEL_DOWN, shSelected, 0)); return true;
                }
            } else if (shMgmtMode == 2 && inside(mx, my, shMg1X, shMgmtY, shMg1W, SH_BTN_H)) {
                String from = shSelInvite >= 0 && shSelInvite < shInvites.size()
                        ? shInvites.get(shSelInvite).from() : "";
                shSelInvite = -1;
                ClientNet.sendToServer(new ShareActionPayload(ShareActionPayload.DECLINE, from, 0)); return true;
            }
            if (shInputActive) shInputActive = false;   // click elsewhere cancels name entry
        }

        // vault grid: route the click through vanilla's container protocol. Empty cursor picks up
        // (left = stack, right = one, shift = stack to inventory); a held stack deposits into the
        // bank (right-click deposits one) -- any grid cell, occupied or empty, is a deposit target.
        if (button == 0 || button == 1) {
            boolean carrying = !this.menu.getCarried().isEmpty();
            if (carrying && inside(mx, my, gridX, gridY, cols * slot, rows * slot)) {
                long t = 0; for (Entry e : entries) t += e.count();
                if (t >= capacity) { flashEnd = System.currentTimeMillis() + 900; depositDenied = true; }
            }
            int cell = gridCellAt(mx, my);
            if (cell < 0 && carrying && inside(mx, my, gridX, gridY, cols * slot, rows * slot)) {
                int c = (mx - gridX) / slot, r = (my - gridY) / slot;
                if (c >= 0 && c < cols && r >= 0 && r < rows) cell = r * cols + c;
            }
            if (cell >= 0 && (carrying ? permLevel >= 1 : permLevel >= 2)) {
                int slotIndex = BankVaultMenu.VIEW_FIRST + cell;
                if (slotIndex < this.menu.slots.size()) {
                    Slot vs = this.menu.slots.get(slotIndex);
                    /* [[[cog
        import compat_core
        compat_core.emit_ct_decl(cog, ver)
        ]]] */
ContainerInput ct = event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
/* [[[end]]] */
                    this.slotClicked(vs, slotIndex, button, ct);
                    return true;
                }
            }
        }

        /* [[[cog
        import compat_core
        compat_core.emit_input_tail(cog, ver, "click")
        ]]] */
return super.mouseClicked((net.minecraft.client.input.MouseButtonEvent) event.raw(), doubleClick);
        /* [[[end]]] */
    }

    /* [[[cog
    import compat_core
    compat_core.emit_input_head(cog, ver, "drag")
    ]]] */
    @Override
    public boolean mouseDragged(MouseButtonEvent event0, double dragX, double dragY) { return mouseDraggedImpl(Ev.mouse(event0), dragX, dragY); }

    private boolean mouseDraggedImpl(Ev event, double dragX, double dragY) {
    /* [[[end]]] */
        if (draggingThumb) {
            int trackTop = sbarTop + SB_W, trackBot = sbarBottom - SB_W;
            dragThumbY = Math.max(trackTop, Math.min(trackBot - SB_W, (int) event.y() - dragOffsetY));
            int span = Math.max(1, (trackBot - trackTop) - SB_W);
            double f = (double)(dragThumbY - trackTop) / span;
            scrollRow = (int) Math.round(Math.max(0, Math.min(1, f)) * maxRow());
            sendGridView();
            return true;
        }
        /* [[[cog
        import compat_core
        compat_core.emit_input_tail(cog, ver, "drag")
        ]]] */
return super.mouseDragged((net.minecraft.client.input.MouseButtonEvent) event.raw(), dragX, dragY);
        /* [[[end]]] */
    }

    /* [[[cog
    import compat_core
    compat_core.emit_input_head(cog, ver, "release")
    ]]] */
    @Override
    public boolean mouseReleased(MouseButtonEvent event0) { return mouseReleasedImpl(Ev.mouse(event0)); }

    private boolean mouseReleasedImpl(Ev event) {
    /* [[[end]]] */
        draggingThumb = false;
        // v1.2 beta.2: TRUE drag-and-drop pinning. A hold-drag from a slot never produces a
        // second click -- the gesture ends in mouseReleased, and vanilla's quick-craft release
        // would scatter the carried stack into the dragged-over slots (this ate Dave's stack in
        // alpha.16). Releasing over the Pin box disarms quick-craft, then routes the drop through
        // the REAL pin slot: the deposit + pin toggle run inside the vanilla click transaction.
        if (ctrlVisible && inside((int) event.x(), (int) event.y(), pinBoxX, ctrlY, pinBoxW, sbH)) {
            ItemStack carried = this.menu.getCarried();
            if (!carried.isEmpty() && selectedKey != null) {
                this.isQuickCrafting = false;
                this.quickCraftSlots.clear();
                pinClick();
            }
            return true;
        }
        /* [[[cog
        import compat_core
        compat_core.emit_input_tail(cog, ver, "release")
        ]]] */
return super.mouseReleased((net.minecraft.client.input.MouseButtonEvent) event.raw());
        /* [[[end]]] */
    }

    /** Drop-to-pin (beta.2, shared by click and drag-release): route the gesture through the
     *  REAL pin slot. The server deposits the carried stack into the vault and toggles its
     *  per-tab pin inside the vanilla click transaction; the UiState sync + vault sync it
     *  sends back update the pins and drive the rebuild. */
    private void pinClick() {
        int idx = BankVaultMenu.PIN_SLOT;
        if (idx < this.menu.slots.size())
            /* [[[cog
        import compat_core
        compat_core.emit_slotclicked_pickup(cog, ver)
        ]]] */
this.slotClicked(this.menu.slots.get(idx), idx, 0, ContainerInput.PICKUP);
/* [[[end]]] */
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (inside(mx, my, railX, btnTop, railW, btnBottom - btnTop)) { btnScroll -= (int) Math.signum(scrollY); clampBtnScroll(); return true; }
        if (inside(mx, my, gridX, gridY, cols * slot, rows * slot) || inside(mx, my, sbarX, sbarTop, SB_W, sbarBottom - sbarTop)) {
            scrollBy(-(int) Math.signum(scrollY)); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Entry gridItemAt(int mx, int my) {
        if (!inside(mx, my, gridX, gridY, cols * slot, rows * slot)) return null;
        return cellEntry((my - gridY) / slot, (mx - gridX) / slot);
    }

    /** Visible-page cell index (0..rows*cols) under the cursor, or -1. Matches the server's viewKeys order. */
    private int gridCellAt(int mx, int my) {
        if (!inside(mx, my, gridX, gridY, cols * slot, rows * slot)) return -1;
        int c = (mx - gridX) / slot, r = (my - gridY) / slot;
        if (c < 0 || c >= cols || r < 0 || r >= rows) return -1;
        return cellEntry(r, c) != null ? r * cols + c : -1;
    }

    /** Tell the server which bank key sits in each visible grid cell so a real-Slot click on the
     *  view maps to the right item. Sent whenever the visible page changes (rebuild / scroll). */
    private void sendGridView() {
        if (cols <= 0 || rows <= 0) return;
        int n = Math.min(rows * cols, BankVaultMenu.VIEW_SIZE);
        List<String> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Entry e = cellEntry(i / cols, i % cols);
            keys.add(e != null ? e.key() : "");
        }
        ClientNet.sendToServer(new GridViewPayload(selectedKey == null ? "" : selectedKey, keys));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static String abbrev(long n) { if (n < 1000) return Long.toString(n); if (n < 1_000_000) return (n / 1000) + "k"; if (n < 1_000_000_000) return (n / 1_000_000) + "m"; return (n / 1_000_000_000) + "b"; }

    /** Item-count label, bottom-right anchored, auto-shrunk so even 4 characters keep a margin. */
    private void countText(Gfx g, String s, int sx, int sy) {
        int w0 = Math.max(1, this.font.width(s));
        float sc = Math.min(1f, 13f / w0);
        float w = w0 * sc;
        g.pushMatrix();
        g.translate(sx + 17 - w, sy + 17 - 8 * sc);
        g.scale(sc, sc);
        g.text(this.font, s, 1, 1, 0xFF3F3F3F);
        g.text(this.font, s, 0, 0, 0xFFFFFFFF);
        g.popMatrix();
    }

    /** An 18x18 vanilla inventory slot box; x,y = the 16x16 interior top-left (absolute).
     *  Exact vanilla palette: face 0xFF8B8B8B, top/left 0xFF373737, bottom/right 0xFFFFFFFF. */
    private void vanillaSlot(Gfx g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        g.fill(x - 1, y - 1, x + 16, y, 0xFF373737);
        g.fill(x - 1, y - 1, x, y + 16, 0xFF373737);
        g.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        g.fill(x + 16, y, x + 17, y + 17, 0xFFFFFFFF);
    }

    private void panel(Gfx g, int x, int y, int w, int h, boolean raised) {
        g.fill(x, y, x + w, y + h, FACE);
        int tl = raised ? LIGHT : DARK, br = raised ? DARK : LIGHT;
        g.fill(x, y, x + w, y + 1, tl); g.fill(x, y, x + 1, y + h, tl);
        g.fill(x, y + h - 1, x + w, y + h, br); g.fill(x + w - 1, y, x + w, y + h, br);
    }
}
