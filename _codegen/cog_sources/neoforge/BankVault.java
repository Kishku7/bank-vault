package com.kishku7.bankvault;

import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge port: plain constants holder (the fabric ModInitializer lives in the fabric build;
 * NeoForge bootstraps through {@link BankVaultNeoForge}). Common code references MOD_ID,
 * LOGGER and the integration flags from here exactly as on fabric.
 */
public class BankVault {

    public static final String MOD_ID = "bankvault";
    public static final Logger LOGGER = LoggerFactory.getLogger("Bank Vault");

    /** Trinkets Updated is a fabric-only mod — never present on NeoForge. */
    public static final boolean TRINKETS = false;
    public static final boolean TRAVELERS_BACKPACK = ModList.get().isLoaded("travelersbackpack");
}
