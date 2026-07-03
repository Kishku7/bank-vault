package com.kishku7.bankvault;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge port: plain constants holder (the fabric ModInitializer lives in the fabric build;
 * Forge bootstraps through {@link BankVaultForge}). Common code references MOD_ID, LOGGER and
 * the integration flags from here exactly as on fabric.
 */
public class BankVault {

    public static final String MOD_ID = "bankvault";
    public static final Logger LOGGER = LoggerFactory.getLogger("Bank Vault");

    /** Trinkets Updated is a fabric-only mod - never present on Forge. */
    public static final boolean TRINKETS = false;
    public static final boolean TRAVELERS_BACKPACK = ModList.get().isLoaded("travelersbackpack");

    private BankVault() {}
}
