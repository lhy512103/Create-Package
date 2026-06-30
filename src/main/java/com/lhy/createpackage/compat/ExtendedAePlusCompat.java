package com.lhy.createpackage.compat;

import net.minecraft.nbt.CompoundTag;

import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;

public final class ExtendedAePlusCompat {
    private static final String SMART_DOUBLING = "smart_doubling";
    private static final String ADVANCED_BLOCKING = "advanced_blocking";
    private static final String LEGACY_SMART_DOUBLING = "epp_smart_doubling";
    private static final String LEGACY_ADVANCED_BLOCKING = "epp_advanced_blocking";

    private ExtendedAePlusCompat() {
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
