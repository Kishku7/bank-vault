package com.kishku7.bankvault.client;

import com.kishku7.bankvault.inventory.BankVaultMenu;
import com.kishku7.bankvault.net.UpgradePayload;
import com.kishku7.bankvault.net.VaultSyncPayload;
import com.kishku7.bankvault.net.VaultSyncPayload.Entry;
import com.kishku7.bankvault.net.WithdrawPayload;
import com.kishku7.bankvault.vault.Catalog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Creative-style bank vault browser, now a real container menu (drag/drop slots + custom vault grid). */
public class BankVaultScreen extends AbstractContainerScreen<BankVaultMenu> {

    private static final int OVERLAY=0xBE060709, FACE=0xFF34343A, LIGHT=0xFF63636D, DARK=0xFF131316,
            WELL=0xFF191920, SLOT_BG=0xFF2A2A31, ACCENT=0xFFE0A92E, TEXT=0xFFD6D6DB, TITLE=0xFFF1F1F4,
            SUBTLE=0xFF8B8B93, UPG_FILL=0xFF232A22, CLOSE=0xFF6B2B27, CLOSE_HOV=0xFF8A3531;

    private static final String[] COLORS={"white","orange","magenta","light_blue","yellow","lime","pink","gray",
            "light_gray","cyan","purple","blue","brown","green","red","black"};
    private static final String[] WOODS={"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry",
            "pale_oak","bamboo","crimson","warped"};
    private static final String[] FORMS={"_fence_gate","_hanging_sign","_pressure_plate","_glass_pane",
            "_concrete_powder","_glazed_terracotta","_stained_glass_pane","_shulker_box","_trapdoor","_fence",
            "_stairs","_slab","_wall","_door","_button","_sign","_planks","_log","_wood","_leaves","_sapling",
            "_carpet","_wool","_concrete","_terracotta","_bricks","_brick","_ingot","_nugget","_block","_ore",
            "_bed","_candle","_banner","_boat","_dye","_seeds","_bulb"};
    private static final String[] TOOLTYPES={"_pickaxe","_sword","_axe","_shovel","_hoe","_helmet","_chestplate","_leggings","_boots"};
    private static final Map<String,Integer> TIER = new HashMap<>();
    static {
        TIER.put("leather",0); TIER.put("wooden",0); TIER.put("chainmail",1); TIER.put("stone",1);
        TIER.put("copper",2); TIER.put("turtle",2); TIER.put("golden",3); TIER.put("iron",4);
        TIER.put("diamond",5); TIER.put("netherite",6);
    }

    private enum SortMode { SMART_FAMILY("Smart(F)"), SMART_TYPE("Smart(T)"), ALPHA("A–Z"), COUNT("Count"); final String label; SortMode(String l){label=l;} }

    private List<Entry> entries = new ArrayList<>();
    private int upgradeCount;
    private long capacity;
    private int permLevel;

    private String selectedTab = "blocks";
    private SortMode sortMode = SortMode.SMART_FAMILY;
    private boolean countDesc = true;
    private int scrollRow = 0, tabScroll = 0;
    private final List<Entry> view = new ArrayList<>();
    private final Map<String, String> nameCache = new HashMap<>();

    private int px, py, pw, ph, railX, railW, gridX, gridY, slot = 18, cols, rows;
    private int tabTop, tabRowH = 22, visibleTabs, tabBottom;
    private int upgX, upgY, upgSize = 16;
    private int closeX, closeY, closeSize = 14;
    private final int[] sbX = new int[4]; private final int[] sbW = new int[4]; private int sbY, sbH = 14;

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
        pw = BankVaultMenu.IMG_W; ph = BankVaultMenu.IMG_H;
        this.leftPos = (this.width - pw) / 2;
        this.topPos = (this.height - ph) / 2;
        px = leftPos; py = topPos;
        closeX = px + pw - 8 - closeSize; closeY = py + 6;
        upgX = px + pw - 8 - 52; upgY = py + 26;
        railX = px + 8; railW = 176;
        int contentBottom = py + BankVaultMenu.INV_Y - 10;
        tabTop = py + 50; tabBottom = contentBottom;
        visibleTabs = Math.max(1, (tabBottom - tabTop) / tabRowH);
        gridX = railX + railW + 8;
        sbY = py + 50;
        SortMode[] modes = SortMode.values();
        int x = gridX;
        for (int i = 0; i < 4; i++) { sbW[i] = this.font.width(modes[i] == SortMode.COUNT ? "Count ↓" : modes[i].label) + 10; sbX[i] = x; x += sbW[i] + 4; }
        gridY = py + 70;
        int gridW = px + pw - 8 - gridX, gridH = contentBottom - gridY;
        cols = Math.max(1, gridW / slot);
        rows = Math.max(1, gridH / slot);
        clampTabScroll();
        rebuild();
    }

    private void clampTabScroll() { tabScroll = Math.max(0, Math.min(tabScroll, Math.max(0, Catalog.tabs().size() - visibleTabs))); }

    private void rebuild() {
        view.clear();
        for (Entry e : entries) if (Catalog.inTab(idOf(e), selectedTab)) view.add(e);
        view.sort(comparator());
        int maxRow = Math.max(0, (int) Math.ceil(view.size() / (double) cols) - rows);
        scrollRow = Math.max(0, Math.min(scrollRow, maxRow));
    }

    // ── sorting (unchanged logic) ───────────────────────────────────────────────

    private Comparator<Entry> comparator() {
        switch (sortMode) {
            case COUNT: return (a, b) -> countDesc ? Long.compare(b.count(), a.count()) : Long.compare(a.count(), b.count());
            case ALPHA: return Comparator.comparing(this::nameOf);
            case SMART_TYPE: return smartType();
            default: return smartFamily();
        }
    }
    private Comparator<Entry> smartFamily() {
        switch (selectedTab) {
            case "equipment": return Comparator.<Entry>comparingInt(e -> tier(idOf(e))).thenComparing(this::nameOf);
            case "food": return Comparator.<Entry, String>comparing(e -> lastWord(nameOf(e))).thenComparing(this::nameOf);
            case "colored": return Comparator.<Entry>comparingInt(e -> prefixIndex(idOf(e), COLORS)).thenComparing(this::nameOf);
            case "wood": return Comparator.<Entry>comparingInt(e -> prefixIndex(idOf(e), WOODS)).thenComparing(this::nameOf);
            case "copper": return Comparator.<Entry>comparingInt(e -> oxidation(idOf(e))).thenComparing(this::nameOf);
            case "dyes": return Comparator.<Entry>comparingInt(e -> path(idOf(e)).endsWith("_dye") ? 1 : 0).thenComparing(this::nameOf);
            default: return Comparator.comparing(this::nameOf);
        }
    }
    private Comparator<Entry> smartType() {
        switch (selectedTab) {
            case "equipment": return Comparator.<Entry, String>comparing(e -> toolType(idOf(e))).thenComparing(Comparator.comparingInt(e -> tier(idOf(e)))).thenComparing(this::nameOf);
            case "colored": return Comparator.<Entry, String>comparing(e -> formKey(idOf(e))).thenComparing(Comparator.comparingInt(e -> prefixIndex(idOf(e), COLORS))).thenComparing(this::nameOf);
            case "wood": return Comparator.<Entry, String>comparing(e -> formKey(idOf(e))).thenComparing(Comparator.comparingInt(e -> prefixIndex(idOf(e), WOODS))).thenComparing(this::nameOf);
            case "copper": return Comparator.<Entry, String>comparing(e -> formKey(idOf(e))).thenComparing(Comparator.comparingInt(e -> oxidation(idOf(e)))).thenComparing(this::nameOf);
            case "dyes": return Comparator.<Entry>comparingInt(e -> { int c = Catalog.colorOf(idOf(e)); return c < 0 ? 99 : c; }).thenComparing(this::nameOf);
            case "food": return Comparator.<Entry, String>comparing(e -> firstWord(nameOf(e))).thenComparing(this::nameOf);
            default: return Comparator.<Entry, String>comparing(e -> formKey(idOf(e))).thenComparing(this::nameOf);
        }
    }
    private static String idOf(Entry e) { return BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString(); }
    private String nameOf(Entry e) { return nameCache.computeIfAbsent(e.key(), k -> e.stack().getHoverName().getString().toLowerCase(Locale.ROOT)); }
    private static String path(String id) { int i = id.indexOf(':'); return i >= 0 ? id.substring(i + 1) : id; }
    private static String lastWord(String n) { String[] t = n.trim().split("\\s+"); return t.length == 0 ? n : t[t.length - 1]; }
    private static String firstWord(String n) { String[] t = n.trim().split("\\s+"); return t.length == 0 ? n : t[0]; }
    private int tier(String id) { String p = path(id); int b = 99; for (Map.Entry<String, Integer> e : TIER.entrySet()) if (p.contains(e.getKey())) b = Math.min(b, e.getValue()); return b; }
    private String formKey(String id) { String p = path(id); for (String f : FORMS) if (p.endsWith(f)) return f; return "~" + p; }
    private String toolType(String id) { String p = path(id); for (String t : TOOLTYPES) if (p.endsWith(t)) return t; return "~" + p; }
    private static int prefixIndex(String id, String[] arr) { String p = path(id); for (int i = 0; i < arr.length; i++) if (p.startsWith(arr[i] + "_") || p.equals(arr[i])) return i; return arr.length; }
    private int oxidation(String id) { String p = path(id); int b = p.contains("oxidized") ? 3 : p.contains("weathered") ? 2 : p.contains("exposed") ? 1 : 0; return b + (p.startsWith("waxed_") ? 4 : 0); }

    // ── render: panel behind, then real slots on top ────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, OVERLAY);
        drawCustom(g, mouseX, mouseY);
        this.hoveredSlot = null;
        for (Slot s : this.menu.slots) if (isHovering(s.x, s.y, 16, 16, mouseX, mouseY)) { this.hoveredSlot = s; break; }
        this.extractSlots(g, mouseX, mouseY);
        this.extractCarriedItem(g, mouseX, mouseY);
        this.extractTooltip(g, mouseX, mouseY);
        Entry h = gridItemAt(mouseX, mouseY);
        if (h != null) g.setTooltipForNextFrame(this.font, h.stack(), mouseX, mouseY);
    }

    private void drawCustom(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        panel(g, px, py, pw, ph, true);
        g.fill(px + 6, py + 6, px + pw - 6, py + 46, WELL);
        g.text(this.font, "BANK VAULT", px + 16, py + 12, TITLE);
        g.text(this.font, "virtual storage", px + 16, py + 26, SUBTLE);
        long total = 0; for (Entry e : entries) total += e.count();
        String stats = String.format("%,d / %,d", total, capacity);
        g.text(this.font, stats, closeX - 8 - this.font.width(stats), py + 12, ACCENT);

        boolean closeHov = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        g.fill(closeX, closeY, closeX + closeSize, closeY + closeSize, closeHov ? CLOSE_HOV : CLOSE);
        g.text(this.font, "✕", closeX + 4, closeY + 3, TITLE);

        g.fill(upgX, upgY, upgX + upgSize, upgY + upgSize, upgradeCount > 0 ? UPG_FILL : SLOT_BG);
        if (upgradeCount > 0) g.item(new ItemStack(Items.CHEST), upgX - 1, upgY - 1);
        g.text(this.font, upgradeCount + "/64", upgX + upgSize + 4, upgY + 4, upgradeCount >= 64 ? ACCENT : TEXT);

        // rail + tabs
        panel(g, railX, py + 46, railW, tabBottom - (py + 46), false);
        List<Catalog.Tab> tabs = Catalog.tabs();
        int ty = tabTop;
        for (int idx = tabScroll; idx < tabs.size() && idx < tabScroll + visibleTabs; idx++) {
            Catalog.Tab tab = tabs.get(idx);
            boolean sel = tab.id().equals(selectedTab);
            boolean hov = inside(mouseX, mouseY, railX + 4, ty, railW - 8, 20);
            g.fill(railX + 4, ty, railX + railW - 4, ty + 20, sel ? 0xFF4A3A12 : (hov ? 0xFF3A3A42 : WELL));
            if (sel) g.fill(railX + 4, ty, railX + 7, ty + 20, ACCENT);
            g.text(this.font, tab.glyph() + " " + tab.name(), railX + 12, ty + 6, sel ? ACCENT : TEXT);
            String cnt = abbrev(tabTotal(tab.id()));
            g.text(this.font, cnt, railX + railW - 8 - this.font.width(cnt), ty + 6, SUBTLE);
            ty += tabRowH;
        }
        if (tabScroll > 0) g.text(this.font, "▲", railX + railW - 14, py + 48, SUBTLE);
        if (tabScroll + visibleTabs < tabs.size()) g.text(this.font, "▼", railX + railW - 14, tabBottom - 12, SUBTLE);

        // sort buttons
        SortMode[] modes = SortMode.values();
        for (int i = 0; i < 4; i++) {
            boolean active = sortMode == modes[i];
            boolean hov = inside(mouseX, mouseY, sbX[i], sbY, sbW[i], sbH);
            g.fill(sbX[i], sbY, sbX[i] + sbW[i], sbY + sbH, active ? 0xFF4A3A12 : (hov ? 0xFF3A3A42 : WELL));
            if (active) g.fill(sbX[i], sbY, sbX[i] + sbW[i], sbY + 1, ACCENT);
            String lbl = modes[i] == SortMode.COUNT ? ("Count " + (countDesc ? "↓" : "↑")) : modes[i].label;
            g.text(this.font, lbl, sbX[i] + (sbW[i] - this.font.width(lbl)) / 2, sbY + 3, active ? ACCENT : TEXT);
        }

        // vault grid (virtual items)
        g.enableScissor(gridX, gridY, gridX + cols * slot, gridY + rows * slot);
        int start = scrollRow * cols;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int idx = start + r * cols + c;
            if (idx >= view.size()) continue;
            int sx = gridX + c * slot, sy = gridY + r * slot;
            boolean hov = inside(mouseX, mouseY, sx, sy, slot, slot);
            g.fill(sx, sy, sx + slot - 1, sy + slot - 1, hov ? 0xFF4A4A55 : SLOT_BG);
            Entry e = view.get(idx);
            g.item(e.stack(), sx + 1, sy + 1);
            g.itemDecorations(this.font, e.stack(), sx + 1, sy + 1, abbrev(e.count()));
        }
        g.disableScissor();

        // labels + slot backgrounds for the real slots (items drawn by the framework)
        g.text(this.font, "Inventory — shift-click to deposit", px + BankVaultMenu.INV_X, py + BankVaultMenu.INV_Y - 10, SUBTLE);
        g.text(this.font, "Unload", px + BankVaultMenu.UNLOAD_X - 2, py + BankVaultMenu.UNLOAD_Y - 10, SUBTLE);
        g.text(this.font, "Out", px + BankVaultMenu.GRAB_X - 2, py + BankVaultMenu.GRAB_Y - 10, SUBTLE);
        for (Slot s : this.menu.slots) g.fill(px + s.x - 1, py + s.y - 1, px + s.x + 17, py + s.y + 17, SLOT_BG);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), button = event.button();
        if (inside(mx, my, closeX, closeY, closeSize, closeSize)) { this.onClose(); return true; }
        SortMode[] modes = SortMode.values();
        for (int i = 0; i < 4; i++) if (inside(mx, my, sbX[i], sbY, sbW[i], sbH)) {
            if (modes[i] == SortMode.COUNT && sortMode == SortMode.COUNT) countDesc = !countDesc; else sortMode = modes[i];
            rebuild(); return true;
        }
        List<Catalog.Tab> tabs = Catalog.tabs();
        int ty = tabTop;
        for (int idx = tabScroll; idx < tabs.size() && idx < tabScroll + visibleTabs; idx++) {
            if (inside(mx, my, railX + 4, ty, railW - 8, 20)) { selectedTab = tabs.get(idx).id(); scrollRow = 0; rebuild(); return true; }
            ty += tabRowH;
        }
        if (inside(mx, my, upgX, upgY, upgSize, upgSize) && permLevel >= 3) { ClientPlayNetworking.send(new UpgradePayload(button == 0)); return true; }
        Entry hit = gridItemAt(mx, my);
        if (hit != null && permLevel >= 2) { ClientPlayNetworking.send(new WithdrawPayload(hit.key(), hit.stack().getMaxStackSize())); return true; }
        return super.mouseClicked(event, doubleClick); // real slots: drag/drop/shift
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (inside(mx, my, railX, tabTop, railW, tabBottom - tabTop)) { tabScroll -= (int) Math.signum(scrollY); clampTabScroll(); return true; }
        if (inside(mx, my, gridX, gridY, cols * slot, rows * slot)) {
            scrollRow -= (int) Math.signum(scrollY);
            int maxRow = Math.max(0, (int) Math.ceil(view.size() / (double) cols) - rows);
            scrollRow = Math.max(0, Math.min(scrollRow, maxRow));
            return true;
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

    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static String abbrev(long n) { if (n < 10_000) return Long.toString(n); if (n < 1_000_000) return (n / 1000) + "k"; if (n < 1_000_000_000) return (n / 1_000_000) + "M"; return (n / 1_000_000_000) + "B"; }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean raised) {
        g.fill(x, y, x + w, y + h, FACE);
        int tl = raised ? LIGHT : DARK, br = raised ? DARK : LIGHT;
        g.fill(x, y, x + w, y + 1, tl); g.fill(x, y, x + 1, y + h, tl);
        g.fill(x, y + h - 1, x + w, y + h, br); g.fill(x + w - 1, y, x + w, y + h, br);
    }
}
