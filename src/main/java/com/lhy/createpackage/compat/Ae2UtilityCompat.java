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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

/** Optional AE2 Utility bridge. Optional classes are isolated from the no-addon path. */
public final class Ae2UtilityCompat {
    public static void registerUpgrades() {
        if (isLoaded() && Integration.BRIDGE != null) {
            var card = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    new net.minecraft.resources.ResourceLocation("ae2utility:nbt_tear_card"));
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

    /** Resolve the optional Forge API once; older Utility releases lack this integration contract. */
    private static final class Integration {
        private static final Bridge BRIDGE = Bridge.load();

        private static IItemHandler tearInventory(PatternProviderLogic logic) {
            return BRIDGE != null && BRIDGE.access.isInstance(logic)
                    ? (IItemHandler) BRIDGE.invoke(BRIDGE.handler, logic) : null;
        }

        private static boolean isTearCard(ItemStack stack) {
            return BRIDGE != null && (boolean) BRIDGE.invoke(BRIDGE.isCard, BRIDGE.api, stack);
        }

        private static ItemStack captureTearCard(@Nullable PatternProviderLogic logic) {
            if (BRIDGE == null) return ItemStack.EMPTY;
            if (BRIDGE.access.isInstance(logic)) {
                return ((ItemStack) BRIDGE.invoke(BRIDGE.effective, logic)).copy();
            }
            Object filter = BRIDGE.invoke(BRIDGE.get, null);
            if (filter == null) return ItemStack.EMPTY;
            ItemStack card = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    new net.minecraft.resources.ResourceLocation("ae2utility", "nbt_tear_card")));
            BRIDGE.invoke(BRIDGE.setFilter, null, card, filter);
            return card;
        }

        private static boolean matches(ItemStack card, AEKey expected, AEKey actual) {
            return isTearCard(card) && (boolean) BRIDGE.invoke(BRIDGE.matches, BRIDGE.api, expected, actual,
                    BRIDGE.invoke(BRIDGE.rule, BRIDGE.api, card));
        }

        private static <T> T withTearCard(ItemStack card, Supplier<T> action) {
            if (BRIDGE == null) return action.get();
            Object previous = BRIDGE.invoke(BRIDGE.get, null);
            try {
                if (isTearCard(card)) {
                    BRIDGE.invoke(BRIDGE.set, null, BRIDGE.invoke(BRIDGE.getFilter, null, card));
                } else {
                    BRIDGE.invoke(BRIDGE.clear, null);
                }
                return action.get();
            } finally {
                if (previous == null) BRIDGE.invoke(BRIDGE.clear, null);
                else BRIDGE.invoke(BRIDGE.set, null, previous);
            }
        }

        private static final class Bridge {
            final Class<?> access;
            final Object api;
            final java.lang.reflect.Method handler, effective, isCard, rule, matches;
            final java.lang.reflect.Method get, set, clear, getFilter, setFilter;

            Bridge() throws ReflectiveOperationException {
                access = Class.forName("com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess");
                handler = access.getMethod("ae2utility$getTearHandler");
                effective = access.getMethod("ae2utility$getEffectiveTearCardStack");
                Class<?> apiClass = Class.forName("com.lhy.ae2utility.api.card.CardApi");
                Class<?> ruleClass = Class.forName("com.lhy.ae2utility.api.card.NbtTearRule");
                api = apiClass.getField("INSTANCE").get(null);
                isCard = apiClass.getMethod("isNbtTearCard", ItemStack.class);
                rule = apiClass.getMethod("getNbtTearRule", ItemStack.class);
                matches = apiClass.getMethod("matchesNbtTear", AEKey.class, AEKey.class, ruleClass);
                Class<?> context = Class.forName("com.lhy.ae2utility.card.NbtTearCardThreadLocal");
                Class<?> filter = Class.forName("com.lhy.ae2utility.card.NbtTearFilter");
                get = context.getMethod("get");
                set = context.getMethod("set", filter);
                clear = context.getMethod("clear");
                Class<?> item = Class.forName("com.lhy.ae2utility.item.NbtTearCardItem");
                getFilter = item.getMethod("getFilter", ItemStack.class);
                setFilter = item.getMethod("setFilter", ItemStack.class, filter);
            }

            static Bridge load() {
                try {
                    return new Bridge();
                } catch (ReflectiveOperationException | LinkageError e) {
                    com.lhy.createpackage.CreatePackage.LOGGER.warn(
                            "AE2 Utility lacks the NBT tear integration API; update Utility to enable shared tear-card slots.", e);
                    return null;
                }
            }

            Object invoke(java.lang.reflect.Method method, Object target, Object... args) {
                try {
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("AE2 Utility integration failed: " + method.getName(), e);
                }
            }
        }
    }
}
