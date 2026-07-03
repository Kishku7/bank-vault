package com.kishku7.bankvault.block;

import com.kishku7.bankvault.entity.BankVaultBlockEntity;
import com.kishku7.bankvault.vault.MultiblockManager;
import com.kishku7.bankvault.vault.VaultInteraction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The single vault block. Place nine in a 3x3 vertical wall; on completion the form manager
 * assigns each its {@link VaultPart} (corner/edge/center) and a shared facing, so the door appears.
 */
public class BankVaultBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<VaultPart> PART = ModProperties.PART;
    public static final MapCodec<BankVaultBlock> CODEC = simpleCodec(BankVaultBlock::new);

    // registerDefaultState is the canonical vanilla ctor pattern; 'this' does not escape further
    @SuppressWarnings("this-escape")
    public BankVaultBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(PART, VaultPart.NONE));
    }

    @Override
    public MapCodec<BankVaultBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BankVaultBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer sp) {
            MultiblockManager.onPlaced(level, pos, sp);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // Unformed: don't capture the click — let normal block placement / other interactions happen.
        if (state.getValue(PART) == VaultPart.NONE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            VaultInteraction.onUse(sp, level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
