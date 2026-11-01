package com.lhy.createpackage.compat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

import appeng.api.config.YesNo;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.util.IConfigManager;

public final class ExtendedAePlusCompat {
    private static final String SMART_DOUBLING = "smart_doubling";
    private static final String ADVANCED_BLOCKING = "advanced_blocking";
    private static final String LEGACY_SMART_DOUBLING = "epp_smart_doubling";
    private static final String LEGACY_ADVANCED_BLOCKING = "epp_advanced_blocking";

    private ExtendedAePlusCompat() {
    }

    /**
     * Applied Flux / ExtendedAE Plus attach upgrade inventories to {@code PatternProviderLogic}.
     * Their menu mixins bind the visible upgrade slots to that inventory, so parallel-card
     * sneak-insert and job logic must use the same instance instead of a second host inventory.
     */
    @Nullable
    public static IUpgradeInventory patternLogicUpgrades(@Nullable Object logic) {
        if (logic instanceof IUpgradeableObject upgradeable) {
            IUpgradeInventory upgrades = upgradeable.getUpgrades();
            if (upgrades != null && upgrades.size() > 0) {
                return upgrades;
            }
        }
        if (logic == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName("com.extendedae_plus.api.bridge.CompatUpgradeProvider");
            if (type.isInstance(logic)) {
                Object result = type.getMethod("eap$getCompatUpgrades").invoke(logic);
                if (result instanceof IUpgradeInventory upgrades && upgrades.size() > 0) {
                    return upgrades;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // ExtendedAE Plus is optional at compile time.
        }
        return null;
    }

    public static void removeProviderSmartSettings(CompoundTag tag) {
        tag.remove(SMART_DOUBLING);
        tag.remove(ADVANCED_BLOCKING);
        tag.remove(LEGACY_SMART_DOUBLING);
        tag.remove(LEGACY_ADVANCED_BLOCKING);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void disableProviderSmartSettings(IConfigManager configManager) {
        for (var setting : configManager.getSettings()) {
            if ((SMART_DOUBLING.equals(setting.getName()) || ADVANCED_BLOCKING.equals(setting.getName()))
                    && setting.getEnumClass() == YesNo.class) {
                if (configManager.getSetting((appeng.api.config.Setting) setting) != YesNo.NO) {
                    configManager.putSetting((appeng.api.config.Setting) setting, YesNo.NO);
                }
            }
        }
    }
}
