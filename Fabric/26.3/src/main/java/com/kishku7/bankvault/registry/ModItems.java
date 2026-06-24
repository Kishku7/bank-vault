package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {

    private ModItems() {}

    public static void init() {
        Identifier id = Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "bank_vault");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, id,
                new BlockItem(ModBlocks.VAULT, new Item.Properties().setId(key)));
    }
}
