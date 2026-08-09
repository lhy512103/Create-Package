package com.lhy.createpackage.content.distributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.locator.MenuLocators;

/**
 * The package distributor block. Bridges an adjacent AE2 pattern provider to a set of linked
 * Create sequenced-assembly machines.
 */
public class PackageDistributorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape NORTH_COLLISION_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 9, 16), Block.box(2, 9, 2, 14, 21, 14),
            Block.box(3, 21, 2, 13, 23, 14), Block.box(5, 23, 2, 11, 25, 14),
            Block.box(6, 25, 1, 10, 27, 15), Block.box(7, 27, 1, 9, 29, 15));
    private static final VoxelShape NORTH_SELECTION_SHAPE = Shapes.or(
            NORTH_COLLISION_SHAPE, Block.box(1, 9, 5, 15, 11, 11), Block.box(2, 22, 3, 3, 32, 13));
    private static final Map<Direction, VoxelShape> COLLISION_SHAPES = shapesByDirection(NORTH_COLLISION_SHAPE);
    private static final Map<Direction, VoxelShape> SELECTION_SHAPES = shapesByDirection(NORTH_SELECTION_SHAPE);

    private final BiFunction<BlockPos, BlockState, BlockEntity> blockEntityFactory;

    public PackageDistributorBlock(Properties properties) {
        this(properties, PackageDistributorBlockEntity::new);
    }

    public PackageDistributorBlock(Properties properties, BiFunction<BlockPos, BlockState, BlockEntity> blockEntityFactory) {
        super(properties);
        this.blockEntityFactory = blockEntityFactory;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SELECTION_SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return COLLISION_SHAPES.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> shapesByDirection(VoxelShape northShape) {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) shapes.put(direction, rotateFromNorth(northShape, direction));
        return Map.copyOf(shapes);
    }

    private static VoxelShape rotateFromNorth(VoxelShape shape, Direction direction) {
        VoxelShape rotated = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            VoxelShape rotatedBox = switch (direction) {
                case NORTH -> Shapes.create(box);
                case EAST -> Shapes.box(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
                case SOUTH -> Shapes.box(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX, box.maxY, 1 - box.minZ);
                case WEST -> Shapes.box(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
                case UP -> Shapes.box(box.minX, box.minZ, 1 - box.maxY, box.maxX, box.maxZ, 1 - box.minY);
                case DOWN -> Shapes.box(box.minX, 1 - box.maxZ, box.minY, box.maxX, 1 - box.minZ, box.maxY);
            };
            rotated = Shapes.or(rotated, rotatedBox);
        }
        return rotated.optimize();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.is(ModItems.MACHINE_LINKER.get())
                || heldItem.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof PatternProviderLogicHost host) {
            if (!level.isClientSide()) host.openMenu(player, MenuLocators.forBlockEntity(host.getBlockEntity()));
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
