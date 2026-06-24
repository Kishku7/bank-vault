package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.net.SharingStatePayload;
import com.kishku7.bankvault.net.UiStateSyncPayload;
import com.kishku7.bankvault.net.VaultSyncPayload;
import com.kishku7.bankvault.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

public class BankVaultClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BankVault.LOGGER.info("[Bank Vault] client initializing");

        MenuScreens.register(ModMenus.BANK_VAULT, BankVaultScreen::new);

        // The screen is created by the menu open; this packet carries the virtual vault grid contents.
        ClientPlayNetworking.registerGlobalReceiver(VaultSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft mc = context.client();
                    if (mc.gui.screen() instanceof BankVaultScreen screen) screen.updateData(payload);
                }));

        // v1.2 last-use memory: arrives right before the menu-open packet; cached for screen init.
        ClientPlayNetworking.registerGlobalReceiver(UiStateSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientUiState.set(payload.lastTab(), payload.sorts(),
                        payload.showSections(), payload.pins())));

        ClientPlayNetworking.registerGlobalReceiver(SharingStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft mc = context.client();
                    if (mc.gui.screen() instanceof BankVaultScreen screen) screen.updateSharing(payload);
                }));
    }
}
