package com.lhy.createpackage.content.crafting;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiPredicate;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Thin wrapper around AE2 network storage insert/extract used by both craft bridges.
 */
public final class NetworkIO {
    private final @Nullable IGrid grid;
    private final IActionSource actionSource;

    public NetworkIO(@Nullable IGrid grid, IActionSource actionSource) {
        this.grid = grid;
        this.actionSource = actionSource;
    }

    public long insert(AEKey what, long amount, Actionable mode) {
        MEStorage storage = storage();
        if (storage == null || amount <= 0) {
            return 0;
        }
        return storage.insert(what, amount, mode, actionSource);
    }

    public long extract(AEKey what, long amount, Actionable mode) {
        MEStorage storage = storage();
        if (storage == null || amount <= 0) {
            return 0;
        }
        return storage.extract(what, amount, mode, actionSource);
    }

    public List<GenericStack> resolveInputs(List<GenericStack> requested, BiPredicate<AEKey, AEKey> matches) {
        if (grid == null) {
            return requested;
        }
        var available = new LinkedHashMap<AEKey, Long>();
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (requested.stream().anyMatch(input -> matches.test(input.what(), entry.getKey()))) {
                long extractable = extract(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE);
                if (extractable > 0) {
                    available.put(entry.getKey(), extractable);
                }
            }
        }
        var inputs = requested.stream()
                .map(input -> new MatchingInputResolver.Input<>(input.what(), input.amount())).toList();
        List<GenericStack> result = new java.util.ArrayList<>();
        for (var input : MatchingInputResolver.resolve(inputs, available, matches)) {
            PatternStacks.addStack(result, new GenericStack(input.key(), input.amount()));
        }
        return result;
    }

    @Nullable
    public NetworkItemStack findItem(Ingredient ingredient, long amount) {
        if (grid == null || ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getLongValue() < amount || !(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            ItemStack stack = itemKey.toStack((int) amount);
            if (ingredient.test(stack)) {
                return new NetworkItemStack(itemKey, stack);
            }
        }
        return null;
    }

    @Nullable
    private MEStorage storage() {
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    public record NetworkItemStack(AEItemKey key, ItemStack stack) {
    }
}
