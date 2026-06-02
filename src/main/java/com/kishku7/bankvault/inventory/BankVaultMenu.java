package com.kishku7.bankvault.inventory;

import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModMenus;
import com.kishku7.bankvault.vault.Bank;
import com.kishku7.bankvault.vault.BankManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Container menu for the vault: real player-inventory slots (drag/drop) + an unload slot and a
 * "grab-me" (return) slot. The virtual vault grid is NOT slots — it is custom-rendered + withdrawn
 * by packet. Shift-clicking an inventory item deposits it (or, if it is a container, routes it to
 * the unload slot, which auto-extracts the contents into the vault and parks the emptied container
 * in the grab-me slot).
 */
public class BankVaultMenu extends AbstractContainerMenu {

    // Layout (relative to the GUI top-left; the screen offsets by leftPos/topPos to match).
    public static final int IMG_W = 600, IMG_H = 540;
    public static final int INV_X = (IMG_W - 9 * 18) / 2;
    public static final int INV_Y = IMG_H - 12 - (3 * 18 + 6 + 18);
    public static final int UNLOAD_X = INV_X + 9 * 18 + 18, UNLOAD_Y = INV_Y + 18;
    public static final int GRAB_X = UNLOAD_X + 24, GRAB_Y = INV_Y + 18;
    public static final int PLAYER_SLOTS = 36;
    public static final int UNLOAD_SLOT = 36, GRAB_SLOT = 37;

    private final Player owner;
    private final SimpleContainer io = new SimpleContainer(2); // 0 = unload, 1 = grab-me
    private boolean processing = false;

    public BankVaultMenu(int containerId, Inventory inv) {
        super(ModMenus.BANK_VAULT, containerId);
        this.owner = inv.player;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18 + 1, INV_Y + r * 18 + 1));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, INV_X + c * 18 + 1, INV_Y + 3 * 18 + 6 + 1));
        addSlot(new Slot(io, 0, UNLOAD_X + 1, UNLOAD_Y + 1));
        addSlot(new Slot(io, 1, GRAB_X + 1, GRAB_Y + 1) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }   // grab-me is output-only
        });
    }

    public Container ioContainer() { return io; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (player instanceof ServerPlayer sp) {
            Bank bank = BankManager.lookup(sp.getUUID());
            if (index < PLAYER_SLOTS) {
                if (ContainerExtractor.isContainer(stack) && io.getItem(0).isEmpty()) {
                    // route the container to the unload slot — extraction fires via slotsChanged
                    if (moveItemStackTo(stack, UNLOAD_SLOT, UNLOAD_SLOT + 1, false)) slot.setChanged();
                } else if (bank != null && bank.levelOf(sp.getUUID()) >= BankManager.DEPOSIT) {
                    long acc = BankManager.depositStack(bank, stack, sp.level().registryAccess());
                    if (acc > 0) { stack.shrink((int) acc); slot.setChanged(); ModNetworking.sendSync(sp, bank); }
                }
            } else {
                if (moveItemStackTo(stack, 0, PLAYER_SLOTS, false)) slot.setChanged();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == io && !processing && owner instanceof ServerPlayer) processUnload();
    }

    /** Empty the unload slot's container into the vault and park the emptied shell in grab-me. */
    private void processUnload() {
        ItemStack container = io.getItem(0);
        if (container.isEmpty()) return;
        if (!(owner instanceof ServerPlayer sp)) return;
        Bank bank = BankManager.lookup(sp.getUUID());
        if (bank == null || bank.levelOf(sp.getUUID()) < BankManager.DEPOSIT) return;

        List<ItemStack> contents;
        try {
            contents = ContainerExtractor.extractAll(container);
        } catch (Exception ex) {
            return; // leave the container as-is on any failure (per design)
        }
        if (contents == null) return; // not an (extractable) container — leave it in the slot

        processing = true;
        try {
            for (ItemStack s : contents) {
                if (s.isEmpty()) continue;
                BankManager.depositStack(bank, s, sp.level().registryAccess());
            }
            ItemStack empty = ContainerExtractor.emptied(container);
            io.setItem(0, ItemStack.EMPTY);
            // bump anything already parked in grab-me back to the player
            ItemStack prev = io.getItem(1);
            if (!prev.isEmpty() && !sp.getInventory().add(prev)) sp.drop(prev, false);
            io.setItem(1, empty);
        } finally {
            processing = false;
        }
        ModNetworking.sendSync(sp, bank);
        broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            for (int i = 0; i < io.getContainerSize(); i++) {
                ItemStack s = io.removeItemNoUpdate(i);
                if (!s.isEmpty() && !player.getInventory().add(s)) player.drop(s, false);
            }
        }
    }
}
