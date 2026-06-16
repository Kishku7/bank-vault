package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.net.ModNetworking;
import com.kishku7.bankvault.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** NeoForge client bootstrap: menu screen registration + the S2C payload sinks (fabric's
 *  ClientPlayNetworking receivers). Only ever loaded on the client dist. */
public final class BankVaultNeoForgeClient {

    private BankVaultNeoForgeClient() {}

    public static void init(IEventBus modBus) {
        BankVault.LOGGER.info("[Bank Vault] client initializing");
        modBus.addListener(BankVaultNeoForgeClient::registerScreens);

        // The screen is created by the menu open; this payload carries the virtual vault grid.
        ModNetworking.vaultSyncSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof BankVaultScreen screen) screen.updateData(payload);
        };
        // v1.2 last-use memory: arrives right before the menu-open packet; cached for screen init.
        ModNetworking.uiStateSink = payload -> ClientUiState.set(payload.lastTab(), payload.sorts(),
                payload.showSections(), payload.pins());
        ModNetworking.sharingSink = payload -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof BankVaultScreen screen) screen.updateSharing(payload);
        };
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BANK_VAULT, BankVaultScreen::new);
    }
}
