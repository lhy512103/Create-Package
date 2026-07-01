package com.lhy.createpackage.client.ponder;

import com.lhy.createpackage.CreatePackage;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class CreatePackagePonderScenes {
    private static final ResourceLocation DEPOT = create("depot");
    private static final ResourceLocation DEPLOYER_PROCESSING = create("deployer/processing");
    private static final ResourceLocation REPACKAGER = create("high_logistics/repackager");

    private CreatePackagePonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(
                        id("package_distributor"),
                        id("basic_package_distributor"),
                        id("advanced_package_distributor"),
                        id("incomplete_package_distributor"))
                .addStoryBoard(DEPOT, CreatePackagePonderScenes::packageDistributors,
                        CreatePackagePonderTags.CREATE_PACKAGE);

        helper.forComponents(id("kinetic_pattern_provider"))
                .addStoryBoard(DEPLOYER_PROCESSING, CreatePackagePonderScenes::kineticPatternProvider,
                        CreatePackagePonderTags.CREATE_PACKAGE);

        helper.forComponents(
                        id("machine_linker"),
                        id("mechanical_pattern_converter"),
                        id("mechanical_package_pattern"))
                .addStoryBoard(DEPOT, CreatePackagePonderScenes::routeTools,
                        CreatePackagePonderTags.CREATE_PACKAGE);

        helper.forComponents(id("parallel_card"))
                .addStoryBoard(REPACKAGER, CreatePackagePonderScenes::parallelCard,
                        CreatePackagePonderTags.CREATE_PACKAGE);
    }

    public static void packageDistributors(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("package_distributors", "Create Package Distributors");
        open(scene, util);
        text(scene, "Package Distributors hand a full AE2 processing pattern to a real Create sequenced assembly line.");
        text(scene, "The route starts at an input depot or belt, visits deployers and spouts in order, and ends at the recovery position.");
        text(scene, "Basic Distributors store AE2 patterns directly. Advanced Distributors read the saved route from each Mechanical Package Pattern.");
        scene.markAsFinished();
    }

    public static void kineticPatternProvider(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("kinetic_pattern_provider", "Kinetic Pattern Provider");
        open(scene, util);
        text(scene, "Kinetic Pattern Providers face one Create machine and supply its working position, held items, or fluids.");
        text(scene, "Set the target side with a wrench. Add Parallel Cards and link matching machines for multi-machine dispatch.");
        text(scene, "Saws, millstones, and crushing wheels still need a recoverable output belt, depot, or inventory at the machine output.");
        scene.markAsFinished();
    }

    public static void routeTools(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("mechanical_route_tools", "Mechanical Route Tools");
        open(scene, util);
        text(scene, "Machine Linkers save one ordered route on a Package Distributor or Basic Package Distributor.");
        text(scene, "Mechanical Pattern Converters and Mechanical Package Patterns can mark route blocks directly.");
        text(scene, "Converted Mechanical Package Patterns store the original AE2 pattern plus the marked Create route for Advanced Distributors.");
        scene.markAsFinished();
    }

    public static void parallelCard(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("parallel_card", "Parallel Card");
        open(scene, util);
        text(scene, "Parallel Cards upgrade Advanced Package Distributors and Kinetic Pattern Providers.");
        text(scene, "Advanced distributors use them for up to four disjoint saved routes.");
        text(scene, "Kinetic providers use one card for up to 16 matching machines, or two cards for up to 32.");
        scene.markAsFinished();
    }

    private static void open(SceneBuilder scene, SceneBuildingUtil util) {
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(10);
    }

    private static void text(SceneBuilder scene, String text) {
        scene.overlay().showText(80)
                .text(text)
                .independent(20);
        scene.idle(90);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreatePackage.MODID, path);
    }

    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }
}
