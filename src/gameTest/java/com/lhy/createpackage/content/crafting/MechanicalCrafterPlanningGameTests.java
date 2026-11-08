package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.CreatePackage;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createpackage")
@PrefixGameTestTemplate(false)
public final class MechanicalCrafterPlanningGameTests {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 1);
    private static final BlockPos CHEST = new BlockPos(3, 1, 1);
    private static final BlockPos PROVIDER = new BlockPos(3, 3, 3);

    @GameTest(template = "empty")
    public static void deduplicatesAndRevalidatesSuccessfulLayouts(GameTestHelper helper) {
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(true);
        try {
            List<BlockPos> positions = prepare(helper);
            var first = plan(helper);
            helper.assertTrue(first.simulate(), "Fresh three-by-three layout is usable");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().placements() == 1,
                    "Nine anchor pairs producing the same placement are evaluated once");
            plan(helper);
            helper.assertTrue(!MechanicalCrafterPlans.lastDebugSearch().reusedLayout(), "Simulation does not cache success");
            helper.assertTrue(first.execute().success(), "Arrow arrangement and input insertion succeed");
            resetMachines(helper, positions);
            var reused = plan(helper);
            helper.assertTrue(reused.simulate(), "Revalidated layout remains usable");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().reusedLayout(), "Completed execution seeds the fast path");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().groupLookups() == 1,
                    "All nine linked inventories require only one full group lookup");

            long start = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                helper.assertTrue(plan(helper).simulate(), "Repeated cached plan remains valid");
            }
            CreatePackage.LOGGER.info("[Crafter planning test] 50 validated cached plans took {} ms",
                    (System.nanoTime() - start) / 1_000_000.0);
            benchmarkIfAvailable(helper, "3x3", 50);

            MechanicalCrafterBlockEntity occupied = (MechanicalCrafterBlockEntity) helper.getBlockEntity(ORIGIN);
            occupied.getInventory().setStackInSlot(0, Items.DIRT.getDefaultInstance());
            helper.assertTrue(!plan(helper).simulate(), "Cached layout never bypasses occupied inputs");
            helper.assertTrue(!MechanicalCrafterPlans.lastDebugSearch().reusedLayout(), "Busy layout takes the fallback path");
            resetMachines(helper, positions);
            MechanicalCrafterPlans.useClaimed(Set.of(helper.absolutePos(ORIGIN)), Set.of());
            helper.assertTrue(!plan(helper).simulate(), "A cached layout cannot claim a crafter owned by another job");
            MechanicalCrafterPlans.useClaimed(null, Set.of(helper.absolutePos(CHEST)));
            helper.assertTrue(!plan(helper).simulate(), "A cached layout cannot take another job's output container");
            MechanicalCrafterPlans.useClaimed(null, null);
            helper.setBlock(CHEST, Blocks.AIR);
            helper.assertTrue(!plan(helper).simulate(), "Removing the output invalidates the cached placement");
            helper.setBlock(CHEST, Blocks.CHEST);
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
            helper.assertTrue(!plan(helper).simulate(), "Removing a recipe cell invalidates the cached placement");
            helper.succeed();
        } finally {
            MechanicalCrafterPlans.clearPattern();
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }

    @GameTest(template = "empty")
    public static void failedExecutionRollsBackAndDoesNotCache(GameTestHelper helper) {
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(true);
        try {
            List<BlockPos> positions = prepare(helper);
            var planned = plan(helper);
            helper.assertTrue(planned.simulate(), "Plan is valid before the simulated race");
            // Make the first insertion fail after the arrangement action has run.
            for (BlockPos pos : positions) {
                MechanicalCrafterBlockEntity crafter = (MechanicalCrafterBlockEntity) helper.getBlockEntity(pos);
                crafter.getInventory().setStackInSlot(0, Items.DIRT.getDefaultInstance());
            }
            helper.assertTrue(!planned.execute().success(), "Execution detects changed inventory");
            for (BlockPos pos : positions) {
                helper.assertTrue(helper.getBlockState(pos).getValue(MechanicalCrafterBlock.POINTING) == Pointing.UP,
                        "Failed execution restores each original arrow");
            }
            resetMachines(helper, positions);
            helper.assertTrue(plan(helper).simulate(), "Plan can be rebuilt after rollback");
            helper.assertTrue(!MechanicalCrafterPlans.lastDebugSearch().reusedLayout(), "Failed execution never enters success cache");
            helper.succeed();
        } finally {
            MechanicalCrafterPlans.clearPattern();
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }

    @GameTest(template = "empty")
    public static void cachedPlacementFallsBackToAnotherFreeGroup(GameTestHelper helper) {
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(true);
        try {
            List<BlockPos> positions = prepare(helper);
            helper.setBlock(new BlockPos(0, 3, 1), Blocks.CHEST);
            List<GenericStack> sparse = new ArrayList<>(java.util.Collections.nCopies(6, null));
            sparse.set(0, new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1));
            sparse.set(3, new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1));
            var output = new GenericStack(AEItemKey.of(Items.STICK), 4);
            MechanicalCrafterPlans.usePattern(new AEProcessingPattern(AEItemKey.of(
                    encodeProcessingPattern(sparse, List.of(output)))));
            var first = MechanicalCrafterPlans.plan(helper.getLevel(), helper.absolutePos(ORIGIN),
                    helper.absolutePos(PROVIDER), output.what(), output.amount(), List.of(output));
            helper.assertTrue(first.simulate() && first.execute().success(), "First small recipe is dispatched");
            var claimed = MechanicalCrafterPlans.chainCells(helper.getLevel(), first.machinePos());
            resetMachines(helper, positions);
            MechanicalCrafterPlans.useClaimed(claimed, Set.of(first.outputPos()));
            var second = MechanicalCrafterPlans.plan(helper.getLevel(), helper.absolutePos(ORIGIN),
                    helper.absolutePos(PROVIDER), output.what(), output.amount(), List.of(output));
            helper.assertTrue(second.simulate(), "A free alternative remains dispatchable when the cached group is claimed");
            helper.assertTrue(!MechanicalCrafterPlans.lastDebugSearch().reusedLayout(), "Claimed cache hint falls back to search");
            helper.assertTrue(!second.outputPos().equals(first.outputPos()), "The second group uses another output container");
            helper.assertTrue(second.execute().success(), "Alternative group executes successfully");
            var secondCells = MechanicalCrafterPlans.chainCells(helper.getLevel(), second.machinePos());
            helper.assertTrue(java.util.Collections.disjoint(claimed, secondCells), "Concurrent groups share no crafters");
            helper.succeed();
        } finally {
            MechanicalCrafterPlans.clearPattern();
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }

    @GameTest(template = "empty_large")
    public static void largerClusterReusesOnePlacement(GameTestHelper helper) {
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(true);
        try {
            List<BlockPos> positions = prepare(helper, 5, 5, new BlockPos(5, 2, 1));
            var first = plan(helper);
            helper.assertTrue(first.simulate(), "Five-by-five cluster accepts the three-by-three recipe");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().placements() == 9,
                    "Nine distinct placements are checked, not eighty-one duplicate anchor pairs");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().sinkScans() == 1,
                    "All nine placements share one physical outlet scan");
            helper.assertTrue(first.execute().success(), "Large cluster's selected arrangement executes");
            resetMachines(helper, positions);
            helper.assertTrue(plan(helper).simulate(), "Large cluster's successful layout remains usable");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().reusedLayout()
                    && MechanicalCrafterPlans.lastDebugSearch().placements() == 1, "Warm search validates one placement");
            benchmarkIfAvailable(helper, "5x5", 20);
            helper.succeed();
        } finally {
            MechanicalCrafterPlans.clearPattern();
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }

    private static List<BlockPos> prepare(GameTestHelper helper) {
        return prepare(helper, 3, 3, CHEST);
    }

    @GameTest(template = "empty_large")
    public static void missingOutputBacksOffAndRecovers(GameTestHelper helper) {
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(true);
        try {
            BlockPos output = new BlockPos(5, 2, 1);
            List<BlockPos> positions = prepare(helper, 5, 5, output);
            helper.setBlock(output, Blocks.AIR);
            MechanicalCrafterPlans.clearPlanningCaches();
            var failed = plan(helper);
            helper.assertTrue("crafter_output_not_provider".equals(failed.statusKey()), "Missing outlet rejects the plan");
            var first = MechanicalCrafterPlans.lastDebugSearch();
            helper.assertTrue(first.sinkScans() == 1 && first.recipeScans() == 0 && first.placements() == 0,
                    "Missing outlet is discovered before recipe lookup or layout enumeration");
            for (int i = 0; i < 100; i++) {
                helper.assertTrue(!plan(helper).isSuccess(), "Repeated failed request remains rejected");
                var hit = MechanicalCrafterPlans.lastDebugSearch();
                helper.assertTrue(hit.failureCached() && hit.sinkScans() == 0 && hit.recipeScans() == 0,
                        "Repeated requests do no expensive work during the cooldown");
            }
            MechanicalCrafterPlans.plan(helper.getLevel(), helper.absolutePos(ORIGIN), helper.absolutePos(PROVIDER),
                    AEItemKey.of(Items.IRON_BLOCK), 2, List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 2)));
            helper.assertTrue(!MechanicalCrafterPlans.lastDebugSearch().failureCached(), "Different pattern output amount is independent");
            benchmarkFailureIfAvailable(helper, "missing_output");
            helper.setBlock(output, Blocks.CHEST);
            resetMachines(helper, positions);
            helper.assertTrue(plan(helper).simulate(), "Neighbor update clears failure and repaired outlet works immediately");
            helper.succeed();
        } finally {
            MechanicalCrafterPlans.clearPattern();
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }

    @GameTest(template = "empty_large")
    public static void stoppedMachineBacksOffAndRecipesReload(GameTestHelper helper) {
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(true);
        try {
            List<BlockPos> positions = prepare(helper, 5, 5, new BlockPos(5, 2, 1));
            for (BlockPos pos : positions) {
                ((MechanicalCrafterBlockEntity) helper.getBlockEntity(pos)).setSpeed(0);
            }
            MechanicalCrafterPlans.clearPlanningCaches();
            helper.assertTrue("crafter_not_powered".equals(plan(helper).statusKey()), "Unpowered outlet is rejected");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().recipeScans() == 0, "No recipe search is needed while stopped");
            plan(helper);
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().failureCached(), "Stopped machine backs off");
            benchmarkFailureIfAvailable(helper, "not_powered");
            resetMachines(helper, positions);
            helper.assertTrue(plan(helper).simulate(), "Power change invalidates the negative cache immediately");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().recipeScans() == 1, "First powered request resolves recipes");
            helper.assertTrue(plan(helper).simulate(), "Second powered request remains valid");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().recipeScans() == 0, "Repeated request reuses immutable recipe layouts");
            MechanicalCrafterPlans.clearPlanningCaches();
            helper.assertTrue(plan(helper).simulate(), "Planning works after the reload invalidation hook");
            helper.assertTrue(MechanicalCrafterPlans.lastDebugSearch().recipeScans() == 1, "Reload invalidates cached recipe layouts");
            helper.succeed();
        } finally {
            MechanicalCrafterPlans.clearPattern();
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }

    private static void benchmarkFailureIfAvailable(GameTestHelper helper, String reason) {
        Class<?> baseline;
        try {
            baseline = Class.forName("com.lhy.createpackage.content.crafting.LegacyMechanicalCrafterPlans");
        } catch (ClassNotFoundException absent) {
            return;
        }
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(false);
        try {
            var inputs = java.util.Collections.nCopies(9, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
            var outputs = List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1));
            baseline.getMethod("usePattern", appeng.api.crafting.IPatternDetails.class).invoke(null,
                    new AEProcessingPattern(AEItemKey.of(encodeProcessingPattern(inputs, outputs))));
            var oldPlan = baseline.getMethod("plan", net.minecraft.world.level.Level.class, BlockPos.class, BlockPos.class,
                    appeng.api.stacks.AEKey.class, long.class, List.class);
            Object[] args = {helper.getLevel(), helper.absolutePos(ORIGIN), helper.absolutePos(PROVIDER),
                    AEItemKey.of(Items.IRON_BLOCK), 1L, outputs};
            for (int i = 0; i < 10; i++) {
                oldPlan.invoke(null, args);
                plan(helper);
            }
            for (int round = 0; round < 3; round++) {
                long oldNanos = 0, newNanos = 0;
                for (int i = 0; i < 50; i++) {
                    long start = System.nanoTime();
                    helper.assertTrue(!((MachinePlanFactory.Plan) oldPlan.invoke(null, args)).isSuccess(), "Baseline reproduces failure");
                    oldNanos += System.nanoTime() - start;
                    start = System.nanoTime();
                    helper.assertTrue(!plan(helper).isSuccess(), "New planner rejects the same invalid target");
                    newNanos += System.nanoTime() - start;
                }
                CreatePackage.LOGGER.info("[Crafter failure benchmark] reason={} round={} calls=50 previousMs={} optimizedMs={}",
                        reason, round, oldNanos / 1_000_000.0, newNanos / 1_000_000.0);
            }
            baseline.getMethod("clearPattern").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not run failure benchmark", failure);
        } finally {
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }

    private static List<BlockPos> prepare(GameTestHelper helper, int width, int height, BlockPos chest) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = new BlockPos(x, y, 1);
                helper.setBlock(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                        new net.minecraft.resources.ResourceLocation("create", "mechanical_crafter")).defaultBlockState()
                        .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(MechanicalCrafterBlock.POINTING, Pointing.UP));
                positions.add(pos);
            }
        }
        helper.setBlock(chest, Blocks.CHEST);
        resetMachines(helper, positions);
        var input = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        ItemStack encoded = encodeProcessingPattern(java.util.Collections.nCopies(9, input),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)));
        MechanicalCrafterPlans.usePattern(new AEProcessingPattern(AEItemKey.of(encoded)));
        MechanicalCrafterPlans.useClaimed(null, null);
        return positions;
    }

    private static void resetMachines(GameTestHelper helper, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            MechanicalCrafterBlockEntity crafter = (MechanicalCrafterBlockEntity) helper.getBlockEntity(pos);
            CompoundTag tag = crafter.saveWithoutMetadata();
            tag.getCompound("Inventory").put("Items", new net.minecraft.nbt.ListTag());
            tag.remove("GroupedItems");
            tag.putString("Phase", "IDLE");
            tag.putFloat("Speed", 64);
            crafter.load(tag);
            crafter.setSpeed(64);
        }
    }

    // AE2 15 (Forge 1.20.1) encodes processing patterns from arrays, including null sparse slots.
    private static ItemStack encodeProcessingPattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        return PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),
                outputs.toArray(GenericStack[]::new));
    }

    private static MachinePlanFactory.Plan plan(GameTestHelper helper) {
        return MechanicalCrafterPlans.plan(helper.getLevel(), helper.absolutePos(ORIGIN), helper.absolutePos(PROVIDER),
                AEItemKey.of(Items.IRON_BLOCK), 1, List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)));
    }

    /** Optional baseline generated from the pre-change Git revision, never shipped in the mod or required by tests. */
    private static void benchmarkIfAvailable(GameTestHelper helper, String cluster, int calls) {
        Class<?> baseline;
        try {
            baseline = Class.forName("com.lhy.createpackage.content.crafting.LegacyMechanicalCrafterPlans");
        } catch (ClassNotFoundException absent) {
            return;
        }
        boolean debug = Config.ENABLE_DEBUG_LOGGING.get();
        Config.ENABLE_DEBUG_LOGGING.set(false);
        try {
            var inputs = java.util.Collections.nCopies(9, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
            var outputs = List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1));
            var pattern = new AEProcessingPattern(AEItemKey.of(encodeProcessingPattern(inputs, outputs)));
            baseline.getMethod("usePattern", appeng.api.crafting.IPatternDetails.class).invoke(null, pattern);
            Class<?>[] signature = {net.minecraft.world.level.Level.class, BlockPos.class, BlockPos.class,
                    appeng.api.stacks.AEKey.class, long.class, List.class};
            var oldPlan = baseline.getMethod("plan", signature);
            var newPlan = MechanicalCrafterPlans.class.getMethod("plan", signature);
            Object[] args = {helper.getLevel(), helper.absolutePos(ORIGIN), helper.absolutePos(PROVIDER),
                    AEItemKey.of(Items.IRON_BLOCK), 1L, outputs};
            // Alternate identical calls to warm up both paths; timing is observational, never a flaky pass criterion.
            for (int i = 0; i < Math.min(40, calls); i++) {
                oldPlan.invoke(null, args);
                newPlan.invoke(null, args);
            }
            for (int round = 0; round < 3; round++) {
                long oldNanos = 0, newNanos = 0;
                for (int i = 0; i < calls; i++) {
                    long start = System.nanoTime();
                    helper.assertTrue(((MachinePlanFactory.Plan) oldPlan.invoke(null, args)).simulate(), "Baseline remains usable");
                    oldNanos += System.nanoTime() - start;
                    start = System.nanoTime();
                    helper.assertTrue(((MachinePlanFactory.Plan) newPlan.invoke(null, args)).simulate(), "Optimized plan remains usable");
                    newNanos += System.nanoTime() - start;
                }
                CreatePackage.LOGGER.info("[Crafter planning benchmark] cluster={} round={} calls={} baselineMs={} optimizedMs={}",
                        cluster, round, calls, oldNanos / 1_000_000.0, newNanos / 1_000_000.0);
            }
            baseline.getMethod("clearPattern").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not run the optional baseline benchmark", failure);
        } finally {
            Config.ENABLE_DEBUG_LOGGING.set(debug);
        }
    }
}
