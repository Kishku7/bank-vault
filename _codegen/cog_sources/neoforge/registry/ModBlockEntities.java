package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.entity.BankVaultBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, BankVault.MOD_ID);

    public static BlockEntityType<BankVaultBlockEntity> BANK_VAULT;

    static {
        BLOCK_ENTITY_TYPES.register("bank_vault", () -> {
            // Vanilla ctor — FabricBlockEntityTypeBuilder is just sugar over this.
            /* [[[cog
            import compat_neoforge
            compat_neoforge.emit_be_create(cog, ver)
            ]]] */
            BANK_VAULT = new BlockEntityType<>(BankVaultBlockEntity::new, Set.of(ModBlocks.VAULT));
            /* [[[end]]] */
            return BANK_VAULT;
        });
    }

    private ModBlockEntities() {}
}
