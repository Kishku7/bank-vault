package com.kishku7.bankvault.vault;

import com.kishku7.bankvault.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Validates a 3x3 vertical wall of nine identical vault blocks (X- or Z-plane).
 * The middle of a complete 3x3 is the center; corners/edges are derived by position.
 */
public final class MultiblockValidator {

    private MultiblockValidator() {}

    public static boolean isVault(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.VAULT);
    }

    public static boolean isFormed(Level level, BlockPos center) {
        return matchedAxis(level, center) != null;
    }

    /** Returns the wall axis (X or Z) of a complete 3x3 centered here, or null if incomplete. */
    public static Direction.Axis matchedAxis(Level level, BlockPos center) {
        if (matches(level, center, Direction.Axis.X)) return Direction.Axis.X;
        if (matches(level, center, Direction.Axis.Z)) return Direction.Axis.Z;
        return null;
    }

    private static boolean matches(Level level, BlockPos center, Direction.Axis horizontal) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dh = -1; dh <= 1; dh++) {
                BlockPos p = (horizontal == Direction.Axis.X)
                        ? center.offset(dh, dy, 0)
                        : center.offset(0, dy, dh);
                if (!isVault(level, p)) return false;
            }
        }
        return true;
    }
}
