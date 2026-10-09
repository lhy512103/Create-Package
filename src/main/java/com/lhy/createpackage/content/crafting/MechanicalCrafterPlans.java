package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler;
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
import net.minecraft.world.item.crafting.Ingredient;
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
        IPatternDetails pattern = PATTERN.get();
        if (!(pattern instanceof AEProcessingPattern processing)) {
            return MachinePlanFactory.Plan.failure("crafter_pattern_shape");
        }
        List<PatternCell> cells = cellsFromRecipe(level, processing, primaryOutput, outputAmount);
        if (cells == null) {
            cells = patternCells(processing.getSparseInputs());
        }
        if (cells == null) {
            return MachinePlanFactory.Plan.failure("crafter_pattern_shape");
        }
        if (cells.isEmpty()) {
            return MachinePlanFactory.Plan.failure("missing_item_input");
        }
        Grid grid = resolve(level, origin);
        if (!matches(grid, cells)) {
            alignToPattern(level, origin, cells);
            grid = resolve(level, origin);
        }
        if (grid == null) {
            return MachinePlanFactory.Plan.failure("crafter_arrows");
        }
        MechanicalCrafterBlockEntity sinkEntity = crafter(level, grid.sink);
        if (sinkEntity == null || sinkEntity.getSpeed() == 0) {
            return MachinePlanFactory.Plan.failure("crafter_not_powered");
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

    private static boolean matches(@Nullable Grid grid, List<PatternCell> cells) {
        if (grid == null) {
            return false;
        }
        int maxX = 0;
        int maxY = 0;
        Map<Long, CrafterCell> byCoord = new HashMap<>();
        for (CrafterCell cell : grid.cells) {
            byCoord.put(pack(cell.x, cell.y), cell);
            maxX = Math.max(maxX, cell.x);
            maxY = Math.max(maxY, cell.y);
        }
        int patternWidth = 0;
        int patternHeight = 0;
        for (PatternCell cell : cells) {
            patternWidth = Math.max(patternWidth, cell.x);
            patternHeight = Math.max(patternHeight, cell.y);
        }
        if (patternWidth != maxX || patternHeight != maxY) {
            return false;
        }
        Set<Long> filled = new HashSet<>();
        for (PatternCell cell : cells) {
            if (byCoord.get(pack(cell.x, maxY - cell.y)) == null) {
                return false;
            }
            filled.add(pack(cell.x, maxY - cell.y));
        }
        for (CrafterCell cell : grid.cells) {
            if (!filled.contains(pack(cell.x, cell.y))) {
                return false;
            }
        }
        return true;
    }

    private static void alignToPattern(Level level, BlockPos origin, List<PatternCell> cells) {
        MechanicalCrafterBlockEntity originCrafter = crafter(level, origin);
        if (originCrafter == null) {
            return;
        }
        Direction facing = originCrafter.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        BlockState sample = originCrafter.getBlockState();
        Direction plusX = MechanicalCrafterBlock.getTargetDirection(sample.setValue(MechanicalCrafterBlock.POINTING, Pointing.LEFT))
                .getOpposite();
        Direction plusY = MechanicalCrafterBlock.getTargetDirection(sample.setValue(MechanicalCrafterBlock.POINTING, Pointing.UP));
        Map<Long, BlockPos> cluster = new HashMap<>();
        List<BlockPos> pending = new ArrayList<>();
        pending.add(origin.immutable());
        cluster.put(pack(0, 0), origin.immutable());
        Map<BlockPos, int[]> worldCoord = new HashMap<>();
        worldCoord.put(origin.immutable(), new int[] { 0, 0 });
        for (int index = 0; index < pending.size(); index++) {
            BlockPos current = pending.get(index);
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() == facing.getAxis()) {
                    continue;
                }
                BlockPos next = current.relative(direction);
                MechanicalCrafterBlockEntity neighbor = crafter(level, next);
                if (neighbor == null || neighbor.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING) != facing
                        || worldCoord.containsKey(next)) {
                    continue;
                }
                int x = axisDelta(origin, next, plusX);
                int y = axisDelta(origin, next, plusY);
                worldCoord.put(next.immutable(), new int[] { x, y });
                cluster.put(pack(x, y), next.immutable());
                pending.add(next.immutable());
            }
        }
        int[] originCoord = worldCoord.get(origin.immutable());
        int maxPatternY = 0;
        for (PatternCell cell : cells) {
            maxPatternY = Math.max(maxPatternY, cell.y);
        }
        int offsetX = 0;
        int offsetY = 0;
        boolean found = false;
        for (PatternCell anchor : cells) {
            int createY = maxPatternY - anchor.y;
            int candidateX = originCoord[0] - anchor.x;
            int candidateY = originCoord[1] - createY;
            boolean fits = true;
            for (PatternCell cell : cells) {
                if (!cluster.containsKey(pack(cell.x + candidateX, maxPatternY - cell.y + candidateY))) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                offsetX = candidateX;
                offsetY = candidateY;
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }
        Map<Long, BlockPos> matched = new HashMap<>();
        for (PatternCell cell : cells) {
            int createY = maxPatternY - cell.y;
            BlockPos pos = cluster.get(pack(cell.x + offsetX, createY + offsetY));
            matched.put(pack(cell.x, createY), pos);
            MechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null || !be.getInventory().getStackInSlot(0).isEmpty()) {
                return;
            }
        }
        BlockPos sink = chooseSink(level, origin, matched);
        if (!routeArrows(level, facing, matched, sink)) {
            return;
        }
        pointExtrasAway(level, facing, cluster, matched);
        connectGroup(level, facing, matched, sink);
    }

    private static BlockPos chooseSink(Level level, BlockPos origin, Map<Long, BlockPos> matched) {
        Set<BlockPos> positions = Set.copyOf(matched.values());
        for (BlockPos pos : positions) {
            MechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null) {
                continue;
            }
            Direction current = MechanicalCrafterBlock.getTargetDirection(be.getBlockState());
            BlockPos outside = pos.relative(current);
            if (!positions.contains(outside) && InventoryAccess.itemHandler(level, outside, null) != null) {
                return pos;
            }
        }
        if (positions.contains(origin)) {
            return origin.immutable();
        }
        return positions.iterator().next();
    }

    private static boolean routeArrows(Level level, Direction facing, Map<Long, BlockPos> matched, BlockPos sink) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        List<BlockPos> pending = new ArrayList<>();
        parent.put(sink, null);
        pending.add(sink);
        for (int index = 0; index < pending.size(); index++) {
            BlockPos current = pending.get(index);
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() == facing.getAxis()) {
                    continue;
                }
                BlockPos next = current.relative(direction);
                if (!matched.containsValue(next) || parent.containsKey(next)) {
                    continue;
                }
                parent.put(next, current);
                pending.add(next);
            }
        }
        if (parent.size() != matched.size()) {
            return false;
        }
        for (Map.Entry<BlockPos, BlockPos> entry : parent.entrySet()) {
            Direction targetDirection = entry.getValue() == null
                    ? outputDirection(level, entry.getKey(), facing, parent.keySet())
                    : directionTo(entry.getKey(), entry.getValue());
            if (targetDirection == null) {
                return false;
            }
            pointAt(level, entry.getKey(), facing, targetDirection);
        }
        return true;
    }

    @Nullable
    private static Direction outputDirection(Level level, BlockPos sink, Direction facing, Set<BlockPos> matched) {
        MechanicalCrafterBlockEntity be = crafter(level, sink);
        if (be == null) {
            return null;
        }
        Direction current = MechanicalCrafterBlock.getTargetDirection(be.getBlockState());
        if (!matched.contains(sink.relative(current))) {
            return current;
        }
        Direction fallback = null;
        for (Pointing pointing : Pointing.values()) {
            Direction direction = MechanicalCrafterBlock.getTargetDirection(
                    be.getBlockState().setValue(MechanicalCrafterBlock.POINTING, pointing));
            if (matched.contains(sink.relative(direction))) {
                continue;
            }
            if (InventoryAccess.itemHandler(level, sink.relative(direction), null) != null) {
                return direction;
            }
            if (fallback == null) {
                fallback = direction;
            }
        }
        return fallback == null ? current : fallback;
    }

    private static void connectGroup(Level level, Direction facing, Map<Long, BlockPos> matched, BlockPos sink) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> pending = new ArrayList<>();
        visited.add(sink);
        pending.add(sink);
        for (int index = 0; index < pending.size(); index++) {
            BlockPos current = pending.get(index);
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() == facing.getAxis()) {
                    continue;
                }
                BlockPos next = current.relative(direction);
                if (!matched.containsValue(next) || !visited.add(next)) {
                    continue;
                }
                pending.add(next);
                if (ConnectedInputHandler.shouldConnect(level, current, direction, direction) && !sameGroup(level, current, next)) {
                    ConnectedInputHandler.toggleConnection(level, current, next);
                }
            }
        }
    }

    private static boolean sameGroup(Level level, BlockPos left, BlockPos right) {
        MechanicalCrafterBlockEntity leftCrafter = crafter(level, left);
        MechanicalCrafterBlockEntity rightCrafter = crafter(level, right);
        if (leftCrafter == null || rightCrafter == null) {
            return false;
        }
        var leftInventories = leftCrafter.getInput().getInventories(level, left);
        var rightInventories = rightCrafter.getInput().getInventories(level, right);
        return !leftInventories.isEmpty() && !rightInventories.isEmpty() && leftInventories.get(0) == rightInventories.get(0);
    }

    private static void pointAt(Level level, BlockPos pos, Direction facing, Direction targetDirection) {
        MechanicalCrafterBlockEntity be = crafter(level, pos);
        if (be == null) {
            return;
        }
        Pointing pointing = MechanicalCrafterBlock.pointingFromFacing(targetDirection, facing);
        BlockState state = be.getBlockState();
        if (state.getValue(MechanicalCrafterBlock.POINTING) == pointing) {
            return;
        }
        KineticBlockEntity.switchToBlockState(level, pos, state.setValue(MechanicalCrafterBlock.POINTING, pointing));
    }

    @Nullable
    private static Direction directionTo(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return null;
    }

    private static int axisDelta(BlockPos origin, BlockPos pos, Direction axis) {
        BlockPos step = BlockPos.ZERO.relative(axis);
        return (pos.getX() - origin.getX()) * step.getX()
                + (pos.getY() - origin.getY()) * step.getY()
                + (pos.getZ() - origin.getZ()) * step.getZ();
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
    private static List<PatternCell> cellsFromRecipe(Level level, AEProcessingPattern pattern, AEKey primaryOutput,
            long outputAmount) {
        if (!(primaryOutput instanceof AEItemKey primaryItem) || outputAmount <= 0) {
            return null;
        }
        Map<AEItemKey, Integer> pool = new HashMap<>();
        for (GenericStack stack : pattern.getSparseInputs()) {
            if (stack == null || stack.amount() <= 0 || stack.what() == null) {
                continue;
            }
            if (!(stack.what() instanceof AEItemKey key) || stack.amount() > Integer.MAX_VALUE) {
                return null;
            }
            pool.merge(key, (int) stack.amount(), Integer::sum);
        }
        List<PatternCell> matched = null;
        for (var holder : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.MECHANICAL_CRAFTING.getType())) {
            Object value = holder.value();
            if (!(value instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe recipe)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            AEItemKey resultKey = AEItemKey.of(result);
            if (result.isEmpty() || result.getCount() != outputAmount || resultKey == null || !resultKey.equals(primaryItem)) {
                continue;
            }
            List<PatternCell> cells = assignRecipe(recipe, pool);
            if (cells == null) {
                continue;
            }
            if (matched != null) {
                return null;
            }
            matched = cells;
        }
        return matched;
    }

    @Nullable
    private static List<PatternCell> assignRecipe(com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe recipe,
            Map<AEItemKey, Integer> pool) {
        Map<AEItemKey, Integer> remaining = new HashMap<>(pool);
        List<PatternCell> cells = new ArrayList<>();
        int width = recipe.getWidth();
        int height = recipe.getHeight();
        List<Ingredient> ingredients = recipe.getIngredients();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Ingredient ingredient = ingredients.get(x + y * width);
                if (ingredient.isEmpty()) {
                    continue;
                }
                AEItemKey match = null;
                for (Map.Entry<AEItemKey, Integer> entry : remaining.entrySet()) {
                    if (entry.getValue() <= 0 || !ingredient.test(entry.getKey().toStack(1))) {
                        continue;
                    }
                    match = entry.getKey();
                    break;
                }
                if (match == null) {
                    return null;
                }
                remaining.put(match, remaining.get(match) - 1);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                cells.add(new PatternCell(x, y, new GenericStack(match, 1)));
            }
        }
        for (int count : remaining.values()) {
            if (count != 0) {
                return null;
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        List<PatternCell> normalized = new ArrayList<>();
        for (PatternCell cell : cells) {
            normalized.add(new PatternCell(cell.x - minX, cell.y - minY, cell.stack));
        }
        return normalized;
    }

    private static void pointExtrasAway(Level level, Direction facing, Map<Long, BlockPos> cluster,
            Map<Long, BlockPos> matched) {
        Set<BlockPos> used = Set.copyOf(matched.values());
        for (BlockPos pos : cluster.values()) {
            if (used.contains(pos)) {
                continue;
            }
            MechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null || !be.getInventory().getStackInSlot(0).isEmpty()) {
                continue;
            }
            Direction current = MechanicalCrafterBlock.getTargetDirection(be.getBlockState());
            if (!used.contains(pos.relative(current))) {
                continue;
            }
            Direction away = outputDirection(level, pos, facing, used);
            if (away != null) {
                pointAt(level, pos, facing, away);
            }
        }
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
