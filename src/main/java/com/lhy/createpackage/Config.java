package com.lhy.createpackage;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config for Create Package behavior.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Log detailed distributor / crafting bridge activity for debugging.")
            .define("enableDebugLogging", false);

    public static final ModConfigSpec.IntValue EMPTY_OUTPUT_REFILL_TIMEOUT_TICKS = BUILDER
            .comment("How long a package distributor waits after starting a sequenced-assembly round before treating "
                    + "no output as an empty probability result and attempting a refill.")
            .defineInRange("emptyOutputRefillTimeoutTicks", 20 * 60, 20, 20 * 60 * 30);

    public static final ModConfigSpec.IntValue MISSING_REFILL_NOTICE_INTERVAL_SECONDS = BUILDER
            .comment("How often package distributors resend chat warnings while a probabilistic refill is missing "
                    + "materials from AE storage.")
            .defineInRange("missingRefillNoticeIntervalSeconds", 30, 1, 60 * 30);

    static final ModConfigSpec SPEC = BUILDER.build();
}
