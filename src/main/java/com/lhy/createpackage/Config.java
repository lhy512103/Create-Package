package com.lhy.createpackage;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config for Create Package. Currently minimal; options will be added as features land.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Log detailed distributor / crafting bridge activity for debugging.")
            .define("enableDebugLogging", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
