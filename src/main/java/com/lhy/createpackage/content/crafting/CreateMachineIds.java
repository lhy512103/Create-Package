package com.lhy.createpackage.content.crafting;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared Create / CreateMoreMachines block-id recognition used by distributors and kinetic providers.
 */
public final class CreateMachineIds {
    public static final String CREATE_MORE_MACHINES = "createmoremachines";

    public static final ResourceLocation DEPLOYER = create("deployer");
    public static final ResourceLocation SPOUT = create("spout");
    public static final ResourceLocation MECHANICAL_PRESS = create("mechanical_press");
    public static final ResourceLocation MECHANICAL_MIXER = create("mechanical_mixer");
    public static final ResourceLocation MECHANICAL_SAW = create("mechanical_saw");
    public static final ResourceLocation BASIN = create("basin");
    public static final ResourceLocation DEPOT = create("depot");
    public static final ResourceLocation BELT = create("belt");
    public static final ResourceLocation MILLSTONE = create("millstone");
    public static final ResourceLocation CRUSHING_WHEEL = create("crushing_wheel");
    public static final ResourceLocation CRUSHING_CONTROLLER = create("crushing_wheel_controller");

    private CreateMachineIds() {
    }

    public static ResourceLocation create(String path) {
        return new ResourceLocation("create", path);
    }

    @Nullable
    public static ResourceLocation idOf(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    @Nullable
    public static ResourceLocation idOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    public static boolean isCreateMoreMachines(@Nullable ResourceLocation id, String suffix) {
        return id != null && CREATE_MORE_MACHINES.equals(id.getNamespace()) && id.getPath().endsWith(suffix);
    }

    public static boolean isDeployer(@Nullable ResourceLocation id) {
        return DEPLOYER.equals(id) || isCreateMoreMachines(id, "_deployer");
    }

    public static boolean isSpout(@Nullable ResourceLocation id) {
        return SPOUT.equals(id) || isCreateMoreMachines(id, "_spout");
    }

    public static boolean isMechanicalPress(@Nullable ResourceLocation id) {
        return MECHANICAL_PRESS.equals(id) || isCreateMoreMachines(id, "_mechanical_press");
    }

    public static boolean isMechanicalMixer(@Nullable ResourceLocation id) {
        return MECHANICAL_MIXER.equals(id) || isCreateMoreMachines(id, "_mechanical_mixer");
    }

    public static boolean isMechanicalSaw(@Nullable ResourceLocation id) {
        return MECHANICAL_SAW.equals(id) || isCreateMoreMachines(id, "_saw");
    }

    public static boolean isBasin(@Nullable ResourceLocation id) {
        return BASIN.equals(id) || isCreateMoreMachines(id, "_basin");
    }

    public static boolean isDepotCarrier(@Nullable ResourceLocation id) {
        return DEPOT.equals(id) || BELT.equals(id);
    }

    public static boolean isMillstone(@Nullable ResourceLocation id) {
        return MILLSTONE.equals(id);
    }

    public static boolean isCrushingWheel(@Nullable ResourceLocation id) {
        return CRUSHING_WHEEL.equals(id);
    }

    public static boolean isCrushingController(@Nullable ResourceLocation id) {
        return CRUSHING_CONTROLLER.equals(id);
    }

    public static String kineticRoleKey(@Nullable ResourceLocation id) {
        if (isDeployer(id)) {
            return "deployer";
        }
        if (isSpout(id)) {
            return "spout";
        }
        if (isMechanicalPress(id)) {
            return "press";
        }
        if (isMechanicalMixer(id)) {
            return "mixer";
        }
        if (isCrushingController(id)) {
            return "crushing";
        }
        if (isMechanicalSaw(id)) {
            return "saw";
        }
        if (isMillstone(id)) {
            return "millstone";
        }
        return "generic";
    }
}
