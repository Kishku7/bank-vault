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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
/* [[[cog
import compat_neoforge
compat_neoforge.emit_break_import(cog, ver)
]]] */
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
/* [[[end]]] */
import net.neoforged.neoforge.event.server.ServerStartingEvent;
/* [[[cog
import compat_neoforge
compat_neoforge.emit_neoentry_payloads_import(cog, ver)
]]] */
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/* [[[end]]] */

@Mod(BankVault.MOD_ID)
public class BankVaultNeoForge {

    /* [[[cog
    import compat_neoforge
    compat_neoforge.emit_neoentry_ctor(cog, ver)
    ]]] */
    public BankVaultNeoForge(ModContainer mod, IEventBus bus, Dist dist) {
    /* [[[end]]] */
        BankVault.LOGGER.info("[Bank Vault] NeoForge initializing (TravelersBackpack={})",
                BankVault.TRAVELERS_BACKPACK);

        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(bus);
        ModMenus.MENUS.register(bus);
        ModCreativeTab.TABS.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::serverStarting);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::blockBroken);

        /* [[[cog
        import compat_neoforge
        compat_neoforge.emit_neoentry_clientgate(cog, ver)
        ]]] */
        if (dist.isClient()) {
        /* [[[end]]] */
            com.kishku7.bankvault.client.BankVaultNeoForgeClient.init(bus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        Catalog.restoreMissingDefaults();
        Catalog.ensureLoaded();
        BankVault.LOGGER.info("[Bank Vault] registration complete");
    }

    /* [[[cog
    import compat_neoforge
    compat_neoforge.emit_neoentry_payloads_method(cog, ver)
    ]]] */
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
    /* [[[end]]] */
        ModNetworking.registerNeoForge(event);
    }

    /** v1.2 last-use memory: the 27 user_settings bucket files load once per server start. */
    private void serverStarting(ServerStartingEvent event) {
        UserSettings.loadAll();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        BankCommand.register(event.getDispatcher());
    }

    /** Fabric's PlayerBlockBreakEvents.AFTER equivalent: BreakEvent fires PRE-removal, so the
     *  unform check is deferred to after the current tick phase (block is gone by then). */
    /* [[[cog
    import compat_neoforge
    compat_neoforge.emit_break_sig(cog, ver)
    ]]] */
    private void blockBroken(BreakBlockEvent event) {
    /* [[[end]]] */
        if (event.getLevel() instanceof ServerLevel level && MultiblockManager.isVaultBlock(event.getState())) {
            var pos = event.getPos().immutable();
            level.getServer().execute(() -> MultiblockManager.onRemoved(level, pos));
        }
    }
}
