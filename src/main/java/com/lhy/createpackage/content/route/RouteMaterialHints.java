package com.lhy.createpackage.content.route;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.LinkedMachines;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.content.recipe.AssemblyPlan;
import com.lhy.createpackage.content.recipe.AssemblyRecipeMatcher;
import com.lhy.createpackage.registry.ModItems;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

/**
 * Resolves which material a selected mechanical-package-pattern route mark is expected to use.
 */
public final class RouteMaterialHints {
    private RouteMaterialHints() {
    }

    public static List<Component> selectedMaterialLines(Level level, ItemStack stack, int selectedIndex) {
        if (!stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get()) || selectedIndex < 0) {
            return List.of();
        }
        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
        if (data == null || data.route().isEmpty() || selectedIndex >= data.route().size()) {
            return List.of();
        }
        BlockPos pos = data.route().get(selectedIndex);
        LinkedMachines.Role role = level == null ? LinkedMachines.Role.UNKNOWN : LinkedMachines.roleOf(level, pos);
        Component material = materialFor(level, data, selectedIndex, role);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.selected_mark",
                selectedIndex + 1, roleLabel(role), pos.getX(), pos.getY(), pos.getZ()));
        if (material != null) {
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.selected_material", material));
        } else {
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.selected_material_unknown"));
        }
        return lines;
    }

    @Nullable
    private static Component materialFor(Level level, MechanicalPackagePatternData data, int selectedIndex,
            LinkedMachines.Role role) {
        if (level == null) {
            return null;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(data.encodedPattern(), level);
        if (details == null) {
            return null;
        }

        List<BlockPos> route = data.route();
        BlockPos pos = route.get(selectedIndex);
        if (role == LinkedMachines.Role.DEPOT) {
            BlockPos first = firstDepot(level, route);
            BlockPos last = lastDepot(level, route);
            if (pos.equals(first)) {
                ItemStack base = firstItemInput(details);
                return base.isEmpty()
                        ? Component.translatable("tooltip." + CreatePackage.MODID + ".route.material.base_input")
                        : base.getHoverName();
            }
            if (pos.equals(last)) {
                GenericStack primary = details.getPrimaryOutput();
                if (primary != null && primary.what() instanceof AEItemKey itemKey) {
                    return itemKey.toStack(1).getHoverName();
                }
                return Component.translatable("tooltip." + CreatePackage.MODID + ".route.material.output");
            }
            return Component.translatable("tooltip." + CreatePackage.MODID + ".route.material.carrier");
        }

        AssemblyPlan plan = matchPlan(level, details);
        if (plan == null) {
            return fallbackFromPatternInputs(details, role);
        }

        if (role == LinkedMachines.Role.DEPLOYER) {
            int deployIndex = indexAmongRole(level, route, selectedIndex, LinkedMachines.Role.DEPLOYER);
            List<AssemblyPlan.Step> deploys = plan.consumingSteps().stream()
                    .filter(step -> step.type() == AssemblyPlan.StepType.DEPLOY)
                    .toList();
            if (deployIndex >= 0 && deployIndex < deploys.size()) {
                AssemblyPlan.Step step = deploys.get(deployIndex);
                ItemStack example = exampleItem(step.heldItem());
                if (!example.isEmpty()) {
                    return Component.translatable(
                            step.keepHeld()
                                    ? "tooltip." + CreatePackage.MODID + ".route.material.deployer_tool"
                                    : "tooltip." + CreatePackage.MODID + ".route.material.deployer_item",
                            example.getHoverName());
                }
            }
            return Component.translatable("tooltip." + CreatePackage.MODID + ".route.material.deployer");
        }

        if (role == LinkedMachines.Role.SPOUT) {
            int spoutIndex = indexAmongRole(level, route, selectedIndex, LinkedMachines.Role.SPOUT);
            List<AssemblyPlan.Step> fills = plan.consumingSteps().stream()
                    .filter(step -> step.type() == AssemblyPlan.StepType.FILL)
                    .toList();
            if (spoutIndex >= 0 && spoutIndex < fills.size()) {
                FluidStack fluid = exampleFluid(fills.get(spoutIndex).fluid());
                if (!fluid.isEmpty()) {
                    return Component.translatable("tooltip." + CreatePackage.MODID + ".route.material.spout_fluid",
                            fluid.getDisplayName(), fluid.getAmount());
                }
            }
            return Component.translatable("tooltip." + CreatePackage.MODID + ".route.material.spout");
        }

        return roleLabel(role);
    }

    @Nullable
    private static AssemblyPlan matchPlan(Level level, IPatternDetails details) {
        GenericStack primary = details.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length > 0 && possible[0].what() instanceof AEItemKey key) {
                inputs.add(key.toStack((int) Math.min(Math.max(1, possible[0].amount()), Integer.MAX_VALUE)));
            }
        }
        List<AssemblyRecipeMatcher.Match> matches =
                AssemblyRecipeMatcher.findCandidates(level, itemKey.toStack(1), inputs);
        if (matches.size() != 1) {
            return null;
        }
        return AssemblyPlan.of(matches.get(0).recipe());
    }

    private static Component fallbackFromPatternInputs(IPatternDetails details, LinkedMachines.Role role) {
        if (role == LinkedMachines.Role.DEPLOYER) {
            ItemStack item = firstItemInput(details);
            if (!item.isEmpty()) {
                return item.getHoverName();
            }
        }
        return roleLabel(role);
    }

    private static ItemStack firstItemInput(IPatternDetails details) {
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length > 0 && possible[0].what() instanceof AEItemKey key) {
                return key.toStack(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static int indexAmongRole(Level level, List<BlockPos> route, int selectedIndex, LinkedMachines.Role role) {
        int count = 0;
        for (int i = 0; i < route.size(); i++) {
            if (LinkedMachines.roleOf(level, route.get(i)) != role) {
                continue;
            }
            if (i == selectedIndex) {
                return count;
            }
            count++;
        }
        return -1;
    }

    @Nullable
    private static BlockPos firstDepot(Level level, List<BlockPos> route) {
        for (BlockPos pos : route) {
            if (LinkedMachines.roleOf(level, pos) == LinkedMachines.Role.DEPOT) {
                return pos;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos lastDepot(Level level, List<BlockPos> route) {
        BlockPos found = null;
        for (BlockPos pos : route) {
            if (LinkedMachines.roleOf(level, pos) == LinkedMachines.Role.DEPOT) {
                found = pos;
            }
        }
        return found;
    }

    private static ItemStack exampleItem(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient == null) {
            return ItemStack.EMPTY;
        }
        ItemStack[] stacks = ingredient.getItems();
        return stacks.length == 0 ? ItemStack.EMPTY : stacks[0].copy();
    }

    private static FluidStack exampleFluid(com.simibubi.create.foundation.fluid.FluidIngredient ingredient) {
        if (ingredient == null) {
            return FluidStack.EMPTY;
        }
        List<FluidStack> stacks = ingredient.getMatchingFluidStacks();
        if (stacks.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = stacks.get(0).copy();
        stack.setAmount(ingredient.getRequiredAmount());
        return stack;
    }

    private static Component roleLabel(LinkedMachines.Role role) {
        String key = switch (role) {
            case DEPOT -> "depot";
            case DEPLOYER -> "deployer";
            case SPOUT -> "spout";
            case PRESS -> "press";
            case MIXER -> "mixer";
            case SAW -> "saw";
            case BASIN -> "basin";
            default -> "unknown";
        };
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.role." + key);
    }
}
