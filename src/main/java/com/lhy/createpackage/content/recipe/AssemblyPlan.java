package com.lhy.createpackage.content.recipe;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * A structured "supply plan" extracted from a {@link SequencedAssemblyRecipe}.
 *
 * <p>Because Create drives the actual processing (the transitional item carries its own progress and
 * machines pull it from depots/belts), the distributor's job is to <em>supply</em> each machine with
 * what it consumes, not to step the recipe itself. This plan flattens a sequenced-assembly recipe
 * into: the base input, plus the ordered list of per-step consumables (held items for deployers,
 * fluids for spouts). The order matches {@link SequencedAssemblyRecipe#getSequence()}, which is how
 * we map each step onto a linked machine.
 *
 * <p>Each deploying/filling step runs once per loop, so callers multiply per-step amounts by
 * {@link #loops()} to get the total a full craft consumes.
 */
public record AssemblyPlan(
        Ingredient baseInput,
        int loops,
        List<Step> steps) {

    /** The kind of machine a step needs. PRESS/CUT consume no per-step input. */
    public enum StepType {
        DEPLOY,
        FILL,
        PRESS,
        CUT,
        OTHER
    }

    /**
     * One step of the sequence.
     *
     * @param type      which machine this step needs
     * @param heldItem  for DEPLOY: the item the deployer must hold/apply; otherwise null
     * @param keepHeld  for DEPLOY: whether the held item survives (tool) instead of being consumed
     * @param fluid     for FILL: the fluid the spout injects; otherwise null
     */
    public record Step(
            StepType type,
            Ingredient heldItem,
            boolean keepHeld,
            SizedFluidIngredient fluid) {
    }

    /**
     * Builds a plan from a sequenced-assembly recipe. Never returns null; PRESS/CUT/unknown steps
     * are recorded with no consumable so the step-to-machine ordering stays intact.
     */
    public static AssemblyPlan of(SequencedAssemblyRecipe recipe) {
        List<Step> steps = new ArrayList<>();
        for (SequencedRecipe<?> seq : recipe.getSequence()) {
            ProcessingRecipe<?, ?> step = seq.getRecipe();
            var type = step.getType();
            if (type == AllRecipeTypes.DEPLOYING.getType() && step instanceof ItemApplicationRecipe dep) {
                steps.add(new Step(StepType.DEPLOY, dep.getRequiredHeldItem(), dep.shouldKeepHeldItem(), null));
            } else if (type == AllRecipeTypes.FILLING.getType() && step instanceof FillingRecipe fill) {
                steps.add(new Step(StepType.FILL, null, false, fill.getRequiredFluid()));
            } else if (type == AllRecipeTypes.PRESSING.getType()) {
                steps.add(new Step(StepType.PRESS, null, false, null));
            } else if (type == AllRecipeTypes.CUTTING.getType()) {
                steps.add(new Step(StepType.CUT, null, false, null));
            } else {
                steps.add(new Step(StepType.OTHER, null, false, null));
            }
        }
        return new AssemblyPlan(recipe.getIngredient(), recipe.getLoops(), steps);
    }

    /** Steps that consume a per-loop item/fluid (DEPLOY/FILL), in sequence order. */
    public List<Step> consumingSteps() {
        List<Step> result = new ArrayList<>();
        for (Step step : steps) {
            if (step.type() == StepType.DEPLOY || step.type() == StepType.FILL) {
                result.add(step);
            }
        }
        return result;
    }
}
