package com.lhy.createpackage.content.recipe;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * Resolves which {@link SequencedAssemblyRecipe} an AE2 pattern corresponds to.
 *
 * <p>Patterns are encoded by the player as plain AE2 processing patterns (inputs -> outputs) and do
 * not carry a Create recipe id. We recover the recipe by matching the pattern's primary output
 * against each sequenced-assembly recipe's final result, then disambiguating with base input and
 * consumable step ingredients (deployer held items / spout fluids).
 */
public final class AssemblyRecipeMatcher {

    /** A matched recipe together with its id, for logging / job tracking. */
    public record Match(net.minecraft.resources.ResourceLocation id, SequencedAssemblyRecipe recipe) {
    }

    /**
     * @param level         the (server) level, used to read the recipe manager
     * @param primaryOutput the pattern's primary output stack
     * @param patternInputs all item inputs of the pattern (used to disambiguate)
     * @return the matching recipes (usually 0 or 1; more than 1 means ambiguous)
     */
    public static List<Match> findCandidates(Level level, ItemStack primaryOutput, List<ItemStack> patternInputs) {
        return findCandidates(level, primaryOutput, patternInputs, List.of());
    }

    /**
     * Prefer this overload when fluid pattern inputs are available for FILL-step disambiguation.
     */
    public static List<Match> findCandidates(Level level, ItemStack primaryOutput, List<ItemStack> patternInputs,
            List<FluidStack> patternFluids) {
        List<Match> result = new ArrayList<>();
        if (primaryOutput.isEmpty()) {
            return result;
        }

        List<RecipeHolder<SequencedAssemblyRecipe>> all =
                level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.SEQUENCED_ASSEMBLY.getType());

        Match best = null;
        int bestScore = Integer.MIN_VALUE;
        boolean tie = false;

        for (RecipeHolder<SequencedAssemblyRecipe> holder : all) {
            SequencedAssemblyRecipe recipe = holder.value();
            ItemStack out = recipe.getResultItem(level.registryAccess());
            if (!ItemStack.isSameItemSameComponents(out, primaryOutput)) {
                continue;
            }
            if (!patternInputs.isEmpty() && !baseInputMatches(recipe, patternInputs)) {
                continue;
            }
            int score = scoreRecipe(recipe, patternInputs, patternFluids);
            if (score < 0) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best = new Match(holder.id(), recipe);
                tie = false;
                result.clear();
                result.add(best);
            } else if (score == bestScore) {
                tie = true;
                result.add(new Match(holder.id(), recipe));
            }
        }
        if (!tie && best != null) {
            return List.of(best);
        }
        return result;
    }

    private static boolean baseInputMatches(SequencedAssemblyRecipe recipe, List<ItemStack> patternInputs) {
        Ingredient base = recipe.getIngredient();
        for (ItemStack input : patternInputs) {
            if (base.test(input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Higher is better. Returns -1 when a required consumable is completely missing from pattern inputs.
     */
    private static int scoreRecipe(SequencedAssemblyRecipe recipe, List<ItemStack> patternInputs,
            List<FluidStack> patternFluids) {
        AssemblyPlan plan = AssemblyPlan.of(recipe);
        int score = 0;
        for (AssemblyPlan.Step step : plan.consumingSteps()) {
            switch (step.type()) {
                case DEPLOY -> {
                    if (step.heldItem() == null) {
                        continue;
                    }
                    if (matchesItem(step.heldItem(), patternInputs)) {
                        score += 2;
                    } else if (!step.keepHeld()) {
                        // consumed held item should usually be present in the pattern
                        return -1;
                    }
                }
                case FILL -> {
                    if (step.fluid() == null) {
                        continue;
                    }
                    if (matchesFluid(step.fluid(), patternFluids)) {
                        score += 2;
                    } else if (patternFluids.isEmpty()) {
                        // fluid list unavailable; don't reject, just no bonus
                        score += 0;
                    } else {
                        return -1;
                    }
                }
                default -> {
                }
            }
        }
        return score;
    }

    private static boolean matchesItem(Ingredient ingredient, List<ItemStack> patternInputs) {
        for (ItemStack input : patternInputs) {
            if (ingredient.test(input)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFluid(SizedFluidIngredient ingredient, List<FluidStack> patternFluids) {
        for (FluidStack fluid : patternFluids) {
            if (ingredient.ingredient().test(fluid)) {
                return true;
            }
        }
        return false;
    }

    private AssemblyRecipeMatcher() {}
}
