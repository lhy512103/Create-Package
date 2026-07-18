package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Builds single-machine supply plans for the Kinetic Pattern Provider.
 *
 * <p>Machine-specific rules are registered as {@link MachineAdapter}s so new Create / addon machines
 * can be added without growing another giant switch statement in the block entity.
 */
public final class MachinePlanFactory {
    public interface MachineAdapter {
        boolean matches(@Nullable ResourceLocation id);

        @Nullable
        Plan plan(Context context);
    }

    public record Context(
            Level level,
            BlockPos machinePos,
            ResourceLocation machineId,
            AEKey primaryOutput,
            long outputAmount,
            List<GenericStack> patternOutputs,
            List<GenericStack> inputs,
            List<GenericStack> originalInputs) {
    }

    public record Plan(
            BlockPos machinePos,
            BlockPos outputPos,
            String machineRole,
            ActionList actions,
            List<GenericStack> patternOutputs,
            List<GenericStack> refillInputs,
            boolean collectUnexpectedOutputs,
            @Nullable String statusKey,
            @Nullable String failureDetail) {

        public static Plan failure(String statusKey) {
            return failure(statusKey, null);
        }

        public static Plan failure(String statusKey, @Nullable String failureDetail) {
            return new Plan(BlockPos.ZERO, BlockPos.ZERO, "generic", new ActionList(List.of()), List.of(), List.of(),
                    false, statusKey, failureDetail);
        }

        public boolean isSuccess() {
            return statusKey == null || "working".equals(statusKey);
        }

        public boolean simulate() {
            return isSuccess() && actions.simulate();
        }

        public ActionList.Result execute() {
            if (!isSuccess()) {
                return ActionList.Result.failed(-1, statusKey == null ? "invalid" : statusKey);
            }
            return actions.execute();
        }
    }

    private static final List<MachineAdapter> ADAPTERS = List.of(
            adapter(CreateMachineIds::isDeployer, MachinePlanFactory::planDeployer),
            adapter(CreateMachineIds::isSpout, MachinePlanFactory::planSpout),
            adapter(CreateMachineIds::isMechanicalPress, MachinePlanFactory::planPress),
            adapter(CreateMachineIds::isMechanicalMixer, MachinePlanFactory::planMixer),
            adapter(CreateMachineIds::isCrushingWheel, (ctx, role) -> Plan.failure("face_crushing_controller")),
            adapter(CreateMachineIds::isCrushingController, MachinePlanFactory::planCrushing),
            adapter(CreateMachineIds::isMechanicalSaw, MachinePlanFactory::planSaw),
            adapter(CreateMachineIds::isMillstone, MachinePlanFactory::planMillstone),
            adapter(id -> true, MachinePlanFactory::planGeneric));

    private MachinePlanFactory() {
    }

    public static Plan create(Level level, BlockPos machinePos, AEKey primaryOutput, long outputAmount,
            List<GenericStack> patternOutputs, List<GenericStack> inputs) {
        if (level == null || !level.isLoaded(machinePos)) {
            return Plan.failure("missing_machine");
        }
        ResourceLocation machineId = CreateMachineIds.idOf(level, machinePos);
        if (machineId == null) {
            return Plan.failure("unsupported_machine");
        }
        List<GenericStack> workingInputs = new ArrayList<>(inputs);
        Context context = new Context(level, machinePos, machineId, primaryOutput, outputAmount,
                new ArrayList<>(patternOutputs), workingInputs, PatternStacks.copyStacks(inputs));
        for (MachineAdapter adapter : ADAPTERS) {
            if (adapter.matches(machineId)) {
                Plan plan = adapter.plan(context);
                return plan == null ? Plan.failure("unsupported_machine") : plan;
            }
        }
        return Plan.failure("unsupported_machine");
    }

    private static MachineAdapter adapter(Predicate<ResourceLocation> match,
            BiFunction<Context, String, Plan> planner) {
        return new MachineAdapter() {
            @Override
            public boolean matches(@Nullable ResourceLocation id) {
                return id != null && match.test(id);
            }

            @Override
            public Plan plan(Context context) {
                return planner.apply(context, CreateMachineIds.kineticRoleKey(context.machineId()));
            }
        };
    }

    private static Plan planDeployer(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack processed = PatternStacks.takeFirstItem(remaining);
        ItemStack held = PatternStacks.takeFirstItem(remaining);
        if (processed.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        if (held.isEmpty()) {
            return Plan.failure("missing_deployer_item");
        }
        BlockPos itemTarget = deployerItemTarget(ctx.level(), ctx.machinePos());
        List<SupplyAction> actions = new ArrayList<>();
        actions.add(SupplyActions.insertItem(ctx.level(), itemTarget, processed));
        actions.add(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), held));
        return success(ctx, itemTarget, role, actions, false, remaining);
    }

    private static Plan planSpout(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack processed = PatternStacks.takeFirstItem(remaining);
        FluidStack fluid = PatternStacks.takeFirstFluid(remaining);
        if (processed.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        if (fluid.isEmpty()) {
            return Plan.failure("missing_spout_fluid");
        }
        BlockPos itemTarget = spoutItemTarget(ctx.machinePos());
        List<SupplyAction> actions = new ArrayList<>();
        actions.add(SupplyActions.insertItem(ctx.level(), itemTarget, processed));
        actions.add(SupplyActions.insertFluid(ctx.level(), ctx.machinePos(), fluid));
        return success(ctx, itemTarget, role, actions, false, remaining);
    }

    private static Plan planPress(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        BlockPos itemTarget = pressItemTarget(ctx.machinePos());
        List<SupplyAction> actions = new ArrayList<>();
        if (isBasin(ctx.level(), itemTarget)) {
            if (!addBasinInputs(actions, remaining, ctx.level(), itemTarget)) {
                return Plan.failure("missing_basin_input");
            }
        } else {
            ItemStack input = PatternStacks.takeFirstItem(remaining);
            if (input.isEmpty()) {
                return Plan.failure("missing_item_input");
            }
            actions.add(SupplyActions.insertItem(ctx.level(), itemTarget, input));
        }
        return success(ctx, itemTarget, role, actions, false, remaining);
    }

    private static Plan planMixer(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        BlockPos itemTarget = basinOperatingTarget(ctx.machinePos());
        if (!isBasin(ctx.level(), itemTarget)) {
            return Plan.failure("missing_basin");
        }
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), itemTarget)) {
            return Plan.failure("missing_basin_input");
        }
        return success(ctx, itemTarget, role, actions, false, remaining);
    }

    private static Plan planCrushing(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        BlockPos crushingOutput = crushingOutputTarget(ctx.level(), ctx.machinePos());
        if (crushingOutput == null || !canRecoverCrushingOutput(ctx.level(), ctx.machinePos(), crushingOutput)) {
            return Plan.failure("missing_crushing_output");
        }
        List<SupplyAction> actions = List.of(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), input));
        return success(ctx, crushingOutput, role, new ArrayList<>(actions), true, remaining);
    }

    private static Plan planSaw(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        Direction outputDirection = sawOutputDirection(ctx.level(), ctx.machinePos());
        if (outputDirection == null) {
            return Plan.failure(sawFailureStatus(ctx.level(), ctx.machinePos()));
        }
        BlockPos sawOutput = ctx.machinePos().relative(outputDirection);
        if (!canRecoverSawOutput(ctx.level(), sawOutput, outputDirection)) {
            return Plan.failure("missing_saw_output");
        }
        List<SupplyAction> actions = new ArrayList<>();
        if (ctx.primaryOutput() instanceof AEItemKey itemPrimary) {
            ItemStack filter = itemPrimary.toStack(1);
            if (!canAccessSawFilter(ctx.level(), ctx.machinePos(), filter)) {
                return Plan.failure("missing_saw_filter");
            }
            actions.add(new SawFilterAction(ctx.level(), ctx.machinePos(), filter));
        }
        actions.add(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), input));
        return success(ctx, sawOutput, role, actions, true, remaining);
    }

    private static Plan planMillstone(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        List<SupplyAction> actions = List.of(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), input));
        return success(ctx, ctx.machinePos(), role, new ArrayList<>(actions), true, remaining);
    }

    private static Plan planGeneric(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        List<SupplyAction> actions = List.of(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), input));
        return success(ctx, ctx.machinePos(), role, new ArrayList<>(actions), false, remaining);
    }

    private static Plan success(Context ctx, BlockPos outputPos, String role, List<SupplyAction> actions,
            boolean collectUnexpected, List<GenericStack> remaining) {
        if (!remaining.isEmpty()) {
            return Plan.failure("unused_inputs", String.valueOf(remaining.size()));
        }
        List<GenericStack> outputs = new ArrayList<>(ctx.patternOutputs());
        if (outputs.stream().noneMatch(output -> output.what().equals(ctx.primaryOutput()))) {
            outputs.add(new GenericStack(ctx.primaryOutput(), ctx.outputAmount()));
        }
        return new Plan(ctx.machinePos(), outputPos, role, new ActionList(actions), outputs,
                PatternStacks.copyStacks(ctx.originalInputs()), collectUnexpected, "working", null);
    }

    private static boolean addBasinInputs(List<SupplyAction> actions, List<GenericStack> remaining, Level level,
            BlockPos basinPos) {
        boolean supplied = false;
        for (int i = 0; i < remaining.size();) {
            GenericStack input = remaining.get(i);
            if (input.what() instanceof AEItemKey itemKey && input.amount() > 0) {
                actions.add(SupplyActions.insertItem(level, basinPos,
                        itemKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE))));
                supplied = true;
                remaining.remove(i);
                continue;
            }
            if (input.what() instanceof AEFluidKey fluidKey && input.amount() > 0) {
                actions.add(SupplyActions.insertFluid(level, basinPos,
                        fluidKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE))));
                supplied = true;
                remaining.remove(i);
                continue;
            }
            i++;
        }
        return supplied;
    }

    public static BlockPos deployerItemTarget(Level level, BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            return machinePos.below(2);
        }
        BlockState state = level.getBlockState(machinePos);
        if (state.hasProperty(DirectionalKineticBlock.FACING)) {
            return machinePos.relative(state.getValue(DirectionalKineticBlock.FACING), 2);
        }
        return machinePos.below(2);
    }

    public static BlockPos spoutItemTarget(BlockPos machinePos) {
        return machinePos.below(2);
    }

    public static BlockPos pressItemTarget(BlockPos machinePos) {
        return machinePos.below(2);
    }

    public static BlockPos basinOperatingTarget(BlockPos machinePos) {
        return machinePos.below(2);
    }

    public static boolean isBasin(Level level, BlockPos pos) {
        return CreateMachineIds.isBasin(CreateMachineIds.idOf(level, pos));
    }

    @Nullable
    public static BlockPos crushingOutputTarget(Level level, BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            return null;
        }
        BlockState state = level.getBlockState(machinePos);
        if (!state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) {
            return null;
        }
        Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
        if (facing == Direction.UP) {
            return null;
        }
        return machinePos.below().relative(facing, facing.getAxis() == Direction.Axis.Y ? 0 : 1);
    }

    public static boolean canRecoverCrushingOutput(Level level, BlockPos machinePos, BlockPos outputPos) {
        if (level == null || !level.isLoaded(machinePos) || !level.isLoaded(outputPos)) {
            return false;
        }
        BlockState state = level.getBlockState(machinePos);
        if (!state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) {
            return false;
        }
        Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
        DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, outputPos, DirectBeltInputBehaviour.TYPE);
        return behaviour != null && behaviour.canInsertFromSide(facing)
                && InventoryAccess.itemHandler(level, outputPos) != null;
    }

    @Nullable
    public static Direction sawOutputDirection(Level level, BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            return null;
        }
        BlockState state = level.getBlockState(machinePos);
        if (!state.hasProperty(SawBlock.FACING) || state.getValue(SawBlock.FACING) != Direction.UP) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(machinePos);
        if (!(blockEntity instanceof KineticBlockEntity kinetic) || kinetic.getSpeed() == 0) {
            return null;
        }
        if (!state.hasProperty(SawBlock.AXIS_ALONG_FIRST_COORDINATE)) {
            return null;
        }
        boolean alongX = !state.getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE);
        int offset = kinetic.getSpeed() < 0 ? -1 : 1;
        if (alongX) {
            return offset > 0 ? Direction.EAST : Direction.WEST;
        }
        return offset > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public static String sawFailureStatus(Level level, BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            return "missing_machine";
        }
        BlockState state = level.getBlockState(machinePos);
        if (!state.hasProperty(SawBlock.FACING) || state.getValue(SawBlock.FACING) != Direction.UP) {
            return "saw_must_face_up";
        }
        BlockEntity blockEntity = level.getBlockEntity(machinePos);
        if (!(blockEntity instanceof KineticBlockEntity kinetic) || kinetic.getSpeed() == 0) {
            return "saw_not_powered";
        }
        if (!state.hasProperty(SawBlock.AXIS_ALONG_FIRST_COORDINATE)) {
            return "unsupported_machine";
        }
        return "unsupported_machine";
    }

    public static boolean canRecoverSawOutput(Level level, BlockPos outputPos, Direction outputDirection) {
        if (level == null || !level.isLoaded(outputPos)) {
            return false;
        }
        DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, outputPos, DirectBeltInputBehaviour.TYPE);
        return behaviour != null && behaviour.canInsertFromSide(outputDirection)
                && InventoryAccess.itemHandler(level, outputPos) != null;
    }

    public static boolean canAccessSawFilter(Level level, BlockPos sawPos, ItemStack filterStack) {
        return level != null && level.isLoaded(sawPos) && !filterStack.isEmpty()
                && BlockEntityBehaviour.get(level, sawPos, FilteringBehaviour.TYPE) != null;
    }

    public static boolean setSawFilter(Level level, BlockPos sawPos, ItemStack filterStack) {
        if (level == null || !level.isLoaded(sawPos) || filterStack.isEmpty()) {
            return false;
        }
        FilteringBehaviour filtering = BlockEntityBehaviour.get(level, sawPos, FilteringBehaviour.TYPE);
        return filtering != null && filtering.setFilter(filterStack);
    }

    private static final class SawFilterAction implements SupplyAction {
        private final Level level;
        private final BlockPos target;
        private final ItemStack filterStack;
        private ItemStack previousFilter = ItemStack.EMPTY;
        private boolean applied;

        private SawFilterAction(Level level, BlockPos target, ItemStack filterStack) {
            this.level = level;
            this.target = target.immutable();
            this.filterStack = filterStack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            if (level == null || !level.isLoaded(target) || filterStack.isEmpty()) {
                return false;
            }
            FilteringBehaviour filtering = BlockEntityBehaviour.get(level, target, FilteringBehaviour.TYPE);
            if (filtering == null) {
                return false;
            }
            if (simulate) {
                return true;
            }
            previousFilter = filtering.getFilter().copy();
            applied = setSawFilter(level, target, filterStack);
            return applied;
        }

        @Override
        public void compensate() {
            if (!applied) {
                return;
            }
            setSawFilter(level, target, previousFilter);
            applied = false;
        }

        @Override
        public String describe() {
            return "saw-filter@" + target.toShortString();
        }
    }
}
