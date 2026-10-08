package com.lhy.createpackage.content.crafting;

import java.lang.reflect.Method;
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
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

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
            List<MachineEndpoint> outputEndpoints,
            String machineRole,
            ActionList actions,
            List<GenericStack> patternOutputs,
            List<GenericStack> refillInputs,
            boolean collectUnexpectedOutputs,
            @Nullable String statusKey,
            @Nullable String failureDetail) {

        public Plan {
            outputEndpoints = List.copyOf(outputEndpoints);
        }

        public static Plan failure(String statusKey) {
            return failure(statusKey, null);
        }

        public static Plan failure(String statusKey, @Nullable String failureDetail) {
            return new Plan(BlockPos.ZERO, BlockPos.ZERO, List.of(), "generic", new ActionList(List.of()), List.of(),
                    List.of(), false, statusKey, failureDetail);
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
            adapter(CreateMachineIds::isRollingMill, MachinePlanFactory::planSingleItemMachine),
            adapter(CreateMachineIds::isTeslaCoil, MachinePlanFactory::planTeslaCoil),
            adapter(CreateMachineIds::isBasinLid, MachinePlanFactory::planBasinLid),
            adapter(CreateMachineIds::isBulkFermenter, MachinePlanFactory::planDirectMixedMachine),
            adapter(CreateMachineIds::isDistillationTank,
                    (ctx, role) -> Plan.failure("unsupported_split_fluid_outputs")),
            // Vintage Improvements
            adapter(CreateMachineIds::isHelveHammer, MachinePlanFactory::planHelveHammer),
            adapter(CreateMachineIds::isCurvingPress, MachinePlanFactory::planCurvingPress),
            adapter(CreateMachineIds::isSpringCoiling, MachinePlanFactory::planSpringCoiling),
            adapter(CreateMachineIds::isCompressor, MachinePlanFactory::planCompressor),
            adapter(CreateMachineIds::isCentrifuge, MachinePlanFactory::planCentrifuge),
            adapter(CreateMachineIds::isVibratingTable, MachinePlanFactory::planMillstone),
            adapter(CreateMachineIds::isGrinderBelt, MachinePlanFactory::planGrinderBelt),
            adapter(CreateMachineIds::isLathe, MachinePlanFactory::planSingleItemMachine),
            adapter(CreateMachineIds::isLaser, MachinePlanFactory::planLaser),
            // Create Metallurgy
            adapter(CreateMachineIds::isFoundryMixer, MachinePlanFactory::planFoundryMixer),
            adapter(CreateMachineIds::isFoundryLid, MachinePlanFactory::planFoundryLid),
            adapter(CreateMachineIds::isFoundryBasin, MachinePlanFactory::planDirectMixedMachine),
            adapter(CreateMachineIds::isCastingTable, MachinePlanFactory::planCastingTable),
            adapter(CreateMachineIds::isMechanicalBeltGrinder, (ctx, role) -> Plan.failure("unsupported_machine")),
            adapter(CreateMachineIds::isIndustrialCrucible, MachinePlanFactory::planIndustrialCrucible),
            // Create Bitterballen
            adapter(CreateMachineIds::isMechanicalFryer, MachinePlanFactory::planMechanicalFryer),
            // Create Enchantment Industry: the drain owns inventory; the wheel above is required to process.
            adapter(CreateMachineIds::isMechanicalGrindstone,
                    (ctx, role) -> Plan.failure("missing_grindstone_drain")),
            adapter(CreateMachineIds::isGrindstoneDrain, MachinePlanFactory::planGrindstoneDrain));

    private MachinePlanFactory() {
    }

    public static Plan create(Level level, BlockPos machinePos, AEKey primaryOutput, long outputAmount,
            List<GenericStack> patternOutputs, List<GenericStack> inputs) {
        if (level == null || !level.isLoaded(machinePos)) {
            return Plan.failure("missing_machine");
        }
        machinePos = CreateMachineIds.normalizeKineticMachinePos(level, machinePos);
        ResourceLocation machineId = CreateMachineIds.idOf(level, machinePos);
        if (machineId == null) {
            return Plan.failure("unsupported_machine");
        }
        if (CreateMachineIds.isMechanicalCrafter(machineId)) {
            return MechanicalCrafterPlans.plan(level, machinePos, primaryOutput, outputAmount, patternOutputs);
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
        BlockPos itemTarget = deployerItemTarget(ctx.level(), ctx.machinePos());
        List<SupplyAction> actions = new ArrayList<>();
        actions.add(SupplyActions.insertItem(ctx.level(), itemTarget, processed));
        if (held.isEmpty()) {
            // Non-consumed tools (inscriber presses, sandpaper, etc.) are often pre-placed in the
            // deployer and omitted from the AE2 processing pattern so the network never requests them.
            if (!InventoryAccess.hasAnyItem(ctx.level(), ctx.machinePos())) {
                return Plan.failure("missing_deployer_item");
            }
        } else {
            actions.add(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), held));
        }
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

    private static Plan planCentrifuge(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), ctx.machinePos())) {
            return Plan.failure("missing_machine_input");
        }
        List<MachineEndpoint> outputs = List.of(
                MachineEndpoint.smartItemOutput(ctx.machinePos()),
                MachineEndpoint.smartFluidOutput(ctx.machinePos()));
        return success(ctx, ctx.machinePos(), outputs, role, actions, true, remaining);
    }

    private static Plan planGrindstoneDrain(Context ctx, String role) {
        if (!CreateMachineIds.isMechanicalGrindstone(CreateMachineIds.idOf(ctx.level(), ctx.machinePos().above()))) {
            return Plan.failure("missing_grindstone");
        }
        Direction outputDirection = grindstoneOutputDirection(ctx.level(), ctx.machinePos());
        if (outputDirection == null) {
            return Plan.failure("grindstone_not_powered");
        }
        List<GenericStack> remaining = ctx.inputs();
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), ctx.machinePos())) {
            return Plan.failure("missing_machine_input");
        }
        BlockPos outputPos = ctx.machinePos().relative(outputDirection);
        return success(ctx, outputPos, beltOrDroppedItemOutput(ctx.level(), outputPos, outputDirection), role, actions,
                true, remaining);
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

    private static Plan planSingleItemMachine(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        List<SupplyAction> actions = List.of(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), input));
        return success(ctx, ctx.machinePos(), role, new ArrayList<>(actions), false, remaining);
    }

    private static Plan planHelveHammer(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        List<SupplyAction> actions = new ArrayList<>();
        for (int i = 0; i < remaining.size();) {
            GenericStack input = remaining.get(i);
            if (!(input.what() instanceof AEItemKey itemKey) || input.amount() <= 0) {
                i++;
                continue;
            }
            actions.add(SupplyActions.insertItem(ctx.level(), MachineEndpoint.itemInput(ctx.machinePos(), null),
                    itemKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE))));
            remaining.remove(i);
        }
        if (actions.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        return success(ctx, ctx.machinePos(), List.of(MachineEndpoint.itemOutput(ctx.machinePos(), null)), role,
                actions, false, remaining);
    }

    private static Plan planTeslaCoil(Context ctx, String role) {
        BlockState state = ctx.level().getBlockState(ctx.machinePos());
        if (!state.hasProperty(BlockStateProperties.FACING)
                || state.getValue(BlockStateProperties.FACING) != Direction.UP) {
            return Plan.failure("tesla_coil_must_face_up");
        }
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        BlockPos itemTarget = ctx.machinePos().below(2);
        List<SupplyAction> actions = List.of(SupplyActions.insertItem(ctx.level(), itemTarget, input));
        return success(ctx, itemTarget, role, new ArrayList<>(actions), false, remaining);
    }

    private static Plan planBasinLid(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        BlockPos basinPos = ctx.machinePos().below();
        if (!isBasin(ctx.level(), basinPos)) {
            return Plan.failure("missing_basin");
        }
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), basinPos)) {
            return Plan.failure("missing_basin_input");
        }
        return success(ctx, basinPos, role, actions, false, remaining);
    }

    private static Plan planMechanicalFryer(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack food = PatternStacks.takeFirstItem(remaining);
        FluidStack oil = PatternStacks.takeFirstFluid(remaining);
        if (food.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        if (oil.isEmpty()) {
            return Plan.failure("missing_fryer_oil");
        }
        if (!remaining.isEmpty()) {
            return Plan.failure("unused_inputs", String.valueOf(remaining.size()));
        }
        BlockPos basinPos = ctx.machinePos().below();
        if (!isBasin(ctx.level(), basinPos)) {
            return Plan.failure("missing_basin");
        }
        List<SupplyAction> actions = List.of(
                SupplyActions.insertItem(ctx.level(), MachineEndpoint.itemInput(ctx.machinePos(), null), food),
                SupplyActions.insertFluid(ctx.level(), MachineEndpoint.fluidInput(basinPos, null), oil));
        return success(ctx, ctx.machinePos(), role, new ArrayList<>(actions), false, remaining);
    }

    private static Plan planDirectMixedMachine(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), ctx.machinePos())) {
            return Plan.failure("missing_machine_input");
        }
        return success(ctx, ctx.machinePos(), role, actions, false, remaining);
    }

    private static Plan planFoundryMixer(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        BlockPos basinPos = basinOperatingTarget(ctx.machinePos());
        if (!isFoundryBasin(ctx.level(), basinPos)) {
            return Plan.failure("missing_basin");
        }
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), basinPos)) {
            return Plan.failure("missing_basin_input");
        }
        return success(ctx, basinPos, role, actions, false, remaining);
    }

    private static Plan planFoundryLid(Context ctx, String role) {
        BlockPos basinPos = ctx.machinePos().below();
        if (!isFoundryBasin(ctx.level(), basinPos)) {
            return Plan.failure("missing_basin");
        }
        List<GenericStack> remaining = ctx.inputs();
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), basinPos)) {
            return Plan.failure("missing_basin_input");
        }
        return success(ctx, basinPos, role, actions, false, remaining);
    }

    private static Plan planIndustrialCrucible(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), ctx.machinePos())) {
            return Plan.failure("missing_machine_input");
        }
        List<MachineEndpoint> outputs = List.of(
                MachineEndpoint.crucibleItemOutput(ctx.machinePos()),
                MachineEndpoint.crucibleFluidOutput(ctx.machinePos()));
        return success(ctx, ctx.machinePos(), outputs, role, actions, false, remaining);
    }

    private static Plan planCastingTable(Context ctx, String role) {
        // Casting table accepts fluid poured into the mold (optional item) placed on the table.
        // Supply all item and fluid inputs directly into the table.
        List<GenericStack> remaining = ctx.inputs();
        List<SupplyAction> actions = new ArrayList<>();
        if (!addBasinInputs(actions, remaining, ctx.level(), ctx.machinePos())) {
            return Plan.failure("missing_machine_input");
        }
        return success(ctx, ctx.machinePos(), role, actions, false, remaining);
    }

    private static Plan planCurvingPress(Context ctx, String role) {
        // Curving press works like mechanical press but outputs to a recoverable position below(2)
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
        // Use collectUnexpected=true to recover actual outputs
        return success(ctx, itemTarget, role, actions, true, remaining);
    }

    private static Plan planSpringCoiling(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        Direction outputDirection = horizontalFacing(ctx.level(), ctx.machinePos());
        if (outputDirection == null) {
            return Plan.failure("unsupported_machine");
        }
        BlockPos outputPos = ctx.machinePos().relative(outputDirection);
        List<SupplyAction> actions = List.of(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), input));
        return success(ctx, outputPos, beltOrDroppedItemOutput(ctx.level(), outputPos, outputDirection), role,
                new ArrayList<>(actions), false, remaining);
    }

    private static Plan planLaser(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        BlockPos carrierPos = ctx.machinePos().below(2);
        BlockPos entityPos = ctx.machinePos().below();
        List<SupplyAction> actions;
        List<MachineEndpoint> outputs;
        BlockPos outputPos;
        if (CreateMachineIds.isDepotCarrier(CreateMachineIds.idOf(ctx.level(), carrierPos))) {
            actions = List.of(SupplyActions.insertItem(ctx.level(), carrierPos, input));
            outputs = List.of(MachineEndpoint.itemOutput(carrierPos, null));
            outputPos = carrierPos;
        } else {
            actions = List.of(SupplyActions.spawnItemEntity(ctx.level(), entityPos, input));
            outputs = List.of(MachineEndpoint.droppedItemOutput(entityPos));
            outputPos = entityPos;
        }
        return success(ctx, outputPos, outputs, role, new ArrayList<>(actions), false, remaining);
    }

    private static final ResourceLocation PRESSURIZING_RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath(
            "vintageimprovements", "pressurizing");

    private static Plan planCompressor(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        BlockPos basinPos = ctx.machinePos().below(2);
        if (!isBasin(ctx.level(), basinPos)) {
            return Plan.failure("missing_basin");
        }
        CompressorRecipeLayout recipe = findPressurizingRecipe(ctx);
        if (recipe == null) {
            // Both the Basin and Vacuum Chamber accept fluid capabilities, so guessing here can consume a
            // valid pattern into the wrong tank. Require the actual pressurizing recipe to choose a route.
            return Plan.failure("missing_compressor_recipe");
        }
        List<SupplyAction> actions = new ArrayList<>();
        if (!addVacuumChamberInputs(actions, remaining, ctx.level(), basinPos, ctx.machinePos(), recipe)) {
            return Plan.failure("missing_machine_input");
        }
        List<MachineEndpoint> outputs = recipe.outputEndpoints(basinPos, ctx.machinePos());
        if (outputs.isEmpty()) {
            return Plan.failure("missing_output");
        }
        BlockPos primaryOutputPos = recipe.isSecondaryFluidResult(ctx.primaryOutput()) ? ctx.machinePos() : basinPos;
        return success(ctx, primaryOutputPos, outputs, role, actions, false, remaining);
    }

    private static Plan planGrinderBelt(Context ctx, String role) {
        List<GenericStack> remaining = ctx.inputs();
        ItemStack input = PatternStacks.takeFirstItem(remaining);
        if (input.isEmpty()) {
            return Plan.failure("missing_item_input");
        }
        Direction outputDirection = grinderOutputDirection(ctx.level(), ctx.machinePos());
        if (outputDirection == null) {
            return Plan.failure("grinder_belt_not_powered");
        }
        BlockPos outputPos = ctx.machinePos().relative(outputDirection);
        List<SupplyAction> actions = new ArrayList<>();
        if (ctx.primaryOutput() instanceof AEItemKey itemPrimary) {
            ItemStack filter = itemPrimary.toStack(1);
            if (!canAccessSawFilter(ctx.level(), ctx.machinePos(), filter)) {
                return Plan.failure("missing_grinder_filter");
            }
            actions.add(new SawFilterAction(ctx.level(), ctx.machinePos(), filter));
        }
        actions.add(SupplyActions.insertItem(ctx.level(), ctx.machinePos(), input));
        return success(ctx, outputPos, beltOrDroppedItemOutput(ctx.level(), outputPos, outputDirection), role, actions,
                false, remaining);
    }

    private static Plan success(Context ctx, BlockPos outputPos, String role, List<SupplyAction> actions,
            boolean collectUnexpected, List<GenericStack> remaining) {
        return success(ctx, outputPos, outputEndpoints(ctx.level(), outputPos), role, actions, collectUnexpected, remaining);
    }

    private static Plan success(Context ctx, BlockPos outputPos, List<MachineEndpoint> endpoints, String role,
            List<SupplyAction> actions, boolean collectUnexpected, List<GenericStack> remaining) {
        if (!remaining.isEmpty()) {
            return Plan.failure("unused_inputs", String.valueOf(remaining.size()));
        }
        List<GenericStack> outputs = new ArrayList<>(ctx.patternOutputs());
        if (outputs.stream().noneMatch(output -> output.what().equals(ctx.primaryOutput()))) {
            outputs.add(new GenericStack(ctx.primaryOutput(), ctx.outputAmount()));
        }
        return new Plan(ctx.machinePos(), outputPos, endpoints, role, new ActionList(actions), outputs,
                PatternStacks.copyStacks(ctx.originalInputs()), collectUnexpected, "working", null);
    }

    private static List<MachineEndpoint> outputEndpoints(Level level, BlockPos outputPos) {
        List<MachineEndpoint> endpoints = new ArrayList<>(2);
        if (InventoryAccess.itemHandler(level, outputPos, null) != null) {
            endpoints.add(MachineEndpoint.itemOutput(outputPos, null));
        }
        if (InventoryAccess.fluidHandler(level, outputPos, null) != null) {
            endpoints.add(MachineEndpoint.fluidOutput(outputPos, null));
        }
        return endpoints;
    }

    private static boolean addVacuumChamberInputs(List<SupplyAction> actions, List<GenericStack> remaining, Level level,
            BlockPos basinPos, BlockPos chamberPos, CompressorRecipeLayout recipe) {
        boolean supplied = false;
        for (int i = 0; i < remaining.size();) {
            GenericStack input = remaining.get(i);
            if (input.what() instanceof AEItemKey itemKey && input.amount() > 0) {
                actions.add(SupplyActions.insertItem(level, MachineEndpoint.itemInput(basinPos, null),
                        itemKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE))));
            } else if (input.what() instanceof AEFluidKey fluidKey && input.amount() > 0) {
                FluidStack fluid = fluidKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE));
                FluidRoute route = recipe.routeFluidInput(fluid);
                if (route == null || route.basinAmount() + route.secondaryAmount() != fluid.getAmount()) {
                    // Never drop a pattern input silently: an unroutable fluid must fail the whole plan.
                    return false;
                }
                if (route.basinAmount() > 0) {
                    actions.add(SupplyActions.insertFluid(level, MachineEndpoint.fluidInput(basinPos, null),
                            fluid.copyWithAmount(route.basinAmount())));
                }
                if (route.secondaryAmount() > 0) {
                    actions.add(SupplyActions.insertFluid(level, MachineEndpoint.smartFluidInput(chamberPos),
                            fluid.copyWithAmount(route.secondaryAmount())));
                }
            } else {
                i++;
                continue;
            }
            supplied = true;
            remaining.remove(i);
        }
        return supplied;
    }

    @Nullable
    private static CompressorRecipeLayout findPressurizingRecipe(Context ctx) {
        CompressorRecipeLayout match = null;
        for (RecipeHolder<?> holder : ctx.level().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof BasinRecipe basinRecipe)
                    || !PRESSURIZING_RECIPE_TYPE.equals(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE
                            .getKey(recipe.getType()))
                    || !matchesCompressorPattern(ctx, basinRecipe)) {
                continue;
            }
            CompressorRecipeLayout layout = CompressorRecipeLayout.from(basinRecipe);
            if (layout == null || (match != null && !match.equals(layout))) {
                return null;
            }
            match = layout;
        }
        return match;
    }

    private static boolean matchesCompressorPattern(Context ctx, BasinRecipe recipe) {
        long multiplier = compressorRecipeMultiplier(ctx, recipe);
        if (multiplier <= 0) {
            return false;
        }
        List<GenericStack> unmatched = PatternStacks.copyStacks(ctx.originalInputs());
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (PatternStacks.takeMatchingItem(unmatched, ingredient, multiplier).isEmpty()) {
                return false;
            }
        }
        for (SizedFluidIngredient ingredient : recipe.getFluidIngredients()) {
            long amount;
            try {
                amount = Math.multiplyExact((long) ingredient.amount(), multiplier);
            } catch (ArithmeticException ignored) {
                return false;
            }
            if (PatternStacks.takeMatchingFluid(unmatched, ingredient, amount).isEmpty()) {
                return false;
            }
        }
        return unmatched.isEmpty();
    }

    private static long compressorRecipeMultiplier(Context ctx, BasinRecipe recipe) {
        long recipeOutputAmount = 0;
        if (ctx.primaryOutput() instanceof AEFluidKey fluidOutput) {
            for (FluidStack result : recipe.getFluidResults()) {
                if (fluidOutput.equals(AEFluidKey.of(result))) {
                    recipeOutputAmount = PatternStacks.safeAdd(recipeOutputAmount, result.getAmount());
                }
            }
        } else if (ctx.primaryOutput() instanceof AEItemKey itemOutput) {
            for (var result : recipe.getRollableResults()) {
                ItemStack stack = result.getStack();
                if (itemOutput.equals(AEItemKey.of(stack))) {
                    recipeOutputAmount = PatternStacks.safeAdd(recipeOutputAmount, stack.getCount());
                }
            }
        }
        if (recipeOutputAmount <= 0 || ctx.outputAmount() <= 0 || ctx.outputAmount() % recipeOutputAmount != 0) {
            return -1;
        }
        return ctx.outputAmount() / recipeOutputAmount;
    }

    /**
     * Fluid routing table for a Vintage Improvements pressurizing recipe.
     *
     * <p>A pressurizing recipe spans two tanks: the Basin below and the Vacuum Chamber itself. The
     * recipe marks one fluid ingredient index and one fluid result index as "secondary", meaning those
     * belong to the chamber instead of the basin. Both the supply side and the collection side must
     * follow that split, otherwise a freshly inserted input fluid can be drained back out of the basin
     * as if it were a product.
     */
    private record CompressorRecipeLayout(List<SizedFluidIngredient> fluidInputs, int secondaryFluidInput,
            List<FluidStack> fluidResults, int secondaryFluidResult, boolean hasItemResults) {
        @Nullable
        static CompressorRecipeLayout from(BasinRecipe recipe) {
            try {
                Method input = recipe.getClass().getMethod("getSecondaryFluidInputs");
                Method output = recipe.getClass().getMethod("getSecondaryFluidResults");
                return new CompressorRecipeLayout(List.copyOf(recipe.getFluidIngredients()),
                        (Integer) input.invoke(recipe), List.copyOf(recipe.getFluidResults()),
                        (Integer) output.invoke(recipe), !recipe.getRollableResults().isEmpty());
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                return null;
            }
        }

        private boolean isSecondaryIndex(int index) {
            return index == secondaryFluidInput;
        }

        /**
         * Splits one pattern fluid input between the basin tank and the chamber tank following the
         * recipe layout. Ingredient identity is matched on fluid type only; the sized amount describes
         * how much each tank wants, so it must not gate whether the ingredient matches at all.
         */
        @Nullable
        FluidRoute routeFluidInput(FluidStack input) {
            long secondaryRequired = 0;
            long basinRequired = 0;
            for (int i = 0; i < fluidInputs.size(); i++) {
                SizedFluidIngredient ingredient = fluidInputs.get(i);
                if (!ingredient.ingredient().test(input)) {
                    continue;
                }
                if (isSecondaryIndex(i)) {
                    secondaryRequired = PatternStacks.safeAdd(secondaryRequired, ingredient.amount());
                } else {
                    basinRequired = PatternStacks.safeAdd(basinRequired, ingredient.amount());
                }
            }
            long totalRequired = PatternStacks.safeAdd(basinRequired, secondaryRequired);
            if (totalRequired <= 0 || input.getAmount() % totalRequired != 0) {
                return null;
            }
            long multiplier = input.getAmount() / totalRequired;
            long basinAmount = basinRequired * multiplier;
            long secondaryAmount = secondaryRequired * multiplier;
            if (basinAmount > Integer.MAX_VALUE || secondaryAmount > Integer.MAX_VALUE) {
                return null;
            }
            return new FluidRoute((int) basinAmount, (int) secondaryAmount);
        }

        private boolean hasSecondaryFluidResult() {
            return secondaryFluidResult >= 0 && secondaryFluidResult < fluidResults.size();
        }

        private boolean hasBasinFluidResult() {
            for (int i = 0; i < fluidResults.size(); i++) {
                if (!(hasSecondaryFluidResult() && i == secondaryFluidResult)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Only expose the tanks this recipe actually produces into. Attaching the basin fluid tank for a
         * recipe whose only fluid product lives in the chamber would let collection steal the basin's
         * unconsumed input fluid.
         */
        List<MachineEndpoint> outputEndpoints(BlockPos basinPos, BlockPos chamberPos) {
            List<MachineEndpoint> endpoints = new ArrayList<>(3);
            if (hasItemResults) {
                endpoints.add(MachineEndpoint.itemOutput(basinPos, null));
            }
            if (hasBasinFluidResult()) {
                endpoints.add(MachineEndpoint.smartFluidOutput(basinPos));
            }
            if (hasSecondaryFluidResult()) {
                endpoints.add(MachineEndpoint.smartFluidOutput(chamberPos));
            }
            return List.copyOf(endpoints);
        }

        boolean isSecondaryFluidResult(AEKey output) {
            return hasSecondaryFluidResult()
                    && output instanceof AEFluidKey fluidOutput
                    && fluidOutput.equals(AEFluidKey.of(fluidResults.get(secondaryFluidResult)));
        }
    }

    private record FluidRoute(int basinAmount, int secondaryAmount) {
    }

    private static List<MachineEndpoint> beltOrDroppedItemOutput(Level level, BlockPos outputPos,
            Direction outputDirection) {
        if (canRecoverSawOutput(level, outputPos, outputDirection)) {
            return List.of(MachineEndpoint.itemOutput(outputPos, null));
        }
        return List.of(MachineEndpoint.droppedItemOutput(outputPos));
    }

    @Nullable
    private static Direction horizontalFacing(Level level, BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            return null;
        }
        BlockState state = level.getBlockState(machinePos);
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : null;
    }

    /**
     * Enchantment Industry grindstone drain ejects to clockwise/counter-clockwise of its shaft facing,
     * matching {@code GrindstoneDrainBlockEntity#getOutputSide} and requiring opposite rotation from
     * the mechanical grindstone directly above.
     */
    @Nullable
    private static Direction grindstoneOutputDirection(Level level, BlockPos drainPos) {
        if (level == null || !level.isLoaded(drainPos)) {
            return null;
        }
        BlockState drainState = level.getBlockState(drainPos);
        if (!drainState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return null;
        }
        Direction facing = drainState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockEntity drainEntity = level.getBlockEntity(drainPos);
        if (!(drainEntity instanceof KineticBlockEntity drain) || drain.getSpeed() == 0) {
            return null;
        }
        BlockPos grindPos = drainPos.above();
        if (!level.isLoaded(grindPos) || !CreateMachineIds.isMechanicalGrindstone(CreateMachineIds.idOf(level, grindPos))) {
            return null;
        }
        BlockState grindState = level.getBlockState(grindPos);
        if (!grindState.hasProperty(BlockStateProperties.AXIS)
                || grindState.getValue(BlockStateProperties.AXIS) != facing.getAxis()) {
            return null;
        }
        BlockEntity grindEntity = level.getBlockEntity(grindPos);
        if (!(grindEntity instanceof KineticBlockEntity grind) || grind.getSpeed() == 0) {
            return null;
        }
        float drainSpeed = drain.getSpeed();
        float grindSpeed = grind.getSpeed();
        if (drainSpeed > 0f ? grindSpeed >= 0f : grindSpeed <= 0f) {
            return null;
        }
        float signed = facing == Direction.WEST || facing == Direction.NORTH ? drainSpeed * -1 : drainSpeed;
        return signed > 0 ? facing.getClockWise() : facing.getCounterClockWise();
    }

    @Nullable
    private static Direction grinderOutputDirection(Level level, BlockPos machinePos) {
        Direction facing = horizontalFacing(level, machinePos);
        BlockEntity blockEntity = level == null ? null : level.getBlockEntity(machinePos);
        if (facing == null || !(blockEntity instanceof KineticBlockEntity kinetic) || kinetic.getSpeed() == 0) {
            return null;
        }
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        boolean positive = kinetic.getSpeed() > 0;
        if (alongX) {
            return positive ? Direction.NORTH : Direction.SOUTH;
        }
        return positive ? Direction.EAST : Direction.WEST;
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

    public static boolean isFoundryBasin(Level level, BlockPos pos) {
        return CreateMachineIds.isFoundryBasin(CreateMachineIds.idOf(level, pos));
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
        public CompensationResult compensate() {
            if (!applied) {
                return CompensationResult.complete();
            }
            FilteringBehaviour filtering = BlockEntityBehaviour.get(level, target, FilteringBehaviour.TYPE);
            if (filtering == null || !filtering.setFilter(previousFilter)) {
                return CompensationResult.failed("previous saw filter could not be restored");
            }
            applied = false;
            return CompensationResult.complete();
        }

        @Override
        public String describe() {
            return "saw-filter@" + target.toShortString();
        }
    }
}
