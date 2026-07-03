package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.inventory.BankVaultMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, BankVault.MOD_ID);

    public static MenuType<BankVaultMenu> BANK_VAULT;

    static {
        MENUS.register("bank_vault", () -> {
            BANK_VAULT = new MenuType<>(BankVaultMenu::new, FeatureFlags.VANILLA_SET);
            return BANK_VAULT;
        });
    }

    private ModMenus() {}
}
