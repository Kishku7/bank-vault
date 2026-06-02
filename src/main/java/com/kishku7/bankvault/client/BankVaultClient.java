package com.kishku7.bankvault.client;

import com.kishku7.bankvault.BankVault;
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
                    if (mc.screen instanceof BankVaultScreen screen) screen.updateData(payload);
                }));
    }
}
