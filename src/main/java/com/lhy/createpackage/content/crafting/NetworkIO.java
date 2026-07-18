package com.lhy.createpackage.content.crafting;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
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
