package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * Pure helpers for reshaping AE2 pattern inputs/outputs.
 */
public final class PatternStacks {
    private PatternStacks() {
    }

    public static List<GenericStack> flattenInputs(KeyCounter[] inputs) {
        List<GenericStack> result = new ArrayList<>();
        for (KeyCounter counter : inputs) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0) {
                    addStack(result, new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        return result;
    }

    public static void addStack(List<GenericStack> stacks, GenericStack stack) {
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack existing = stacks.get(i);
            if (existing.what().equals(stack.what())) {
                stacks.set(i, new GenericStack(existing.what(), safeAdd(existing.amount(), stack.amount())));
                return;
            }
        }
        stacks.add(stack);
    }

    public static List<GenericStack> copyStacks(List<GenericStack> inputs) {
        return new ArrayList<>(inputs);
    }

    public static List<ItemStack> itemInputs(List<GenericStack> inputs) {
        List<ItemStack> items = new ArrayList<>();
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEItemKey itemKey) {
                items.add(itemKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE)));
            }
        }
        return items;
    }

    public static List<FluidStack> fluidInputs(List<GenericStack> inputs) {
        List<FluidStack> fluids = new ArrayList<>();
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEFluidKey fluidKey) {
                fluids.add(fluidKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE)));
            }
        }
        return fluids;
    }

    public static long countMatchingInput(List<GenericStack> inputs, Ingredient ingredient) {
        if (ingredient == null) {
            return 0;
        }
        long amount = 0;
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEItemKey itemKey) {
                ItemStack stack = itemKey.toStack(1);
                if (ingredient.test(stack)) {
                    amount += input.amount();
                }
            }
        }
        return amount;
    }

    public static long countMatchingInput(List<GenericStack> inputs, SizedFluidIngredient ingredient) {
        if (ingredient == null) {
            return 0;
        }
        long amount = 0;
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEFluidKey fluidKey) {
                FluidStack stack = fluidKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE));
                if (ingredient.ingredient().test(stack)) {
                    amount += input.amount();
                }
            }
        }
        return amount;
    }

    public static ItemStack takeMatchingItem(List<GenericStack> inputs, Ingredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEItemKey itemKey && input.amount() >= amount) {
                ItemStack stack = itemKey.toStack((int) amount);
                if (ingredient.test(stack)) {
                    removeAmount(inputs, i, amount);
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static FluidStack takeMatchingFluid(List<GenericStack> inputs, SizedFluidIngredient ingredient,
            long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEFluidKey fluidKey && input.amount() >= amount) {
                FluidStack stack = fluidKey.toStack((int) amount);
                if (ingredient.ingredient().test(stack)) {
                    removeAmount(inputs, i, amount);
                    return stack;
                }
            }
        }
        return FluidStack.EMPTY;
    }

    public static ItemStack takeFirstItem(List<GenericStack> inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEItemKey itemKey && input.amount() > 0) {
                int amount = (int) Math.min(input.amount(), Integer.MAX_VALUE);
                removeAmount(inputs, i, amount);
                return itemKey.toStack(amount);
            }
        }
        return ItemStack.EMPTY;
    }

    public static FluidStack takeFirstFluid(List<GenericStack> inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEFluidKey fluidKey && input.amount() > 0) {
                int amount = (int) Math.min(input.amount(), Integer.MAX_VALUE);
                removeAmount(inputs, i, amount);
                return fluidKey.toStack(amount);
            }
        }
        return FluidStack.EMPTY;
    }

    public static void removeAmount(List<GenericStack> inputs, int index, long amount) {
        GenericStack old = inputs.get(index);
        long remaining = old.amount() - amount;
        if (remaining <= 0) {
            inputs.remove(index);
        } else {
            inputs.set(index, new GenericStack(old.what(), remaining));
        }
    }

    public static void consumeInputs(KeyCounter[] inputs) {
        for (KeyCounter counter : inputs) {
            counter.clear();
        }
    }

    public static ItemStack exampleItemStack(Ingredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = stacks[0].copy();
        stack.setCount((int) amount);
        return stack;
    }

    public static FluidStack exampleFluidStack(SizedFluidIngredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return FluidStack.EMPTY;
        }
        FluidStack[] stacks = ingredient.getFluids();
        if (stacks.length == 0) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = stacks[0].copy();
        stack.setAmount((int) amount);
        return stack;
    }

    public static void addRefillInput(List<GenericStack> inputs, ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key != null && !stack.isEmpty()) {
            addStack(inputs, new GenericStack(key, stack.getCount()));
        }
    }

    public static void addRefillInput(List<GenericStack> inputs, FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        if (key != null && !stack.isEmpty()) {
            addStack(inputs, new GenericStack(key, stack.getAmount()));
        }
    }

    public static void addRefillInput(List<GenericStack> inputs, GenericStack stack) {
        if (stack != null && stack.amount() > 0) {
            addStack(inputs, stack);
        }
    }

    public static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0 && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }
}
