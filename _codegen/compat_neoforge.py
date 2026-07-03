"""compat_neoforge.py -- NeoForge-specific era emitters for Bank Vault.

Known boundaries:
  break-event @26 : net.neoforged.neoforge.event.level.block.BreakBlockEvent
                    pre-26 = classic net.neoforged.neoforge.event.level.BlockEvent.BreakEvent
                    (same getLevel/getState/getPos accessors, both fire pre-removal)
"""
import compat_core


def emit_break_import(cog, ver):
    if compat_core.is26(ver):
        cog.outl("import net.neoforged.neoforge.event.level.block.BreakBlockEvent;")
    else:
        cog.outl("import net.neoforged.neoforge.event.level.BlockEvent;")


def break_event_type(ver):
    return "BreakBlockEvent" if compat_core.is26(ver) else "BlockEvent.BreakEvent"


def emit_break_sig(cog, ver):
    cog.outl("private void blockBroken(" + break_event_type(ver) + " event) {")


def emit_tab_builder(cog, ver):
    if compat_core.is26(ver):
        cog.outl('TABS.register("bankvault", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)')
    else:
        # NeoForge 1.21.x deprecates builder(Row,int); their no-arg builder() is the replacement
        cog.outl('TABS.register("bankvault", () -> CreativeModeTab.builder()')


# ---- client->server send: ClientPacketDistributor (26 + 1.21.8-era) vs PacketDistributor.sendToServer (<=1.21.5) ----
def emit_clientnet(cog, ver):
    modern = compat_core._vt(ver) >= (1, 21, 8)
    dist_import = ("import net.neoforged.neoforge.client.network.ClientPacketDistributor;" if modern
                   else "import net.neoforged.neoforge.network.PacketDistributor;")
    send = ("ClientPacketDistributor.sendToServer(payload);" if modern
            else "PacketDistributor.sendToServer(payload);")
    lines = [
        "package com.kishku7.bankvault.client;",
        "",
        "import net.minecraft.network.protocol.common.custom.CustomPacketPayload;",
        dist_import,
        "",
        "/** Client networking seam: shared client code sends to the server through this one name. */",
        "public final class ClientNet {",
        "    private ClientNet() {}",
        "",
        "    public static void sendToServer(CustomPacketPayload payload) {",
        "        " + send,
        "    }",
        "}",
    ]
    for ln in lines:
        cog.outl(ln)
