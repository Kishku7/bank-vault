package com.kishku7.bankvault.client;

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
        }));
    }
}
