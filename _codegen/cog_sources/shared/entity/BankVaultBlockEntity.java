package com.kishku7.bankvault.entity;

import com.kishku7.bankvault.block.ModProperties;
import com.kishku7.bankvault.block.VaultPart;
import com.kishku7.bankvault.registry.ModBlockEntities;
import com.kishku7.bankvault.vault.Bank;
import com.kishku7.bankvault.vault.BankManager;
import com.kishku7.bankvault.vault.VaultCapacity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/* [[[cog
import compat_core
compat_core.emit_be_io_imports(cog, ver)
]]] */
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
/* [[[end]]] */

import java.util.UUID;

/**
 * BlockEntity on every vault block. When formed it carries the owner's UUID (stamped by the form
 * manager) so any block can act as a hopper sink into that owner's bank. Deposit-only via hopper.
 */
public class BankVaultBlockEntity extends BlockEntity implements WorldlyContainer {

    private static final int[] SINK = {0};

    private UUID builderUUID;

    public BankVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANK_VAULT, pos, state);
    }

    public UUID getBuilderUUID() { return builderUUID; }
    public void setBuilderUUID(UUID uuid) { this.builderUUID = uuid; setChanged(); }

    private boolean formed() {
        return getBlockState().getValue(ModProperties.PART) != VaultPart.NONE;
    }

    private Bank bank() {
        return builderUUID == null ? null : BankManager.lookup(builderUUID);
    }

    // -- NBT -------------------------------------------------------------------

    /* [[[cog
    import compat_core
    compat_core.emit_be_io(cog, ver)
    ]]] */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (builderUUID != null) output.putString("builder", builderUUID.toString());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String b = input.getStringOr("builder", "");
        this.builderUUID = b.isEmpty() ? null : UUID.fromString(b);
    }
    /* [[[end]]] */

    // -- Container (hopper sink) ------------------------------------------------

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return true; }
    @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() { }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Level l = getLevel();
        if (l == null || l.isClientSide() || !formed()) return;
        Bank b = bank();
        if (b == null) return;
        BankManager.depositStack(b, stack, l.registryAccess());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!formed()) return false;
        Bank b = bank();
        return b != null && b.totalItems() < VaultCapacity.capacityFor(b.upgradeCount);
    }

    @Override public int[] getSlotsForFace(Direction side) { return SINK; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) { return false; }
}
