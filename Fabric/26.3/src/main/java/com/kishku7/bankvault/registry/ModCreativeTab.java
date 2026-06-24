package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {

    private ModCreativeTab() {}

    public static void init() {
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.bankvault.bankvault"))
                .icon(() -> new ItemStack(ModBlocks.VAULT))
                .displayItems((params, output) -> output.accept(ModBlocks.VAULT))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "bankvault"), tab);
    }
}
