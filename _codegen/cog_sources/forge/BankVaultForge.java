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
import com.kishku7.bankvault.vault.UserSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint (58.x / EventBus 7): the @Mod constructor receives the loading context, the
 * mod bus is a BusGroup, game-bus events subscribe via each event's static BUS field.
 */
@Mod(BankVault.MOD_ID)
public final class BankVaultForge {

    public BankVaultForge(FMLJavaModLoadingContext context) {
        BankVault.LOGGER.info("[Bank Vault] Forge initializing (TravelersBackpack={})",
                BankVault.TRAVELERS_BACKPACK);
        BusGroup modBus = context.getModBusGroup();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTab.TABS.register(modBus);

        ModNetworking.init();   // Forge builds its payload channel directly (no register-event)

        FMLCommonSetupEvent.getBus(modBus).addListener(e -> {
            Catalog.restoreMissingDefaults();
            Catalog.ensureLoaded();
            BankVault.LOGGER.info("[Bank Vault] registration complete");
        });

        ServerStartingEvent.BUS.addListener(e -> UserSettings.loadAll());
        RegisterCommandsEvent.BUS.addListener(e -> BankCommand.register(e.getDispatcher()));
        BlockEvent.BreakEvent.BUS.addListener(BankVaultForge::blockBroken);

        if (FMLEnvironment.dist.isClient()) {
            com.kishku7.bankvault.client.BankVaultForgeClient.init(modBus);
        }
    }

    /** Fabric's PlayerBlockBreakEvents.AFTER equivalent: BreakEvent fires PRE-removal, so the
     *  unform check is deferred to after the current tick phase (block is gone by then). */
    private static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && MultiblockManager.isVaultBlock(event.getState())) {
            var pos = event.getPos().immutable();
            level.getServer().execute(() -> MultiblockManager.onRemoved(level, pos));
        }
    }
}
