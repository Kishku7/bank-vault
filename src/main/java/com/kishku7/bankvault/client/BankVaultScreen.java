package com.kishku7.bankvault.client;

import com.kishku7.bankvault.inventory.BankVaultMenu;
import com.kishku7.bankvault.inventory.ContainerExtractor;
import com.kishku7.bankvault.net.GridViewPayload;
import com.kishku7.bankvault.net.UpgradePayload;
import com.kishku7.bankvault.net.VaultSyncPayload;
import com.kishku7.bankvault.net.VaultSyncPayload.Entry;
import com.kishku7.bankvault.vault.Catalog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
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

    private String selectedTab = null; // resolved to the first configured tab on init
    private SortMode sortMode = SortMode.SMART_FAMILY;
    private boolean countDesc = true;
    private int scrollRow = 0, tabScroll = 0;
    private final List<Entry> view = new ArrayList<>();
    private final Map<String, String> nameCache = new HashMap<>();

    // layout (absolute unless noted Rel = panel-relative for real slots)
    private int px, py, pw, ph;
    private int railX, railW, railTop, railBottom;
    private int tabTop, tabBottom, tabRowH = 20, visibleTabs;
    private int gridX, gridY, gridBottom, slot = 18, cols, rows;
    private int sbarX, sbarTop, sbarBottom;
    private int rpX, rpY;                       // right panel (native inventory) top-left, absolute
    private static final int RP_W = 176, RP_H = 166;
    private int quY;                            // quick-unload cluster row, absolute
    private int upgX, upgY, upgChestX, upgSize = 16;
    private int closeX, closeY, closeSize = 14;
    private final int[] sbX = new int[4]; private final int[] sbW = new int[4]; private int sbY, sbH = 14;
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
        rebuild();
    }

    @Override
    protected void init() {
        super.init();
        // Fill the screen: full vertical space at every GUI scale (no design-height cap).
        pw = Math.max(MIN_W, Math.min(DESIGN_W, this.width - 2 * MARGIN));
        // Height: just enough to fit the tab rail (or the right panel cluster), capped to the screen.
        if (selectedTab == null || Catalog.tabs().stream().noneMatch(t -> t.id().equals(selectedTab)))
            selectedTab = Catalog.tabs().isEmpty() ? "uncategorized" : Catalog.tabs().get(0).id();
        int tabsNeeded = Catalog.tabs().size() * 20 + 16;
        int tRowsPre = (this.menu.trinketSlotCount + 8) / 9;
        int clusterNeeded = RP_H + 6 + 18 + 14 + (tRowsPre > 0 ? tRowsPre * 18 + 4 : 0);
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

        // --- full-height left rail ---
        railX = px + 8;
        railW = Math.max(110, Math.min(160, pw - 439));
        railTop = contentTop; railBottom = py + ph - 8;
        tabTop = railTop + 4; tabBottom = railBottom - 4;
        int tabCount = Math.max(1, Catalog.tabs().size());
        int railInner = tabBottom - tabTop;
        tabRowH = Math.max(16, Math.min(28, railInner / tabCount));
        visibleTabs = Math.max(1, railInner / tabRowH);

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
        searchBoxW = Math.max(60, goX - 4 - searchBoxX);

        positionRealSlots();
        clampTabScroll();
        rebuild();
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

    private void clampTabScroll() { tabScroll = Math.max(0, Math.min(tabScroll, Math.max(0, Catalog.tabs().size() - visibleTabs))); }

    private int totalRows() { return (int) Math.ceil(view.size() / (double) Math.max(1, cols)); }
    private int maxRow() { return Math.max(0, totalRows() - rows); }
    private void scrollBy(int d) { scrollRow = Math.max(0, Math.min(scrollRow + d, maxRow())); sendGridView(); }

    private void rebuild() {
        view.clear();
        String q = searchText == null ? "" : searchText.trim();
        boolean searching = !q.isEmpty();
        for (Entry e : entries) {
            // search is GLOBAL: with text in the box, results come from the whole vault, not the tab
            if (!searching && !Catalog.inTab(idOf(e), selectedTab)) continue;
            if (searching && !nameOf(e).contains(q)) continue;
            view.add(e);
        }
        view.sort(comparator());
        scrollRow = Math.max(0, Math.min(scrollRow, maxRow()));
        sendGridView();
    }

    // -- sorting --

    private Comparator<Entry> comparator() {
        switch (sortMode) {
            case COUNT: return (a, b) -> countDesc ? Long.compare(b.count(), a.count()) : Long.compare(a.count(), b.count());
            case ALPHA: return Comparator.comparing(this::nameOf);
            case SMART_TYPE: return smartType();
            default: return smartFamily();
        }
    }
    /** SETTINGS-DRIVEN smart sort: interprets the step chain from categories.json tabSort.
     *  Steps: "name", "form", "oxidation", "color", "firstword", "lastword",
     *  "prefix:<list>", "tier:<list>" (ordered infix), "suffix:<list>". */
    private Comparator<Entry> smartFamily() { return smart("family"); }
    private Comparator<Entry> smartType() { return smart("type"); }
    private Comparator<Entry> smart(String mode) {
        Comparator<Entry> cmp = null;
        for (String step : Catalog.sortSteps(selectedTab, mode)) {
            Comparator<Entry> c;
            if (step.equals("list")) c = Comparator.comparingInt(e -> Catalog.orderIndex(selectedTab, mode, idOf(e)));
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

    private void textScaled(GuiGraphicsExtractor g, String s, int x, int y, int color, float sc) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.text(this.font, s, 0, 0, color);
        g.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, OVERLAY);
        drawCustom(g, mouseX, mouseY);
        this.hoveredSlot = null;
        for (Slot s : this.menu.slots) if (s.isActive() && isHovering(s.x, s.y, 16, 16, mouseX, mouseY)) { this.hoveredSlot = s; break; }
        g.pose().pushMatrix();
        g.pose().translate(this.leftPos, this.topPos);
        this.extractSlots(g, mouseX, mouseY);
        g.pose().popMatrix();
        this.extractCarriedItem(g, mouseX, mouseY);
        this.extractTooltip(g, mouseX, mouseY);
        Entry h = gridItemAt(mouseX, mouseY);
        if (h != null) g.setTooltipForNextFrame(this.font, h.stack(), mouseX, mouseY);
    }

    private void drawCustom(GuiGraphicsExtractor g, int mouseX, int mouseY) {
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

        // left rail + tabs
        panel(g, railX, railTop, railW, railBottom - railTop, false);
        List<Catalog.Tab> tabs = Catalog.tabs();
        int rowH = tabRowH - 2;
        int ty = tabTop;
        for (int idx = tabScroll; idx < tabs.size() && idx < tabScroll + visibleTabs; idx++) {
            Catalog.Tab tab = tabs.get(idx);
            boolean sel = tab.id().equals(selectedTab);
            boolean hov = inside(mouseX, mouseY, railX + 4, ty, railW - 8, rowH);
            g.fill(railX + 4, ty, railX + railW - 4, ty + rowH, sel ? 0xFF4A3A12 : (hov ? 0xFF3A3A42 : WELL));
            if (sel) g.fill(railX + 4, ty, railX + 7, ty + rowH, ACCENT);
            int textY = ty + (rowH - 7) / 2;
            textScaled(g, tab.glyph() + " " + tab.name(), railX + 10, textY, sel ? ACCENT : TEXT, TAB_SCALE);
            String cnt = abbrev(tabTotal(tab.id()));
            textScaled(g, cnt, railX + railW - 8 - (int) (this.font.width(cnt) * TAB_SCALE), textY, SUBTLE, TAB_SCALE);
            ty += tabRowH;
        }
        if (tabScroll > 0) g.text(this.font, "▲", railX + railW - 12, railTop + 2, SUBTLE);
        if (tabScroll + visibleTabs < tabs.size()) g.text(this.font, "▼", railX + railW - 12, railBottom - 10, SUBTLE);

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

        // vault grid (stretches to the search bar at the bottom)
        g.enableScissor(gridX, gridY, gridX + cols * slot, gridY + rows * slot);
        long flashLeft = flashEnd - System.currentTimeMillis();
        boolean flashOn = flashLeft > 0 && (flashLeft / 150) % 2 == 0;   // 3 blinks over ~900ms
        int start = scrollRow * cols;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int idx = start + r * cols + c;
            if (idx >= view.size()) continue;
            int sx = gridX + c * slot, sy = gridY + r * slot;
            boolean hov = inside(mouseX, mouseY, sx, sy, slot, slot);
            g.fill(sx, sy, sx + slot - 1, sy + slot - 1, flashOn ? 0xFFA02820 : (hov ? 0xFF4A4A55 : SLOT_BG));
            Entry e = view.get(idx);
            g.item(e.stack(), sx + 1, sy + 1);
            g.itemDecorations(this.font, e.stack(), sx + 1, sy + 1, null);
            countText(g, abbrev(e.count()), sx, sy);
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
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, rpX + 28, rpY + 8, rpX + 68, rpY + 58,
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
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
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
            g.pose().pushMatrix();
            g.pose().translate(t[0], t[1]);
            g.pose().scale((float) SB_W / 16f, (float) SB_W / 16f);
            g.item(new ItemStack(Items.CHISELED_STONE_BRICKS), 0, 0);
            g.pose().popMatrix();
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

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchFocused) {
            if (event.input() == 259) { // BACKSPACE
                if (!searchText.isEmpty()) { searchText = searchText.substring(0, searchText.length() - 1); rebuild(); }
                return true;
            }
            if (event.isEscape()) { searchFocused = false; return true; }
            if (event.isConfirmation()) { searchFocused = false; return true; }
            return true; // consume all other keys while focused
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused) {
            if (event.isAllowedChatCharacter() && searchText.length() < 32) {
                searchText = searchText + event.codepointAsString();
                rebuild();
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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

        SortMode[] modes = SortMode.values();
        for (int i = 0; i < 4; i++) if (inside(mx, my, sbX[i], sbY, sbW[i], sbH)) {
            if (modes[i] == SortMode.COUNT && sortMode == SortMode.COUNT) countDesc = !countDesc; else sortMode = modes[i];
            rebuild(); return true;
        }
        List<Catalog.Tab> tabs = Catalog.tabs();
        int rowH = tabRowH - 2, ty2 = tabTop;
        for (int idx = tabScroll; idx < tabs.size() && idx < tabScroll + visibleTabs; idx++) {
            if (inside(mx, my, railX + 4, ty2, railW - 8, rowH)) { selectedTab = tabs.get(idx).id(); scrollRow = 0; rebuild(); return true; }
            ty2 += tabRowH;
        }
        if (button == 1 && inside(mx, my, upgX, upgY, upgSize, upgSize) && permLevel >= 3
                && this.menu.getCarried().isEmpty()) { ClientPlayNetworking.send(new UpgradePayload(false)); return true; }

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
                    ContainerInput ct = event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
                    this.slotClicked(vs, slotIndex, button, ct);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingThumb) {
            int trackTop = sbarTop + SB_W, trackBot = sbarBottom - SB_W;
            dragThumbY = Math.max(trackTop, Math.min(trackBot - SB_W, (int) event.y() - dragOffsetY));
            int span = Math.max(1, (trackBot - trackTop) - SB_W);
            double f = (double)(dragThumbY - trackTop) / span;
            scrollRow = (int) Math.round(Math.max(0, Math.min(1, f)) * maxRow());
            sendGridView();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (inside(mx, my, railX, tabTop, railW, tabBottom - tabTop)) { tabScroll -= (int) Math.signum(scrollY); clampTabScroll(); return true; }
        if (inside(mx, my, gridX, gridY, cols * slot, rows * slot) || inside(mx, my, sbarX, sbarTop, SB_W, sbarBottom - sbarTop)) {
            scrollBy(-(int) Math.signum(scrollY)); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private long tabTotal(String tab) { long t = 0; for (Entry e : entries) if (Catalog.inTab(idOf(e), tab)) t += e.count(); return t; }

    private Entry gridItemAt(int mx, int my) {
        if (!inside(mx, my, gridX, gridY, cols * slot, rows * slot)) return null;
        int c = (mx - gridX) / slot, r = (my - gridY) / slot;
        int idx = scrollRow * cols + r * cols + c;
        return (c >= 0 && c < cols && idx >= 0 && idx < view.size()) ? view.get(idx) : null;
    }

    /** Visible-page cell index (0..rows*cols) under the cursor, or -1. Matches the server's viewKeys order. */
    private int gridCellAt(int mx, int my) {
        if (!inside(mx, my, gridX, gridY, cols * slot, rows * slot)) return -1;
        int c = (mx - gridX) / slot, r = (my - gridY) / slot;
        if (c < 0 || c >= cols || r < 0 || r >= rows) return -1;
        int cell = r * cols + c;
        int idx = scrollRow * cols + cell;
        return idx < view.size() ? cell : -1;
    }

    /** Tell the server which bank key sits in each visible grid cell so a real-Slot click on the
     *  view maps to the right item. Sent whenever the visible page changes (rebuild / scroll). */
    private void sendGridView() {
        if (cols <= 0 || rows <= 0) return;
        int n = Math.min(rows * cols, BankVaultMenu.VIEW_SIZE);
        int start = scrollRow * cols;
        List<String> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            keys.add(idx < view.size() ? view.get(idx).key() : "");
        }
        ClientPlayNetworking.send(new GridViewPayload(keys));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static String abbrev(long n) { if (n < 1000) return Long.toString(n); if (n < 1_000_000) return (n / 1000) + "k"; if (n < 1_000_000_000) return (n / 1_000_000) + "m"; return (n / 1_000_000_000) + "b"; }

    /** Item-count label, bottom-right anchored, auto-shrunk so even 4 characters keep a margin. */
    private void countText(GuiGraphicsExtractor g, String s, int sx, int sy) {
        int w0 = Math.max(1, this.font.width(s));
        float sc = Math.min(1f, 13f / w0);
        float w = w0 * sc;
        g.pose().pushMatrix();
        g.pose().translate(sx + 17 - w, sy + 17 - 8 * sc);
        g.pose().scale(sc, sc);
        g.text(this.font, s, 1, 1, 0xFF3F3F3F);
        g.text(this.font, s, 0, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    /** An 18x18 vanilla inventory slot box; x,y = the 16x16 interior top-left (absolute).
     *  Exact vanilla palette: face 0xFF8B8B8B, top/left 0xFF373737, bottom/right 0xFFFFFFFF. */
    private void vanillaSlot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        g.fill(x - 1, y - 1, x + 16, y, 0xFF373737);
        g.fill(x - 1, y - 1, x, y + 16, 0xFF373737);
        g.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        g.fill(x + 16, y, x + 17, y + 17, 0xFFFFFFFF);
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean raised) {
        g.fill(x, y, x + w, y + h, FACE);
        int tl = raised ? LIGHT : DARK, br = raised ? DARK : LIGHT;
        g.fill(x, y, x + w, y + 1, tl); g.fill(x, y, x + 1, y + h, tl);
        g.fill(x, y + h - 1, x + w, y + h, br); g.fill(x + w - 1, y, x + w, y + h, br);
    }
}
