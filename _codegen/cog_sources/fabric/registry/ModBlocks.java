package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.block.BankVaultBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {

    /** The single vault block — place nine in a 3x3 to form a vault. */
    public static Block VAULT;

    private ModBlocks() {}

    public static void init() {
        VAULT = register("bank_vault", new BankVaultBlock(props("bank_vault")));
    }

    /** Iron-block-like, blast-resistant, piston-immovable (grief protection). */
    private static Block.Properties props(String name) {
        return Block.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(50.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                /* [[[cog
                import compat_core
                compat_core.emit_block_props_tail(cog, ver)
                ]]] */
                .pushReaction(PushReaction.BLOCK)
                .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(BankVault.MOD_ID, name)));
                /* [[[end]]] */
    }

    private static Block register(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(BankVault.MOD_ID, name), block);
    }
}
