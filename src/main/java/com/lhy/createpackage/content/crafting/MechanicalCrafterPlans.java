package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;

import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places one processing-pattern slot into each mechanical crafter of an arrow chain.
 * Wrench-linked inventories are not required; Create assembles the recipe by following arrows.
 */
public final class MechanicalCrafterPlans {
    private static final ThreadLocal<IPatternDetails> PATTERN = new ThreadLocal<>();

    private MechanicalCrafterPlans() {
    }

    public static void usePattern(@Nullable IPatternDetails pattern) {
        PATTERN.set(pattern);
    }

    public static void clearPattern() {
        PATTERN.remove();
    }

    public static boolean sameGrid(@Nullable Level level, BlockPos left, BlockPos right) {
        if (level == null || left == null || right == null || left.equals(right)) {
            return false;
        }
        BlockPos leftSink = sink(level, left);
        BlockPos rightSink = sink(level, right);
        return leftSink != null && leftSink.equals(rightSink);
    }

    public static MachinePlanFactory.Plan plan(Level level, BlockPos origin, AEKey primaryOutput, long outputAmount,
            List<GenericStack> patternOutputs) {
        Grid grid = resolve(level, origin);
        if (grid == null) {
            return MachinePlanFactory.Plan.failure("crafter_arrows");
        }
        MechanicalCrafterBlockEntity sink = crafter(level, grid.sink);
        if (sink == null || sink.getSpeed() == 0) {
            return MachinePlanFactory.Plan.failure("crafter_not_powered");
        }
        IPatternDetails pattern = PATTERN.get();
        if (!(pattern instanceof AEProcessingPattern processing)) {
            return MachinePlanFactory.Plan.failure("crafter_pattern_shape");
        }
        List<PatternCell> cells = patternCells(processing.getSparseInputs());
        if (cells == null) {
            return MachinePlanFactory.Plan.failure("crafter_pattern_shape");
        }
        if (cells.isEmpty()) {
            return MachinePlanFactory.Plan.failure("missing_item_input");
        }

        Map<Long, CrafterCell> byCoord = new HashMap<>();
        int maxX = 0;
        int maxY = 0;
        for (CrafterCell cell : grid.cells) {
            byCoord.put(pack(cell.x, cell.y), cell);
            maxX = Math.max(maxX, cell.x);
            maxY = Math.max(maxY, cell.y);
        }
        int patternHeight = 0;
        int patternWidth = 0;
        for (PatternCell cell : cells) {
            patternWidth = Math.max(patternWidth, cell.x);
            patternHeight = Math.max(patternHeight, cell.y);
        }
        if (patternWidth != maxX || patternHeight != maxY) {
            return MachinePlanFactory.Plan.failure("crafter_shape");
        }

        List<SupplyAction> actions = new ArrayList<>();
        Set<Long> filled = new HashSet<>();
        for (PatternCell cell : cells) {
            int gridY = maxY - cell.y;
            CrafterCell target = byCoord.get(pack(cell.x, gridY));
            if (target == null) {
                return MachinePlanFactory.Plan.failure("crafter_shape");
            }
            filled.add(pack(cell.x, gridY));
            ItemStack stack = ((AEItemKey) cell.stack.what()).toStack(1);
            actions.add(new CrafterInsertAction(level, target.pos, stack));
        }
        for (CrafterCell cell : grid.cells) {
            if (!filled.contains(pack(cell.x, cell.y))) {
                return MachinePlanFactory.Plan.failure("crafter_shape");
            }
        }

        List<MachineEndpoint> outputs = new ArrayList<>();
        if (InventoryAccess.itemHandler(level, grid.outputPos, null) != null) {
            outputs.add(MachineEndpoint.itemOutput(grid.outputPos, null));
        }
        outputs.add(MachineEndpoint.droppedItemOutput(grid.outputPos));
        for (CrafterCell cell : grid.cells) {
            outputs.add(MachineEndpoint.droppedItemOutput(cell.pos));
        }
        List<GenericStack> declared = new ArrayList<>(patternOutputs);
        if (declared.stream().noneMatch(output -> output != null && primaryOutput.equals(output.what()))) {
            declared.add(new GenericStack(primaryOutput, Math.max(1, outputAmount)));
        }
        return new MachinePlanFactory.Plan(grid.sink, grid.outputPos, outputs, "mechanical_crafter",
                new ActionList(actions), declared, List.of(), true, "working", null);
    }

    @Nullable
    private static BlockPos sink(Level level, BlockPos origin) {
        if (crafter(level, origin) == null) {
            return null;
        }
        BlockPos cursor = origin.immutable();
        Set<BlockPos> seen = new HashSet<>();
        while (seen.add(cursor)) {
            MechanicalCrafterBlockEntity current = crafter(level, cursor);
            if (current == null) {
                return null;
            }
            MechanicalCrafterBlockEntity target = RecipeGridHandler.getTargetingCrafter(current);
            if (target == null) {
                return cursor;
            }
            cursor = target.getBlockPos().immutable();
        }
        return null;
    }

    @Nullable
    private static Grid resolve(Level level, BlockPos origin) {
        BlockPos sink = sink(level, origin);
        if (sink == null) {
            return null;
        }
        MechanicalCrafterBlockEntity sinkCrafter = crafter(level, sink);
        if (sinkCrafter == null) {
            return null;
        }
        List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(sinkCrafter);
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        Map<BlockPos, int[]> coords = new HashMap<>();
        List<BlockPos> pending = new ArrayList<>();
        coords.put(sink, new int[] { 0, 0 });
        pending.add(sink);
        for (int index = 0; index < pending.size(); index++) {
            BlockPos currentPos = pending.get(index);
            MechanicalCrafterBlockEntity current = crafter(level, currentPos);
            if (current == null) {
                return null;
            }
            int[] currentCoord = coords.get(currentPos);
            for (MechanicalCrafterBlockEntity preceding : RecipeGridHandler.getPrecedingCrafters(current)) {
                BlockPos precedingPos = preceding.getBlockPos().immutable();
                if (coords.containsKey(precedingPos)) {
                    continue;
                }
                BlockState state = preceding.getBlockState();
                if (!state.hasProperty(MechanicalCrafterBlock.POINTING)) {
                    return null;
                }
                Pointing pointing = state.getValue(MechanicalCrafterBlock.POINTING);
                int x = currentCoord[0] + (pointing == Pointing.LEFT ? 1 : pointing == Pointing.RIGHT ? -1 : 0);
                int y = currentCoord[1] + (pointing == Pointing.DOWN ? 1 : pointing == Pointing.UP ? -1 : 0);
                coords.put(precedingPos, new int[] { x, y });
                pending.add(precedingPos);
            }
        }
        if (coords.size() != chain.size()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int[] coord : coords.values()) {
            minX = Math.min(minX, coord[0]);
            minY = Math.min(minY, coord[1]);
        }
        List<CrafterCell> cells = new ArrayList<>();
        for (Map.Entry<BlockPos, int[]> entry : coords.entrySet()) {
            cells.add(new CrafterCell(entry.getKey(), entry.getValue()[0] - minX, entry.getValue()[1] - minY));
        }
        Direction outputDirection = MechanicalCrafterBlock.getTargetDirection(sinkCrafter.getBlockState());
        return new Grid(sink, sink.relative(outputDirection).immutable(), cells);
    }

    @Nullable
    private static List<PatternCell> patternCells(List<GenericStack> sparse) {
        if (sparse == null || sparse.isEmpty()) {
            return List.of();
        }
        int stride = sparse.size() > 9 ? 9 : 3;
        if (sparse.size() % stride != 0) {
            return null;
        }
        List<PatternCell> cells = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int index = 0; index < sparse.size(); index++) {
            GenericStack stack = sparse.get(index);
            if (stack == null || stack.amount() <= 0 || stack.what() == null) {
                continue;
            }
            if (!(stack.what() instanceof AEItemKey) || stack.amount() != 1) {
                return null;
            }
            int x = index % stride;
            int y = index / stride;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            cells.add(new PatternCell(x, y, stack));
        }
        List<PatternCell> normalized = new ArrayList<>();
        for (PatternCell cell : cells) {
            normalized.add(new PatternCell(cell.x - minX, cell.y - minY, cell.stack));
        }
        return normalized;
    }

    @Nullable
    private static MechanicalCrafterBlockEntity crafter(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity crafter)) {
            return null;
        }
        BlockState state = crafter.getBlockState();
        if (!state.hasProperty(HorizontalKineticBlock.HORIZONTAL_FACING)) {
            return null;
        }
        return crafter;
    }

    private static long pack(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private record Grid(BlockPos sink, BlockPos outputPos, List<CrafterCell> cells) {
    }

    private record CrafterCell(BlockPos pos, int x, int y) {
    }

    private record PatternCell(int x, int y, GenericStack stack) {
    }

    private static final class CrafterInsertAction implements SupplyAction {
        private final Level level;
        private final BlockPos pos;
        private final ItemStack stack;
        private boolean inserted;

        private CrafterInsertAction(Level level, BlockPos pos, ItemStack stack) {
            this.level = level;
            this.pos = pos.immutable();
            this.stack = stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            MechanicalCrafterBlockEntity crafter = crafter(level, pos);
            if (crafter == null) {
                return false;
            }
            ItemStack remaining = crafter.getInventory().insertItem(0, stack.copy(), simulate);
            if (!remaining.isEmpty()) {
                return false;
            }
            if (!simulate) {
                inserted = true;
            }
            return true;
        }

        @Override
        public CompensationResult compensate() {
            if (!inserted) {
                return CompensationResult.complete();
            }
            MechanicalCrafterBlockEntity crafter = crafter(level, pos);
            if (crafter == null) {
                return CompensationResult.failed("crafter missing; 1 item remains inserted");
            }
            crafter.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            inserted = false;
            return CompensationResult.complete();
        }

        @Override
        public String describe() {
            return "crafter->" + pos.toShortString() + " x" + stack.getCount();
        }
    }
}
