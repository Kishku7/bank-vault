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
