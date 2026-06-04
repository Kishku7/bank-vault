package com.kishku7.bankvault.inventory;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Player;

import java.util.TreeMap;

/**
 * Soft integration with Trinkets Updated (mod id {@code trinkets_updated}).
 *
 * <p>This class is ONLY loaded when the mod is present -- callers must guard with
 * {@code FabricLoader.getInstance().isModLoaded("trinkets_updated")} AND catch Throwable, so a
 * missing or API-shifted trinkets jar can never crash the vault. The trinkets jar is a
 * compileOnly dependency; nothing from it ships in or is required by bank-vault.
 *
 * <p>Each {@link TrinketInventory} implements vanilla {@link net.minecraft.world.Container}, so
 * trinket slots are plain real Slots and all vanilla cursor mechanics apply. Inventories are
 * iterated in sorted-key order so the client and server menus build identical slot lists.
 */
public final class TrinketCompat {

    private TrinketCompat() {}

    /** Adds one real slot per trinket slot. Returns the number of slots added. */
    public static int addTrinketSlots(BankVaultMenu menu, Player player) {
        TrinketAttachment att = TrinketsApi.getAttachment(player);
        if (att == null) return 0;
        int n = 0;
        TreeMap<String, TrinketInventory> invs = new TreeMap<>(att.getInventories());
        for (TrinketInventory ti : invs.values()) {
            for (int i = 0; i < ti.getContainerSize(); i++) {
                menu.addTrinketSlot(ti, i);
                n++;
            }
        }
        return n;
    }
}
