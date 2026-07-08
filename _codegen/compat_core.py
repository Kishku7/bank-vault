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
    "26.1": 84, "26.2": 88, "26.3": 91,  # 26.3 -> snapshot-3 resource pack_format (89/90/91 for snap-1/2/3; line advanced to snap-3)
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
# Eras: 26 (GuiGraphicsExtractor, native names) | 1.21.6-1.21.11 "new" (GuiGraphics, drawString/
# renderItem renames; pose/tooltip/blit-pipeline identical to 26) | 1.21.2-1.21.5 "mid"
# (PoseStack pose, renderTooltip, blit via Function<RL,RenderType>). Older eras: extend here.
def gfx_era(ver):
    if is26(ver):
        return "26"
    if _vt(ver) >= (1, 21, 6):
        return "new"
    if _vt(ver) >= (1, 21, 2):
        return "mid"
    return "old"   # 1.20.x - 1.21.1: as mid but direct blit(tex,...) (no RenderType function param)


def emit_gfx(cog, ver):
    era = gfx_era(ver)
    G = "GuiGraphicsExtractor" if era == "26" else "GuiGraphics"
    text_body = "g.text(font, s, x, y, color);" if era == "26" else "g.drawString(font, s, x, y, color, false);"
    item_body = "g.item(stack, x, y);" if era == "26" else "g.renderItem(stack, x, y);"
    deco_body = ("g.itemDecorations(font, stack, x, y, s);" if era == "26"
                 else "g.renderItemDecorations(font, stack, x, y, s);")
    ent_call = ("InventoryScreen.extractEntityInInventoryFollowsMouse" if era == "26"
                else "InventoryScreen.renderEntityInInventoryFollowsMouse")
    tt = "setTooltipForNextFrame" if era in ("26", "new") else "renderTooltip"
    if era in ("mid", "old"):
        pose_ops = [
            "    public void pushMatrix() { g.pose().pushPose(); }",
            "",
            "    public void translate(float x, float y) { g.pose().translate(x, y, 0); }",
            "",
            "    public void scale(float sx, float sy) { g.pose().scale(sx, sy, 1); }",
            "",
            "    public void popMatrix() { g.pose().popPose(); }",
        ]
        if era == "mid":
            blit_body = "g.blit(RenderType::guiTextured, tex, x, y, u, v, w, h, tw, th);"
            blit_imports = ["import net.minecraft.client.renderer.RenderType;"]
        else:
            blit_body = "g.blit(tex, x, y, u, v, w, h, tw, th);"
            blit_imports = []
    else:
        pose_ops = [
            "    public void pushMatrix() { g.pose().pushMatrix(); }",
            "",
            "    public void translate(float x, float y) { g.pose().translate(x, y); }",
            "",
            "    public void scale(float sx, float sy) { g.pose().scale(sx, sy); }",
            "",
            "    public void popMatrix() { g.pose().popMatrix(); }",
        ]
        blit_body = "g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, tw, th);"
        blit_imports = ["import net.minecraft.client.renderer.RenderPipelines;"]
    lines = [
        "package com.kishku7.bankvault.client;",
        "",
        "import java.util.List;",
        "import java.util.Optional;",
        "",
        "import net.minecraft.client.gui.Font;",
        "import net.minecraft.client.gui." + G + ";",
        "import net.minecraft.client.gui.screens.inventory.InventoryScreen;",
    ] + blit_imports + [
        "import net.minecraft.network.chat.Component;",
        "import net.minecraft.resources.Identifier;",
        "import net.minecraft.world.entity.LivingEntity;",
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
    ] + pose_ops + [
        "",
        "    public void enableScissor(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }",
        "",
        "    public void disableScissor() { g.disableScissor(); }",
        "",
        "    public void blitGuiTextured(Identifier tex, int x, int y, float u, float v,",
        "                                int w, int h, int tw, int th) { " + blit_body + " }",
        "",
        "    public void setTooltipForNextFrame(Font font, List<Component> lines, Optional<TooltipComponent> comp,",
        "                                       int mx, int my) { g." + tt + "(font, lines, comp, mx, my); }",
        "",
        "    public void setTooltipForNextFrame(Font font, ItemStack stack, int mx, int my) { g." + tt + "(font, stack, mx, my); }",
        "",
        "    public void setTooltipForNextFrame(Font font, Component c, int mx, int my) { g." + tt + "(font, c, mx, my); }",
        "",
        "    public void entityInInventoryFollowsMouse(int x1, int y1, int x2, int y2, int scale, float yOff,",
        "                                              float mouseX, float mouseY, LivingEntity entity) {",
        ("        " + ent_call + "(g, x1, y1, x2, y2, scale, yOff, mouseX, mouseY, entity);"
         if _vt(ver) >= (1, 20, 2) else
         "        " + ent_call + "(g, (x1 + x2) / 2, y2, scale, (x1 + x2) / 2.0F - mouseX, (y1 + y2) / 2.0F - 25 - mouseY, entity);"),
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


# ---- input-record era: MouseButtonEvent/KeyEvent/CharacterEvent exist from 1.21.9 ----
def modern_input(ver):
    return _vt(ver) >= (1, 21, 9)


# perms API (net.minecraft.server.permissions) arrived at 1.21.11 (NOT the 1.21.9 input wave)
def modern_perms(ver):
    return _vt(ver) >= (1, 21, 11)


def emit_input_imports(cog, ver):
    if modern_input(ver):
        cog.outl("import net.minecraft.client.input.CharacterEvent;")
        cog.outl("import net.minecraft.client.input.KeyEvent;")
        cog.outl("import net.minecraft.client.input.MouseButtonEvent;")


# Ev: BV-owned input shim -- one shared accessor surface over the 1.21.9+ input records or
# pre-1.21.9 primitive parameters. Whole-file emitted.
def emit_ev(cog, ver):
    if modern_input(ver):
        lines = [
            "package com.kishku7.bankvault.client;",
            "",
            "import net.minecraft.client.input.CharacterEvent;",
            "import net.minecraft.client.input.KeyEvent;",
            "import net.minecraft.client.input.MouseButtonEvent;",
            "",
            "/** Input shim: same accessor surface every era; wraps the 1.21.9+ input records here. */",
            "public final class Ev {",
            "",
            "    private final Object raw;",
            "",
            "    private Ev(Object raw) { this.raw = raw; }",
            "",
            "    public static Ev key(KeyEvent e) { return new Ev(e); }",
            "",
            "    public static Ev chr(CharacterEvent e) { return new Ev(e); }",
            "",
            "    public static Ev mouse(MouseButtonEvent e) { return new Ev(e); }",
            "",
            "    public Object raw() { return raw; }",
            "",
            "    public int input() { return ((KeyEvent) raw).input(); }",
            "",
            "    public boolean isEscape() { return ((KeyEvent) raw).isEscape(); }",
            "",
            "    public boolean isConfirmation() { return ((KeyEvent) raw).isConfirmation(); }",
            "",
            "    public boolean isAllowedChatCharacter() { return ((CharacterEvent) raw).isAllowedChatCharacter(); }",
            "",
            "    public String codepointAsString() { return ((CharacterEvent) raw).codepointAsString(); }",
            "",
            "    public double x() { return ((MouseButtonEvent) raw).x(); }",
            "",
            "    public double y() { return ((MouseButtonEvent) raw).y(); }",
            "",
            "    public int button() { return ((MouseButtonEvent) raw).button(); }",
            "",
            "    public boolean hasShiftDown() { return ((MouseButtonEvent) raw).hasShiftDown(); }",
            "}",
        ]
    else:
        lines = [
            "package com.kishku7.bankvault.client;",
            "",
            "import net.minecraft.client.gui.screens.Screen;",
            ("import net.minecraft.util.StringUtil;" if _vt(ver) >= (1, 20, 5)
             else "import net.minecraft.SharedConstants;"),
            "",
            "/** Input shim: same accessor surface every era; carries pre-1.21.9 primitive params here. */",
            "public final class Ev {",
            "",
            "    private final double mx, my;",
            "    private final int button;",
            "    private final int key, scan, mods;",
            "    private final int codepoint;",
            "",
            "    private Ev(double mx, double my, int button, int key, int scan, int mods, int codepoint) {",
            "        this.mx = mx; this.my = my; this.button = button;",
            "        this.key = key; this.scan = scan; this.mods = mods; this.codepoint = codepoint;",
            "    }",
            "",
            "    public static Ev key(int key, int scan, int mods) { return new Ev(0, 0, -1, key, scan, mods, 0); }",
            "",
            "    public static Ev chr(char c, int mods) { return new Ev(0, 0, -1, 0, 0, mods, c); }",
            "",
            "    public static Ev mouse(double x, double y, int button) { return new Ev(x, y, button, 0, 0, 0, 0); }",
            "",
            "    public int key() { return key; }",
            "",
            "    public int scan() { return scan; }",
            "",
            "    public int mods() { return mods; }",
            "",
            "    public char codepoint() { return (char) codepoint; }",
            "",
            "    public int input() { return key; }",
            "",
            "    public boolean isEscape() { return key == 256; }",
            "",
            "    public boolean isConfirmation() { return key == 257 || key == 335; }",
            "",
            ("    public boolean isAllowedChatCharacter() { return StringUtil.isAllowedChatCharacter((char) codepoint); }"
             if _vt(ver) >= (1, 20, 5) else
             "    public boolean isAllowedChatCharacter() { return SharedConstants.isAllowedChatCharacter((char) codepoint); }"),
            "",
            "    public String codepointAsString() { return String.valueOf((char) codepoint); }",
            "",
            "    public double x() { return mx; }",
            "",
            "    public double y() { return my; }",
            "",
            "    public int button() { return button; }",
            "",
            "    public boolean hasShiftDown() { return Screen.hasShiftDown(); }",
            "}",
        ]
    for ln in lines:
        cog.outl(ln)


_INPUT_HEADS = {
    "key": ("public boolean keyPressed(KeyEvent event0) { return keyPressedImpl(Ev.key(event0)); }",
            "public boolean keyPressed(int key, int scan, int mods) { return keyPressedImpl(Ev.key(key, scan, mods)); }",
            "private boolean keyPressedImpl(Ev event) {"),
    "chr": ("public boolean charTyped(CharacterEvent event0) { return charTypedImpl(Ev.chr(event0)); }",
            "public boolean charTyped(char c, int mods) { return charTypedImpl(Ev.chr(c, mods)); }",
            "private boolean charTypedImpl(Ev event) {"),
    "click": ("public boolean mouseClicked(MouseButtonEvent event0, boolean doubleClick) { return mouseClickedImpl(Ev.mouse(event0), doubleClick); }",
              "public boolean mouseClicked(double x, double y, int button) { return mouseClickedImpl(Ev.mouse(x, y, button), false); }",
              "private boolean mouseClickedImpl(Ev event, boolean doubleClick) {"),
    "drag": ("public boolean mouseDragged(MouseButtonEvent event0, double dragX, double dragY) { return mouseDraggedImpl(Ev.mouse(event0), dragX, dragY); }",
             "public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) { return mouseDraggedImpl(Ev.mouse(x, y, button), dragX, dragY); }",
             "private boolean mouseDraggedImpl(Ev event, double dragX, double dragY) {"),
    "release": ("public boolean mouseReleased(MouseButtonEvent event0) { return mouseReleasedImpl(Ev.mouse(event0)); }",
                "public boolean mouseReleased(double x, double y, int button) { return mouseReleasedImpl(Ev.mouse(x, y, button)); }",
                "private boolean mouseReleasedImpl(Ev event) {"),
}

_INPUT_TAILS = {
    "key": ("return super.keyPressed((net.minecraft.client.input.KeyEvent) event.raw());",
            "return super.keyPressed(event.key(), event.scan(), event.mods());"),
    "chr": ("return super.charTyped((net.minecraft.client.input.CharacterEvent) event.raw());",
            "return super.charTyped(event.codepoint(), event.mods());"),
    "click": ("return super.mouseClicked((net.minecraft.client.input.MouseButtonEvent) event.raw(), doubleClick);",
              "return super.mouseClicked(event.x(), event.y(), event.button());"),
    "drag": ("return super.mouseDragged((net.minecraft.client.input.MouseButtonEvent) event.raw(), dragX, dragY);",
             "return super.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);"),
    "release": ("return super.mouseReleased((net.minecraft.client.input.MouseButtonEvent) event.raw());",
                "return super.mouseReleased(event.x(), event.y(), event.button());"),
}


def emit_input_head(cog, ver, kind):
    modern, legacy, impl = _INPUT_HEADS[kind]
    cog.outl("    @Override")
    cog.outl("    " + (modern if modern_input(ver) else legacy))
    cog.outl("")
    cog.outl("    " + impl)


def emit_input_tail(cog, ver, kind):
    modern, legacy = _INPUT_TAILS[kind]
    cog.outl(modern if modern_input(ver) else legacy)


def emit_perm_gamemaster26(cog, ver):
    # 1.21.9+ permissions() API vs classic int level
    if modern_perms(ver):
        cog.outl("s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)")
    else:
        cog.outl("s.hasPermission(2)")


# ---- BlockEntity save/load IO era: ValueInput/Output @1.21.6+; CompoundTag(+Provider) before ----
def emit_be_io_imports(cog, ver):
    if _vt(ver) >= (1, 21, 6):
        cog.outl("import net.minecraft.world.level.storage.ValueInput;")
        cog.outl("import net.minecraft.world.level.storage.ValueOutput;")
    elif _vt(ver) >= (1, 20, 5):
        cog.outl("import net.minecraft.core.HolderLookup;")
        cog.outl("import net.minecraft.nbt.CompoundTag;")
    else:
        cog.outl("import net.minecraft.nbt.CompoundTag;")


def emit_be_io(cog, ver):
    if _vt(ver) >= (1, 21, 6):
        lines = [
            "    @Override",
            "    protected void saveAdditional(ValueOutput output) {",
            "        super.saveAdditional(output);",
            '        if (builderUUID != null) output.putString("builder", builderUUID.toString());',
            "    }",
            "",
            "    @Override",
            "    protected void loadAdditional(ValueInput input) {",
            "        super.loadAdditional(input);",
            '        String b = input.getStringOr("builder", "");',
            "        this.builderUUID = b.isEmpty() ? null : UUID.fromString(b);",
            "    }",
        ]
    elif _vt(ver) >= (1, 20, 5):
        read = ('tag.getStringOr("builder", "")' if _vt(ver) >= (1, 21, 5)
                else 'tag.getString("builder")')
        lines = [
            "    @Override",
            "    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {",
            "        super.saveAdditional(tag, registries);",
            '        if (builderUUID != null) tag.putString("builder", builderUUID.toString());',
            "    }",
            "",
            "    @Override",
            "    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {",
            "        super.loadAdditional(tag, registries);",
            "        String b = " + read + ";",
            "        this.builderUUID = b.isEmpty() ? null : UUID.fromString(b);",
            "    }",
        ]
    else:
        lines = [
            "    @Override",
            "    protected void saveAdditional(CompoundTag tag) {",
            "        super.saveAdditional(tag);",
            '        if (builderUUID != null) tag.putString("builder", builderUUID.toString());',
            "    }",
            "",
            "    @Override",
            "    public void load(CompoundTag tag) {",
            "        super.load(tag);",
            '        String b = tag.getString("builder");',
            "        this.builderUUID = b.isEmpty() ? null : UUID.fromString(b);",
            "    }",
        ]
    for ln in lines:
        cog.outl(ln)


# ---- Slot.getNoItemIcon: single id @1.21.4+; Pair<atlas,sprite> before ----
def emit_no_item_icon_shield(cog, ver):
    if _vt(ver) >= (1, 21, 4):
        cog.outl("@Override public " + id_type(ver) + " getNoItemIcon() { return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD; }")
    else:
        cog.outl("@Override public com.mojang.datafixers.util.Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {")
        cog.outl("    return com.mojang.datafixers.util.Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);")
        cog.outl("}")


# ---- registration ids: Properties.setId / BlockItem id key exist from 1.21.2 ----
def has_setid(ver):
    return _vt(ver) >= (1, 21, 2)


def emit_block_props_tail(cog, ver):
    if has_setid(ver):
        cog.outl("                .pushReaction(PushReaction.BLOCK)")
        cog.outl("                .setId(ResourceKey.create(Registries.BLOCK, " + id_type(ver) + ".fromNamespaceAndPath(BankVault.MOD_ID, name)));")
    else:
        cog.outl("                .pushReaction(PushReaction.BLOCK);")


def emit_blockitem_fabric(cog, ver):
    if has_setid(ver):
        cog.outl("        Registry.register(BuiltInRegistries.ITEM, id,")
        cog.outl("                new BlockItem(ModBlocks.VAULT, new Item.Properties().setId(key)));")
    else:
        cog.outl("        Registry.register(BuiltInRegistries.ITEM, id,")
        cog.outl("                new BlockItem(ModBlocks.VAULT, new Item.Properties()));")


def emit_blockitem_deferred(cog, ver):
    if has_setid(ver):
        cog.outl("            return new BlockItem(ModBlocks.VAULT, new Item.Properties().setId(key));")
    else:
        cog.outl("            return new BlockItem(ModBlocks.VAULT, new Item.Properties());")


def emit_ominous_set(cog, ver):
    if _vt(ver) >= (1, 21, 2):
        cog.outl("s.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, new net.minecraft.world.item.component.OminousBottleAmplifier(amp));")
    else:
        cog.outl("s.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, amp);")


# ---- CraftingMenu.slotChangedCraftingGrid call: +RecipeHolder param @1.21+; 5-arg on 1.20.x ----
def emit_slot_changed_call(cog, ver):
    if _vt(ver) >= (1, 21):
        cog.outl("CraftingMenu.slotChangedCraftingGrid(this, sl, owner, craftSlots, resultSlots, null);")
    else:
        cog.outl("CraftingMenu.slotChangedCraftingGrid(this, sl, owner, craftSlots, resultSlots);")


# ---- ArmorSlot class exists from 1.21; before, use a plain Slot (armor swap validation is a
# GUI nicety -- the 1.20.x era menu simply exposes the armor inventory slots) ----
def emit_armor_slot(cog, ver):
    if _vt(ver) >= (1, 21):
        cog.outl("addSlot(new ArmorSlot(inv, inv.player, ARMOR_ORDER[i], ARMOR_INV_INDEX[i], 0, 0, ARMOR_ICONS[i]));")
    else:
        cog.outl("addSlot(new Slot(inv, ARMOR_INV_INDEX[i], 0, 0));")


def emit_armor_slot_import(cog, ver):
    if _vt(ver) >= (1, 21):
        cog.outl("import net.minecraft.world.inventory.ArmorSlot;")


# ---- ItemEnchantments.Mutable.set: Holder overload @1.21+; raw Enchantment at 1.20.5/6 ----
def emit_ench_set(cog, ver):
    if _vt(ver) >= (1, 21):
        cog.outl("mut.set(holder, lvl);")
    else:
        cog.outl("mut.set(holder.value(), lvl);")


# ================= pre-components era (< 1.20.5) =================

def emit_stackstore_ops(cog, ver):
    if has_components(ver):
        cog.outl("return ra.createSerializationContext(JsonOps.INSTANCE);")
    else:
        cog.outl("return JsonOps.INSTANCE;   // pre-1.20.5: plain NBT-backed codec needs no registry context")


def emit_stackstore_isplain(cog, ver):
    if has_components(ver):
        cog.outl("return stack.getComponentsPatch().isEmpty();")
    else:
        cog.outl("return !stack.hasTag();")


def emit_bc_import_components(cog, ver):
    if has_components(ver):
        cog.outl("import net.minecraft.core.component.DataComponents;")


def emit_bc_import_registries(cog, ver):
    if has_components(ver):
        cog.outl("import net.minecraft.core.registries.Registries;")


def emit_bc_import_brew(cog, ver):
    if has_components(ver):
        cog.outl("import net.minecraft.world.item.alchemy.PotionContents;")
        cog.outl("import net.minecraft.world.item.enchantment.ItemEnchantments;")
    else:
        cog.outl("import net.minecraft.world.item.EnchantedBookItem;")
        cog.outl("import net.minecraft.world.item.alchemy.Potion;")
        cog.outl("import net.minecraft.world.item.alchemy.PotionUtils;")
        cog.outl("import net.minecraft.world.item.enchantment.Enchantment;")
        cog.outl("import net.minecraft.world.item.enchantment.EnchantmentInstance;")


def emit_fillall_variants(cog, ver):
    if has_components(ver):
        ench_set = "mut.set(holder, lvl);" if _vt(ver) >= (1, 21) else "mut.set(holder.value(), lvl);"
        omin_set = ("s.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, new net.minecraft.world.item.component.OminousBottleAmplifier(amp));"
                    if _vt(ver) >= (1, 21, 2)
                    else "s.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, amp);")
        lines = [
            "        var enchants = ra.lookupOrThrow(Registries.ENCHANTMENT);",
            "        for (var holder : enchants.listElements().toList()) {",
            "            int max = holder.value().getMaxLevel();",
            "            for (int lvl = 1; lvl <= max; lvl++) {",
            "                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);",
            "                ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);",
            "                " + ench_set,
            "                book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());",
            "                if (!BankManager.hasExact(bank, book, ra) && BankManager.depositStack(bank, book, ra) > 0) books++;",
            "            }",
            "        }",
            "        var pots = ra.lookupOrThrow(Registries.POTION);",
            "        for (var holder : pots.listElements().toList()) {",
            "            boolean hasEffects = !holder.value().getEffects().isEmpty();",
            "            for (Item base : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {",
            "                if (base == Items.TIPPED_ARROW && !hasEffects) continue; // no-effect tipped arrows aren't survival",
            "                ItemStack s = PotionContents.createItemStack(base, holder);",
            "                if (!BankManager.hasExact(bank, s, ra) && BankManager.depositStack(bank, s, ra) > 0) potions++;",
            "            }",
            "        }",
            "        for (int amp = 0; amp < 5; amp++) {",
            "            ItemStack s = new ItemStack(Items.OMINOUS_BOTTLE);",
            "            " + omin_set,
            "            if (!BankManager.hasExact(bank, s, ra) && BankManager.depositStack(bank, s, ra) > 0) other++;",
            "        }",
        ]
    else:
        lines = [
            "        for (Enchantment ench : BuiltInRegistries.ENCHANTMENT) {",
            "            int max = ench.getMaxLevel();",
            "            for (int lvl = 1; lvl <= max; lvl++) {",
            "                ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, lvl));",
            "                if (!BankManager.hasExact(bank, book, ra) && BankManager.depositStack(bank, book, ra) > 0) books++;",
            "            }",
            "        }",
            "        for (Potion potion : BuiltInRegistries.POTION) {",
            "            boolean hasEffects = !potion.getEffects().isEmpty();",
            "            for (Item base : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {",
            "                if (base == Items.TIPPED_ARROW && !hasEffects) continue;",
            "                ItemStack s = PotionUtils.setPotion(new ItemStack(base), potion);",
            "                if (!BankManager.hasExact(bank, s, ra) && BankManager.depositStack(bank, s, ra) > 0) potions++;",
            "            }",
            "        }",
            "        // ominous bottles do not exist before the 1.21-era content",
        ]
    for ln in lines:
        cog.outl(ln)


def mouse_scroll4(ver):
    return _vt(ver) >= (1, 20, 2)


def emit_scroll_head(cog, ver):
    cog.outl("    @Override")
    if mouse_scroll4(ver):
        cog.outl("    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {")
    else:
        cog.outl("    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {")
        cog.outl("        final double scrollX = 0;   // pre-1.20.2 scroll events are vertical-only")


def emit_scroll_tail(cog, ver):
    if mouse_scroll4(ver):
        cog.outl("return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);")
    else:
        cog.outl("return super.mouseScrolled(mouseX, mouseY, scrollY);")


def has_block_codec(ver):
    # block MapCodec/simpleCodec/codec() exist 1.20.3 .. 26.2; REMOVED at 26.3-snapshot-2
    return (1, 20, 3) <= _vt(ver) < (26, 3)


def emit_block_codec_import(cog, ver):
    if has_block_codec(ver):
        cog.outl("import com.mojang.serialization.MapCodec;")


def emit_block_codec_field(cog, ver):
    if has_block_codec(ver):
        cog.outl("    public static final MapCodec<BankVaultBlock> CODEC = simpleCodec(BankVaultBlock::new);")


def emit_block_codec_override(cog, ver):
    if has_block_codec(ver):
        cog.outl("    @Override")
        cog.outl("    public MapCodec<BankVaultBlock> codec() {")
        cog.outl("        return CODEC;")
        cog.outl("    }")
    else:
        cog.outl("    // no block MapCodec before 1.20.3")


# ---- potion/enchant sort keys: components @1.20.5+; PotionUtils/EnchantedBookItem NBT before ----
def emit_sort_keys(cog, ver):
    if has_components(ver):
        lines = [
            "    private static String potionEffectKey(Entry e) {",
            "        var pc = e.stack().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);",
            '        if (pc == null || pc.potion().isEmpty()) return "~";',
            "        String p = pc.potion().get().getRegisteredName();",
            "        p = p.substring(p.indexOf(':') + 1);",
            '        if (p.startsWith("long_")) p = p.substring(5);',
            '        if (p.startsWith("strong_")) p = p.substring(7);',
            "        return p;",
            "    }",
            "",
            '    /** 0 = plain, 1 = long, 2 = strong -- "logically sub-sorted by effect power". */',
            "    private static int potionPower(Entry e) {",
            "        var pc = e.stack().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);",
            "        if (pc == null || pc.potion().isEmpty()) return 0;",
            "        String p = pc.potion().get().getRegisteredName();",
            '        if (p.contains(":long_")) return 1;',
            '        if (p.contains(":strong_")) return 2;',
            "        return 0;",
            "    }",
            "",
            '    /** Sort key for enchanted books (all share one hover name): first stored enchantment id, then level. */',
            "    private static String enchantKey(Entry e) {",
            "        var stored = e.stack().get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);",
            '        if (stored == null || stored.isEmpty()) return "~";',
            "        var en = stored.entrySet().iterator().next();",
            '        return en.getKey().getRegisteredName() + String.format("%02d", en.getIntValue());',
            "    }",
        ]
    else:
        lines = [
            "    private static String potionEffectKey(Entry e) {",
            "        var potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(e.stack());",
            '        if (potion == net.minecraft.world.item.alchemy.Potions.EMPTY) return "~";',
            "        String p = net.minecraft.core.registries.BuiltInRegistries.POTION.getKey(potion).toString();",
            "        p = p.substring(p.indexOf(':') + 1);",
            '        if (p.startsWith("long_")) p = p.substring(5);',
            '        if (p.startsWith("strong_")) p = p.substring(7);',
            "        return p;",
            "    }",
            "",
            '    /** 0 = plain, 1 = long, 2 = strong -- "logically sub-sorted by effect power". */',
            "    private static int potionPower(Entry e) {",
            "        var potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(e.stack());",
            "        if (potion == net.minecraft.world.item.alchemy.Potions.EMPTY) return 0;",
            "        String p = net.minecraft.core.registries.BuiltInRegistries.POTION.getKey(potion).toString();",
            '        if (p.contains(":long_")) return 1;',
            '        if (p.contains(":strong_")) return 2;',
            "        return 0;",
            "    }",
            "",
            '    /** Sort key for enchanted books (all share one hover name): first stored enchantment id, then level. */',
            "    private static String enchantKey(Entry e) {",
            "        var tag = net.minecraft.world.item.EnchantedBookItem.getEnchantments(e.stack());",
            '        if (tag.isEmpty()) return "~";',
            "        var ct = (net.minecraft.nbt.CompoundTag) tag.get(0);",
            '        return ct.getString("id") + String.format("%02d", ct.getShort("lvl"));',
            "    }",
        ]
    for ln in lines:
        cog.outl(ln)


# ---- block interaction: useWithoutItem @1.20.5+; classic use() with InteractionHand before ----
def emit_block_use_head(cog, ver):
    if has_components(ver):
        cog.outl("    @Override")
        cog.outl("    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,")
        cog.outl("                                               Player player, BlockHitResult hit) {")
    else:
        cog.outl("    @Override")
        cog.outl('    @SuppressWarnings("deprecation")   // use() IS the 1.20.x interaction override; the replacement only exists from 1.20.5')
        cog.outl("    public InteractionResult use(BlockState state, Level level, BlockPos pos,")
        cog.outl("                                 Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {")
# ================= BvCompat era bodies =================
# 26 line: ONE compile serves 26.1 -> 26.3, so fault-line bridges use reflection (mojmap runtime
# on ALL 26 loaders -- resolves fine). Pre-26: reflection-by-mojmap-name MISSES on Fabric
# (intermediary runtime) -- currentScreen returned null and the vault screen never received
# VaultSync/SharingState (the 1.4.0 spot-play bug). Per the fall-through rule the pre-26 bodies
# are DIRECT code (loom/FG6/MDG remap it per cell), which is universal.

def emit_bvcompat_setscreen(cog, ver):
    if is26(ver):
        cog.outl('        if (invoke1(mc, "setScreenAndShow", Screen.class, screen)) return;')
        cog.outl('        invoke1(mc, "setScreen", Screen.class, screen);')
    else:
        cog.outl('        mc.setScreen(screen);')


def emit_bvcompat_currentscreen(cog, ver):
    if is26(ver):
        cog.outl('        try {')
        cog.outl('            Field guiF = Minecraft.class.getField("gui");')
        cog.outl('            Object gui = guiF.get(mc);')
        cog.outl('            Method m = gui.getClass().getMethod("screen");')
        cog.outl('            return (Screen) m.invoke(gui);')
        cog.outl('        } catch (Exception ignored) {}')
        cog.outl('        try {')
        cog.outl('            Field f = Minecraft.class.getField("screen");')
        cog.outl('            return (Screen) f.get(mc);')
        cog.outl('        } catch (Exception e) {')
        cog.outl('            return null;')
        cog.outl('        }')
    else:
        cog.outl('        return mc.screen;')


def emit_bvcompat_itemcopies(cog, ver):
    if is26(ver):
        cog.outl('        for (String name : new String[]{"itemCopies", "itemCopyStream"}) {')
        cog.outl('            try {')
        cog.outl('                Method m = bundleContents.getClass().getMethod(name);')
        cog.outl('                return (Stream<net.minecraft.world.item.ItemStack>) m.invoke(bundleContents);')
        cog.outl('            } catch (NoSuchMethodException ignored) {')
        cog.outl('            } catch (Exception e) {')
        cog.outl('                throw new RuntimeException(e);')
        cog.outl('            }')
        cog.outl('        }')
        cog.outl('        throw new RuntimeException("BvCompat.itemCopies: no itemCopies/itemCopyStream on " + bundleContents.getClass());')
    elif has_components(ver):
        cog.outl('        return ((net.minecraft.world.item.component.BundleContents) bundleContents).itemCopyStream();')
    else:
        cog.outl('        throw new UnsupportedOperationException("bundle components do not exist pre-1.20.5");')


def emit_bvcompat_invoke1(cog, ver):
    if not is26(ver):
        return
    cog.outl('')
    cog.outl('    private static boolean invoke1(Object target, String method, Class<?> paramType, Object arg) {')
    cog.outl('        try {')
    cog.outl('            Method m = target.getClass().getMethod(method, paramType);')
    cog.outl('            m.invoke(target, arg);')
    cog.outl('            return true;')
    cog.outl('        } catch (NoSuchMethodException e) {')
    cog.outl('            return false;')
    cog.outl('        } catch (Exception e) {')
    cog.outl('            throw new RuntimeException("BvCompat." + method + " failed", e);')
    cog.outl('        }')
    cog.outl('    }')
