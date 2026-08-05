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

    public static final ModConfigSpec.IntValue ADVANCED_DISTRIBUTOR_PARALLEL_CARD_CAPACITY = BUILDER
            .comment("Number of active jobs provided by each Parallel Card in an Advanced Package Distributor.")
            .defineInRange("advancedDistributorParallelCardCapacity", 4, 1, 64);

    public static final ModConfigSpec.IntValue DISTRIBUTOR_MAX_JOB_TICKS = BUILDER
            .comment("Hard timeout for one package-distributor craft job before it is marked timed out.")
            .defineInRange("distributorMaxJobTicks", 20 * 60 * 10, 20 * 30, 20 * 60 * 60);

    public static final ModConfigSpec.IntValue KINETIC_MAX_JOB_TICKS = BUILDER
            .comment("Hard timeout for one kinetic pattern provider craft job before it is marked timed out.")
            .defineInRange("kineticMaxJobTicks", 20 * 60 * 5, 20 * 30, 20 * 60 * 60);

    public static final ModConfigSpec.IntValue REFILL_RETRY_TICKS = BUILDER
            .comment("How often craft bridges retry a blocked probabilistic refill while waiting for materials or "
                    + "target space.")
            .defineInRange("refillRetryTicks", 20 * 5, 5, 20 * 60);

    public static final ModConfigSpec.IntValue PENDING_DISPATCH_RETRY_TICKS = BUILDER
            .comment("How often kinetic pattern providers retry a pending smart-batch dispatch.")
            .defineInRange("pendingDispatchRetryTicks", 5, 1, 20 * 30);

    public static final ModConfigSpec.IntValue MISSING_REFILL_NOTICE_RADIUS = BUILDER
            .comment("Chat notice radius around a package distributor that is missing refill materials.")
            .defineInRange("missingRefillNoticeRadius", 64, 8, 256);

    public static final ModConfigSpec.IntValue AUTO_ROUTE_MAX_LENGTH = BUILDER
            .comment("Maximum Manhattan length of the straight corridor scanned by G-key auto-route.")
            .defineInRange("autoRouteMaxLength", 512, 8, 2048);

    public static final ModConfigSpec.BooleanValue AUTO_ROUTE_ALLOW_L_SHAPED = BUILDER
            .comment("Allow G-key auto-route to rebuild one-bend (L-shaped) same-Y corridors when a pure straight "
                    + "path is unavailable.")
            .define("autoRouteAllowLShaped", true);

    public static final ModConfigSpec.IntValue EXTENDED_KINETIC_PROVIDER_PAGE_MULTIPLIER = BUILDER
            .comment("Number of 36-pattern pages available in an Extended Kinetic Pattern Provider. "
                    + "Pages containing saved patterns remain accessible after this value is reduced.")
            .defineInRange("extendedKineticProviderPageMultiplier", 1, 1, 9);

    public static final ModConfigSpec.BooleanValue ENABLE_PROBABILISTIC_EMPTY_REFILL = BUILDER
            .comment("When enabled, craft bridges treat a full empty-output timeout as a failed probability roll and "
                    + "attempt a refill round.")
            .define("enableProbabilisticEmptyRefill", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static int emptyOutputRefillTimeoutTicks(int fallback) {
        return safeInt(EMPTY_OUTPUT_REFILL_TIMEOUT_TICKS, fallback);
    }

    public static int missingRefillNoticeIntervalSeconds(int fallback) {
        return safeInt(MISSING_REFILL_NOTICE_INTERVAL_SECONDS, fallback);
    }

    public static int advancedDistributorParallelCardCapacity() {
        return safeInt(ADVANCED_DISTRIBUTOR_PARALLEL_CARD_CAPACITY, 4);
    }

    public static int distributorMaxJobTicks() {
        return safeInt(DISTRIBUTOR_MAX_JOB_TICKS, 20 * 60 * 10);
    }

    public static int kineticMaxJobTicks() {
        return safeInt(KINETIC_MAX_JOB_TICKS, 20 * 60 * 5);
    }

    public static int refillRetryTicks() {
        return safeInt(REFILL_RETRY_TICKS, 20 * 5);
    }

    public static int pendingDispatchRetryTicks() {
        return safeInt(PENDING_DISPATCH_RETRY_TICKS, 5);
    }

    public static int missingRefillNoticeRadius() {
        return safeInt(MISSING_REFILL_NOTICE_RADIUS, 64);
    }

    public static int autoRouteMaxLength() {
        return safeInt(AUTO_ROUTE_MAX_LENGTH, 512);
    }

    public static boolean autoRouteAllowLShaped() {
        return safeBool(AUTO_ROUTE_ALLOW_L_SHAPED, true);
    }

    public static int extendedKineticProviderPageMultiplier() {
        return safeInt(EXTENDED_KINETIC_PROVIDER_PAGE_MULTIPLIER, 1);
    }

    public static boolean enableProbabilisticEmptyRefill() {
        return safeBool(ENABLE_PROBABILISTIC_EMPTY_REFILL, true);
    }

    public static boolean debugLoggingEnabled() {
        return safeBool(ENABLE_DEBUG_LOGGING, false);
    }

    private static int safeInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private static boolean safeBool(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }
}
