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
    # 26.x deprecates the static CreativeModeTab.builder(Row,int); its public Builder constructor is
    # the non-deprecated replacement there. Pre-26 NeoForge keeps the no-arg builder().
    if compat_core.is26(ver):
        cog.outl('        TABS.register("bankvault", () -> new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 0)')
    else:
        cog.outl('        TABS.register("bankvault", () -> CreativeModeTab.builder()')

def emit_clientnet(cog, ver):
    modern = compat_core._vt(ver) >= (1, 21, 8)
    dist_import = ("import net.neoforged.neoforge.client.network.ClientPacketDistributor;" if modern
                   else "import net.neoforged.neoforge.network.PacketDistributor;")
    if compat_core.has_components(ver):
        send = ("ClientPacketDistributor.sendToServer(payload);" if modern
                else "PacketDistributor.sendToServer(payload);")
    else:
        send = "PacketDistributor.SERVER.noArg().send(payload);   // 20.4 form"
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


# ---- BlockEntityType creation: direct ctor @1.21.2+ (Builder removed); vanilla Builder before ----
def emit_be_create(cog, ver):
    if compat_core._vt(ver) >= (1, 21, 2):
        cog.outl("            BANK_VAULT = new BlockEntityType<>(BankVaultBlockEntity::new, Set.of(ModBlocks.VAULT));")
    else:
        cog.outl("            BANK_VAULT = BlockEntityType.Builder.of(BankVaultBlockEntity::new, ModBlocks.VAULT).build(null);")


# ================= NeoForge 20.4 era (MC 1.20.4; pre-components networking) =================

def neo204(ver):
    return not compat_core.has_components(ver)


def emit_neoentry_ctor(cog, ver):
    if neo204(ver):
        cog.outl("    public BankVaultNeoForge(IEventBus bus) {   // 20.4: only the mod bus is injectable")
    else:
        cog.outl("    public BankVaultNeoForge(ModContainer mod, IEventBus bus, Dist dist) {")


def emit_neoentry_clientgate(cog, ver):
    if neo204(ver):
        cog.outl("        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {")
    else:
        cog.outl("        if (dist.isClient()) {")


def emit_neoentry_payloads_import(cog, ver):
    if neo204(ver):
        cog.outl("import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;")
    else:
        cog.outl("import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;")


def emit_neoentry_payloads_method(cog, ver):
    if neo204(ver):
        cog.outl("    private void registerPayloads(RegisterPayloadHandlerEvent event) {")
    else:
        cog.outl("    private void registerPayloads(RegisterPayloadHandlersEvent event) {")


_NEO_C2S = ["Withdraw", "Upgrade", "Deposit", "GridView", "DepositAll", "ShareAction", "UiState"]
_NEO_S2C = [("VaultSync", "vaultSyncSink"), ("UiStateSync", "uiStateSink"), ("SharingState", "sharingSink")]


def emit_neonet_imports(cog, ver):
    if neo204(ver):
        cog.outl("import net.neoforged.neoforge.network.PacketDistributor;")
        cog.outl("import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;")
        cog.outl("import net.neoforged.neoforge.network.registration.IPayloadRegistrar;")
    else:
        cog.outl("import net.neoforged.neoforge.network.PacketDistributor;")
        cog.outl("import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;")
        cog.outl("import net.neoforged.neoforge.network.registration.PayloadRegistrar;")


def emit_neonet_register(cog, ver):
    if neo204(ver):
        lines = [
            "    public static void registerNeoForge(RegisterPayloadHandlerEvent event) {",
            '        IPayloadRegistrar r = event.registrar("bankvault").versioned("1.2.0").optional();',
            "        // S2C -- handlers run client-side only; sinks keep client classes off the server.",
        ]
        for n, sink in _NEO_S2C:
            lines.append("        r.play(" + n + "Payload.ID, " + n + "Payload::decode, (p, ctx) -> ctx.workHandler().execute(() -> " + sink + ".accept(p)));")
        lines.append("        // C2S")
        for n in _NEO_C2S:
            lines.append("        r.play(" + n + "Payload.ID, " + n + "Payload::decode, (p, ctx) -> ctx.workHandler().execute(() -> ctx.player().ifPresent(pl -> { if (pl instanceof ServerPlayer sp) on" + n + "(p, sp); })));")
        lines.append("    }")
    else:
        lines = _NEONET_MODERN_REGISTER.split("\n")
    for ln in lines:
        cog.outl(ln)


def emit_neonet_sendto(cog, ver):
    if neo204(ver):
        cog.outl("    private static void sendToPlayerSeam(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {")
        cog.outl("        PacketDistributor.PLAYER.with(player).send(p);")
        cog.outl("    }")
    else:
        cog.outl("    private static void sendToPlayerSeam(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {")
        cog.outl("        PacketDistributor.sendToPlayer(player, p);")
        cog.outl("    }")


_NEONET_MODERN_REGISTER = '    public static void registerNeoForge(RegisterPayloadHandlersEvent event) {\n        PayloadRegistrar r = event.registrar("bankvault").versioned("1.2.0");\n        // S2C — handlers run client-side only; sinks keep client classes out of the server.\n        r.playToClient(VaultSyncPayload.TYPE, VaultSyncPayload.CODEC, (p, ctx) -> vaultSyncSink.accept(p));\n        r.playToClient(UiStateSyncPayload.TYPE, UiStateSyncPayload.CODEC, (p, ctx) -> uiStateSink.accept(p));\n        r.playToClient(SharingStatePayload.TYPE, SharingStatePayload.CODEC, (p, ctx) -> sharingSink.accept(p));\n        // C2S\n        r.playToServer(WithdrawPayload.TYPE, WithdrawPayload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) onWithdraw(p, sp); });\n        r.playToServer(UpgradePayload.TYPE, UpgradePayload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) onUpgrade(p, sp); });\n        r.playToServer(DepositPayload.TYPE, DepositPayload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) onDeposit(p, sp); });\n        r.playToServer(GridViewPayload.TYPE, GridViewPayload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) onGridView(p, sp); });\n        r.playToServer(DepositAllPayload.TYPE, DepositAllPayload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) onDepositAll(p, sp); });\n        r.playToServer(ShareActionPayload.TYPE, ShareActionPayload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) onShareAction(p, sp); });\n        r.playToServer(UiStatePayload.TYPE, UiStatePayload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) onUiState(p, sp); });\n    }'
