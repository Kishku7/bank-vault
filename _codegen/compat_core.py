"""compat_core.py -- loader-AGNOSTIC, version-keyed drift brain for Bank Vault's shared Java.

Used by cog markers in _codegen/cog_sources/shared/* materialized into pre-26 cells' gen/ trees.
Loader-specific registration/entrypoint/networking live in compat_fabric / compat_forge /
compat_neoforge. The 26 cells NEVER run cog (they srcDir shared_minecraft directly).

Version axes (fill boundaries as the compiler enumerates them; every confirmed boundary is
also recorded in Memory/knowledge/dev/mod-version-gates.md):
  renamed   @1.21.11 : ResourceLocation -> Identifier rename
  components @1.20.5 : ItemStack data components era (pre: NBT tags); StackStore codec drift
  perms26   @26      : CommandSourceStack.permissions().hasPermission(Permissions.X)
                       (pre-26: s.hasPermission(int))
  datadirs  <1.21    : plural datapack dirs (advancements/loot_tables/recipes/tags/blocks)
  itemdefs  @1.21.4  : assets/<ns>/items/ item model definitions exist
"""


def _vt(ver):
    return tuple(int(x) for x in ver.split("-")[0].split("."))


def renamed(ver):
    return _vt(ver) >= (1, 21, 11)


def has_components(ver):
    return _vt(ver) >= (1, 20, 5)


def is26(ver):
    return _vt(ver) >= (26,)


def java17(ver):
    return _vt(ver) < (1, 20, 5)


def plural_datadirs(ver):
    return _vt(ver) < (1, 21)


def has_item_defs(ver):
    return _vt(ver) >= (1, 21, 4)


def id_type(ver):
    return "Identifier" if renamed(ver) else "ResourceLocation"


def id_import(ver):
    return "import net.minecraft.resources." + id_type(ver) + ";"


def has_rl_factory(ver, loader=None):
    # fromNamespaceAndPath from 1.21 vanilla; Forge backported + deprecated the ctor at 1.20.4.
    if loader == "forge" and _vt(ver) >= (1, 20, 4):
        return True
    return _vt(ver) >= (1, 21)


def make_id(ver, ns_expr, path_expr, loader=None):
    if has_rl_factory(ver, loader):
        return "{0}.fromNamespaceAndPath({1}, {2})".format(id_type(ver), ns_expr, path_expr)
    return "new ResourceLocation({0}, {1})".format(ns_expr, path_expr)


# ---- command permission check (BankCommand op-only subcommands) ----
def emit_perm_gamemaster(cog, ver):
    if is26(ver):
        cog.out("s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)")
    else:
        cog.out("s.hasPermission(2)")


# ---- Fabric-only client annotation ----
def emit_environment(cog, loader):
    if loader == "fabric":
        cog.outl("@Environment(EnvType.CLIENT)")


def emit_environment_imports(cog, loader):
    if loader == "fabric":
        cog.outl("import net.fabricmc.api.EnvType;")
        cog.outl("import net.fabricmc.api.Environment;")


# ---- pack_format (resource) per version; authoritative: Memory/knowledge/pack-formats.md ----
PACK_FORMATS = {
    "1.20": 15, "1.20.1": 15, "1.20.2": 18, "1.20.3": 22, "1.20.4": 22,
    "1.20.5": 32, "1.20.6": 32,
    "1.21": 34, "1.21.1": 34, "1.21.2": 42, "1.21.3": 42, "1.21.4": 46,
    "1.21.5": 55, "1.21.6": 63, "1.21.7": 64, "1.21.8": 64,
    "1.21.9": 69, "1.21.10": 69, "1.21.11": 75,
}


# ---- accesswidener / AT: the one drifting entry is CraftingMenu.slotChangedCraftingGrid.
# Per-version signatures are read from the MC-Java deobf sources during the walk and locked
# here. Key = first version the signature applies to (floor); lookup takes the highest floor
# <= target version. VERIFY each new line against MC-Java before trusting.
SLOT_CHANGED_SIG = {
    # floor: (paramtypes descriptor)
    "1.21.11": "(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/inventory/ResultContainer;Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
}


def slot_changed_sig(ver):
    floors = sorted(SLOT_CHANGED_SIG.keys(), key=_vt)
    best = None
    for f in floors:
        if _vt(ver) >= _vt(f):
            best = f
    if best is None:
        raise KeyError("no slotChangedCraftingGrid signature locked for " + ver +
                       " -- read MC-Java\\" + ver + " and extend SLOT_CHANGED_SIG")
    return SLOT_CHANGED_SIG[best]


# ---- container click type: 26 ContainerInput vs pre-26 ClickType (same enum constants) ----
def click_type(ver):
    return "ContainerInput" if is26(ver) else "ClickType"


def emit_click_import(cog, ver):
    cog.outl("import net.minecraft.world.inventory." + click_type(ver) + ";")


def emit_clicked_sig(cog, ver):
    cog.outl("public void clicked(int slotId, int button, " + click_type(ver) + " clickType, Player player) {")


def emit_viewclick_sig(cog, ver):
    cog.outl("private void handleViewClick(int cell, int button, " + click_type(ver) + " clickType, Player player) {")


def emit_pickup_check(cog, ver):
    cog.outl("if (clickType == " + click_type(ver) + ".PICKUP && !carried.isEmpty()) {")


# ---- ItemContainerContents copy-iteration: 26 nonEmptyItemCopyStream vs 1.21.x nonEmptyItemsCopy ----
def emit_copy_each(cog, ver, recv):
    if is26(ver):
        cog.outl(recv + ".nonEmptyItemCopyStream().forEach(out::add);")
    else:
        cog.outl(recv + ".nonEmptyItemsCopy().forEach(out::add);")


# ---- Gfx facade: era-stable drawing surface for shared GUI code ----
# 26: wraps GuiGraphicsExtractor (native names). Pre-26 (1.21.6-1.21.11 era): wraps GuiGraphics
# (text->drawString+noShadow, item->renderItem, itemDecorations->renderItemDecorations; pose(),
# scissor, blit, setTooltipForNextFrame identical at 1.21.11). Older eras extend the branches.
def emit_gfx(cog, ver):
    modern = is26(ver)
    G = "GuiGraphicsExtractor" if modern else "GuiGraphics"
    text_body = "g.text(font, s, x, y, color);" if modern else "g.drawString(font, s, x, y, color, false);"
    ent_call = ("InventoryScreen.extractEntityInInventoryFollowsMouse" if modern
                else "InventoryScreen.renderEntityInInventoryFollowsMouse")
    item_body = "g.item(stack, x, y);" if modern else "g.renderItem(stack, x, y);"
    deco_body = ("g.itemDecorations(font, stack, x, y, s);" if modern
                 else "g.renderItemDecorations(font, stack, x, y, s);")
    lines = [
        "package com.kishku7.bankvault.client;",
        "",
        "import java.util.List;",
        "import java.util.Optional;",
        "",
        "import com.mojang.blaze3d.pipeline.RenderPipeline;",
        "",
        "import net.minecraft.client.gui.Font;",
        "import net.minecraft.client.gui.screens.inventory.InventoryScreen;",
        "import net.minecraft.client.gui." + G + ";",
        "import net.minecraft.network.chat.Component;",
        "import net.minecraft.world.entity.LivingEntity;",
        "import net.minecraft.resources.Identifier;",
        "import net.minecraft.world.inventory.tooltip.TooltipComponent;",
        "import net.minecraft.world.item.ItemStack;",
        "",
        "/** Era-stable drawing facade: shared GUI code draws through this one surface; the per-era",
        " *  graphics object and its renamed methods live behind it (cog-emitted per MC version). */",
        "public final class Gfx {",
        "",
        "    private final " + G + " g;",
        "",
        "    private Gfx(" + G + " g) { this.g = g; }",
        "",
        "    public static Gfx of(" + G + " g) { return new Gfx(g); }",
        "",
        "    public " + G + " raw() { return g; }",
        "",
        "    public void fill(int x0, int y0, int x1, int y1, int col) { g.fill(x0, y0, x1, y1, col); }",
        "",
        "    public void text(Font font, String s, int x, int y, int color) { " + text_body + " }",
        "",
        "    public void item(ItemStack stack, int x, int y) { " + item_body + " }",
        "",
        "    public void itemDecorations(Font font, ItemStack stack, int x, int y, String s) { " + deco_body + " }",
        "",
        "    public void pushMatrix() { g.pose().pushMatrix(); }",
        "",
        "    public void translate(float x, float y) { g.pose().translate(x, y); }",
        "",
        "    public void scale(float sx, float sy) { g.pose().scale(sx, sy); }",
        "",
        "    public void popMatrix() { g.pose().popMatrix(); }",
        "",
        "    public void enableScissor(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }",
        "",
        "    public void disableScissor() { g.disableScissor(); }",
        "",
        "    public void blit(RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v,",
        "                     int w, int h, int tw, int th) { g.blit(pipeline, tex, x, y, u, v, w, h, tw, th); }",
        "",
        "    public void setTooltipForNextFrame(Font font, List<Component> lines, Optional<TooltipComponent> comp,",
        "                                       int mx, int my) { g.setTooltipForNextFrame(font, lines, comp, mx, my); }",
        "",
        "    public void setTooltipForNextFrame(Font font, ItemStack stack, int mx, int my) { g.setTooltipForNextFrame(font, stack, mx, my); }",
        "",
        "    public void setTooltipForNextFrame(Font font, Component c, int mx, int my) { g.setTooltipForNextFrame(font, c, mx, my); }",
        "",
        "    public void entityInInventoryFollowsMouse(int x1, int y1, int x2, int y2, int scale, float yOff,",
        "                                              float mouseX, float mouseY, LivingEntity entity) {",
        "        " + ent_call + "(g, x1, y1, x2, y2, scale, yOff, mouseX, mouseY, entity);",
        "    }",
        "}",
    ]
    for ln in lines:
        cog.outl(ln)


# ---- BankVaultScreen render entry points (the 26 extract era vs the pre-26 render/renderBg era) ----
def emit_screen_entries(cog, ver):
    if is26(ver):
        lines = [
            "    @Override",
            "    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {",
            "        Gfx g = Gfx.of(gg);",
            "        g.fill(0, 0, this.width, this.height, OVERLAY);",
            "        drawCustom(g, mouseX, mouseY);",
            "        this.hoveredSlot = null;",
            "        for (Slot s : this.menu.slots) if (s.isActive() && isHovering(s.x, s.y, 16, 16, mouseX, mouseY)) { this.hoveredSlot = s; break; }",
            "        g.pushMatrix();",
            "        g.translate(this.leftPos, this.topPos);",
            "        this.extractSlots(gg, mouseX, mouseY);",
            "        g.popMatrix();",
            "        this.extractCarriedItem(gg, mouseX, mouseY);",
            "        this.extractTooltip(gg, mouseX, mouseY);",
            "        renderHoverTooltips(g, mouseX, mouseY);",
            "    }",
        ]
    else:
        lines = [
            "    @Override",
            "    public void render(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY, float partialTick) {",
            "        super.render(gg, mouseX, mouseY, partialTick);   // renderBg (custom UI) + slots + carried item",
            "        this.renderTooltip(gg, mouseX, mouseY);          // hovered real-slot tooltip",
            "        renderHoverTooltips(Gfx.of(gg), mouseX, mouseY);",
            "    }",
            "",
            "    @Override",
            "    protected void renderBg(net.minecraft.client.gui.GuiGraphics gg, float partialTick, int mouseX, int mouseY) {",
            "        Gfx g = Gfx.of(gg);",
            "        g.fill(0, 0, this.width, this.height, OVERLAY);",
            "        drawCustom(g, mouseX, mouseY);",
            "        this.hoveredSlot = null;",
            "        for (Slot s : this.menu.slots) if (s.isActive() && isHovering(s.x, s.y, 16, 16, mouseX, mouseY)) { this.hoveredSlot = s; break; }",
            "    }",
            "",
            "    @Override",
            "    protected void renderLabels(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY) {",
            "        // all text is drawn in drawCustom (absolute coordinates); suppress the vanilla titles",
            "    }",
        ]
    for ln in lines:
        cog.outl(ln)


def emit_ct_decl(cog, ver):
    t = click_type(ver)
    cog.outl(t + " ct = event.hasShiftDown() ? " + t + ".QUICK_MOVE : " + t + ".PICKUP;")


def emit_slotclicked_pickup(cog, ver):
    cog.outl("this.slotClicked(this.menu.slots.get(idx), idx, 0, " + click_type(ver) + ".PICKUP);")
