package com.kishku7.bankvault.inventory;

import com.kishku7.bankvault.inventory.BankVaultMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * NeoForge stub: Trinkets Updated is a fabric-only mod, so trinket slots and dynamic trinket
 * detection are permanently inert on this loader (BankVault.TRINKETS is false; these methods
 * are unreachable in practice but keep the common call sites compiling unchanged).
 */
public final class TrinketCompat {

    private TrinketCompat() {}

    public static int addTrinketSlots(BankVaultMenu menu, Player player) {
        return 0;
    }

    public static boolean isTrinket(ItemStack stack, Player player) {
        return false;
    }
}
