package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.content.recipe.AssemblyPlan;
import com.lhy.createpackage.content.recipe.AssemblyRecipeMatcher;
import com.lhy.createpackage.content.distributor.LinkedMachines;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Builds a sequenced-assembly supply plan for package distributors.
 */
public final class AssemblyPlanner {
    private AssemblyPlanner() {
    }

    public record PendingSupply(BlockPos target, GenericStack stack) {
        public PendingSupply {
            target = target.immutable();
        }

        public PendingSupply withAmount(long amount) {
            return new PendingSupply(target, new GenericStack(stack.what(), amount));
        }
    }

    public record Plan(
            ResourceLocation recipeId,
            AEItemKey primaryOutputKey,
            long primaryOutputAmount,
            BlockPos inputPos,
            BlockPos outputPos,
            AEItemKey transitionalItemKey,
            int sequenceLength,
            int loops,
            ActionList actions,
            List<PendingSupply> pendingSupplies,
            List<GenericStack> refillInputs,
            String statusKey,
            @Nullable String failureDetail) {

        public static Plan failure(String statusKey) {
            return failure(statusKey, null);
        }

        public static Plan failure(String statusKey, @Nullable String failureDetail) {
            return new Plan(null, null, 0, BlockPos.ZERO, BlockPos.ZERO, null, 0, 0,
                    new ActionList(List.of()), List.of(), List.of(), statusKey, failureDetail);
        }

        public boolean isSuccess() {
            return recipeId != null && actions != null && (statusKey == null || "working".equals(statusKey));
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

    public static Plan create(Level level, NetworkIO network, IPatternDetails patternDetails, KeyCounter[] inputs,
            List<BlockPos> route) {
        var patternInputs = PatternStacks.flattenInputs(inputs);
        var primary = patternDetails.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutputKey)) {
            return Plan.failure("no_primary_output");
        }

        ItemStack primaryOutput = primaryOutputKey.toStack((int) Math.min(primary.amount(), Integer.MAX_VALUE));
        var candidates = AssemblyRecipeMatcher.findCandidates(level, primaryOutput,
                PatternStacks.itemInputs(patternInputs), PatternStacks.fluidInputs(patternInputs));
        if (candidates.isEmpty()) {
            return Plan.failure("no_matching_recipe");
        }
        if (candidates.size() > 1) {
            return Plan.failure("ambiguous_recipe",
                    candidates.stream().map(c -> c.id().toString()).reduce((a, b) -> a + ", " + b).orElse(""));
        }

        var match = candidates.get(0);
        AssemblyPlan plan = AssemblyPlan.of(match.recipe());
        AEItemKey transitionalItemKey = AEItemKey.of(match.recipe().getTransitionalItem());
        if (transitionalItemKey == null) {
            return Plan.failure("no_matching_recipe");
        }

        LinkedMachines machines = LinkedMachines.resolve(level, route);
        BlockPos inputPos = machines.inputDepot();
        BlockPos outputPos = machines.outputDepot();
        if (inputPos == null || outputPos == null) {
            return Plan.failure("missing_io");
        }
        if (inputPos.equals(outputPos)) {
            return Plan.failure("same_io");
        }

        ItemStack inputStack = PatternStacks.takeMatchingItem(patternInputs, plan.baseInput(), 1);
        if (inputStack.isEmpty()) {
            return Plan.failure("missing_base");
        }

        List<SupplyAction> returnActions = new ArrayList<>();
        List<SupplyAction> actions = new ArrayList<>();
        List<PendingSupply> pendingSupplies = new ArrayList<>();
        List<GenericStack> refillInputs = new ArrayList<>();

        if (!scheduleItemSupply(level, inputPos, inputStack, actions, pendingSupplies)) {
            return Plan.failure("simulate_failed", "input " + inputPos.toShortString());
        }
        PatternStacks.addRefillInput(refillInputs, inputStack);

        var deployers = machines.withRole(LinkedMachines.Role.DEPLOYER);
        var spouts = machines.withRole(LinkedMachines.Role.SPOUT);
        int deployIdx = 0;
        int fillIdx = 0;
        for (AssemblyPlan.Step step : plan.consumingSteps()) {
            switch (step.type()) {
                case DEPLOY -> {
                    Optional<Plan> fail = planDeployStep(level, network, patternInputs, step, deployers, deployIdx,
                            plan.loops(), actions, returnActions, pendingSupplies, refillInputs);
                    if (fail.isPresent()) {
                        return fail.get();
                    }
                    deployIdx++;
                }
                case FILL -> {
                    Optional<Plan> fail = planFillStep(level, patternInputs, step, spouts, fillIdx, plan.loops(),
                            actions, pendingSupplies, refillInputs);
                    if (fail.isPresent()) {
                        return fail.get();
                    }
                    fillIdx++;
                }
                default -> {
                }
            }
        }

        if (!patternInputs.isEmpty()) {
            return Plan.failure("unused_inputs", String.valueOf(patternInputs.size()));
        }

        List<SupplyAction> allActions = new ArrayList<>(returnActions.size() + actions.size());
        allActions.addAll(returnActions);
        allActions.addAll(actions);
        return new Plan(match.id(), primaryOutputKey, primary.amount(), inputPos, outputPos, transitionalItemKey,
                plan.steps().size(), plan.loops(), new ActionList(allActions), pendingSupplies, refillInputs, "working",
                null);
    }

    private static Optional<Plan> planDeployStep(Level level, NetworkIO network, List<GenericStack> patternInputs,
            AssemblyPlan.Step step, List<LinkedMachines.Entry> deployers, int deployIdx, int loops,
            List<SupplyAction> actions, List<SupplyAction> returnActions, List<PendingSupply> pendingSupplies,
            List<GenericStack> refillInputs) {
        if (deployIdx >= deployers.size()) {
            return Optional.of(Plan.failure("missing_deployer"));
        }
        BlockPos deployer = deployers.get(deployIdx).pos();
        long existing = InventoryAccess.countMatchingItem(level, deployer, step.heldItem());
        if (step.keepHeld()) {
            if (existing <= 0) {
                ItemStack held = PatternStacks.takeMatchingItem(patternInputs, step.heldItem(), 1);
                boolean supplied = false;
                if (held.isEmpty()) {
                    NetworkIO.NetworkItemStack networkHeld = network.findItem(step.heldItem(), 1);
                    if (networkHeld != null) {
                        actions.add(SupplyActions.networkItemToTarget(level, network, deployer, networkHeld.key(),
                                networkHeld.stack()));
                        supplied = true;
                    }
                } else if (scheduleItemSupply(level, deployer, held, actions, pendingSupplies)) {
                    supplied = true;
                } else {
                    return Optional.of(Plan.failure("simulate_failed", "deployer " + deployer.toShortString()));
                }
                if (!supplied) {
                    return Optional.of(Plan.failure("missing_deployer_item"));
                }
            }
            returnAllMatchingInputs(network, patternInputs, step.heldItem(), returnActions);
        } else {
            long amount = loops;
            long availableInput = PatternStacks.countMatchingInput(patternInputs, step.heldItem());
            if (availableInput + existing < amount) {
                return Optional.of(Plan.failure("missing_deployer_item"));
            }
            long supplyAmount = availableInput > 0 ? Math.min(availableInput, amount) : 0;
            if (supplyAmount > 0) {
                ItemStack held = PatternStacks.takeMatchingItem(patternInputs, step.heldItem(), supplyAmount);
                if (held.isEmpty()) {
                    return Optional.of(Plan.failure("missing_deployer_item"));
                }
                if (!scheduleItemSupply(level, deployer, held, actions, pendingSupplies)) {
                    return Optional.of(Plan.failure("simulate_failed", "deployer " + deployer.toShortString()));
                }
            }
            ItemStack refill = PatternStacks.exampleItemStack(step.heldItem(), amount);
            if (refill.isEmpty()) {
                return Optional.of(Plan.failure("missing_deployer_item"));
            }
            PatternStacks.addRefillInput(refillInputs, refill);
        }
        return Optional.empty();
    }

    private static Optional<Plan> planFillStep(Level level, List<GenericStack> patternInputs, AssemblyPlan.Step step,
            List<LinkedMachines.Entry> spouts, int fillIdx, int loops, List<SupplyAction> actions,
            List<PendingSupply> pendingSupplies, List<GenericStack> refillInputs) {
        if (fillIdx >= spouts.size()) {
            return Optional.of(Plan.failure("missing_spout"));
        }
        long amount = (long) loops * step.fluid().amount();
        BlockPos spout = spouts.get(fillIdx).pos();
        long availableInput = PatternStacks.countMatchingInput(patternInputs, step.fluid());
        long existing = InventoryAccess.countMatchingFluid(level, spout, step.fluid());
        if (availableInput + existing < amount) {
            return Optional.of(Plan.failure("missing_spout_fluid"));
        }
        long supplyAmount = availableInput > 0 ? Math.min(availableInput, amount) : 0;
        if (supplyAmount > 0) {
            FluidStack fluid = PatternStacks.takeMatchingFluid(patternInputs, step.fluid(), supplyAmount);
            if (fluid.isEmpty()) {
                return Optional.of(Plan.failure("missing_spout_fluid"));
            }
            if (!scheduleFluidSupply(level, spout, fluid, actions, pendingSupplies)) {
                return Optional.of(Plan.failure("simulate_failed", "spout " + spout.toShortString()));
            }
        }
        FluidStack refill = PatternStacks.exampleFluidStack(step.fluid(), amount);
        if (refill.isEmpty()) {
            return Optional.of(Plan.failure("missing_spout_fluid"));
        }
        PatternStacks.addRefillInput(refillInputs, refill);
        return Optional.empty();
    }

    public static boolean scheduleItemSupply(Level level, BlockPos target, ItemStack stack, List<SupplyAction> actions,
            List<PendingSupply> pendingSupplies) {
        if (stack.isEmpty()) {
            return true;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return false;
        }
        int accepted = InventoryAccess.acceptedItemAmount(level, target, stack);
        if (accepted <= 0 && !InventoryAccess.itemTargetCouldEventuallyAccept(level, target, stack)) {
            return false;
        }
        if (accepted > 0) {
            actions.add(SupplyActions.insertItem(level, target, stack.copyWithCount(accepted)));
        }
        int pending = stack.getCount() - accepted;
        if (pending > 0) {
            pendingSupplies.add(new PendingSupply(target, new GenericStack(key, pending)));
        }
        return true;
    }

    public static boolean scheduleFluidSupply(Level level, BlockPos target, FluidStack stack,
            List<SupplyAction> actions, List<PendingSupply> pendingSupplies) {
        if (stack.isEmpty()) {
            return true;
        }
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            return false;
        }
        int accepted = InventoryAccess.acceptedFluidAmount(level, target, stack);
        if (accepted <= 0 && !InventoryAccess.fluidTargetCouldEventuallyAccept(level, target, stack)) {
            return false;
        }
        if (accepted > 0) {
            actions.add(SupplyActions.insertFluid(level, target, stack.copyWithAmount(accepted)));
        }
        long pending = (long) stack.getAmount() - accepted;
        if (pending > 0) {
            pendingSupplies.add(new PendingSupply(target, new GenericStack(key, pending)));
        }
        return true;
    }

    private static void returnAllMatchingInputs(NetworkIO network, List<GenericStack> inputs, Ingredient ingredient,
            List<SupplyAction> actions) {
        if (ingredient == null) {
            return;
        }
        for (int i = inputs.size() - 1; i >= 0; i--) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEItemKey itemKey && ingredient.test(itemKey.toStack(1))) {
                actions.add(SupplyActions.returnToNetwork(network, input));
                inputs.remove(i);
            }
        }
    }
}
