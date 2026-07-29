package com.kishku7.bankvault.vault;

/**
 * Storage capacity as a function of upgrade-chest count.
 *
 * <p>Back-loaded exponential (k = 2) on a floor of {@code 2048 + 1728*c}, so capacity
 * always exceeds simply placing the same chests on the ground (27 slots * 64 = 1728 each).
 * Endpoints: 0 chests = 2,048; 64 chests = 20,000,000. The last 8 chests carry ~74% of max.
 */
public final class VaultCapacity {

    public static final long BASE_ITEMS   = 2_048L;       // capacity with 0 upgrade chests
    public static final long MAX_ITEMS    = 20_000_000L;  // capacity at 64 chests
    public static final int  CHEST_SLOTS  = 27 * 64;      // 1728 -- a real chest's item ceiling
    public static final int  MAX_UPGRADES = 64;

    private VaultCapacity() {}

    /** floor(c) = base + one real chest's worth per upgrade chest. */
    private static long floor(int chests) {
        return BASE_ITEMS + (long) CHEST_SLOTS * chests;
    }

    /** Total item capacity for the given number of upgrade chests (clamped 0..64). */
    public static long capacityFor(int chests) {
        if (chests <= 0)            return BASE_ITEMS;
        if (chests >= MAX_UPGRADES) return MAX_ITEMS;
        double r = (double) MAX_ITEMS / floor(MAX_UPGRADES); // growth factor ~177.557
        double t = (double) chests / MAX_UPGRADES;
        return Math.round(floor(chests) * Math.pow(r, t * t));
    }
}
