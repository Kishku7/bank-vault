package com.kishku7.bankvault.vault;

import com.kishku7.bankvault.block.BankVaultBlock;
import com.kishku7.bankvault.block.ModProperties;
import com.kishku7.bankvault.block.VaultPart;
import com.kishku7.bankvault.entity.BankVaultBlockEntity;
import com.kishku7.bankvault.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies the formed look at runtime. Nine identical vault blocks stay plain steel until a 3x3
 * completes, then each block is stamped with its {@link VaultPart} (rotated per position so the
 * frame traces the border) + a shared facing + the owner's UUID.
 */
public final class MultiblockManager {

    private MultiblockManager() {}

    public static boolean isVaultBlock(BlockState s) {
        return s.is(ModBlocks.VAULT);
    }

    public static void onPlaced(Level level, BlockPos pos, ServerPlayer placer) {
        if (level.isClientSide()) return;
        for (BlockPos c : around(pos, 1)) {
            Direction.Axis axis = MultiblockValidator.matchedAxis(level, c);
            if (axis != null) form(level, c, axis, placer);
        }
    }

    private static void form(Level level, BlockPos center, Direction.Axis axis, ServerPlayer placer) {
        if (level.getBlockState(center).getValue(ModProperties.PART) == VaultPart.CENTER) return; // already formed

        // Door faces out toward the side the player is standing on (not their look direction).
        Direction facing;
        if (axis == Direction.Axis.X) {
            facing = placer.getZ() < center.getZ() + 0.5 ? Direction.NORTH : Direction.SOUTH;
        } else {
            facing = placer.getX() < center.getX() + 0.5 ? Direction.WEST : Direction.EAST;
        }
        UUID builder = placer.getUUID();
        BankManager.getOrCreate(placer);

        for (int dy = -1; dy <= 1; dy++) {
            for (int dh = -1; dh <= 1; dh++) {
                BlockPos p = (axis == Direction.Axis.X) ? center.offset(dh, dy, 0) : center.offset(0, dy, dh);
                BlockState s = level.getBlockState(p);
                if (!s.is(ModBlocks.VAULT)) continue;
                VaultPart part = shapeFor(axis, facing, dh, dy);
                level.setBlock(p, s.setValue(ModProperties.PART, part).setValue(BankVaultBlock.FACING, facing), 3);
                if (level.getBlockEntity(p) instanceof BankVaultBlockEntity be) be.setBuilderUUID(builder);
            }
        }
    }

    /** Picks the rotated part for a position, using viewer handedness so corners/edges line up. */
    private static VaultPart shapeFor(Direction.Axis axis, Direction facing, int dh, int dy) {
        if (dh == 0 && dy == 0) return VaultPart.CENTER;
        // viewer's right-hand sign for the in-plane horizontal offset dh
        int rightSign = (axis == Direction.Axis.X)
                ? (facing == Direction.SOUTH ? dh : -dh)
                : (facing == Direction.WEST ? dh : -dh);
        if (dh != 0 && dy != 0) { // corner
            if (dy > 0) return rightSign < 0 ? VaultPart.CORNER0 : VaultPart.CORNER90;
            return rightSign > 0 ? VaultPart.CORNER180 : VaultPart.CORNER270;
        }
        // edge
        if (dy > 0) return VaultPart.EDGE0;
        if (dy < 0) return VaultPart.EDGE180;
        return rightSign > 0 ? VaultPart.EDGE90 : VaultPart.EDGE270;
    }

    public static void onRemoved(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        for (BlockPos p : around(pos, 2)) {
            BlockState s = level.getBlockState(p);
            if (!isVaultBlock(s) || s.getValue(ModProperties.PART) == VaultPart.NONE) continue;
            boolean stillValid = false;
            for (BlockPos c : around(p, 1)) {
                if (MultiblockValidator.matchedAxis(level, c) != null) { stillValid = true; break; }
            }
            if (!stillValid) {
                level.setBlock(p, s.setValue(ModProperties.PART, VaultPart.NONE), 3);
                if (level.getBlockEntity(p) instanceof BankVaultBlockEntity be) be.setBuilderUUID(null);
            }
        }
    }

    private static List<BlockPos> around(BlockPos pos, int r) {
        List<BlockPos> l = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++)
                    l.add(pos.offset(dx, dy, dz));
        return l;
    }
}
