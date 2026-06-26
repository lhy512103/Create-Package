package com.lhy.createpackage.content.distributor;

import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.locator.MenuLocators;

/**
 * The package distributor block. Bridges an adjacent AE2 pattern provider to a set of linked
 * Create sequenced-assembly machines.
 */
public class PackageDistributorBlock extends BaseEntityBlock {

    public static final MapCodec<PackageDistributorBlock> CODEC = simpleCodec(PackageDistributorBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final BiFunction<BlockPos, BlockState, BlockEntity> blockEntityFactory;

    public PackageDistributorBlock(Properties properties) {
        this(properties, PackageDistributorBlockEntity::new);
    }

    public PackageDistributorBlock(Properties properties, BiFunction<BlockPos, BlockState, BlockEntity> blockEntityFactory) {
        super(properties);
        this.blockEntityFactory = blockEntityFactory;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityFactory.apply(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (heldItem.is(ModItems.MACHINE_LINKER.get())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof PatternProviderLogicHost host) {
            if (!level.isClientSide()) {
                host.openMenu(player, MenuLocators.forBlockEntity(host.getBlockEntity()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
