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
 * Ponder scenes built from Create Package blocks so captions match the on-screen setup.
 *
 * <p>Storyboards only supply a Create base plate; scene content is placed with {@code setBlock}.
 * Create Deployers sit two blocks above their depot, leaving room for the extended hand.
 */
public final class CreatePackagePonderScenes {
    /** Small Create schematic used only for the base plate. */
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

        helper.forComponents(id("kinetic_pattern_provider"))
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
        // Storyboard only provides the plate; clear leftover schematic blocks above it.
        // Do not hideSection here: unshown sections have no WorldSectionElement and crash Ponder.
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, 4, 4, 4), Blocks.AIR.defaultBlockState(), false);
        scene.idle(10);

        // Route: input depot -> deployer two blocks above middle depot -> output depot
        BlockPos distributor = util.grid().at(2, 1, 4);
        BlockPos inputDepot = util.grid().at(1, 1, 2);
        BlockPos processDepot = util.grid().at(2, 1, 2);
        BlockPos deployer = processDepot.above(2); // y=3; y=2 is the hand workspace
        BlockPos outputDepot = util.grid().at(3, 1, 2);

        scene.world().setBlock(distributor, ModBlocks.PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(distributor), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.BLUE, "distributor", util.select().position(distributor), 60);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Package Distributors hand a full AE2 processing pattern to a real Create sequenced assembly line.")
                .pointAt(util.vector().topOf(distributor))
                .placeNearTarget();
        scene.idle(80);

        scene.world().setBlock(inputDepot, createBlockState("depot"), false);
        scene.world().setBlock(processDepot, createBlockState("depot"), false);
        scene.world().setBlock(deployer, createDeployerFacing(Direction.DOWN), false);
        scene.world().setBlock(outputDepot, createBlockState("depot"), false);
        Selection input = util.select().position(inputDepot);
        Selection processing = util.select().position(processDepot)
                .add(util.select().position(deployer));
        Selection output = util.select().position(outputDepot);
        scene.world().showSection(input, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(processing, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(output, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.GREEN, "input", input, 50);
        scene.overlay().showOutline(PonderPalette.WHITE, "machine", processing, 50);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "output", output, 50);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("The route starts at an input depot or belt, visits deployers and spouts in order, and ends at the recovery position.")
                .pointAt(util.vector().centerOf(deployer))
                .placeNearTarget();
        scene.idle(20);
        scene.world().moveDeployer(deployer, 1, 20);
        scene.idle(20);
        scene.world().moveDeployer(deployer, -1, 20);
        scene.idle(30);

        scene.overlay().showControls(util.vector().topOf(distributor), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Basic Distributors store AE2 patterns directly. Advanced Distributors read the saved route from each Mechanical Package Pattern.")
                .pointAt(util.vector().topOf(distributor))
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
        scene.idle(10);

        // The provider must be directly adjacent to the machine block, not its working depot.
        BlockPos machineDepot = util.grid().at(2, 1, 2);
        BlockPos machine = machineDepot.above(2);
        BlockPos provider = util.grid().at(2, 3, 3);
        BlockPos supportBottom = util.grid().at(2, 1, 3);
        BlockPos supportTop = supportBottom.above();
        BlockPos extraDepot = util.grid().at(3, 1, 2);
        BlockPos extraMachine = extraDepot.above(2);

        scene.world().setBlock(supportBottom, createBlockState("andesite_casing"), false);
        scene.world().setBlock(supportTop, createBlockState("andesite_casing"), false);
        scene.world().setBlock(provider, ModBlocks.KINETIC_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(KineticPatternProviderBlock.PUSH_DIRECTION, PushDirection.NORTH), false);
        scene.world().showSection(util.select().fromTo(supportBottom, provider), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.BLUE, "provider", util.select().position(provider), 60);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Place the Kinetic Pattern Provider directly beside a Create machine and set the target side with a wrench.")
                .pointAt(util.vector().centerOf(provider))
                .placeNearTarget();
        scene.idle(80);

        scene.world().setBlock(machineDepot, createBlockState("depot"), false);
        scene.world().setBlock(machine, createDeployerFacing(Direction.DOWN), false);
        Selection primaryStation = util.select().position(machineDepot)
                .add(util.select().position(machine));
        scene.world().showSection(primaryStation, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "primary_machine", primaryStation, 60);
        scene.overlay().showText(75)
                .attachKeyFrame()
                .text("The target is the adjacent machine block; the Depot two blocks below is its working position.")
                .pointAt(util.vector().centerOf(machine))
                .placeNearTarget();
        scene.idle(20);
        scene.world().moveDeployer(machine, 1, 20);
        scene.idle(20);
        scene.world().moveDeployer(machine, -1, 20);
        scene.idle(25);

        scene.world().setBlock(extraDepot, createBlockState("depot"), false);
        scene.world().setBlock(extraMachine, createDeployerFacing(Direction.DOWN), false);
        Selection extraStation = util.select().position(extraDepot)
                .add(util.select().position(extraMachine));
        scene.world().showSection(extraStation, Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(provider), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.PARALLEL_CARD.get()));
        scene.overlay().showControls(util.vector().topOf(extraMachine), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showOutline(PonderPalette.OUTPUT, "linked_machine", extraStation, 60);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Parallel Cards unlock extra matching machines; use the Machine Linker to register each additional machine.")
                .pointAt(util.vector().centerOf(extraMachine))
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
        scene.idle(10);

        // Ordered route on three depots: input -> press two blocks above depot -> output
        BlockPos distributor = util.grid().at(2, 1, 4);
        BlockPos inputDepot = util.grid().at(1, 1, 2);
        BlockPos processDepot = util.grid().at(2, 1, 2);
        BlockPos press = processDepot.above(2);
        BlockPos outputDepot = util.grid().at(3, 1, 2);

        scene.world().setBlock(distributor, ModBlocks.BASIC_PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(distributor), Direction.DOWN);
        scene.idle(10);

        scene.world().setBlock(inputDepot, createBlockState("depot"), false);
        scene.world().setBlock(processDepot, createBlockState("depot"), false);
        scene.world().setBlock(press, createPressFacing(Direction.WEST), false);
        scene.world().setBlock(outputDepot, createBlockState("depot"), false);
        Selection route = util.select().position(inputDepot)
                .add(util.select().position(processDepot))
                .add(util.select().position(press))
                .add(util.select().position(outputDepot));
        scene.world().showSection(route, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showControls(util.vector().topOf(inputDepot), Pointing.DOWN, 35)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showControls(util.vector().topOf(press), Pointing.DOWN, 35)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showControls(util.vector().topOf(outputDepot), Pointing.DOWN, 35)
                .withItem(new ItemStack(ModItems.MACHINE_LINKER.get()));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Machine Linkers save one ordered route on a Package Distributor or Basic Package Distributor.")
                .pointAt(util.vector().centerOf(press))
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

        scene.overlay().showControls(util.vector().topOf(distributor), Pointing.DOWN, 50)
                .withItem(new ItemStack(ModItems.MECHANICAL_PACKAGE_PATTERN.get()));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Converted Mechanical Package Patterns store the original AE2 pattern plus the marked Create route for Advanced Distributors.")
                .pointAt(util.vector().topOf(distributor))
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
        scene.idle(10);

        // Left: Advanced Distributor route capacity. Right: Kinetic Provider machine capacity.
        BlockPos advanced = util.grid().at(1, 1, 3);
        BlockPos lineA = util.grid().at(0, 1, 1);
        BlockPos lineB = util.grid().at(1, 1, 1);

        BlockPos depotA = util.grid().at(3, 1, 2);
        BlockPos machineA = depotA.above(2);
        BlockPos kinetic = util.grid().at(3, 3, 3);
        BlockPos supportBottom = util.grid().at(3, 1, 3);
        BlockPos supportTop = supportBottom.above();
        BlockPos depotB = util.grid().at(4, 1, 2);
        BlockPos machineB = depotB.above(2);

        scene.world().setBlock(advanced, ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(advanced), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(advanced), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.PARALLEL_CARD.get()));
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("On an Advanced Package Distributor, each Parallel Card doubles the number of saved routes that may run together.")
                .pointAt(util.vector().topOf(advanced))
                .placeNearTarget();
        scene.idle(80);

        scene.world().setBlock(lineA, createBlockState("depot"), false);
        scene.world().setBlock(lineB, createBlockState("depot"), false);
        scene.world().showSection(util.select().position(lineA), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(lineB), Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "route_one", util.select().position(lineA), 55);
        scene.overlay().showOutline(PonderPalette.BLUE, "route_two", util.select().position(lineB), 55);
        scene.idle(65);

        scene.world().setBlock(supportBottom, createBlockState("andesite_casing"), false);
        scene.world().setBlock(supportTop, createBlockState("andesite_casing"), false);
        scene.world().setBlock(kinetic, ModBlocks.KINETIC_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(KineticPatternProviderBlock.PUSH_DIRECTION, PushDirection.NORTH), false);
        scene.world().setBlock(depotA, createBlockState("depot"), false);
        scene.world().setBlock(machineA, createDeployerFacing(Direction.DOWN), false);
        Selection primaryStation = util.select().fromTo(supportBottom, kinetic)
                .add(util.select().position(depotA))
                .add(util.select().position(machineA));
        scene.world().showSection(primaryStation, Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(kinetic), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.PARALLEL_CARD.get()));
        scene.overlay().showText(75)
                .attachKeyFrame()
                .text("On a Kinetic Pattern Provider, the card increases the number of matching machines available for dispatch.")
                .pointAt(util.vector().centerOf(kinetic))
                .placeNearTarget();
        scene.idle(85);

        scene.world().setBlock(depotB, createBlockState("depot"), false);
        scene.world().setBlock(machineB, createDeployerFacing(Direction.DOWN), false);
        Selection extraStation = util.select().position(depotB)
                .add(util.select().position(machineB));
        scene.world().showSection(extraStation, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "primary_station",
                util.select().position(depotA).add(util.select().position(machineA)), 60);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "extra_station", extraStation, 60);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("One card permits up to 16 active matching machines; two cards permit up to 32.")
                .pointAt(util.vector().centerOf(machineB))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    private static BlockState createBlockState(String path) {
        Block block = BuiltInRegistries.BLOCK.get(create(path));
        return block.defaultBlockState();
    }

    private static BlockState createDeployerFacing(Direction facing) {
        BlockState state = createBlockState("deployer");
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, facing);
        }
        return state;
    }

    private static BlockState createPressFacing(Direction facing) {
        BlockState state = createBlockState("mechanical_press");
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return state;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreatePackage.MODID, path);
    }

    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }
}
