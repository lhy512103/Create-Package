package com.lhy.createpackage.client.ponder;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

/**
 * Loads Ponderer JSON DSL scenes from {@code assets/createpackage/ponderer/} and plays them
 * with Create Ponder. The JSON is the same format Ponderer uses, so scenes can be edited
 * in-game with Ponderer and dropped back into this folder.
 */
public final class PondererJsonScenes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("create", "gauges");
    private static final String[] SCENE_FILES = {
            "package_distributors.json",
            "kinetic_pattern_provider.json",
            "mechanical_route_tools.json",
            "parallel_card.json"
    };

    private PondererJsonScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        for (String file : SCENE_FILES) {
            DslScene scene = load(file);
            if (scene == null || scene.items == null || scene.items.isEmpty()) {
                LOGGER.warn("Skipping empty Ponderer scene {}", file);
                continue;
            }
            ResourceLocation[] components = scene.items.stream()
                    .map(ResourceLocation::tryParse)
                    .filter(id -> id != null)
                    .toArray(ResourceLocation[]::new);
            if (components.length == 0) {
                continue;
            }
            var registration = helper.forComponents(components);
            List<DslSegment> segments = scene.scenes == null ? List.of() : scene.scenes;
            for (int i = 0; i < segments.size(); i++) {
                DslSegment segment = segments.get(i);
                if (segment == null || segment.steps == null || segment.steps.isEmpty()) {
                    continue;
                }
                ResourceLocation[] tags = scene.tags == null ? new ResourceLocation[0]
                        : scene.tags.stream()
                                .map(ResourceLocation::tryParse)
                                .filter(id -> id != null)
                                .toArray(ResourceLocation[]::new);
                if (tags.length == 0) {
                    registration.addStoryBoard(BASE, (builder, util) -> play(builder, util, scene, segment));
                } else {
                    registration.addStoryBoard(BASE, (builder, util) -> play(builder, util, scene, segment), tags);
                }
            }
        }
    }

    private static DslScene load(String file) {
        String path = "/assets/createpackage/ponderer/" + file;
        try (var stream = PondererJsonScenes.class.getResourceAsStream(path)) {
            if (stream == null) {
                LOGGER.error("Missing Ponderer scene {}", path);
                return null;
            }
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), DslScene.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load Ponderer scene {}", file, e);
            return null;
        }
    }

    private static void play(SceneBuilder builder, SceneBuildingUtil util, DslScene dsl, DslSegment segment) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        String title = resolveText(segment.title);
        if (title.isBlank()) {
            title = resolveText(dsl.title);
        }
        ResourceLocation sceneId = dsl.id == null ? null : ResourceLocation.tryParse(dsl.id);
        String path = sceneId == null ? "scene" : sceneId.getPath();
        scene.title(path, title);
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, 4, 4, 4), Blocks.AIR.defaultBlockState(), false);

        for (DslStep step : segment.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            apply(scene, util, step);
        }
        scene.markAsFinished();
    }

    private static void apply(CreateSceneBuilder scene, SceneBuildingUtil util, DslStep step) {
        if (Boolean.TRUE.equals(step.attachKeyFrame)) {
            scene.addKeyframe();
        }
        switch (step.type.toLowerCase(Locale.ROOT)) {
            case "idle" -> scene.idle(duration(step, 20));
            case "text" -> applyText(scene, step);
            case "show_controls" -> applyControls(scene, step);
            case "highlight_section" -> applyHighlight(scene, util, step);
            case "set_block" -> applySetBlock(scene, step);
            case "replace_blocks" -> applyReplace(scene, util, step);
            case "show_section_and_merge", "show_section" -> applyShowSection(scene, util, step);
            case "hide_section" -> applyHideSection(scene, util, step);
            case "encapsulate_bounds" -> {
                if (step.bounds != null && step.bounds.size() >= 3) {
                    scene.configureBasePlate(0, 0, Math.max(5, step.bounds.get(0)));
                }
            }
            case "rotate_camera_y" -> scene.rotateCameraY(step.degrees == null ? 90f : step.degrees);
            case "zoom_scene" -> {
                if (step.scale != null && step.scale > 0) {
                    scene.scaleSceneView(step.scale);
                }
            }
            case "show_structure" -> {
                // Structures are optional; this pack builds with set_block instead.
            }
            default -> LOGGER.warn("Unsupported Ponderer step type '{}'", step.type);
        }
    }

    private static void applyText(CreateSceneBuilder scene, DslStep step) {
        TextElementBuilder builder = scene.overlay()
                .showText(duration(step, 60))
                .text(resolveText(step.text));
        if (step.point != null && step.point.size() >= 3) {
            builder.pointAt(new Vec3(step.point.get(0), step.point.get(1), step.point.get(2)));
        }
        PonderPalette palette = palette(step.color);
        if (palette != null) {
            builder.colored(palette);
        }
        if (Boolean.TRUE.equals(step.placeNearTarget)) {
            builder.placeNearTarget();
        }
    }

    private static void applyControls(CreateSceneBuilder scene, DslStep step) {
        Vec3 point = step.point != null && step.point.size() >= 3
                ? new Vec3(step.point.get(0), step.point.get(1), step.point.get(2))
                : Vec3.ZERO;
        InputElementBuilder builder = scene.overlay().showControls(point, pointing(step.direction), duration(step, 60));
        if (step.action != null) {
            switch (step.action.toLowerCase(Locale.ROOT)) {
                case "left" -> builder.leftClick();
                case "right" -> builder.rightClick();
                case "scroll" -> builder.scroll();
                default -> {
                }
            }
        }
        if (step.item != null && !step.item.isBlank()) {
            ResourceLocation itemId = ResourceLocation.tryParse(step.item);
            if (itemId != null) {
                builder.withItem(new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
            }
        }
        if (Boolean.TRUE.equals(step.whileSneaking)) {
            builder.whileSneaking();
        }
        if (Boolean.TRUE.equals(step.whileCTRL)) {
            builder.whileCTRL();
        }
    }

    private static void applyHighlight(CreateSceneBuilder scene, SceneBuildingUtil util, DslStep step) {
        Selection selection = selection(util, step);
        if (selection == null) {
            return;
        }
        PonderPalette palette = palette(step.color);
        if (palette == null) {
            palette = PonderPalette.BLUE;
        }
        scene.overlay().showOutline(palette, new Object(), selection, duration(step, 40));
    }

    private static void applySetBlock(CreateSceneBuilder scene, DslStep step) {
        BlockPos pos = blockPos(step.blockPos);
        if (pos == null || step.block == null) {
            return;
        }
        scene.world().setBlock(pos, blockState(step.block, step.blockProperties), false);
    }

    private static void applyReplace(CreateSceneBuilder scene, SceneBuildingUtil util, DslStep step) {
        Selection selection = selection(util, step);
        if (selection == null || step.block == null) {
            return;
        }
        scene.world().setBlocks(selection, blockState(step.block, step.blockProperties), false);
    }

    private static void applyShowSection(CreateSceneBuilder scene, SceneBuildingUtil util, DslStep step) {
        Selection selection = selection(util, step);
        if (selection == null) {
            return;
        }
        scene.world().showSection(selection, direction(step.direction, Direction.DOWN));
    }

    private static void applyHideSection(CreateSceneBuilder scene, SceneBuildingUtil util, DslStep step) {
        Selection selection = selection(util, step);
        if (selection == null) {
            return;
        }
        scene.world().hideSection(selection, direction(step.direction, Direction.UP));
    }

    private static Selection selection(SceneBuildingUtil util, DslStep step) {
        BlockPos pos1 = blockPos(step.blockPos);
        if (pos1 == null) {
            return null;
        }
        BlockPos pos2 = blockPos(step.blockPos2);
        return pos2 == null ? util.select().position(pos1) : util.select().fromTo(pos1, pos2);
    }

    private static BlockPos blockPos(List<Integer> values) {
        if (values == null || values.size() < 3) {
            return null;
        }
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }

    private static BlockState blockState(String id, Map<String, String> properties) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Block block = location == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(location);
        BlockState state = block.defaultBlockState();
        if (properties == null || properties.isEmpty()) {
            return state;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property != null) {
                state = setProperty(state, property, entry.getValue());
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(t -> state.setValue(property, t)).orElse(state);
    }

    private static int duration(DslStep step, int fallback) {
        return step.duration == null ? fallback : Math.max(0, step.duration);
    }

    private static String resolveText(LocalizedText text) {
        if (text == null) {
            return "";
        }
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (language != null && language.toLowerCase(Locale.ROOT).startsWith("zh") && text.zh_cn != null) {
            return text.zh_cn;
        }
        if (text.en_us != null) {
            return text.en_us;
        }
        return text.zh_cn == null ? "" : text.zh_cn;
    }

    private static PonderPalette palette(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        return switch (color.toLowerCase(Locale.ROOT)) {
            case "white" -> PonderPalette.WHITE;
            case "black" -> PonderPalette.BLACK;
            case "red" -> PonderPalette.RED;
            case "green" -> PonderPalette.GREEN;
            case "blue" -> PonderPalette.BLUE;
            case "input" -> PonderPalette.INPUT;
            case "output" -> PonderPalette.OUTPUT;
            default -> null;
        };
    }

    private static Pointing pointing(String direction) {
        if (direction == null) {
            return Pointing.DOWN;
        }
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "up" -> Pointing.UP;
            case "left" -> Pointing.LEFT;
            case "right" -> Pointing.RIGHT;
            default -> Pointing.DOWN;
        };
    }

    private static Direction direction(String value, Direction fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> fallback;
        };
    }

    static final class DslScene {
        String id;
        List<String> items;
        LocalizedText title;
        List<String> tags;
        List<DslSegment> scenes;
    }

    static final class DslSegment {
        String id;
        LocalizedText title;
        List<DslStep> steps;
    }

    static final class DslStep {
        String type;
        Integer duration;
        LocalizedText text;
        List<Double> point;
        String direction;
        String action;
        String item;
        String color;
        String block;
        Map<String, String> blockProperties;
        List<Integer> blockPos;
        List<Integer> blockPos2;
        List<Integer> bounds;
        Float degrees;
        Float scale;
        Boolean placeNearTarget;
        Boolean attachKeyFrame;
        Boolean whileSneaking;
        Boolean whileCTRL;
    }

    static final class LocalizedText {
        String en_us;
        String zh_cn;
    }
}
