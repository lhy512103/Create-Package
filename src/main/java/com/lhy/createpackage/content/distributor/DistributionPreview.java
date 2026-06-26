package com.lhy.createpackage.content.distributor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.lhy.createpackage.content.recipe.AssemblyPlan;
import com.lhy.createpackage.content.recipe.AssemblyRecipeMatcher;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * Computes and logs a full distribution dry-run for a pattern push, without moving any items.
 *
 * <p>This proves the planning pipeline end to end (pattern -> recipe -> supply plan -> machine
 * mapping) in-game safely. Actual item injection, result recovery and busy tracking are wired in a
 * later milestone, because they must land together to avoid item loss or stalling AE2.
 */
public final class DistributionPreview {

    public static void log(Logger logger, Level level, IPatternDetails pattern,
            LinkedMachines machines, java.util.Set<net.minecraft.core.BlockPos> rawLinks) {

        // Pattern outputs / inputs as item stacks (item keys only; fluids handled later).
        ItemStack primaryOutput = asItemStack(pattern.getPrimaryOutput());
        List<ItemStack> patternInputs = new ArrayList<>();
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length > 0) {
                ItemStack stack = asItemStack(possible[0]);
                if (!stack.isEmpty()) {
                    patternInputs.add(stack);
                }
            }
        }

        logger.info("[Distributor preview] output={}, {} item input(s), {} linked machine(s)",
                primaryOutput, patternInputs.size(), rawLinks.size());

        // 1) Resolve the Create recipe behind this pattern.
        var candidates = AssemblyRecipeMatcher.findCandidates(level, primaryOutput, patternInputs);
        if (candidates.isEmpty()) {
            logger.info("    no matching sequenced-assembly recipe found");
            return;
        }
        if (candidates.size() > 1) {
            logger.info("    ambiguous: {} matching recipes {}", candidates.size(),
                    candidates.stream().map(AssemblyRecipeMatcher.Match::id).toList());
        }
        var match = candidates.get(0);
        logger.info("    matched recipe: {}", match.id());

        // 2) Flatten the recipe into a supply plan.
        AssemblyPlan plan = AssemblyPlan.of(match.recipe());
        logger.info("    base input: {}, loops: {}, steps: {}",
                describeIngredient(plan.baseInput()), plan.loops(), plan.steps().size());

        // 3) Map machine roles and report what each would receive.
        logger.info("    input depot: {}, output depot: {}", machines.inputDepot(), machines.outputDepot());
        var deployers = machines.withRole(LinkedMachines.Role.DEPLOYER);
        var spouts = machines.withRole(LinkedMachines.Role.SPOUT);
        logger.info("    linked: {} deployer(s), {} spout(s)", deployers.size(), spouts.size());

        int deployIdx = 0;
        int fillIdx = 0;
        for (AssemblyPlan.Step step : plan.consumingSteps()) {
            switch (step.type()) {
                case DEPLOY -> {
                    var target = deployIdx < deployers.size() ? deployers.get(deployIdx).pos() : null;
                    logger.info("    DEPLOY x{} of {} (keep={}) -> deployer {}",
                            plan.loops(), describeIngredient(step.heldItem()), step.keepHeld(), target);
                    deployIdx++;
                }
                case FILL -> {
                    var target = fillIdx < spouts.size() ? spouts.get(fillIdx).pos() : null;
                    int perLoop = step.fluid() != null ? step.fluid().amount() : 0;
                    logger.info("    FILL x{} ({}mB/loop) -> spout {}", plan.loops(), perLoop, target);
                    fillIdx++;
                }
                default -> { /* PRESS/CUT/OTHER consume nothing */ }
            }
        }
        if (deployIdx > deployers.size() || fillIdx > spouts.size()) {
            logger.info("    WARNING: not enough linked machines for all steps "
                    + "(need {} deployer(s), {} spout(s))", deployIdx, fillIdx);
        }
    }

    private static ItemStack asItemStack(GenericStack stack) {
        if (stack != null && stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
        }
        return ItemStack.EMPTY;
    }

    private static String describeIngredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient == null) {
            return "<none>";
        }
        ItemStack[] items = ingredient.getItems();
        return items.length > 0 ? items[0].toString() : "<empty>";
    }

    private DistributionPreview() {}
}
