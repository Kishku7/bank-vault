package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.inventory.BankVaultMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {

    public static MenuType<BankVaultMenu> BANK_VAULT;

    private ModMenus() {}

    public static void init() {
        BANK_VAULT = Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "bank_vault"),
                new MenuType<>(BankVaultMenu::new, FeatureFlags.VANILLA_SET));
    }
}
