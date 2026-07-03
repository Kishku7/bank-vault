package com.kishku7.bankvault.inventory;

import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModMenus;
import com.kishku7.bankvault.vault.Bank;
import com.kishku7.bankvault.vault.BankManager;
import com.kishku7.bankvault.vault.StackStore;
import com.kishku7.bankvault.vault.UserSettings;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
/* [[[cog
import compat_core
compat_core.emit_click_import(cog, ver)
]]] */
import net.minecraft.world.inventory.ContainerInput;
/* [[[end]]] */
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import com.kishku7.bankvault.vault.VaultCapacity;

import java.util.List;

/**
 * Container menu for the vault. All interactive elements are REAL slots so vanilla mechanics apply:
 *
 *   0..26   player main inventory          27..35  hotbar
 *   36      unload (containers only)       37      grab-me (output only)
 *   38      upgrade (chests only)
 *   39..42  armor (head/chest/legs/feet)   43      offhand
 *   44..52  3x3 crafting grid              53      crafting result
 *   54      pin (drop-to-pin deposit; see clicked())
 *   55..    vault-grid view slots (invisible click targets; see clicked())
 *
 * The vault grid contents are synced for display by VaultSyncPayload; clicking a grid cell routes a
 * vanilla container click to the matching view slot, and clicked() implements infinite-source
 * pickup / deposit so the cursor behaves exactly like any vanilla container.
 */
public class BankVaultMenu extends AbstractContainerMenu {

    public static final int PLAYER_SLOTS = 36;
    public static final int UNLOAD_SLOT = 36, GRAB_SLOT = 37, UPGRADE_SLOT = 38;
    public static final int ARMOR_FIRST = 39;                 // 39..42 head,chest,legs,feet
    public static final int OFFHAND_SLOT = 43;
    public static final int CRAFT_FIRST = 44;                 // 44..52 full 3x3 grid
    public static final int CRAFT_RESULT = 53;
    public static final int PIN_SLOT = 54;                    // drop-to-pin (real slot, invisible)

    // Vault grid backed by real Slots. The screen may not draw more cells than these caps so every
    // visible cell maps to one real view slot. Rows are generous: the grid fills vertical space.
    public static final int VIEW_COLS = 12, VIEW_ROWS = 36, VIEW_SIZE = VIEW_COLS * VIEW_ROWS;
    public static final int VIEW_FIRST = PIN_SLOT + 1;        // 55
    public static final int TRINKET_FIRST = VIEW_FIRST + VIEW_SIZE;   // trinket slots (if any) come last

    private static final EquipmentSlot[] ARMOR_ORDER =
            { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final int[] ARMOR_INV_INDEX = { 39, 38, 37, 36 };
    private static final Identifier[] ARMOR_ICONS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS };

    private final Player owner;
    // setChanged -> slotsChanged wiring (vanilla GrindstoneMenu idiom). A plain SimpleContainer
    // never notifies the menu, so unload/upgrade processing would never run on item placement.
    private final SimpleContainer io = new SimpleContainer(2) {   // 0 = unload, 1 = grab-me
        @Override public void setChanged() { super.setChanged(); BankVaultMenu.this.slotsChanged(this); }
    };
    private final SimpleContainer upg = new SimpleContainer(1) {  // chest drop -> upgrades
        @Override public void setChanged() { super.setChanged(); BankVaultMenu.this.slotsChanged(this); }
    };
    private final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final SimpleContainer pinBacking = new SimpleContainer(1);          // stays empty; pin slot is logical
    private final SimpleContainer viewBacking = new SimpleContainer(VIEW_SIZE); // stays empty; view slots are logical
    private final String[] viewKeys = new String[VIEW_SIZE]; // server-side: cell -> bank key
    private boolean processing = false;
    /** Number of trinket slots appended after the view block (0 when trinkets_updated is absent). */
    public int trinketSlotCount = 0;
    /** Server-side: the tab the client grid currently shows (sent with every GridViewPayload). */
    private String currentTab = "";

    // vanilla menu ctor pattern: addSlot/craft-container wiring self-references are benign
    @SuppressWarnings("this-escape")
    public BankVaultMenu(int containerId, Inventory inv) {
        super(ModMenus.BANK_VAULT, containerId);
        this.owner = inv.player;
        // 0..26 main, 27..35 hotbar (positions are set by the screen each init())
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, 9 + r * 9 + c, 0, 0));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, 0, 0));
        addSlot(new Slot(io, 0, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return ContainerExtractor.isContainerType(stack); }
        });
        addSlot(new Slot(io, 1, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }   // grab-me is output-only
        });
        addSlot(new Slot(upg, 0, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() == Items.CHEST; }
        });
        // 39..42 armor + 43 offhand -- bound to the player's real equipment
        for (int i = 0; i < 4; i++)
            addSlot(new ArmorSlot(inv, inv.player, ARMOR_ORDER[i], ARMOR_INV_INDEX[i], 0, 0, ARMOR_ICONS[i]));
        addSlot(new Slot(inv, Inventory.SLOT_OFFHAND, 0, 0) {
            @Override public Identifier getNoItemIcon() { return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD; }
        });
        // 44..52 full 3x3 crafting grid + 53 result (the vault doubles as a crafting table)
        for (int i = 0; i < 9; i++) addSlot(new Slot(craftSlots, i, 0, 0));
        addSlot(new ResultSlot(inv.player, craftSlots, resultSlots, 0, 0, 0));
        // 54 pin: a REAL slot so drop-to-pin rides the vanilla click transaction (see clicked()).
        // Invisible and inactive -- the screen routes Pin-box clicks and drag-releases here.
        addSlot(new Slot(pinBacking, 0, -9999, -9999) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return false; }
        });
        // 49.. view slots: real Slots so vanilla container clicks are valid, but invisible
        // (off-screen + inactive) so they never render or get hovered.
        for (int i = 0; i < VIEW_SIZE; i++) {
            addSlot(new Slot(viewBacking, i, -9999, -9999) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean isActive() { return false; }
            });
        }
        // Non-standard character slots (Trinkets Updated / Travelers Backpack via its trinket slot).
        // Appended LAST so every fixed slot index above stays valid. Soft dependency: guarded by
        // isModLoaded + Throwable catch -- absence or API drift can never error the vault.
        if (com.kishku7.bankvault.BankVault.TRINKETS) {
            try {
                this.trinketSlotCount = TrinketCompat.addTrinketSlots(this, inv.player);
            } catch (Throwable t) {
                this.trinketSlotCount = 0;
            }
        }
    }

    /** Used by TrinketCompat: trinket inventories are vanilla Containers, so real Slots work.
     *  The compat class builds the Slot itself (icon + validator overrides need trinkets types). */
    void addTrinketSlotDirect(Slot slot) {
        addSlot(slot);
    }

    public Container ioContainer() { return io; }

    /** Server-side: record which bank key each visible grid cell currently shows (cell order). */
    public void setViewKeys(List<String> keys) {
        for (int i = 0; i < viewKeys.length; i++) {
            String k = (keys != null && i < keys.size()) ? keys.get(i) : null;
            viewKeys[i] = (k == null || k.isEmpty()) ? null : k;
        }
    }

    /** Server-side: the tab whose contents the grid shows -- the pin target tab (drop-to-pin). */
    public void setCurrentTab(String tab) { this.currentTab = tab == null ? "" : tab; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    /* [[[cog
import compat_core
compat_core.emit_clicked_sig(cog, ver)
]]] */
public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
/* [[[end]]] */
        if (slotId == PIN_SLOT) {
            handlePinClick(player);
            return;
        }
        if (slotId >= VIEW_FIRST && slotId < VIEW_FIRST + VIEW_SIZE) {
            handleViewClick(slotId - VIEW_FIRST, button, clickType, player);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Infinite-source pickup / deposit for a vault grid cell. Runs the real mutation on the server
     * inside the vanilla click transaction, then forces a full resync so the client cursor matches.
     * The client side is a no-op -- the resync delivers the result.
     */
    /* [[[cog
import compat_core
compat_core.emit_viewclick_sig(cog, ver)
]]] */
private void handleViewClick(int cell, int button, ContainerInput clickType, Player player) {
/* [[[end]]] */
        if (!(player instanceof ServerPlayer sp)) return;            // client: server resync delivers the result
        if (cell < 0 || cell >= viewKeys.length) return;
        Bank bank = BankManager.lookup(sp.getUUID());
        if (bank == null) return;
        int level = bank.levelOf(sp.getUUID());
        ItemStack carried = getCarried();

        /* [[[cog
import compat_core
compat_core.emit_pickup_check(cog, ver)
]]] */
if (clickType == ContainerInput.PICKUP && !carried.isEmpty()) {
/* [[[end]]] */
            // Cursor is holding items: a grid click deposits them (right-click deposits one) --
            // the vanilla "click a container while holding = put it in" model. Any cell works.
            if (level < BankManager.DEPOSIT) return;
            int amount = (button == 1) ? 1 : carried.getCount();
            long acc = BankManager.depositStack(bank, carried.copyWithCount(amount), sp.level().registryAccess());
            if (acc > 0) carried.shrink((int) acc);
            else sp.sendSystemMessage(Component.literal("§cVault is full."));
        } else {
            String key = viewKeys[cell];
            if (key == null || level < BankManager.MEMBER) return;
            ItemStack proto = protoFor(bank, key, sp);
            if (proto.isEmpty()) return;
            int max = proto.getMaxStackSize();
            switch (clickType) {
                case PICKUP -> {                                      // empty cursor: withdraw to cursor
                    int want = (button == 1) ? 1 : max;               // right-click pulls one, left-click a stack
                    long take = BankManager.withdrawKey(bank, key, want);
                    if (take > 0) setCarried(proto.copyWithCount((int) take));
                }
                case QUICK_MOVE -> {                                  // shift-click: one stack to inventory.
                    // Fill order (Dave, v1.1): main rows 1->3 first, hotbar LAST. Menu slots 0..26
                    // are inv rows top->bottom, 27..35 hotbar, so an ascending moveItemStackTo walks
                    // exactly that order -- and can never touch trinket/backpack slots (range-capped).
                    long take = BankManager.withdrawKey(bank, key, max);
                    if (take > 0) {
                        ItemStack g = proto.copyWithCount((int) take);
                        moveItemStackTo(g, 0, PLAYER_SLOTS, false);
                        if (!g.isEmpty()) {
                            // No room: remainder goes straight back to the vault -- never dropped.
                            BankManager.depositStack(bank, g, sp.level().registryAccess());
                        }
                    }
                }
                default -> { return; }                                // ignore THROW/SWAP/CLONE/PICKUP_ALL for now
            }
        }
        ModNetworking.sendSync(sp, bank);
        broadcastChanges();
        broadcastFullState();   // push the authoritative cursor + slots so the result sticks client-side
    }

    /**
     * v1.2 beta.2 drop-to-pin: the Pin box is backed by a REAL slot so the gesture arrives as a
     * vanilla container click. The carried stack is DEPOSITED into the vault and its item id is
     * pin-toggled for the player's current tab, all inside the click transaction (no stateId
     * desync). A full vault denies: the stack stays on the cursor and nothing toggles.
     */
    private void handlePinClick(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;   // client: server resync delivers the result
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return;
        Bank bank = BankManager.lookup(sp.getUUID());
        if (bank == null || bank.levelOf(sp.getUUID()) < BankManager.DEPOSIT) return;
        String id = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        long acc = BankManager.depositStack(bank, carried, sp.level().registryAccess());
        if (acc > 0) {
            carried.shrink((int) acc);
            if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
            if (currentTab != null && !currentTab.isEmpty()) UserSettings.togglePin(sp, currentTab, id);
            ModNetworking.sendUiState(sp);   // pins changed -- must land before the vault sync rebuild
        } else {
            sp.sendSystemMessage(Component.literal("§cVault is full."));
        }
        ModNetworking.sendSync(sp, bank);
        broadcastChanges();
        broadcastFullState();   // authoritative cursor + slots so the result sticks client-side
    }

    /** Rebuild the display prototype (count 1) for a stored key: plain id or "id#hash" special. */
    private ItemStack protoFor(Bank bank, String key, ServerPlayer sp) {
        RegistryAccess ra = sp.level().registryAccess();
        if (key.contains("#")) {
            Bank.Special special = bank.special.get(key);
            return special == null ? ItemStack.EMPTY : StackStore.decode(special.stack, ra);
        }
        Identifier id = StackStore.parseId(key);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (player instanceof ServerPlayer sp) {
            Bank bank = BankManager.lookup(sp.getUUID());
            if (index < PLAYER_SLOTS) {
                if (ContainerExtractor.isContainer(stack) && io.getItem(0).isEmpty()) {
                    // route the container to the unload slot -- extraction fires via slotsChanged
                    if (moveItemStackTo(stack, UNLOAD_SLOT, UNLOAD_SLOT + 1, false)) slot.setChanged();
                } else if (bank != null && bank.levelOf(sp.getUUID()) >= BankManager.DEPOSIT) {
                    long acc = BankManager.depositStack(bank, stack, sp.level().registryAccess());
                    if (acc > 0) { stack.shrink((int) acc); slot.setChanged(); ModNetworking.sendSync(sp, bank); }
                    else sp.sendSystemMessage(Component.literal("§cVault is full."));
                }
            } else if (index == CRAFT_RESULT) {
                // return the crafted stack (not EMPTY) so vanilla's QUICK_MOVE loop keeps crafting
                ItemStack taken = stack.copy();
                if (moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
                    slot.onTake(player, taken);
                    slot.setChanged();
                    return taken;
                }
            } else if (index < VIEW_FIRST) {
                // unload/grab/upgrade/armor/offhand/craft -> back to the player inventory
                if (moveItemStackTo(stack, 0, PLAYER_SLOTS, false)) slot.setChanged();
            } else if (index >= TRINKET_FIRST) {
                if (moveItemStackTo(stack, 0, PLAYER_SLOTS, false)) slot.setChanged();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == craftSlots && owner instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
            CraftingMenu.slotChangedCraftingGrid(this, sl, owner, craftSlots, resultSlots, null);
        }
        if (processing || !(owner instanceof ServerPlayer)) return;
        if (container == io) processUnload();
        else if (container == upg) processUpgradeInput();
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
        if (contents == null) {
            // structurally a container but nothing inside: pass it straight through to grab-me
            processing = true;
            try {
                io.setItem(0, ItemStack.EMPTY);
                ItemStack prev = io.getItem(1);
                if (!prev.isEmpty() && !sp.getInventory().add(prev)) sp.drop(prev, false);
                io.setItem(1, container);
            } finally {
                processing = false;
            }
            broadcastChanges();
            return;
        }

        long total = 0;
        for (ItemStack s : contents) total += s.getCount();
        long room = VaultCapacity.capacityFor(bank.upgradeCount) - bank.totalItems();
        if (room < total) {
            sp.sendSystemMessage(Component.literal("§cVault is full — cannot unload ("
                    + total + " items, room for " + Math.max(0, room) + ")."));
            return; // leave the container untouched in the unload slot
        }

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

    /** A chest dropped into the upgrade slot becomes upgrades (Master+ only); any overflow is returned. */
    private void processUpgradeInput() {
        ItemStack in = upg.getItem(0);
        if (in.isEmpty()) return;
        if (!(owner instanceof ServerPlayer sp)) return;
        Bank bank = BankManager.lookup(sp.getUUID());
        processing = true;
        try {
            boolean allowed = bank != null && bank.levelOf(sp.getUUID()) >= BankManager.MASTER;
            if (!allowed) {
                if (bank != null) sp.sendSystemMessage(Component.literal("§cOnly Bank Masters or the Owner can change upgrades."));
                ItemStack back = in.copy();
                upg.setItem(0, ItemStack.EMPTY);
                if (!sp.getInventory().add(back)) sp.drop(back, false);
            } else {
                int room = Math.max(0, VaultCapacity.MAX_UPGRADES - bank.upgradeCount);
                int take = Math.min(room, in.getCount());
                if (take > 0) { bank.upgradeCount += take; BankManager.save(bank); }
                int remainder = in.getCount() - take;
                upg.setItem(0, ItemStack.EMPTY);
                if (remainder > 0) {
                    ItemStack back = new ItemStack(Items.CHEST, remainder);
                    if (!sp.getInventory().add(back)) sp.drop(back, false);
                }
                ModNetworking.sendSync(sp, bank);
            }
        } finally {
            processing = false;
        }
        processUnload();   // capacity may have grown -- retry a parked unload container
        broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultSlots.clearContent();
        if (!player.level().isClientSide()) {
            for (int i = 0; i < io.getContainerSize(); i++) {
                ItemStack s = io.removeItemNoUpdate(i);
                if (!s.isEmpty() && !player.getInventory().add(s)) player.drop(s, false);
            }
            ItemStack u = upg.removeItemNoUpdate(0);
            if (!u.isEmpty() && !player.getInventory().add(u)) player.drop(u, false);
            for (int i = 0; i < craftSlots.getContainerSize(); i++) {
                ItemStack s = craftSlots.removeItemNoUpdate(i);
                if (!s.isEmpty() && !player.getInventory().add(s)) player.drop(s, false);
            }
        }
    }
}
