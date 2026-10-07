package com.lhy.createpackage.client.ponder;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlock;
import com.lhy.createpackage.registry.ModBlocks;
import com.lhy.createpackage.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import appeng.block.crafting.PushDirection;

/**
 * Ponder scenes placed to match Create's camera (south-east isometric).
 *
 * <p>x increases right, z increases toward the camera, y is up. Host blocks sit on the sides or
 * at the back so they do not cover the machines. Overlay {@code text()} strings must match
 * {@code en_us.json} so {@code text_1/2/3} translations stay aligned with each keyframe.
 */
public final class CreatePackagePonderScenes {
    private static final ResourceLocation BASE = create("gauges");

    private CreatePackagePonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(
                        id("package_distributor"),
                        id("basic_package_distributor"),
                        id("advanced_package_distributor"),
                        id("incomplete_package_distributor"))
                .addStoryBoard(BASE, CreatePackagePonderScenes::packageDistributors,
                        CreatePackagePonderTags.CREATE_PACKAGE);

        helper.forComponents(id("kinetic_pattern_provider"), id("extended_kinetic_pattern_provider"))
                .addStoryBoard(BASE, CreatePackagePonderScenes::kineticPatternProvider,
                        CreatePackagePonderTags.CREATE_PACKAGE);

        helper.forComponents(
                        id("machine_linker"),
                        id("mechanical_pattern_converter"),
                        id("mechanical_package_pattern"))
                .addStoryBoard(BASE, CreatePackagePonderScenes::routeTools,
                        CreatePackagePonderTags.CREATE_PACKAGE);

        helper.forComponents(id("parallel_card"))
                .addStoryBoard(BASE, CreatePackagePonderScenes::parallelCard,
                        CreatePackagePonderTags.CREATE_PACKAGE);
    }

    public static void packageDistributors(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("package_distributors", "Create Package Distributors");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, 4, 4, 4), Blocks.AIR.defaultBlockState(), false);
        scene.idle(8);

        BlockPos regular = util.grid().at(1, 1, 0);
        BlockPos basic = util.grid().at(2, 1, 0);
        BlockPos advanced = util.grid().at(3, 1, 0);
        BlockPos inputDepot = util.grid().at(0, 1, 2);
        BlockPos deployDepot = util.grid().at(1, 1, 2);
        BlockPos spoutDepot = util.grid().at(2, 1, 2);
        BlockPos outputDepot = util.grid().at(3, 1, 2);
        BlockPos deployer = deployDepot.above(2);
        BlockPos spout = spoutDepot.above(2);

        scene.world().setBlock(regular, ModBlocks.PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().setBlock(basic, ModBlocks.BASIC_PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().setBlock(advanced, ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(regular, advanced), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.BLUE, "distributors", util.select().fromTo(regular, advanced), 55);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Package Distributors hand a full AE2 processing pattern to a real Create sequenced assembly line.")
                .pointAt(util.vector().topOf(basic))
                .placeNearTarget();
        scene.idle(75);

        scene.world().setBlock(inputDepot, createBlockState("depot"), false);
        scene.world().setBlock(deployDepot, createBlockState("depot"), false);
        scene.world().setBlock(spoutDepot, createBlockState("depot"), false);
        scene.world().setBlock(outputDepot, createBlockState("depot"), false);
        scene.world().setBlock(deployer, facingDown("deployer"), false);
        scene.world().setBlock(spout, createBlockState("spout"), false);
        Selection input = util.select().position(inputDepot);
        Selection machines = util.select().position(deployDepot)
                .add(util.select().position(deployer))
                .add(util.select().position(spoutDepot))
                .add(util.select().position(spout));
        Selection output = util.select().position(outputDepot);
        scene.world().showSection(util.select().fromTo(inputDepot, outputDepot)
                .add(util.select().position(deployer))
                .add(util.select().position(spout)), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.GREEN, "input", input, 60);
        scene.overlay().showOutline(PonderPalette.WHITE, "machines", machines, 60);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "output", output, 60);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("The route starts at an input depot or belt, visits deployers and spouts in order, and ends at the recovery position.")
                .pointAt(util.vector().blockSurface(deployer, Direction.WEST))
                .placeNearTarget();
        scene.idle(16);
        scene.world().moveDeployer(deployer, 1, 16);
        scene.idle(16);
        scene.world().moveDeployer(deployer, -1, 16);
        scene.idle(40);

        scene.overlay().showControls(util.vector().topOf(basic), Pointing.DOWN, 45)
                .withItem(aeItem("processing_pattern"));
        scene.overlay().showControls(util.vector().topOf(advanced), Pointing.DOWN, 45)
                .withItem(new ItemStack(ModItems.MECHANICAL_PACKAGE_PATTERN.get()));
        scene.overlay().showOutline(PonderPalette.GREEN, "basic", util.select().position(basic), 55);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "advanced", util.select().position(advanced), 55);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Basic Distributors store AE2 patterns directly. Advanced Distributors read the saved route from each Mechanical Package Pattern.")
                .pointAt(util.vector().topOf(advanced))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    public static void kineticPatternProvider(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("kinetic_pattern_provider", "Kinetic Pattern Provider");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, 4, 4, 4), Blocks.AIR.defaultBlockState(), false);
        scene.idle(8);

        BlockPos depot = util.grid().at(2, 1, 2);
        BlockPos machine = depot.above(2);
        BlockPos provider = util.grid().at(1, 3, 2);
        BlockPos casingLow = util.grid().at(1, 1, 2);
        BlockPos casingMid = util.grid().at(1, 2, 2);
        BlockPos extraDepot = util.grid().at(3, 1, 2);
        BlockPos extraMachine = extraDepot.above(2);

        scene.world().setBlock(casingLow, createBlockState("andesite_casing"), false);
        scene.world().setBlock(casingMid, createBlockState("andesite_casing"), false);
        scene.world().setBlock(provider, ModBlocks.KINETIC_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(KineticPatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST), false);
        scene.world().setBlock(depot, createBlockState("depot"), false);
        scene.world().setBlock(machine, facingDown("deployer"), false);
        Selection station = util.select().fromTo(casingLow, provider)
                .add(util.select().position(depot))
                .add(util.select().position(machine));
        scene.world().showSection(station, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.BLUE, "provider", util.select().position(provider), 55);
        scene.overlay().showOutline(PonderPalette.GREEN, "machine", util.select().position(machine)
                .add(util.select().position(depot)), 55);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Kinetic Pattern Providers face one Create machine and supply its working position, held items, or fluids.")
                .pointAt(util.vector().blockSurface(provider, Direction.EAST))
                .placeNearTarget();
        scene.idle(16);
        scene.world().moveDeployer(machine, 1, 16);
        scene.idle(16);
        scene.world().moveDeployer(machine, -1, 16);
        scene.idle(30);

        scene.world().setBlock(extraDepot, createBlockState("depot"), false);
        scene.world().setBlock(extraMachine, facingDown("deployer"), false);
        scene.world().showSection(util.select().position(extraDepot).add(util.select().position(extraMachine)),
                Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(provider), Pointing.DOWN, 40)
                .withItem(createItem("wrench"));
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(provider), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.PARALLEL_CARD.get()));
        scene.overlay().showControls(util.vector().topOf(extraMachine), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showOutline(PonderPalette.OUTPUT, "linked", util.select().position(extraDepot)
                .add(util.select().position(extraMachine)), 55);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Set the target side with a wrench. Add Parallel Cards and link matching machines with the Machine Linker for multi-machine dispatch.")
                .pointAt(util.vector().centerOf(extraMachine))
                .placeNearTarget();
        scene.idle(90);

        scene.world().setBlocks(util.select().fromTo(1, 1, 2, 3, 3, 2), Blocks.AIR.defaultBlockState(), false);
        BlockPos millstone = util.grid().at(2, 1, 2);
        BlockPos millOutput = util.grid().at(2, 1, 3);
        BlockPos millProvider = util.grid().at(1, 1, 2);
        scene.world().setBlock(millstone, createBlockState("millstone"), false);
        scene.world().setBlock(millOutput, createBlockState("depot"), false);
        scene.world().setBlock(millProvider, ModBlocks.KINETIC_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(KineticPatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST), false);
        Selection recovery = util.select().position(millstone)
                .add(util.select().position(millOutput))
                .add(util.select().position(millProvider));
        scene.world().showSection(recovery, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "recovery", util.select().position(millOutput), 60);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Saws, millstones, and crushing wheels still need a recoverable output belt, depot, or inventory at the machine output.")
                .pointAt(util.vector().topOf(millOutput))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    public static void routeTools(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("mechanical_route_tools", "Mechanical Route Tools");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, 4, 4, 4), Blocks.AIR.defaultBlockState(), false);
        scene.idle(8);

        BlockPos host = util.grid().at(4, 1, 2);
        BlockPos inputDepot = util.grid().at(0, 1, 2);
        BlockPos pressDepot = util.grid().at(1, 1, 2);
        BlockPos press = pressDepot.above(2);
        BlockPos outputDepot = util.grid().at(2, 1, 2);

        scene.world().setBlock(host, ModBlocks.BASIC_PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().setBlock(inputDepot, createBlockState("depot"), false);
        scene.world().setBlock(pressDepot, createBlockState("depot"), false);
        scene.world().setBlock(press, horizontalFacing("mechanical_press", Direction.WEST), false);
        scene.world().setBlock(outputDepot, createBlockState("depot"), false);
        Selection route = util.select().position(inputDepot)
                .add(util.select().position(pressDepot))
                .add(util.select().position(press))
                .add(util.select().position(outputDepot));
        scene.world().showSection(util.select().position(host).add(route), Direction.DOWN);
        scene.idle(8);

        scene.overlay().showControls(util.vector().topOf(host), Pointing.DOWN, 30)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.idle(12);
        scene.overlay().showControls(util.vector().topOf(inputDepot), Pointing.DOWN, 25)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(press), Pointing.DOWN, 25)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(outputDepot), Pointing.DOWN, 25)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showOutline(PonderPalette.GREEN, "linked_route", route, 50);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Machine Linkers save one ordered route on a Package Distributor or Basic Package Distributor.")
                .pointAt(util.vector().blockSurface(press, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(inputDepot), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.MECHANICAL_PATTERN_CONVERTER.get()));
        scene.overlay().showControls(util.vector().topOf(press), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.MECHANICAL_PACKAGE_PATTERN.get()));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Mechanical Pattern Converters and Mechanical Package Patterns can mark route blocks directly.")
                .pointAt(util.vector().centerOf(press))
                .placeNearTarget();
        scene.idle(90);

        scene.world().setBlock(host, ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.overlay().showControls(util.vector().topOf(host), Pointing.DOWN, 50)
                .withItem(new ItemStack(ModItems.MECHANICAL_PACKAGE_PATTERN.get()));
        scene.overlay().showOutline(PonderPalette.OUTPUT, "advanced_host", util.select().position(host), 55);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Converted Mechanical Package Patterns store the original AE2 pattern plus the marked Create route for Advanced Distributors.")
                .pointAt(util.vector().topOf(host))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    public static void parallelCard(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("parallel_card", "Parallel Card");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, 4, 4, 4), Blocks.AIR.defaultBlockState(), false);
        scene.idle(8);

        BlockPos advanced = util.grid().at(1, 1, 3);
        BlockPos kinetic = util.grid().at(3, 3, 2);
        BlockPos kineticCasingLow = util.grid().at(3, 1, 2);
        BlockPos kineticCasingMid = util.grid().at(3, 2, 2);
        BlockPos kineticDepot = util.grid().at(4, 1, 2);
        BlockPos kineticMachine = kineticDepot.above(2);

        scene.world().setBlock(advanced, ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().setBlock(kineticCasingLow, createBlockState("andesite_casing"), false);
        scene.world().setBlock(kineticCasingMid, createBlockState("andesite_casing"), false);
        scene.world().setBlock(kinetic, ModBlocks.KINETIC_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(KineticPatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST), false);
        scene.world().setBlock(kineticDepot, createBlockState("depot"), false);
        scene.world().setBlock(kineticMachine, facingDown("deployer"), false);
        scene.world().showSection(util.select().position(advanced), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(kineticCasingLow, kinetic)
                .add(util.select().position(kineticDepot))
                .add(util.select().position(kineticMachine)), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(advanced), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.PARALLEL_CARD.get()));
        scene.overlay().showControls(util.vector().topOf(kinetic), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.PARALLEL_CARD.get()));
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Parallel Cards upgrade Advanced Package Distributors and Kinetic Pattern Providers.")
                .pointAt(util.vector().topOf(advanced))
                .placeNearTarget();
        scene.idle(75);

        BlockPos routeA = util.grid().at(0, 1, 1);
        BlockPos routeB = util.grid().at(1, 1, 1);
        scene.world().setBlock(routeA, createBlockState("depot"), false);
        scene.world().setBlock(routeB, createBlockState("depot"), false);
        scene.world().showSection(util.select().position(routeA).add(util.select().position(routeB)), Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "route_a", util.select().position(routeA), 55);
        scene.overlay().showOutline(PonderPalette.BLUE, "route_b", util.select().position(routeB), 55);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("In an Advanced Distributor, one Parallel Card provides 4 concurrent jobs and two provide 8; the capacity is configurable.")
                .pointAt(util.vector().topOf(advanced))
                .placeNearTarget();
        scene.idle(85);

        BlockPos extraDepot = util.grid().at(4, 1, 1);
        BlockPos extraMachine = extraDepot.above(2);
        scene.world().setBlock(extraDepot, createBlockState("depot"), false);
        scene.world().setBlock(extraMachine, facingDown("deployer"), false);
        scene.world().showSection(util.select().position(extraDepot).add(util.select().position(extraMachine)),
                Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "primary_kinetic",
                util.select().position(kineticDepot).add(util.select().position(kineticMachine)), 55);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "extra_kinetic",
                util.select().position(extraDepot).add(util.select().position(extraMachine)), 55);
        scene.overlay().showControls(util.vector().topOf(extraMachine), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("In a Kinetic Pattern Provider, one card allows up to 16 active matching machines; two cards allow up to 32.")
                .pointAt(util.vector().centerOf(extraMachine))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    private static BlockState createBlockState(String path) {
        Block block = BuiltInRegistries.BLOCK.get(create(path));
        return block.defaultBlockState();
    }

    private static BlockState facingDown(String path) {
        BlockState state = createBlockState(path);
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, Direction.DOWN);
        }
        return state;
    }

    private static BlockState horizontalFacing(String path, Direction facing) {
        BlockState state = createBlockState(path);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return state;
    }

    private static ItemStack createItem(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(create(path)));
    }

    private static ItemStack aeItem(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", path)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreatePackage.MODID, path);
    }

    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }
}
