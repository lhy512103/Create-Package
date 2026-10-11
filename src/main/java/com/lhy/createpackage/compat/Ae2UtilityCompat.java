package com.lhy.createpackage.compat;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

/** Optional AE2 Utility bridge. Optional classes are isolated from the no-addon path. */
public final class Ae2UtilityCompat {
    public static void registerUpgrades() {
        if (isLoaded()) {
            var card = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse("ae2utility:nbt_tear_card"));
            for (var machine : List.of(
                    com.lhy.createpackage.registry.ModItems.BASIC_PACKAGE_DISTRIBUTOR.get(),
                    com.lhy.createpackage.registry.ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get(),
                    com.lhy.createpackage.registry.ModItems.KINETIC_PATTERN_PROVIDER.get(),
                    com.lhy.createpackage.registry.ModItems.EXTENDED_KINETIC_PATTERN_PROVIDER.get())) {
                appeng.api.upgrades.Upgrades.add(card, machine, 1);
            }
        }
    }

    public static ItemStack findInstalledCard(@Nullable appeng.api.upgrades.IUpgradeInventory upgrades) {
        if (upgrades != null) {
            for (ItemStack stack : upgrades) {
                if (isTearCard(stack)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void migrateLegacyCard(appeng.helpers.patternprovider.PatternProviderLogicHost host,
            appeng.api.upgrades.IUpgradeInventory upgrades) {
        var block = host.getBlockEntity();
        var level = block.getLevel();
        var legacy = tearInventory(host.getLogic());
        if (level == null || level.isClientSide() || legacy == null) {
            return;
        }
        ItemStack card = legacy.extractItem(0, 64, false);
        if (!card.isEmpty()) {
            ItemStack remainder = upgrades.addItems(card);
            if (!remainder.isEmpty()) {
                var pos = block.getBlockPos();
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                        pos.getZ() + 0.5, remainder);
            }
            host.saveChanges();
        }
    }
    private Ae2UtilityCompat() {
    }

    @Nullable
    public static IItemHandler tearInventory(PatternProviderLogic logic) {
        return isLoaded() ? Integration.tearInventory(logic) : null;
    }

    public static boolean isTearCard(ItemStack stack) {
        return isLoaded() && Integration.isTearCard(stack);
    }

    public static ItemStack captureTearCard(@Nullable PatternProviderLogic logic) {
        return isLoaded() ? Integration.captureTearCard(logic) : ItemStack.EMPTY;
    }

    public static boolean matches(ItemStack card, AEKey expected, AEKey actual) {
        if (Objects.equals(expected, actual)) {
            return true;
        }
        return !card.isEmpty() && isLoaded() && Integration.matches(card, expected, actual);
    }

    public static boolean matches(PatternProviderLogic logic, AEKey expected, AEKey actual) {
        return Objects.equals(expected, actual) || matches(captureTearCard(logic), expected, actual);
    }

    public static boolean acceptsProcessingInputs(ItemStack card, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!(pattern instanceof AEProcessingPattern)) {
            return true; // Crafting patterns have their own recipe-substitution rules.
        }
        var definitions = pattern.getInputs();
        if (definitions.length != inputs.length) {
            return false;
        }
        for (int i = 0; i < inputs.length; i++) {
            for (var entry : inputs[i]) {
                if (entry.getLongValue() <= 0) {
                    continue;
                }
                boolean accepted = false;
                for (var template : definitions[i].getPossibleInputs()) {
                    if (matches(card, template.what(), entry.getKey())) {
                        accepted = true;
                        break;
                    }
                }
                if (!accepted) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Scope AE's CPU-return matching to this provider, restoring any outer provider's context. */
    public static <T> T withTearCard(ItemStack card, Supplier<T> action) {
        return isLoaded() ? Integration.withTearCard(card, action) : action.get();
    }

    private static boolean isLoaded() {
        return ModList.get().isLoaded("ae2utility");
    }

    private static final class Integration {
        @Nullable
        private static IItemHandler tearInventory(PatternProviderLogic logic) {
            return logic instanceof com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess access
                    ? access.ae2utility$getTearHandler() : null;
        }

        private static boolean isTearCard(ItemStack stack) {
            return com.lhy.ae2utility.api.card.CardApi.INSTANCE.isNbtTearCard(stack);
        }

        private static ItemStack captureTearCard(@Nullable PatternProviderLogic logic) {
            if (logic instanceof com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess access) {
                return access.ae2utility$getEffectiveTearCardStack().copy();
            }
            // An ordinary distributor is pushed by an external provider (including cable parts).
            // Capture its scoped rule while pushPattern is executing, before Utility clears it.
            var filter = com.lhy.ae2utility.card.NbtTearCardThreadLocal.get();
            if (filter == null) {
                return ItemStack.EMPTY;
            }
            ItemStack card = com.lhy.ae2utility.init.ModItems.NBT_TEAR_CARD.toStack();
            card.set(com.lhy.ae2utility.init.ModDataComponents.NBT_TEAR_FILTER, filter);
            return card;
        }

        private static boolean matches(ItemStack card, AEKey expected, AEKey actual) {
            var api = com.lhy.ae2utility.api.card.CardApi.INSTANCE;
            return api.isNbtTearCard(card) && api.matchesNbtTear(expected, actual, api.getNbtTearRule(card));
        }

        private static <T> T withTearCard(ItemStack card, Supplier<T> action) {
            var previous = com.lhy.ae2utility.card.NbtTearCardThreadLocal.get();
            try {
                if (isTearCard(card)) {
                    com.lhy.ae2utility.card.NbtTearCardThreadLocal.set(card.getOrDefault(
                            com.lhy.ae2utility.init.ModDataComponents.NBT_TEAR_FILTER,
                            com.lhy.ae2utility.card.NbtTearFilter.DEFAULT));
                } else {
                    com.lhy.ae2utility.card.NbtTearCardThreadLocal.clear();
                }
                return action.get();
            } finally {
                if (previous == null) {
                    com.lhy.ae2utility.card.NbtTearCardThreadLocal.clear();
                } else {
                    com.lhy.ae2utility.card.NbtTearCardThreadLocal.set(previous);
                }
            }
        }
    }
}
