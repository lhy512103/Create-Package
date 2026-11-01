package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.CreatePackage;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;

import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plans one Mechanical Crafter arrow chain per pattern for the Kinetic Pattern Provider.
 *
 * <p>The recipe layout comes from Create's own recipes, so the crafters only need to sit next to each other
 * facing the same way. Planning never touches the world: when the arrows or group links do not match yet, the
 * plan carries an {@link ArrangeAction} that rewrites them as the first step of execution and rolls them back
 * if a later step fails.
 */
public final class MechanicalCrafterPlans {
    private static final ThreadLocal<IPatternDetails> PATTERN = new ThreadLocal<>();
    private static final ThreadLocal<Set<BlockPos>> CLAIMED = new ThreadLocal<>();
    private static final ThreadLocal<Set<BlockPos>> CLAIMED_CONTAINERS = new ThreadLocal<>();
    // Values hold only immutable layout data, never a level, block entity, inventory or simulated action.
    private static final Map<Level, Map<LayoutKey, LayoutHint>> SUCCESSFUL_LAYOUTS = new WeakHashMap<>();
    private static final int MAX_LAYOUT_HINTS_PER_LEVEL = 256;
    private static final Map<Level, Map<RecipeKey, List<List<PatternCell>>>> RECIPE_LAYOUTS = new WeakHashMap<>();
    private static final Map<Level, PlanningFailureCache<LayoutKey, FailureState>> FAILURES = new WeakHashMap<>();
    private static final int FAILURE_RETRY_TICKS = 5;
    private static final ThreadLocal<SearchStats> LAST_DEBUG_SEARCH = new ThreadLocal<>();

    record SearchStats(int placements, int groupLookups, boolean reusedLayout, int sinkScans, int recipeScans,
            boolean failureCached) {
    }

    @Nullable
    static SearchStats lastDebugSearch() {
        return LAST_DEBUG_SEARCH.get();
    }

    public static void onAddReloadListeners(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> clearPlanningCaches());
    }

    public static void onNeighborNotify(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            invalidateFailures(level);
        }
    }

    static synchronized void invalidateFailures(Level level) {
        FAILURES.remove(level);
    }

    static synchronized void clearPlanningCaches() {
        RECIPE_LAYOUTS.clear();
        SUCCESSFUL_LAYOUTS.clear();
        FAILURES.clear();
    }

    private MechanicalCrafterPlans() {
    }

    public static void usePattern(@Nullable IPatternDetails pattern) {
        PATTERN.set(pattern);
    }

    public static void clearPattern() {
        PATTERN.remove();
        CLAIMED.remove();
        CLAIMED_CONTAINERS.remove();
    }

    /**
     * Crafters and output containers that running jobs already use; a new plan must not place its recipe on those
     * crafters or hand its product to those containers.
     */
    public static void useClaimed(@Nullable Set<BlockPos> crafters, @Nullable Set<BlockPos> containers) {
        if (crafters == null || crafters.isEmpty()) {
            CLAIMED.remove();
        } else {
            CLAIMED.set(Set.copyOf(crafters));
        }
        if (containers == null || containers.isEmpty()) {
            CLAIMED_CONTAINERS.remove();
        } else {
            CLAIMED_CONTAINERS.set(Set.copyOf(containers));
        }
    }

    /** Every crafter the chain of a plan uses: recipe cells, empty links and the sink. */
    public static Set<BlockPos> chainCells(@Nullable Level level, BlockPos sinkPos) {
        if (level == null) {
            return Set.of();
        }
        MechanicalCrafterBlockEntity sink = crafter(level, sinkPos);
        if (sink == null) {
            return Set.of();
        }
        List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChainIf(sink, crafter -> true, true);
        Set<BlockPos> cells = new HashSet<>();
        cells.add(sinkPos.immutable());
        if (chain != null) {
            for (MechanicalCrafterBlockEntity crafter : chain) {
                cells.add(crafter.getBlockPos().immutable());
            }
        }
        return cells;
    }

    /** Planner-side trace; shares the provider's debug switch and log prefix so both appear in the same stream. */
    private static void debugLog(String message, Object... args) {
        if (!Config.debugLoggingEnabled()) {
            return;
        }
        CreatePackage.LOGGER.info("[Mechanical Crafter Plan] " + message, args);
    }

    /**
     * True while any crafter of the chain still holds an item or is part of a craft in progress, which means a
     * product may still come out of it.
     */
    public static boolean chainHoldsItems(@Nullable Level level, BlockPos sinkPos) {
        if (level == null) {
            return false;
        }
        MechanicalCrafterBlockEntity sink = crafter(level, sinkPos);
        if (sink == null) {
            return false;
        }
        List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChainIf(sink, crafter -> true, true);
        if (chain == null) {
            chain = List.of(sink);
        }
        for (MechanicalCrafterBlockEntity crafter : chain) {
            if (!crafter.getInventory().getStackInSlot(0).isEmpty() || isCrafting(level, crafter)) {
                return true;
            }
        }
        return false;
    }

    public static MachinePlanFactory.Plan plan(Level level, BlockPos origin, BlockPos providerPos, AEKey primaryOutput,
            long outputAmount, List<GenericStack> patternOutputs) {
        LAST_DEBUG_SEARCH.remove();
        IPatternDetails pattern = PATTERN.get();
        if (!(pattern instanceof AEProcessingPattern processing)) {
            return MachinePlanFactory.Plan.failure("crafter_pattern_shape");
        }
        var originCrafter = crafter(level, origin);
        if (originCrafter == null) {
            return MachinePlanFactory.Plan.failure("missing_machine");
        }
        Set<BlockPos> claimed = CLAIMED.get() == null ? Set.of() : CLAIMED.get();
        Set<BlockPos> claimedContainers = CLAIMED_CONTAINERS.get() == null ? Set.of() : CLAIMED_CONTAINERS.get();
        LayoutKey cacheKey = new LayoutKey(origin.immutable(), providerPos.immutable(), processing.getDefinition(),
                primaryOutput, outputAmount);
        FailureState state = new FailureState(originCrafter.getBlockState(), originCrafter.getSpeed(), claimed,
                claimedContainers, AllConfigs.server().recipes.allowRegularCraftingInCrafter.get());
        String failed = recentFailure(level, cacheKey, state);
        if (failed != null) {
            if (Config.debugLoggingEnabled()) {
                LAST_DEBUG_SEARCH.set(new SearchStats(0, 0, false, 0, 0, true));
            }
            return MachinePlanFactory.Plan.failure(failed);
        }
        PlanningContext context = new PlanningContext(level);
        MachinePlanFactory.Plan result = planFresh(level, origin, providerPos, primaryOutput, outputAmount,
                patternOutputs, processing, claimed, claimedContainers, cacheKey, context);
        if (!result.isSuccess()) {
            rememberFailure(level, cacheKey, state, result.statusKey());
        }
        if (Config.debugLoggingEnabled()) {
            LAST_DEBUG_SEARCH.set(new SearchStats(context.placements, context.groups.lookups(), context.reusedLayout,
                    context.sinkScans, context.recipeScans, false));
            debugLog("search placements={} groupLookups={} reused={} sinkScans={} recipeScans={}",
                    context.placements, context.groups.lookups(), context.reusedLayout, context.sinkScans, context.recipeScans);
        }
        return result;
    }

    private static MachinePlanFactory.Plan planFresh(Level level, BlockPos origin, BlockPos providerPos, AEKey primaryOutput,
            long outputAmount, List<GenericStack> patternOutputs, AEProcessingPattern processing, Set<BlockPos> claimed,
            Set<BlockPos> claimedContainers, LayoutKey cacheKey, PlanningContext context) {
        if (claimed.contains(origin)) {
            return MachinePlanFactory.Plan.failure("crafter_occupied");
        }
        Cluster cluster = Cluster.around(level, origin, claimed);
        // Outlet discovery depends on the structure, not the recipe placement. Fail before scanning recipes.
        context.sinks = discoverSinks(context, cluster, providerPos, claimedContainers);
        if (context.sinks.isEmpty()) {
            return MachinePlanFactory.Plan.failure("crafter_output_not_provider");
        }
        if (context.sinks.stream().noneMatch(sink -> context.crafter(sink.crafter()).getSpeed() != 0)) {
            return MachinePlanFactory.Plan.failure("crafter_not_powered");
        }
        List<List<PatternCell>> layouts = recipeLayouts(context, processing, primaryOutput, outputAmount);
        if (layouts.isEmpty()) {
            List<PatternCell> sparse = patternCells(processing.getSparseInputs());
            if (sparse == null) {
                return MachinePlanFactory.Plan.failure("crafter_pattern_shape");
            }
            if (sparse.isEmpty()) {
                return MachinePlanFactory.Plan.failure("missing_item_input");
            }
            layouts = List.of(sparse);
        }
        debugLog("plan origin={} provider={} layouts={} cluster={} claimed={}", origin, providerPos, layouts.size(),
                cluster.positions().size(), claimed.size());
        Arrangement best = null;
        List<PatternCell> bestLayout = null;
        String failure = null;
        LayoutHint hint = layoutHint(level, cacheKey);
        if (hint != null && layouts.contains(hint.layout())
                && hint.positions().stream().allMatch(cluster.coords()::containsKey)) {
            Result result = tryPlacement(context, cluster, hint.layout(), hint.positions(), providerPos, claimed);
            Arrangement candidate = result.arrangement();
            if (candidate != null && !candidate.busy() && candidate.changes() == 0
                    && candidate.sink().equals(hint.sink()) && candidate.outputPos().equals(hint.outputPos())) {
                best = candidate;
                bestLayout = hint.layout();
            }
        }
        context.reusedLayout = best != null;
        if (!context.reusedLayout) {
            for (List<PatternCell> layout : layouts) {
                Result result = arrangeLayout(context, cluster, layout, providerPos, claimed);
                if (result.arrangement() == null) {
                    debugLog("layout rejected cells={} failure={}", layout.size(), result.failure());
                    failure = failure == null ? result.failure() : failure;
                    continue;
                }
                if (best == null || result.arrangement().changes() < best.changes()) {
                    best = result.arrangement();
                    bestLayout = layout;
                }
            }
        }
        if (best == null) {
            return MachinePlanFactory.Plan.failure(failure == null ? "crafter_shape_missing" : failure);
        }

        // Into the provider itself: AE empties its return slots and the provider credits the job from there, so there
        // is nothing to scan. Into a container: the provider pulls the product out of that container, and picks up
        // anything dropped beside the sink in case the container is full or was removed.
        List<MachineEndpoint> outputs = best.outputPos().equals(providerPos)
                ? List.<MachineEndpoint>of()
                : List.of(MachineEndpoint.itemOutput(best.outputPos(), null),
                        MachineEndpoint.droppedItemOutput(best.sink()));
        List<GenericStack> declared = new ArrayList<>(patternOutputs);
        if (declared.stream().noneMatch(output -> output != null && primaryOutput.equals(output.what()))) {
            declared.add(new GenericStack(primaryOutput, Math.max(1, outputAmount)));
        }

        List<SupplyAction> actions = new ArrayList<>();
        if (best.busy()) {
            // Mechanical crafters report a failed simulate as "waiting for target space", so a busy chain never runs.
            actions.add(new BusyAction());
        }
        actions.add(new ArrangeAction(level, best.pointings(), best.connections()));
        for (Placed cell : best.cells()) {
            actions.add(new CrafterInsertAction(level, cell.pos(), cell.stack()));
        }
        if (best.emptyLinks()) {
            // A normal start refuses a chain with empty crafters, so the chain is started the way a redstone pulse
            // would, once every recipe item is in place.
            actions.add(new PoweredStartAction(level, best.cells().get(0).pos()));
        }
        // Simulation cannot populate this cache. Only a fully executed supply plan remembers its placement.
        actions.add(new RememberLayoutAction(level, cacheKey, new LayoutHint(List.copyOf(bestLayout),
                best.cells().stream().map(Placed::pos).toList(), best.sink(), best.outputPos())));
        return new MachinePlanFactory.Plan(best.sink(), best.outputPos(), outputs, "mechanical_crafter",
                new ActionList(actions), declared, List.of(), true, "working", null);
    }

    /**
     * Tries every anchor cell of the layout against the physical cluster and keeps the placement that needs the
     * fewest arrow and link changes.
     */
    private static Result arrangeLayout(PlanningContext context, Cluster cluster, List<PatternCell> layout, BlockPos providerPos,
            Set<BlockPos> claimed) {
        int maxY = 0;
        for (PatternCell cell : layout) {
            maxY = Math.max(maxY, cell.y);
        }
        String failure = null;
        // Any crafter in the cluster may host any recipe cell, so the recipe is not tied to the front crafter. Every
        // placement is scored, because a recipe far from the provider drags a long chain of empty crafters with it.
        Arrangement best = null;
        Set<Long> visitedOffsets = new HashSet<>();
        for (BlockPos anchorPos : cluster.positions()) {
            for (PatternCell anchor : layout) {
                // Shift the layout so this anchor cell lands on anchorPos.
                int offsetX = cluster.x(anchorPos) - anchor.x;
                int offsetY = cluster.y(anchorPos) - (maxY - anchor.y);
                if (!visitedOffsets.add(pack(offsetX, offsetY))) {
                    continue;
                }
                List<BlockPos> positions = new ArrayList<>(layout.size());
                for (PatternCell cell : layout) {
                    BlockPos pos = cluster.at(cell.x + offsetX, maxY - cell.y + offsetY);
                    if (pos == null) {
                        positions = null;
                        break;
                    }
                    positions.add(pos);
                }
                if (positions == null) {
                    continue;
                }
                Result result = tryPlacement(context, cluster, layout, positions, providerPos, claimed);
                if (result.arrangement() == null) {
                    debugLog("candidate anchor={} anchorCell=({},{}) positions={} failure={}", anchorPos, anchor.x,
                            anchor.y, positions, result.failure());
                    failure = failure == null ? result.failure() : failure;
                    continue;
                }
                if (best == null || result.arrangement().isBetterThan(best)) {
                    best = result.arrangement();
                }
            }
        }
        if (best != null) {
            return Result.ok(best);
        }
        return Result.failed(failure == null ? "crafter_shape_missing" : failure);
    }

    private static Result tryPlacement(PlanningContext context, Cluster cluster, List<PatternCell> layout, List<BlockPos> positions,
            BlockPos providerPos, Set<BlockPos> claimed) {
        Level level = context.level;
        context.placements++;
        List<Placed> cells = new ArrayList<>(layout.size());
        Set<BlockPos> recipe = new HashSet<>(positions);
        boolean busy = false;
        for (int i = 0; i < positions.size(); i++) {
            MechanicalCrafterBlockEntity crafter = context.crafter(positions.get(i));
            if (crafter == null || claimed.contains(positions.get(i))) {
                return Result.failed("crafter_shape_missing");
            }
            // A crafter still holding items, or one mid-craft with its items already in the grid, is busy. The insert
            // step reports held items as a simulate failure so the job can queue; only a chain that already matches
            // may be used as-is.
            busy |= !crafter.getInventory().getStackInSlot(0).isEmpty() || context.isCrafting(crafter);
            cells.add(new Placed(positions.get(i), ((AEItemKey) layout.get(i).stack().what()).toStack(1)));
        }

        // The chain's output face has to point at an inventory the player placed beside it, so the finished product
        // has somewhere to land. The sink does not have to hold a recipe item, because a powered start lets empty
        // crafters ride along in the chain.
        Sink found = chooseSink(context.sinks, recipe, providerPos);
        if (found == null) {
            debugLog("no output container next to the cluster, positions={}", positions);
            return Result.failed("crafter_output_not_provider");
        }
        BlockPos sink = found.crafter();
        BlockPos containerPos = found.container();
        MechanicalCrafterBlockEntity sinkCrafter = crafter(level, sink);
        if (sinkCrafter == null || sinkCrafter.getSpeed() == 0) {
            return Result.failed("crafter_not_powered");
        }

        // Spanning tree rooted at the sink that reaches every recipe cell. Crafters on the way that hold no recipe
        // item join the chain as empty links.
        Map<BlockPos, BlockPos> parent = chainTree(cluster, sink, recipe);
        if (parent == null) {
            return Result.failed("crafter_disconnected");
        }
        Set<BlockPos> chain = parent.keySet();

        Map<BlockPos, Pointing> desired = new LinkedHashMap<>();
        Direction outputDirection = directionTo(sink, containerPos);
        for (Map.Entry<BlockPos, BlockPos> entry : parent.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = crafter(level, pos).getBlockState();
            Direction direction;
            if (entry.getValue() == null) {
                direction = outputDirection;
            } else {
                direction = directionTo(pos, entry.getValue());
            }
            Pointing pointing = direction == null ? null : pointingToward(state, direction);
            if (pointing == null) {
                return Result.failed("crafter_shape");
            }
            desired.put(pos, pointing);
        }

        // Crafters outside the chain must not point into it, or into a chain another running group owns, or Create
        // would pull them into that chain.
        Set<BlockPos> avoid = new HashSet<>(chain);
        avoid.addAll(claimed);
        for (BlockPos pos : cluster.positions()) {
            if (chain.contains(pos)) {
                continue;
            }
            BlockState state = crafter(level, pos).getBlockState();
            if (!avoid.contains(pos.relative(target(state)))) {
                continue;
            }
            Direction free = freeTarget(level, pos, state, avoid);
            Pointing pointing = free == null ? null : pointingToward(state, free);
            if (pointing == null) {
                return Result.failed("crafter_extra_blocked");
            }
            desired.put(pos, pointing);
        }

        // Empty links must really be empty, otherwise a leftover item would be pulled into the recipe.
        int emptyCount = 0;
        for (BlockPos pos : chain) {
            if (recipe.contains(pos)) {
                continue;
            }
            emptyCount++;
            if (!crafter(level, pos).getInventory().getStackInSlot(0).isEmpty()) {
                return Result.failed("crafter_occupied");
            }
        }

        boolean emptyLinks = emptyCount > 0;
        List<PointingChange> pointingChanges = new ArrayList<>();
        for (Map.Entry<BlockPos, Pointing> entry : desired.entrySet()) {
            BlockState state = crafter(level, entry.getKey()).getBlockState();
            if (state.getValue(MechanicalCrafterBlock.POINTING) != entry.getValue()) {
                pointingChanges.add(new PointingChange(entry.getKey(), entry.getValue()));
            }
        }
        if (busy) {
            if (!pointingChanges.isEmpty()) {
                return Result.failed("crafter_occupied");
            }
            // Linking a chain that is mid-craft would change shared inventories under it, so leave it alone.
            return Result.ok(new Arrangement(sink, containerPos, cells, List.of(), List.of(), true, emptyLinks, emptyCount));
        }
        List<Connection> connections = groupConnections(context, new ArrayList<>(chain), chain);
        return Result.ok(new Arrangement(sink, containerPos, cells, pointingChanges, connections, false, emptyLinks, emptyCount));
    }


    /**
     * Create keeps the craft phase private; the saved NBT is the only public window onto it. Anything other than
     * IDLE means the crafter is part of a craft in progress.
     */
    private static boolean isCrafting(Level level, MechanicalCrafterBlockEntity crafter) {
        CompoundTag saved = new CompoundTag();
        crafter.write(saved, level.registryAccess(), false);
        String phase = saved.getString("Phase");
        return !phase.isEmpty() && !"IDLE".equals(phase);
    }

    /** Pairs of matched crafters to link into one Create group, skipping pairs that are already linked. */
    private static List<Connection> groupConnections(PlanningContext context, List<BlockPos> positions, Set<BlockPos> matched) {
        Level level = context.level;
        List<Connection> connections = new ArrayList<>();
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : positions) {
            done.add(pos);
            Direction facing = crafter(level, pos).getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() == facing.getAxis()) {
                    continue;
                }
                BlockPos next = pos.relative(direction);
                if (!matched.contains(next) || done.contains(next)) {
                    continue;
                }
                if (context.canConnect(pos, next, direction) && !context.sameGroup(pos, next)) {
                    connections.add(new Connection(pos, next));
                }
            }
        }
        return connections;
    }

    /**
     * Output container next to a cluster crafter: the player puts a chest, depot, basin or similar inventory beside
     * the group, and the provider pulls the product out of it. The sink is the crafter whose arrow can point at that
     * container. A crafter that already holds a recipe cell is preferred so the chain stays short.
     */
    private record Sink(BlockPos crafter, BlockPos container) {
    }

    private static List<Sink> discoverSinks(PlanningContext context, Cluster cluster, BlockPos providerPos,
            Set<BlockPos> blocked) {
        context.sinkScans++;
        List<Sink> sinks = new ArrayList<>();
        for (BlockPos pos : cluster.positions()) {
            BlockState state = context.crafter(pos).getBlockState();
            for (Direction direction : Direction.values()) {
                BlockPos target = pos.relative(direction);
                boolean isProvider = target.equals(providerPos);
                if (cluster.coords().containsKey(target) || (!isProvider && blocked.contains(target))
                        || context.crafter(target) != null
                        || InventoryAccess.itemHandler(context.level, target, direction.getOpposite()) == null
                        || pointingToward(state, direction) == null) {
                    continue;
                }
                sinks.add(new Sink(pos, target));
            }
        }
        return List.copyOf(sinks);
    }

    @Nullable
    private static Sink chooseSink(List<Sink> sinks, Set<BlockPos> recipe, BlockPos providerPos) {
        Sink direct = null;
        Sink container = null;
        Sink directFallback = null;
        Sink containerFallback = null;
        for (Sink sink : sinks) {
            boolean holdsRecipe = recipe.contains(sink.crafter());
            if (sink.container().equals(providerPos)) {
                if (holdsRecipe && direct == null) {
                    direct = sink;
                } else if (directFallback == null) {
                    directFallback = sink;
                }
            } else if (holdsRecipe && container == null) {
                container = sink;
            } else if (containerFallback == null) {
                containerFallback = sink;
            }
        }
        if (direct != null) {
            return direct;
        }
        if (directFallback != null) {
            return directFallback;
        }
        return container != null ? container : containerFallback;
    }

    /**
     * Spanning tree rooted at the sink that reaches every recipe cell, each child pointing at its parent. Walking
     * breadth-first and then pruning branches that lead to no recipe cell keeps only the empty links that are
     * needed to connect the recipe to the sink. Returns {@code null} when some recipe cell cannot be reached.
     */
    @Nullable
    private static Map<BlockPos, BlockPos> chainTree(Cluster cluster, BlockPos sink, Set<BlockPos> recipe) {
        Map<BlockPos, BlockPos> parent = new LinkedHashMap<>();
        List<BlockPos> pending = new ArrayList<>();
        parent.put(sink, null);
        pending.add(sink);
        for (int index = 0; index < pending.size(); index++) {
            BlockPos current = pending.get(index);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (parent.containsKey(next) || cluster.coords().get(next) == null) {
                    continue;
                }
                parent.put(next, current);
                pending.add(next);
            }
        }
        for (BlockPos cell : recipe) {
            if (!parent.containsKey(cell)) {
                return null;
            }
        }
        // Keep the sink, every recipe cell, and the ancestors that link them to the sink.
        Set<BlockPos> keep = new HashSet<>();
        keep.add(sink);
        for (BlockPos cell : recipe) {
            for (BlockPos cursor = cell; cursor != null && keep.add(cursor); cursor = parent.get(cursor)) {
                // keep.add already marks the cursor; the loop only walks up until a known ancestor is reached.
            }
        }
        Map<BlockPos, BlockPos> pruned = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockPos> entry : parent.entrySet()) {
            if (keep.contains(entry.getKey())) {
                pruned.put(entry.getKey(), entry.getValue());
            }
        }
        return pruned;
    }


    /**
     * Picks the arrow target for a crafter that has no fixed successor inside the recipe. Keeps the current
     * target when it is free, otherwise prefers a side with an inventory, then any free side.
     */
    @Nullable
    private static Direction freeTarget(Level level, BlockPos pos, BlockState state, Set<BlockPos> matched) {
        Direction current = target(state);
        if (!matched.contains(pos.relative(current))) {
            return current;
        }
        Direction fallback = null;
        for (Pointing pointing : Pointing.values()) {
            Direction direction = target(state.setValue(MechanicalCrafterBlock.POINTING, pointing));
            if (matched.contains(pos.relative(direction))) {
                continue;
            }
            if (InventoryAccess.itemHandler(level, pos.relative(direction), null) != null) {
                return direction;
            }
            if (fallback == null) {
                fallback = direction;
            }
        }
        return fallback;
    }

    /**
     * Looks the pointing up by its target instead of inverting {@code pointingFromFacing}, so the arrow always
     * points where {@link MechanicalCrafterBlock#getTargetDirection(BlockState)} will read it back.
     */
    @Nullable
    private static Pointing pointingToward(BlockState state, Direction target) {
        for (Pointing pointing : Pointing.values()) {
            if (target(state.setValue(MechanicalCrafterBlock.POINTING, pointing)) == target) {
                return pointing;
            }
        }
        return null;
    }

    private static Direction target(BlockState state) {
        return MechanicalCrafterBlock.getTargetDirection(state);
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

    private static int axisDelta(BlockPos origin, BlockPos pos, Direction axis) {
        BlockPos step = BlockPos.ZERO.relative(axis);
        return (pos.getX() - origin.getX()) * step.getX()
                + (pos.getY() - origin.getY()) * step.getY()
                + (pos.getZ() - origin.getZ()) * step.getZ();
    }

    /**
     * Every recipe layout that matches the pattern's materials and the requested output. Recipes that do not fit
     * the physical crafters are rejected later, so several candidates can coexist here.
     */
    private static List<List<PatternCell>> recipeLayouts(PlanningContext context, AEProcessingPattern pattern,
            AEKey primaryOutput, long outputAmount) {
        Level level = context.level;
        boolean regular = AllConfigs.server().recipes.allowRegularCraftingInCrafter.get();
        RecipeKey key = new RecipeKey(pattern.getDefinition(), primaryOutput, outputAmount, regular);
        List<List<PatternCell>> cached = cachedRecipes(level, key);
        if (cached != null) {
            return cached;
        }
        context.recipeScans++;
        List<List<PatternCell>> layouts = findRecipeLayouts(level, pattern, primaryOutput, outputAmount, regular)
                .stream().map(List::copyOf).toList();
        cacheRecipes(level, key, layouts);
        return layouts;
    }

    private static List<List<PatternCell>> findRecipeLayouts(Level level, AEProcessingPattern pattern, AEKey primaryOutput,
            long outputAmount, boolean regular) {
        if (!(primaryOutput instanceof AEItemKey primaryItem) || outputAmount <= 0) {
            return List.of();
        }
        Map<AEItemKey, Integer> pool = new HashMap<>();
        for (GenericStack stack : pattern.getSparseInputs()) {
            if (stack == null || stack.amount() <= 0 || stack.what() == null) {
                continue;
            }
            if (!(stack.what() instanceof AEItemKey key) || stack.amount() > Integer.MAX_VALUE) {
                return List.of();
            }
            pool.merge(key, (int) stack.amount(), Integer::sum);
        }
        if (pool.isEmpty()) {
            return List.of();
        }
        List<List<PatternCell>> layouts = new ArrayList<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.MECHANICAL_CRAFTING.getType())) {
            Object value = holder.value();
            if (value instanceof ShapedRecipe recipe) {
                addLayout(layouts, level, recipe, primaryItem, outputAmount, pool);
            }
        }
        if (regular) {
            for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                if (holder.value() instanceof ShapedRecipe recipe
                        && matchesRecipeOutput(level, recipe, primaryItem, outputAmount)
                        && !AllRecipeTypes.shouldIgnoreInAutomation(holder)) {
                    addLayout(layouts, level, recipe, primaryItem, outputAmount, pool);
                }
            }
        }
        return layouts;
    }

    private static boolean matchesRecipeOutput(Level level, ShapedRecipe recipe, AEItemKey output, long amount) {
        ItemStack result = recipe.getResultItem(level.registryAccess());
        return !result.isEmpty() && result.getCount() == amount && output.equals(AEItemKey.of(result));
    }

    private static void addLayout(List<List<PatternCell>> layouts, Level level, ShapedRecipe recipe,
            AEItemKey primaryItem, long outputAmount, Map<AEItemKey, Integer> pool) {
        if (!matchesRecipeOutput(level, recipe, primaryItem, outputAmount)) {
            return;
        }
        List<PatternCell> cells = assignRecipe(recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(), pool);
        if (cells != null && !layouts.contains(cells)) {
            layouts.add(cells);
        }
    }

    /**
     * Assigns each pattern item to one non-empty ingredient slot, backtracking when a greedy choice blocks a later
     * slot. Returns the layout normalized to the slots' bounding box, or {@code null} if the pool does not fit.
     */
    @Nullable
    private static List<PatternCell> assignRecipe(int width, int height, List<Ingredient> ingredients,
            Map<AEItemKey, Integer> pool) {
        List<Slot> slots = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Ingredient ingredient = ingredients.get(x + y * width);
                if (!ingredient.isEmpty()) {
                    slots.add(new Slot(x, y, ingredient));
                }
            }
        }
        int total = 0;
        for (int count : pool.values()) {
            total += count;
        }
        if (slots.isEmpty() || total != slots.size()) {
            return null;
        }
        List<AEItemKey> keys = new ArrayList<>(pool.keySet());
        Map<AEItemKey, Integer> remaining = new HashMap<>(pool);
        AEItemKey[] chosen = new AEItemKey[slots.size()];
        if (!assign(0, slots, keys, remaining, chosen)) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (Slot slot : slots) {
            minX = Math.min(minX, slot.x());
            minY = Math.min(minY, slot.y());
        }
        List<PatternCell> cells = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            cells.add(new PatternCell(slot.x() - minX, slot.y() - minY, new GenericStack(chosen[i], 1)));
        }
        return cells;
    }

    private static boolean assign(int index, List<Slot> slots, List<AEItemKey> keys, Map<AEItemKey, Integer> remaining,
            AEItemKey[] chosen) {
        if (index == slots.size()) {
            return true;
        }
        Slot slot = slots.get(index);
        for (AEItemKey key : keys) {
            int count = remaining.getOrDefault(key, 0);
            if (count <= 0 || !slot.ingredient().test(key.toStack(1))) {
                continue;
            }
            remaining.put(key, count - 1);
            chosen[index] = key;
            if (assign(index + 1, slots, keys, remaining, chosen)) {
                return true;
            }
            remaining.put(key, count);
        }
        return false;
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
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MechanicalCrafterBlockEntity crafter)) {
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

    /**
     * Same-facing crafters connected to the front crafter, addressed by their coordinates in the front crafter's plane.
     * {@link #positions()} is ordered nearest-first, because it is built breadth-first from the front crafter.
     */
    private record Cluster(Map<Long, BlockPos> byCoord, Map<BlockPos, int[]> coords, List<BlockPos> positions) {
        static Cluster around(Level level, BlockPos origin, Set<BlockPos> claimed) {
            BlockState sample = crafter(level, origin).getBlockState();
            Direction facing = sample.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            Direction plusX = target(sample.setValue(MechanicalCrafterBlock.POINTING, Pointing.LEFT)).getOpposite();
            Direction plusY = target(sample.setValue(MechanicalCrafterBlock.POINTING, Pointing.UP));

            Map<Long, BlockPos> byCoord = new HashMap<>();
            Map<BlockPos, int[]> coords = new HashMap<>();
            List<BlockPos> positions = new ArrayList<>();
            Set<BlockPos> visited = new HashSet<>();
            BlockPos start = origin.immutable();
            byCoord.put(pack(0, 0), start);
            coords.put(start, new int[] { 0, 0 });
            positions.add(start);
            visited.add(start);
            for (int index = 0; index < positions.size(); index++) {
                BlockPos current = positions.get(index);
                for (Direction direction : Direction.values()) {
                    if (direction.getAxis() == facing.getAxis()) {
                        continue;
                    }
                    BlockPos next = current.relative(direction);
                    if (visited.contains(next)) {
                        continue;
                    }
                    MechanicalCrafterBlockEntity neighbor = crafter(level, next);
                    if (neighbor == null || neighbor.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING) != facing
                            || claimed.contains(next)) {
                        continue;
                    }
                    visited.add(next);
                    int x = axisDelta(start, next, plusX);
                    int y = axisDelta(start, next, plusY);
                    byCoord.put(pack(x, y), next.immutable());
                    coords.put(next.immutable(), new int[] { x, y });
                    positions.add(next.immutable());
                }
            }
            return new Cluster(byCoord, coords, List.copyOf(positions));
        }

        @Nullable
        BlockPos at(int x, int y) {
            return byCoord.get(pack(x, y));
        }

        int x(BlockPos pos) {
            return coords.get(pos)[0];
        }

        int y(BlockPos pos) {
            return coords.get(pos)[1];
        }
    }

    private record Slot(int x, int y, Ingredient ingredient) {
    }

    private record PatternCell(int x, int y, GenericStack stack) {
    }

    private record Placed(BlockPos pos, ItemStack stack) {
    }

    private record PointingChange(BlockPos pos, Pointing pointing) {
    }

    private record Connection(BlockPos left, BlockPos right) {
    }

    private record Arrangement(BlockPos sink, BlockPos outputPos, List<Placed> cells, List<PointingChange> pointings,
            List<Connection> connections, boolean busy, boolean emptyLinks, int emptyCount) {
        int changes() {
            return pointings.size() + connections.size();
        }

        /** Fewer empty links first (shortest chain), then fewer arrow and group edits. */
        boolean isBetterThan(Arrangement other) {
            if (emptyCount != other.emptyCount) {
                return emptyCount < other.emptyCount;
            }
            return changes() < other.changes();
        }
    }

    private record Result(@Nullable Arrangement arrangement, @Nullable String failure) {
        static Result ok(Arrangement arrangement) {
            return new Result(arrangement, null);
        }

        static Result failed(String failure) {
            return new Result(null, failure);
        }
    }

    /** Scoped to one read-only search; never reused after ArrangeAction mutates the world. */
    private static final class PlanningContext {
        private final Level level;
        private final Map<BlockPos, MechanicalCrafterBlockEntity> crafters = new HashMap<>();
        private final Map<BlockPos, Boolean> crafting = new HashMap<>();
        private final Map<Connection, Boolean> connectable = new HashMap<>();
        private final GroupIdentityCache<MechanicalCrafterBlockEntity.Inventory> groups = new GroupIdentityCache<>();
        private int placements;
        private int sinkScans;
        private int recipeScans;
        private boolean reusedLayout;
        private List<Sink> sinks = List.of();

        private PlanningContext(Level level) {
            this.level = level;
        }

        @Nullable
        private MechanicalCrafterBlockEntity crafter(BlockPos pos) {
            if (!crafters.containsKey(pos)) {
                crafters.put(pos, MechanicalCrafterPlans.crafter(level, pos));
            }
            return crafters.get(pos);
        }

        private boolean isCrafting(MechanicalCrafterBlockEntity crafter) {
            return crafting.computeIfAbsent(crafter.getBlockPos(), pos -> MechanicalCrafterPlans.isCrafting(level, crafter));
        }

        private boolean canConnect(BlockPos left, BlockPos right, Direction direction) {
            return connectable.computeIfAbsent(new Connection(left, right),
                    pair -> ConnectedInputHandler.shouldConnect(level, left, direction, direction));
        }

        private boolean sameGroup(BlockPos left, BlockPos right) {
            var leftCrafter = crafter(left);
            var rightCrafter = crafter(right);
            if (leftCrafter == null || rightCrafter == null) {
                return false;
            }
            var leftGroup = groups.representative(leftCrafter.getInventory(),
                    () -> leftCrafter.getInput().getInventories(level, left));
            var rightGroup = groups.representative(rightCrafter.getInventory(),
                    () -> rightCrafter.getInput().getInventories(level, right));
            return leftGroup != null && leftGroup == rightGroup;
        }
    }

    private record LayoutKey(BlockPos origin, BlockPos provider, AEItemKey definition, AEKey output, long amount) {
    }

    private record RecipeKey(AEItemKey definition, AEKey output, long amount, boolean regularCrafting) {
    }

    private record FailureState(BlockState originState, float speed, Set<BlockPos> claimed,
            Set<BlockPos> claimedContainers, boolean regularCrafting) {
    }

    @Nullable
    private static synchronized String recentFailure(Level level, LayoutKey key, FailureState state) {
        var failures = FAILURES.get(level);
        return failures == null ? null : failures.get(key, state, level.getGameTime());
    }

    private static synchronized void rememberFailure(Level level, LayoutKey key, FailureState state, String reason) {
        FAILURES.computeIfAbsent(level, unused -> new PlanningFailureCache<>(MAX_LAYOUT_HINTS_PER_LEVEL, FAILURE_RETRY_TICKS))
                .remember(key, state, level.getGameTime(), reason);
    }

    @Nullable
    private static synchronized List<List<PatternCell>> cachedRecipes(Level level, RecipeKey key) {
        var layouts = RECIPE_LAYOUTS.get(level);
        return layouts == null ? null : layouts.get(key);
    }

    private static synchronized void cacheRecipes(Level level, RecipeKey key, List<List<PatternCell>> layouts) {
        var cached = RECIPE_LAYOUTS.computeIfAbsent(level, unused -> new LinkedHashMap<>(16, 0.75f, true));
        cached.put(key, layouts);
        if (cached.size() > MAX_LAYOUT_HINTS_PER_LEVEL) {
            cached.remove(cached.keySet().iterator().next());
        }
    }

    private record LayoutHint(List<PatternCell> layout, List<BlockPos> positions, BlockPos sink, BlockPos outputPos) {
    }

    @Nullable
    private static synchronized LayoutHint layoutHint(Level level, LayoutKey key) {
        var hints = SUCCESSFUL_LAYOUTS.get(level);
        return hints == null ? null : hints.get(key);
    }

    private static synchronized void rememberLayout(Level level, LayoutKey key, LayoutHint hint) {
        var hints = SUCCESSFUL_LAYOUTS.computeIfAbsent(level, unused -> new LinkedHashMap<>(16, 0.75f, true));
        hints.put(key, hint);
        if (hints.size() > MAX_LAYOUT_HINTS_PER_LEVEL) {
            hints.remove(hints.keySet().iterator().next());
        }
    }

    private static synchronized void forgetLayout(Level level, LayoutKey key, LayoutHint hint) {
        var hints = SUCCESSFUL_LAYOUTS.get(level);
        if (hints != null) {
            hints.remove(key, hint);
        }
    }

    private record RememberLayoutAction(Level level, LayoutKey key, LayoutHint hint) implements SupplyAction {
        @Override
        public boolean perform(boolean simulate) {
            if (!simulate) {
                rememberLayout(level, key, hint);
            }
            return true;
        }

        @Override
        public CompensationResult compensate() {
            forgetLayout(level, key, hint);
            return CompensationResult.complete();
        }
    }

    /** Fails every simulate, so a plan over a busy chain reports "wait" instead of inserting into it. */
    private static final class BusyAction implements SupplyAction {
        @Override
        public boolean perform(boolean simulate) {
            return false;
        }

        @Override
        public String describe() {
            return "crafter busy";
        }
    }

    /**
     * Rewrites the arrows and group links of a planned chain. Simulation only checks that the crafters are still
     * there; execution records the previous state so a later failure can restore the arrows.
     */
    private static final class ArrangeAction implements SupplyAction {
        private final Level level;
        private final List<PointingChange> pointings;
        private final List<Connection> connections;
        private final List<PointingChange> previousPointings = new ArrayList<>();
        private final List<Connection> linked = new ArrayList<>();

        private ArrangeAction(Level level, List<PointingChange> pointings, List<Connection> connections) {
            this.level = level;
            this.pointings = List.copyOf(pointings);
            this.connections = List.copyOf(connections);
        }

        @Override
        public boolean perform(boolean simulate) {
            for (PointingChange change : pointings) {
                if (crafter(level, change.pos()) == null) {
                    return false;
                }
            }
            for (Connection connection : connections) {
                if (crafter(level, connection.left()) == null || crafter(level, connection.right()) == null) {
                    return false;
                }
            }
            if (simulate) {
                return true;
            }
            for (PointingChange change : pointings) {
                MechanicalCrafterBlockEntity crafter = crafter(level, change.pos());
                if (crafter == null) {
                    return false;
                }
                BlockState state = crafter.getBlockState();
                previousPointings.add(new PointingChange(change.pos(), state.getValue(MechanicalCrafterBlock.POINTING)));
                if (state.getValue(MechanicalCrafterBlock.POINTING) != change.pointing()) {
                    KineticBlockEntity.switchToBlockState(level, change.pos(),
                            state.setValue(MechanicalCrafterBlock.POINTING, change.pointing()));
                }
            }
            for (Connection connection : connections) {
                if (sameGroup(level, connection.left(), connection.right())) {
                    continue;
                }
                ConnectedInputHandler.toggleConnection(level, connection.left(), connection.right());
                linked.add(connection);
            }
            return true;
        }

        @Override
        public CompensationResult compensate() {
            for (int i = linked.size() - 1; i >= 0; i--) {
                Connection connection = linked.get(i);
                ConnectedInputHandler.toggleConnection(level, connection.left(), connection.right());
            }
            linked.clear();
            boolean restored = true;
            for (int i = previousPointings.size() - 1; i >= 0; i--) {
                PointingChange previous = previousPointings.get(i);
                MechanicalCrafterBlockEntity crafter = crafter(level, previous.pos());
                if (crafter == null) {
                    restored = false;
                    continue;
                }
                BlockState state = crafter.getBlockState();
                if (state.getValue(MechanicalCrafterBlock.POINTING) != previous.pointing()) {
                    KineticBlockEntity.switchToBlockState(level, previous.pos(),
                            state.setValue(MechanicalCrafterBlock.POINTING, previous.pointing()));
                }
            }
            previousPointings.clear();
            return restored ? CompensationResult.complete()
                    : CompensationResult.failed("crafter missing while restoring arrows");
        }

        @Override
        public String describe() {
            return "arrange crafters arrows=" + pointings.size() + " links=" + connections.size();
        }
    }

    /**
     * Starts the chain with Create's own powered-start entry point, which ignores crafters that hold nothing. Nothing
     * has to be undone: if a later step fails, the inserted items are removed before the crafter ever begins.
     */
    private static final class PoweredStartAction implements SupplyAction {
        private final Level level;
        private final BlockPos pos;

        private PoweredStartAction(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        @Override
        public boolean perform(boolean simulate) {
            MechanicalCrafterBlockEntity crafter = crafter(level, pos);
            if (crafter == null) {
                return false;
            }
            if (!simulate) {
                crafter.checkCompletedRecipe(true);
            }
            return true;
        }

        @Override
        public String describe() {
            return "powered start@" + pos.toShortString();
        }
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
