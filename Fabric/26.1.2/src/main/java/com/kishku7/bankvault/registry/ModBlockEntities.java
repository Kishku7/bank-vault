package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.entity.BankVaultBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static BlockEntityType<BankVaultBlockEntity> BANK_VAULT;

    private ModBlockEntities() {}

    public static void init() {
        BANK_VAULT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "bank_vault"),
                FabricBlockEntityTypeBuilder.create(BankVaultBlockEntity::new, ModBlocks.VAULT).build());
    }
}
