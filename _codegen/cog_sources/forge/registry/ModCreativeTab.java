package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;

public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BankVault.MOD_ID);

    static {
        TABS.register("bankvault", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.bankvault.bankvault"))
                .icon(() -> new ItemStack(ModBlocks.VAULT))
                .displayItems((params, output) -> output.accept(ModBlocks.VAULT))
                .build());
    }

    private ModCreativeTab() {}
}
