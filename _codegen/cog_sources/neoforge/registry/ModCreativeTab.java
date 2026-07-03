package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, BankVault.MOD_ID);

    static {
        /* [[[cog
        import compat_neoforge
        compat_neoforge.emit_tab_builder(cog, ver)
        ]]] */
        TABS.register("bankvault", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        /* [[[end]]] */
                .title(Component.translatable("itemGroup.bankvault.bankvault"))
                .icon(() -> new ItemStack(ModBlocks.VAULT))
                .displayItems((params, output) -> output.accept(ModBlocks.VAULT))
                .build());
    }

    private ModCreativeTab() {}
}
