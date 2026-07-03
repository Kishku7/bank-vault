"""compat_forge.py -- Forge-specific era emitters for Bank Vault.

Era boundary: Forge 58 (MC 1.21.8) moved to EventBus 7 (BusGroup mod bus, Event.BUS statics,
@Mod ctor receives FMLJavaModLoadingContext). Forge 55-57 (MC 1.21.5-1.21.7) and older use
EventBus 6 (IEventBus mod bus via FMLJavaModLoadingContext.get(), MinecraftForge.EVENT_BUS).
"""
import compat_core


def eb7(ver):
    return compat_core._vt(ver) >= (1, 21, 8)


ENTRY_EB7 = '''package com.kishku7.bankvault;

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
}'''

ENTRY_EB6 = '''package com.kishku7.bankvault;

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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint (EventBus 6 era, Forge <=57): classic no-arg @Mod constructor, mod bus via
 * FMLJavaModLoadingContext.get(), game-bus events on MinecraftForge.EVENT_BUS.
 */
@Mod(BankVault.MOD_ID)
public final class BankVaultForge {

    @SuppressWarnings("removal")   // FMLJavaModLoadingContext.get() is the era-correct accessor here
    public BankVaultForge() {
        BankVault.LOGGER.info("[Bank Vault] Forge initializing (TravelersBackpack={})",
                BankVault.TRAVELERS_BACKPACK);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTab.TABS.register(modBus);

        ModNetworking.init();   // Forge builds its payload channel directly (no register-event)

        modBus.addListener((FMLCommonSetupEvent e) -> {
            Catalog.restoreMissingDefaults();
            Catalog.ensureLoaded();
            BankVault.LOGGER.info("[Bank Vault] registration complete");
        });

        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> UserSettings.loadAll());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BankCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(BankVaultForge::blockBroken);

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
}'''

CLIENT_EB7 = '''package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge client bootstrap (58.x): no RegisterMenuScreensEvent on Forge -- bind the menu screen via
 * vanilla MenuScreens.register inside FMLClientSetupEvent (enqueueWork), and point the S2C sinks
 * at the screen cache. Only ever loaded on the client dist.
 */
public final class BankVaultForgeClient {

    private BankVaultForgeClient() {}

    public static void init(BusGroup modBus) {
        BankVault.LOGGER.info("[Bank Vault] client initializing");
        FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BANK_VAULT, BankVaultScreen::new);
            wireSinks();
        }));
    }

    private static void wireSinks() {
        ModNetworking.vaultSyncSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateData(payload);
        };
        ModNetworking.uiStateSink = payload -> ClientUiState.set(payload.lastTab(), payload.sorts(),
                payload.showSections(), payload.pins());
        ModNetworking.sharingSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateSharing(payload);
        };
    }
}'''

CLIENT_EB6 = '''package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge client bootstrap (EventBus 6 era, Forge <=57): bind the menu screen via vanilla
 * MenuScreens.register inside FMLClientSetupEvent (enqueueWork), and point the S2C sinks at the
 * screen cache. Only ever loaded on the client dist.
 */
public final class BankVaultForgeClient {

    private BankVaultForgeClient() {}

    public static void init(IEventBus modBus) {
        BankVault.LOGGER.info("[Bank Vault] client initializing");
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BANK_VAULT, BankVaultScreen::new);
            wireSinks();
        }));
    }

    private static void wireSinks() {
        ModNetworking.vaultSyncSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateData(payload);
        };
        ModNetworking.uiStateSink = payload -> ClientUiState.set(payload.lastTab(), payload.sorts(),
                payload.showSections(), payload.pins());
        ModNetworking.sharingSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (com.kishku7.bankvault.BvCompat.currentScreen(mc) instanceof BankVaultScreen screen) screen.updateSharing(payload);
        };
    }
}'''


def emit_entrypoint(cog, ver):
    for ln in (ENTRY_EB7 if eb7(ver) else ENTRY_EB6).split("\n"):
        cog.outl(ln)


def emit_client(cog, ver):
    for ln in (CLIENT_EB7 if eb7(ver) else CLIENT_EB6).split("\n"):
        cog.outl(ln)
