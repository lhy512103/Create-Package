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
    public static final String CREATE_ADDITION = "createaddition";
    public static final String CREATE_DIESEL_GENERATORS = "createdieselgenerators";
    public static final String VINTAGE_IMPROVEMENTS = "vintageimprovements";
    public static final String CREATE_METALLURGY = "createmetallurgy";
    public static final String CREATE_BITTERBALLEN = "create_bic_bit";
    public static final String CREATE_CONFECTIONERY = "create_confectionery";
    public static final String CREATE_ENCHANTMENT_INDUSTRY = "create_enchantment_industry";

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
    public static final ResourceLocation ROLLING_MILL = addon(CREATE_ADDITION, "rolling_mill");
    public static final ResourceLocation TESLA_COIL = addon(CREATE_ADDITION, "tesla_coil");
    public static final ResourceLocation BASIN_LID = addon(CREATE_DIESEL_GENERATORS, "basin_lid");
    public static final ResourceLocation BULK_FERMENTER = addon(CREATE_DIESEL_GENERATORS, "bulk_fermenter");
    public static final ResourceLocation DISTILLATION_TANK = addon(CREATE_DIESEL_GENERATORS, "distillation_tank");

    // Vintage Improvements machines
    public static final ResourceLocation HELVE_HAMMER = addon(VINTAGE_IMPROVEMENTS, "helve_hammer");
    public static final ResourceLocation HELVE_STRUCTURE = addon(VINTAGE_IMPROVEMENTS, "helve_structure");
    public static final ResourceLocation HELVE_KINETIC = addon(VINTAGE_IMPROVEMENTS, "helve_kinetic");
    public static final ResourceLocation CENTRIFUGE = addon(VINTAGE_IMPROVEMENTS, "centrifuge");
    public static final ResourceLocation CENTRIFUGE_STRUCTURE = addon(VINTAGE_IMPROVEMENTS, "centrifuge_structure");
    public static final ResourceLocation CURVING_PRESS = addon(VINTAGE_IMPROVEMENTS, "curving_press");
    public static final ResourceLocation SPRING_COILING = addon(VINTAGE_IMPROVEMENTS, "spring_coiling_machine");
    public static final ResourceLocation COMPRESSOR = addon(VINTAGE_IMPROVEMENTS, "vacuum_chamber");
    public static final ResourceLocation LASER = addon(VINTAGE_IMPROVEMENTS, "laser");
    public static final ResourceLocation VIBRATING_TABLE = addon(VINTAGE_IMPROVEMENTS, "vibrating_table");
    public static final ResourceLocation GRINDER_BELT = addon(VINTAGE_IMPROVEMENTS, "belt_grinder");
    public static final ResourceLocation LATHE = addon(VINTAGE_IMPROVEMENTS, "lathe");
    public static final ResourceLocation LATHE_MOVING = addon(VINTAGE_IMPROVEMENTS, "lathe_moving");

    // Create Metallurgy machines
    public static final ResourceLocation FOUNDRY_BASIN = addon(CREATE_METALLURGY, "foundry_basin");
    public static final ResourceLocation CASTING_TABLE = addon(CREATE_METALLURGY, "casting_table");
    public static final ResourceLocation MECHANICAL_BELT_GRINDER = addon(CREATE_METALLURGY, "mechanical_belt_grinder");
    public static final ResourceLocation FOUNDRY_MIXER = addon(CREATE_METALLURGY, "foundry_mixer");
    public static final ResourceLocation FOUNDRY_LID = addon(CREATE_METALLURGY, "foundry_lid");
    public static final ResourceLocation INDUSTRIAL_CRUCIBLE = addon(CREATE_METALLURGY, "industrial_crucible");

    // Create Bitterballen machines
    public static final ResourceLocation MECHANICAL_FRYER = addon(CREATE_BITTERBALLEN, "mechanical_fryer");

    // Create Enchantment Industry
    public static final ResourceLocation MECHANICAL_GRINDSTONE = addon(CREATE_ENCHANTMENT_INDUSTRY,
            "mechanical_grindstone");
    public static final ResourceLocation GRINDSTONE_DRAIN = addon(CREATE_ENCHANTMENT_INDUSTRY, "grindstone_drain");

    private CreateMachineIds() {
    }

    public static ResourceLocation create(String path) {
        return addon("create", path);
    }

    private static ResourceLocation addon(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
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

    public static boolean isRollingMill(@Nullable ResourceLocation id) {
        return ROLLING_MILL.equals(id);
    }

    public static boolean isTeslaCoil(@Nullable ResourceLocation id) {
        return TESLA_COIL.equals(id);
    }

    public static boolean isBasinLid(@Nullable ResourceLocation id) {
        return BASIN_LID.equals(id);
    }

    public static boolean isBulkFermenter(@Nullable ResourceLocation id) {
        return BULK_FERMENTER.equals(id);
    }

    public static boolean isDistillationTank(@Nullable ResourceLocation id) {
        return DISTILLATION_TANK.equals(id);
    }

    // Vintage Improvements machine checks
    public static boolean isHelveHammer(@Nullable ResourceLocation id) {
        return HELVE_HAMMER.equals(id);
    }

    public static boolean isHelveSlave(@Nullable ResourceLocation id) {
        return HELVE_STRUCTURE.equals(id) || HELVE_KINETIC.equals(id);
    }

    /**
     * Resolves linkable Vintage multipart blocks to the block that owns their runtime state and capabilities.
     * Each slave block is followed using its own facing, matching Vintage Improvements' getMaster methods.
     */
    public static BlockPos normalizeKineticMachinePos(Level level, BlockPos observedPos) {
        if (level == null || observedPos == null || !level.isLoaded(observedPos)) {
            return observedPos;
        }
        BlockPos above = observedPos.above();
        if (level.isLoaded(above) && isHelveHammer(idOf(level, above))) {
            return above.immutable();
        }
        if (isMechanicalGrindstone(idOf(level, observedPos))) {
            BlockPos below = observedPos.below();
            if (level.isLoaded(below) && isGrindstoneDrain(idOf(level, below))) {
                return below.immutable();
            }
        }

        BlockPos current = observedPos.immutable();
        for (int step = 0; step < 8 && level.isLoaded(current); step++) {
            ResourceLocation currentId = idOf(level, current);
            if (isHelveHammer(currentId) || isCentrifuge(currentId) || isLathe(currentId)) {
                return current.immutable();
            }
            if (isIndustrialCrucible(currentId)) {
                return industrialCrucibleController(level, current);
            }
            if (!isHelveSlave(currentId)
                    && !CENTRIFUGE_STRUCTURE.equals(currentId)
                    && !LATHE_MOVING.equals(currentId)) {
                return observedPos.immutable();
            }
            net.minecraft.core.Direction facing = facingOf(level.getBlockState(current));
            if (facing == null) {
                return observedPos.immutable();
            }
            int distance = HELVE_KINETIC.equals(currentId) ? 2 : 1;
            current = current.relative(facing, distance);
        }
        return observedPos.immutable();
    }

    private static BlockPos industrialCrucibleController(Level level, BlockPos partPos) {
        Object blockEntity = level.getBlockEntity(partPos);
        if (blockEntity == null) {
            return partPos.immutable();
        }
        try {
            Object controller = blockEntity.getClass().getMethod("getController").invoke(blockEntity);
            if (controller instanceof BlockPos controllerPos
                    && level.isLoaded(controllerPos)
                    && isIndustrialCrucible(idOf(level, controllerPos))) {
                return controllerPos.immutable();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Optional compatibility boundary: an unavailable method leaves the observed block as the target.
        }
        return partPos.immutable();
    }

    @Nullable
    private static net.minecraft.core.Direction facingOf(BlockState state) {
        for (var property : state.getProperties()) {
            if ("facing".equals(property.getName())
                    && state.getValue(property) instanceof net.minecraft.core.Direction facing) {
                return facing;
            }
        }
        return null;
    }

    public static boolean isCentrifuge(@Nullable ResourceLocation id) {
        return CENTRIFUGE.equals(id);
    }

    public static boolean isCurvingPress(@Nullable ResourceLocation id) {
        return CURVING_PRESS.equals(id);
    }

    public static boolean isSpringCoiling(@Nullable ResourceLocation id) {
        return SPRING_COILING.equals(id);
    }

    public static boolean isCompressor(@Nullable ResourceLocation id) {
        return COMPRESSOR.equals(id);
    }

    public static boolean isLaser(@Nullable ResourceLocation id) {
        return LASER.equals(id);
    }

    public static boolean isVibratingTable(@Nullable ResourceLocation id) {
        return VIBRATING_TABLE.equals(id);
    }

    public static boolean isGrinderBelt(@Nullable ResourceLocation id) {
        return GRINDER_BELT.equals(id);
    }

    public static boolean isLathe(@Nullable ResourceLocation id) {
        return LATHE.equals(id);
    }

    // Create Metallurgy machine checks
    public static boolean isFoundryBasin(@Nullable ResourceLocation id) {
        return FOUNDRY_BASIN.equals(id);
    }

    public static boolean isCastingTable(@Nullable ResourceLocation id) {
        return CASTING_TABLE.equals(id);
    }

    public static boolean isMechanicalBeltGrinder(@Nullable ResourceLocation id) {
        return MECHANICAL_BELT_GRINDER.equals(id);
    }

    public static boolean isFoundryMixer(@Nullable ResourceLocation id) {
        return FOUNDRY_MIXER.equals(id);
    }

    public static boolean isFoundryLid(@Nullable ResourceLocation id) {
        return FOUNDRY_LID.equals(id);
    }

    public static boolean isIndustrialCrucible(@Nullable ResourceLocation id) {
        return INDUSTRIAL_CRUCIBLE.equals(id);
    }

    // Create Bitterballen machine checks
    public static boolean isMechanicalFryer(@Nullable ResourceLocation id) {
        return MECHANICAL_FRYER.equals(id);
    }

    public static boolean isMechanicalGrindstone(@Nullable ResourceLocation id) {
        return MECHANICAL_GRINDSTONE.equals(id);
    }

    public static boolean isGrindstoneDrain(@Nullable ResourceLocation id) {
        return GRINDSTONE_DRAIN.equals(id);
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
        if (isRollingMill(id)) {
            return "rolling_mill";
        }
        if (isTeslaCoil(id)) {
            return "tesla_coil";
        }
        if (isBasinLid(id)) {
            return "basin_lid";
        }
        if (isBulkFermenter(id)) {
            return "bulk_fermenter";
        }
        if (isDistillationTank(id)) {
            return "distillation_tank";
        }
        // Vintage Improvements
        if (isHelveHammer(id)) {
            return "helve_hammer";
        }
        if (isCentrifuge(id)) {
            return "centrifuge";
        }
        if (isCurvingPress(id)) {
            return "curving_press";
        }
        if (isSpringCoiling(id)) {
            return "spring_coiling";
        }
        if (isCompressor(id)) {
            return "compressor";
        }
        if (isLaser(id)) {
            return "laser";
        }
        if (isVibratingTable(id)) {
            return "vibrating_table";
        }
        if (isGrinderBelt(id)) {
            return "grinder_belt";
        }
        if (isLathe(id)) {
            return "lathe";
        }
        // Create Metallurgy
        if (isFoundryBasin(id)) {
            return "foundry_basin";
        }
        if (isCastingTable(id)) {
            return "casting_table";
        }
        if (isMechanicalBeltGrinder(id)) {
            return "mechanical_belt_grinder";
        }
        if (isFoundryMixer(id)) {
            return "foundry_mixer";
        }
        if (isFoundryLid(id)) {
            return "foundry_lid";
        }
        if (isIndustrialCrucible(id)) {
            return "industrial_crucible";
        }
        // Create Bitterballen
        if (isMechanicalFryer(id)) {
            return "mechanical_fryer";
        }
        if (isGrindstoneDrain(id) || isMechanicalGrindstone(id)) {
            return "grindstone_drain";
        }
        return "generic";
    }
}
