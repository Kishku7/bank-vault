package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BankVault.MOD_ID);

    static {
        ITEMS.register("bank_vault", () -> {
            Identifier id = Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "bank_vault");
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            // Item registry fires after blocks: ModBlocks.VAULT is populated by then.
            return new BlockItem(ModBlocks.VAULT, new Item.Properties().setId(key));
        });
    }

    private ModItems() {}
}
