package com.lhy.createpackage.content.recipe;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * Resolves which {@link SequencedAssemblyRecipe} an AE2 pattern corresponds to.
 *
 * <p>Patterns are encoded by the player as plain AE2 processing patterns (inputs -> outputs) and do
 * not carry a Create recipe id. We recover the recipe by matching the pattern's primary output
 * against each sequenced-assembly recipe's final result, then (when several share an output)
 * disambiguating by checking the recipe's base ingredient is among the pattern's inputs.
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
        List<Match> result = new ArrayList<>();
        if (primaryOutput.isEmpty()) {
            return result;
        }

        List<RecipeHolder<SequencedAssemblyRecipe>> all =
                level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.SEQUENCED_ASSEMBLY.getType());

        for (RecipeHolder<SequencedAssemblyRecipe> holder : all) {
            SequencedAssemblyRecipe recipe = holder.value();
            ItemStack out = recipe.getResultItem(level.registryAccess());
            if (!ItemStack.isSameItemSameComponents(out, primaryOutput)) {
                continue;
            }
            // Disambiguate: the recipe's base input must be satisfiable by one of the pattern inputs.
            if (!patternInputs.isEmpty() && !baseInputMatches(recipe, patternInputs)) {
                continue;
            }
            result.add(new Match(holder.id(), recipe));
        }
        return result;
    }

    private static boolean baseInputMatches(SequencedAssemblyRecipe recipe, List<ItemStack> patternInputs) {
        var base = recipe.getIngredient();
        for (ItemStack input : patternInputs) {
            if (base.test(input)) {
                return true;
            }
        }
        return false;
    }

    private AssemblyRecipeMatcher() {}
}
