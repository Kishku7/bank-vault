package com.kishku7.bankvault.inventory;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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

    /** Adds one real slot per trinket slot. Returns the number of slots added.
     *  v1.1: slots carry the trinkets-defined empty-slot icon ({@code SlotType.icon()}) and
     *  enforce the per-slot item validators ({@code SlotType.validatorCheck}) plus the slot's
     *  max stack size -- trinket slots can no longer be used as general item storage. */
    public static int addTrinketSlots(BankVaultMenu menu, Player player) {
        TrinketAttachment att = TrinketsApi.getAttachment(player);
        if (att == null) return 0;
        int n = 0;
        TreeMap<String, TrinketInventory> invs = new TreeMap<>(att.getInventories());
        for (TrinketInventory ti : invs.values()) {
            for (int i = 0; i < ti.getContainerSize(); i++) {
                final TrinketInventory inv = ti;
                final int idx = i;
                menu.addTrinketSlotDirect(new Slot(inv, idx, -9999, -9999) {
                    @Override public boolean mayPlace(ItemStack stack) {
                        try {
                            return inv.slotType().validatorCheck(stack, inv.getOrCreateSlotAccess(idx), player);
                        } catch (Throwable t) {
                            return super.mayPlace(stack);     // API drift: fall back to permissive
                        }
                    }
                    @Override public int getMaxStackSize(ItemStack stack) {
                        try {
                            return Math.min(super.getMaxStackSize(stack), inv.slotType().maxStackSize(stack));
                        } catch (Throwable t) {
                            return super.getMaxStackSize(stack);
                        }
                    }
                    @Override public Identifier getNoItemIcon() {
                        try {
                            return inv.slotType().icon();     // trinkets ships per-slot empty icons
                        } catch (Throwable t) {
                            return null;
                        }
                    }
                });
                n++;
            }
        }
        return n;
    }

    /** v1.2: is this stack accepted by ANY of the player's trinket slots? Dynamic detection --
     *  works for every trinkets-backed mod without inventorying item ids. Same guard contract
     *  as the rest of this class: callers check TRINKETS and catch Throwable. */
    public static boolean isTrinket(ItemStack stack, Player player) {
        TrinketAttachment att = TrinketsApi.getAttachment(player);
        if (att == null) return false;
        for (TrinketInventory inv : att.getInventories().values()) {
            if (inv.getContainerSize() <= 0) continue;
            try {
                if (inv.slotType().validatorCheck(stack, inv.getOrCreateSlotAccess(0), player)) return true;
            } catch (Throwable ignored) {
                // API drift on one slot type must not break detection for the others
            }
        }
        return false;
    }
}
