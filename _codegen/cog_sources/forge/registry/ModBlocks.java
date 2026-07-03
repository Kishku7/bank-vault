package com.kishku7.bankvault.registry;

import com.kishku7.bankvault.BankVault;
import com.kishku7.bankvault.block.BankVaultBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BankVault.MOD_ID);

    /** The single vault block — place nine in a 3x3 to form a vault. Populated at registration
     *  so common code keeps its plain-field access pattern. */
    public static Block VAULT;

    static {
        BLOCKS.register("bank_vault", () -> {
            VAULT = new BankVaultBlock(props("bank_vault"));
            return VAULT;
        });
    }

    private ModBlocks() {}

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
}
