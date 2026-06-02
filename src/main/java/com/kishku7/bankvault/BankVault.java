package com.kishku7.bankvault;

import com.kishku7.bankvault.command.BankCommand;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModBlockEntities;
import com.kishku7.bankvault.registry.ModBlocks;
import com.kishku7.bankvault.registry.ModCreativeTab;
import com.kishku7.bankvault.registry.ModItems;
import com.kishku7.bankvault.registry.ModMenus;
import com.kishku7.bankvault.vault.Catalog;
import com.kishku7.bankvault.vault.MultiblockManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankVault implements ModInitializer {

    public static final String MOD_ID = "bankvault";
    public static final Logger LOGGER = LoggerFactory.getLogger("Bank Vault");

    /** Optional integrations — soft dependencies, guarded everywhere they are used. */
    public static final boolean TRINKETS =
            FabricLoader.getInstance().isModLoaded("trinkets_updated");
    public static final boolean TRAVELERS_BACKPACK =
            FabricLoader.getInstance().isModLoaded("travelersbackpack");

    @Override
    public void onInitialize() {
        LOGGER.info("[Bank Vault] initializing (Trinkets={}, TravelersBackpack={})",
                TRINKETS, TRAVELERS_BACKPACK);
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModCreativeTab.init();
        ModMenus.init();
        Catalog.ensureLoaded();
        ModNetworking.registerCommon();

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (MultiblockManager.isVaultBlock(state)) MultiblockManager.onRemoved(world, pos);
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> BankCommand.register(dispatcher));

        LOGGER.info("[Bank Vault] registration complete");
    }
}
