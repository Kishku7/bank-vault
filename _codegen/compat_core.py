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
    "26": "(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/inventory/ResultContainer;Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
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
