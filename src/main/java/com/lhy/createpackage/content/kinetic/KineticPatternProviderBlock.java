package com.lhy.createpackage.content.kinetic;

import com.mojang.serialization.MapCodec;

import com.lhy.createpackage.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.crafting.PushDirection;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.hooks.WrenchHook;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import appeng.util.Platform;

public class KineticPatternProviderBlock extends BaseEntityBlock {
    public static final MapCodec<KineticPatternProviderBlock> CODEC = simpleCodec(KineticPatternProviderBlock::new);
    public static final EnumProperty<PushDirection> PUSH_DIRECTION = EnumProperty.create("push_direction",
            PushDirection.class);

    public KineticPatternProviderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PUSH_DIRECTION);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticPatternProviderBlockEntity(pos, state);
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
        if (!InteractionUtil.isInAlternateUseMode(player) && InteractionUtil.canWrenchRotate(heldItem)) {
            setSide(level, pos, hit.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    public void setSide(Level level, BlockPos pos, Direction facing) {
        BlockState currentState = level.getBlockState(pos);
        Direction pushSide = currentState.getValue(PUSH_DIRECTION).getDirection();

        PushDirection newPushDirection;
        if (pushSide == facing.getOpposite()) {
            newPushDirection = PushDirection.fromDirection(facing);
        } else if (pushSide == facing) {
            newPushDirection = PushDirection.ALL;
        } else if (pushSide == null) {
            newPushDirection = PushDirection.fromDirection(facing.getOpposite());
        } else {
            newPushDirection = PushDirection.fromDirection(Platform.rotateAround(pushSide, facing));
        }

        level.setBlockAndUpdate(pos, currentState.setValue(PUSH_DIRECTION, newPushDirection));
        if (level.getBlockEntity(pos) instanceof KineticPatternProviderBlockEntity provider) {
            provider.updateGridConnectableSides();
        }
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (!WrenchHook.isDisassembling()) {
            super.spawnDestroyParticles(level, player, pos, state);
        }
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
