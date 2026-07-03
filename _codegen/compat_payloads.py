"""compat_payloads.py -- pre-components (MC < 1.20.5) payload generation for Bank Vault.

Modern era (>=1.20.5): every payload file materializes as a verbatim passthrough of its
shared_minecraft twin (records + CustomPacketPayload + StreamCodec).
Pre era: plain records implementing the BV-own BvPayload (id() + write(FriendlyByteBuf)) with a
static decode(FriendlyByteBuf), generated from the field tables below.
"""
import os

import compat_core

_REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def _twin_shared(rel):
    p = os.path.join(_REPO, "shared_minecraft", "src", "main", "java", "com", "kishku7", "bankvault", rel)
    with open(p, "r", encoding="utf-8") as f:
        return f.read().splitlines()


def modern(ver):
    return compat_core.has_components(ver)


# field kinds: utf | varint | varlong | bool | item | list_utf | list:<Nested>
PAYLOADS = {
    "WithdrawPayload": {
        "id": "withdraw", "doc": "Client -> server: withdraw {amount} of {itemId}.",
        "fields": [("itemId", "utf"), ("amount", "varint")],
    },
    "UpgradePayload": {
        "id": "upgrade", "doc": "Client -> server: add (true) or remove (false) one upgrade chest.",
        "fields": [("add", "bool")],
    },
    "DepositPayload": {
        "id": "deposit", "doc": "Client -> server: deposit the stack in inventory slot {slot}.",
        "fields": [("slot", "varint")],
    },
    "GridViewPayload": {
        "id": "grid_view", "doc": "Client -> server: which bank keys the visible grid shows (per tab).",
        "fields": [("tab", "utf"), ("keys", "list_utf")],
    },
    "DepositAllPayload": {
        "id": "deposit_all", "doc": "Client -> server: bulk deposit (main rows; hotbar too when {includeHotbar}).",
        "fields": [("includeHotbar", "bool")],
    },
    "ShareActionPayload": {
        "id": "share_action", "doc": "Client -> server: a Sharing-corner action (v1.1). Checks are server-side.",
        "fields": [("op", "varint"), ("target", "utf"), ("level", "varint")],
        "extras": ["    public static final int INVITE = 0, ACCEPT = 1, DECLINE = 2, KICK = 3,",
                   "            LEVEL_UP = 4, LEVEL_DOWN = 5, LEAVE = 6, REFRESH = 7;", ""],
    },
    "UiStatePayload": {
        "id": "ui_state", "doc": "Client -> server: v1.2 last-use memory update (tab/sort/sections).",
        "fields": [("lastTab", "utf"), ("tab", "utf"), ("sort", "utf"), ("sections", "utf")],
    },
    "UiStateSyncPayload": {
        "id": "ui_state_sync", "doc": "Server -> client: remembered UI state, sent before menu open.",
        "fields": [("lastTab", "utf"), ("sorts", "list:TabSort"), ("showSections", "bool"), ("pins", "list:TabPins")],
        "nested": {"TabSort": [("tab", "utf"), ("sort", "utf")],
                   "TabPins": [("tab", "utf"), ("ids", "list_utf")]},
    },
    "SharingStatePayload": {
        "id": "sharing_state", "doc": "Server -> client: sharing-corner membership + pending invites.",
        "fields": [("members", "list:Member"), ("invites", "list:InviteEntry")],
        "nested": {"Member": [("uuid", "utf"), ("name", "utf"), ("level", "varint")],
                   "InviteEntry": [("from", "utf"), ("level", "varint")]},
    },
    "VaultSyncPayload": {
        "id": "vault_sync", "doc": "Server -> client: the bank snapshot the screen renders.",
        "fields": [("entries", "list:Entry"), ("upgradeCount", "varint"), ("capacity", "varlong"), ("permLevel", "varint")],
        "nested": {"Entry": [("key", "utf"), ("stack", "item"), ("count", "varlong")]},
    },
}

_JT = {"utf": "String", "varint": "int", "varlong": "long", "bool": "boolean", "item": "ItemStack",
       "list_utf": "List<String>"}


def _jtype(kind):
    if kind.startswith("list:"):
        return "List<" + kind[5:] + ">"
    return _JT[kind]


def _write_stmt(field, kind, owner_is_nested):
    acc = ("v." + field + "()") if owner_is_nested else (field + "()")
    if kind == "utf":
        return "buf.writeUtf(" + acc + ");"
    if kind == "varint":
        return "buf.writeVarInt(" + acc + ");"
    if kind == "varlong":
        return "buf.writeVarLong(" + acc + ");"
    if kind == "bool":
        return "buf.writeBoolean(" + acc + ");"
    if kind == "item":
        return "buf.writeItem(" + acc + ");"
    if kind == "list_utf":
        return ("buf.writeVarInt(" + acc + ".size()); for (String s : " + acc + ") buf.writeUtf(s);")
    if kind.startswith("list:"):
        n = kind[5:]
        return ("buf.writeVarInt(" + acc + ".size()); for (" + n + " e : " + acc + ") " + n + ".write(buf, e);")
    raise KeyError(kind)


def _read_expr(kind, tmp):
    if kind == "utf":
        return None, "buf.readUtf()"
    if kind == "varint":
        return None, "buf.readVarInt()"
    if kind == "varlong":
        return None, "buf.readVarLong()"
    if kind == "bool":
        return None, "buf.readBoolean()"
    if kind == "item":
        return None, "buf.readItem()"
    if kind == "list_utf":
        pre = ("int n{i} = buf.readVarInt(); List<String> {t} = new ArrayList<>(n{i}); "
               "for (int i{i} = 0; i{i} < n{i}; i{i}++) {t}.add(buf.readUtf());").replace("{t}", tmp).replace("{i}", tmp)
        return pre, tmp
    if kind.startswith("list:"):
        n = kind[5:]
        pre = ("int n{i} = buf.readVarInt(); List<" + n + "> {t} = new ArrayList<>(n{i}); "
               "for (int i{i} = 0; i{i} < n{i}; i{i}++) {t}.add(" + n + ".read(buf));").replace("{t}", tmp).replace("{i}", tmp)
        return pre, tmp
    raise KeyError(kind)


def _record_io(name, fields, nested_level):
    """write(buf, v) + read(buf) statics for a nested record; or instance write + static decode for the payload."""
    out = []
    ind = "        " if nested_level else "    "
    if nested_level:
        out.append(ind + "public static void write(FriendlyByteBuf buf, " + name + " v) {")
        for f, k in fields:
            out.append(ind + "    " + _write_stmt(f, k, True))
        out.append(ind + "}")
        out.append("")
        out.append(ind + "public static " + name + " read(FriendlyByteBuf buf) {")
    else:
        out.append(ind + "@Override")
        out.append(ind + "public void write(FriendlyByteBuf buf) {")
        for f, k in fields:
            out.append(ind + "    " + _write_stmt(f, k, False))
        out.append(ind + "}")
        out.append("")
        out.append(ind + "public static " + name + " decode(FriendlyByteBuf buf) {")
    args = []
    for f, k in fields:
        pre, expr = _read_expr(k, "l" + f)
        if pre:
            out.append(ind + "    " + pre)
            args.append(expr)
        else:
            # scalars also land in locals so the BUFFER READ ORDER always matches write order
            out.append(ind + "    " + _jtype(k) + " l" + f + " = " + expr + ";")
            args.append("l" + f)
    out.append(ind + "    return new " + name + "(" + ", ".join(args) + ");")
    out.append(ind + "}")
    return out


def emit_payload(cog, ver, name):
    if modern(ver):
        for ln in _twin_shared(os.path.join("net", name + ".java")):
            cog.outl(ln)
        return
    spec = PAYLOADS[name]
    kinds = [k for _, k in spec["fields"]]
    for sub in spec.get("nested", {}).values():
        kinds += [k for _, k in sub]
    needs_list = any(k.startswith("list") for k in kinds)
    needs_item = "item" in kinds
    lines = [
        "package com.kishku7.bankvault.net;",
        "",
        "import com.kishku7.bankvault.BankVault;",
        "import net.minecraft.network.FriendlyByteBuf;",
        "import net.minecraft.resources.ResourceLocation;",
    ]
    if needs_item:
        lines.append("import net.minecraft.world.item.ItemStack;")
    if needs_list:
        lines += ["", "import java.util.ArrayList;", "import java.util.List;"]
    sig = ", ".join(_jtype(k) + " " + f for f, k in spec["fields"])
    lines += [
        "",
        "/** " + spec["doc"] + " (pre-1.20.5 raw-buffer form; StreamCodec begins at 1.20.5.) */",
        "public record " + name + "(" + sig + ") implements BvPayload {",
        "",
    ]
    lines += spec.get("extras", [])
    lines.append('    public static final ResourceLocation ID = new ResourceLocation(BankVault.MOD_ID, "' + spec["id"] + '");')
    lines.append("")
    for sub, sf in spec.get("nested", {}).items():
        ssig = ", ".join(_jtype(k) + " " + f for f, k in sf)
        lines.append("    public record " + sub + "(" + ssig + ") {")
        lines += _record_io(sub, sf, True)
        lines.append("    }")
        lines.append("")
    lines.append("    @Override")
    lines.append("    public ResourceLocation id() { return ID; }")
    lines.append("")
    lines += _record_io(name, spec["fields"], False)
    lines.append("}")
    for ln in lines:
        cog.outl(ln)


def emit_bvpayload(cog, ver):
    if modern(ver):
        cog.outl("// modern era (>=1.20.5): payloads implement vanilla CustomPacketPayload; this shim is unused")
        return
    for ln in [
        "package com.kishku7.bankvault.net;",
        "",
        "import net.minecraft.network.FriendlyByteBuf;",
        "import net.minecraft.resources.ResourceLocation;",
        "",
        "/** Pre-1.20.5 payload shim: BV's own channel-id + raw-buffer contract (CustomPacketPayload",
        " *  with StreamCodecs only exists from 1.20.5; 1.20.1 predates the interface entirely). */",
        ("public interface BvPayload extends net.minecraft.network.protocol.common.custom.CustomPacketPayload {"
         if compat_core._vt(ver) >= (1, 20, 2) else
         "public interface BvPayload {"),
        "",
        "    ResourceLocation id();",
        "",
        "    void write(FriendlyByteBuf buf);",
        "}",
    ]:
        cog.outl(ln)


def emit_container_extractor(cog, ver):
    """Modern: passthrough of the shared components-era twin. Pre-1.20.5: the proven NBT-era
    implementation (1.2.4 line) kept as a template -- same public surface + Extension hooks."""
    if modern(ver):
        for ln in _twin_shared(os.path.join("inventory", "ContainerExtractor.java")):
            # 26 renamed the copy-stream accessor; 1.20.5-1.21.x use nonEmptyItemsCopy()
            if not compat_core.is26(ver):
                ln = ln.replace(".nonEmptyItemCopyStream().forEach(out::add);",
                                ".nonEmptyItemsCopy().forEach(out::add);")
            cog.outl(ln)
        return
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates", "ContainerExtractor_nbt.java")
    with open(p, "r", encoding="utf-8") as f:
        for ln in f.read().splitlines():
            cog.outl(ln)
