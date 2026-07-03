"""compat_fabric.py -- Fabric-specific era emitters for Bank Vault's per-loader sources.

Whole-file passthrough pattern: for a file whose 26 form is canonical, the emitter reads the
PLAIN 26 twin from the repo and outputs it verbatim at 26 (check-sync stays green by
construction) and an era variant below the drift boundary.
"""
import os

import compat_core

_REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def _twin(rel):
    """Read a plain 26-cell twin file, return its text lines (verbatim)."""
    p = os.path.join(_REPO, "Fabric", "26", "src", "main", "java", "com", "kishku7", "bankvault", rel)
    with open(p, "r", encoding="utf-8") as f:
        return f.read().splitlines()


def _out_lines(cog, lines):
    for ln in lines:
        cog.outl(ln)


# ---- TrinketCompat: real integration on 26 (trinkets_updated exists there only); stub below ----
TRINKET_STUB = '''package com.kishku7.bankvault.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Trinkets Updated integration stub -- the mod only exists on the 26 line, so pre-26 builds
 * carry a no-op with the same public surface (callers already guard with BankVault.TRINKETS,
 * which is false when the mod is absent).
 */
public final class TrinketCompat {

    private TrinketCompat() {}

    public static int addTrinketSlots(BankVaultMenu menu, Player player) {
        return 0;
    }

    public static boolean isTrinket(ItemStack stack, Player player) {
        return false;
    }
}'''


def emit_trinket_compat(cog, ver):
    if compat_core.is26(ver):
        _out_lines(cog, _twin(os.path.join("inventory", "TrinketCompat.java")))
    else:
        _out_lines(cog, TRINKET_STUB.splitlines())


# ---- PayloadTypeRegistry era names: 26 serverboundPlay/clientboundPlay vs pre-26 playC2S/playS2C ----
_PAYLOADS = [
    ("clientbound", "VaultSyncPayload"),
    ("serverbound", "WithdrawPayload"),
    ("serverbound", "UpgradePayload"),
    ("serverbound", "DepositPayload"),
    ("serverbound", "GridViewPayload"),
    ("serverbound", "DepositAllPayload"),
    ("clientbound", "SharingStatePayload"),
    ("serverbound", "ShareActionPayload"),
    ("serverbound", "UiStatePayload"),
    ("clientbound", "UiStateSyncPayload"),
]


def emit_payload_registry(cog, ver):
    modern = compat_core.is26(ver)
    for d, n in _PAYLOADS:
        if modern:
            m = "serverboundPlay()" if d == "serverbound" else "clientboundPlay()"
        else:
            m = "playC2S()" if d == "serverbound" else "playS2C()"
        cog.outl("        PayloadTypeRegistry." + m + ".register(" + n + ".TYPE, " + n + ".CODEC);")


# ---- BlockEntityType creation: fabric builder (modern, matches 26 twin) vs vanilla Builder <1.21.2 ----
def emit_be_create(cog, ver):
    if compat_core._vt(ver) >= (1, 21, 2):
        cog.outl("        BANK_VAULT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,")
        cog.outl('                Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "bank_vault"),')
        cog.outl("                FabricBlockEntityTypeBuilder.create(BankVaultBlockEntity::new, ModBlocks.VAULT).build());")
    else:
        cog.outl("        BANK_VAULT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,")
        cog.outl('                Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "bank_vault"),')
        cog.outl("                BlockEntityType.Builder.of(BankVaultBlockEntity::new, ModBlocks.VAULT).build(null));")


# ================= pre-components fabric networking (< 1.20.5) =================

_C2S = ["Withdraw", "Upgrade", "Deposit", "GridView", "DepositAll", "ShareAction", "UiState"]


def emit_fnet_imports(cog, ver):
    if compat_core.has_components(ver):
        cog.outl("import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;")
        cog.outl("import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;")
    else:
        cog.outl("import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;")
        cog.outl("import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;")
        cog.outl("import net.minecraft.network.FriendlyByteBuf;")


def emit_fnet_register(cog, ver):
    if compat_core.has_components(ver):
        for d, n in _PAYLOADS:
            m = "serverboundPlay()" if d == "serverbound" else "clientboundPlay()"
            if not compat_core.is26(ver):
                m = "playC2S()" if d == "serverbound" else "playS2C()"
            cog.outl("        PayloadTypeRegistry." + m + ".register(" + n + ".TYPE, " + n + ".CODEC);")
        for n in _C2S:
            cog.outl("        ServerPlayNetworking.registerGlobalReceiver(" + n + "Payload.TYPE, (payload, context) -> on" + n + "(payload, context.player()));")
    else:
        for n in _C2S:
            cog.outl("        ServerPlayNetworking.registerGlobalReceiver(" + n + "Payload.ID, (server, player, handler, buf, sender) -> {")
            cog.outl("            " + n + "Payload p = " + n + "Payload.decode(buf);")
            cog.outl("            server.execute(() -> on" + n + "(p, player));")
            cog.outl("        });")


def emit_fnet_sendto(cog, ver):
    if compat_core.has_components(ver):
        cog.outl("    private static void sendTo(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {")
        cog.outl("        ServerPlayNetworking.send(player, payload);")
        cog.outl("    }")
    else:
        cog.outl("    private static void sendTo(ServerPlayer player, BvPayload payload) {")
        cog.outl("        FriendlyByteBuf buf = PacketByteBufs.create();")
        cog.outl("        payload.write(buf);")
        cog.outl("        ServerPlayNetworking.send(player, payload.id(), buf);")
        cog.outl("    }")


def emit_client_init(cog, ver):
    if compat_core.has_components(ver):
        for ln in _twin(os.path.join("client", "BankVaultClient.java")):
            cog.outl(ln)
        return
    for ln in """package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.net.SharingStatePayload;
import com.kishku7.bankvault.net.UiStateSyncPayload;
import com.kishku7.bankvault.net.VaultSyncPayload;
import com.kishku7.bankvault.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

/** Pre-1.20.5 client bootstrap: raw channel receivers decode on the netty thread, then hop to
 *  the client thread (payload-object receivers only exist from 1.20.5). */
public class BankVaultClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BankVault.LOGGER.info("[Bank Vault] client initializing");

        MenuScreens.register(ModMenus.BANK_VAULT, BankVaultScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(VaultSyncPayload.ID, (client, handler, buf, sender) -> {
            VaultSyncPayload payload = VaultSyncPayload.decode(buf);
            client.execute(() -> {
                Minecraft mc = client;
                if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateData(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UiStateSyncPayload.ID, (client, handler, buf, sender) -> {
            UiStateSyncPayload payload = UiStateSyncPayload.decode(buf);
            client.execute(() -> ClientUiState.set(payload.lastTab(), payload.sorts(),
                    payload.showSections(), payload.pins()));
        });

        ClientPlayNetworking.registerGlobalReceiver(SharingStatePayload.ID, (client, handler, buf, sender) -> {
            SharingStatePayload payload = SharingStatePayload.decode(buf);
            client.execute(() -> {
                Minecraft mc = client;
                if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateSharing(payload);
            });
        });
    }
}""".split("\n"):
        cog.outl(ln)


def emit_clientnet_fabric(cog, ver):
    if compat_core.has_components(ver):
        for ln in _twin(os.path.join("client", "ClientNet.java")):
            cog.outl(ln)
        return
    for ln in """package com.kishku7.bankvault.client;

import com.kishku7.bankvault.net.BvPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/** Client networking seam: shared client code sends to the server through this one name. */
public final class ClientNet {
    private ClientNet() {}

    public static void sendToServer(BvPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ClientPlayNetworking.send(payload.id(), buf);
    }
}""".split("\n"):
        cog.outl(ln)
