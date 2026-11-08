package com.lhy.createpackage.client.ponder;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.ponder.element.BeltItemElement;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Loads scenes from {@code assets/createpackage/ponderer/}. Static steps follow the
 * Ponderer JSON DSL; the machine and transport steps are Create Package extensions.
 * These are scripted demonstrations, not a running AE2 network simulation.
 */
public final class PondererJsonScenes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("create", "gauges");
    private static final String[] SCENE_FILES = {
            "package_distributors.json",
            "kinetic_pattern_provider.json",
            "mechanical_route_tools.json",
            "parallel_card.json",
            "kinetic_crushing_wheels.json",
            "kinetic_mechanical_crafters.json",
            "kinetic_machine_workstations.json",
            "kinetic_batch_linking.json"
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
        if (segment.id != null && !segment.id.isBlank()) {
            path = path + "_" + segment.id;
        }
        scene.title(path, title);
        int size = segment.basePlateSize == null ? 5 : Math.max(5, segment.basePlateSize);
        int height = 5;
        for (DslStep step : segment.steps) {
            if (step != null && step.bounds != null && step.bounds.size() >= 3) {
                size = Math.max(size, Math.max(step.bounds.get(0), step.bounds.get(2)));
                height = Math.max(height, step.bounds.get(1));
            }
        }
        // The stock schematic supplies a world, not our layout. Changing only the
        // base plate leaves instructions outside its original bounds silently ignored.
        BoundingBox bounds = new BoundingBox(0, 0, 0, size - 1, height - 1, size - 1);
        scene.addInstruction(s -> s.getWorld().setBounds(bounds));
        scene.configureBasePlate(0, 0, size);
        scene.scaleSceneView(segment.viewScale == null ? 0.9f : segment.viewScale);
        scene.world().setBlocks(util.select().fromTo(0, 1, 0, size - 1, height - 1, size - 1),
                Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlocks(util.select().fromTo(0, 0, 0, size - 1, 0, size - 1),
                Blocks.GRAY_CONCRETE.defaultBlockState(), false);
        scene.showBasePlate();

        Map<String, ElementLink<BeltItemElement>> beltItems = new HashMap<>();
        for (DslStep step : segment.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            apply(scene, util, step, beltItems);
        }
        scene.markAsFinished();
    }

    private static void apply(CreateSceneBuilder scene, SceneBuildingUtil util, DslStep step,
            Map<String, ElementLink<BeltItemElement>> beltItems) {
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
            case "create_belt" -> applyCreateBelt(scene, util, step);
            case "set_kinetic_speed" -> {
                Selection selected = selection(util, step);
                if (selected != null && step.speed != null) {
                    scene.world().setKineticSpeed(selected, step.speed);
                }
            }
            case "set_deployer_item" -> {
                Selection selected = selection(util, step);
                if (selected != null) {
                    scene.world().modifyBlockEntityNBT(selected, DeployerBlockEntity.class,
                            nbt -> nbt.put("HeldItem", itemStack(step).save(new CompoundTag())));
                }
            }
            case "set_millstone_item" -> {
                BlockPos pos = blockPos(step.blockPos);
                if (pos != null) {
                    scene.world().modifyBlockEntity(pos, MillstoneBlockEntity.class, millstone -> {
                        if (Boolean.TRUE.equals(step.output)) {
                            millstone.outputInv.setStackInSlot(0, itemStack(step));
                        } else {
                            ItemStack input = itemStack(step);
                            millstone.inputInv.setStackInSlot(0, input);
                            millstone.timer = input.isEmpty() ? 0 : 50;
                        }
                    });
                }
            }
            case "create_depot_item" -> {
                BlockPos pos = blockPos(step.blockPos);
                if (pos != null) {
                    scene.world().createItemOnBeltLike(pos, direction(step.direction, Direction.UP), itemStack(step));
                }
            }
            case "remove_depot_item" -> {
                BlockPos pos = blockPos(step.blockPos);
                if (pos != null) {
                    scene.world().removeItemsFromBelt(pos);
                }
            }
            case "move_deployer" -> {
                BlockPos pos = blockPos(step.blockPos);
                if (pos != null && step.distance != null) {
                    scene.world().moveDeployer(pos, step.distance, Math.max(1, duration(step, 15)));
                }
            }
            case "create_belt_item" -> {
                BlockPos pos = blockPos(step.blockPos);
                if (pos != null && step.link != null) {
                    ElementLink<BeltItemElement> link = scene.world().createItemOnBelt(pos,
                            direction(step.direction, Direction.UP), itemStack(step));
                    beltItems.put(step.link, link);
                    scene.world().stallBeltItem(link, true);
                }
            }
            case "move_belt_item", "set_belt_item_position" -> applyBeltItemPosition(scene, step, beltItems);
            case "change_belt_item", "remove_belt_item" -> {
                ElementLink<BeltItemElement> link = beltItems.get(step.link);
                if (link != null) {
                    scene.world().changeBeltItemTo(link,
                            step.type.equalsIgnoreCase("remove_belt_item") ? ItemStack.EMPTY : itemStack(step));
                }
            }
            case "show_line" -> {
                if (step.point != null && step.point.size() >= 3 && step.point2 != null && step.point2.size() >= 3) {
                    PonderPalette color = palette(step.color);
                    scene.overlay().showLine(color == null ? PonderPalette.BLUE : color,
                            vector(step.point), vector(step.point2), duration(step, 40));
                }
            }
            case "indicate_success" -> {
                BlockPos pos = blockPos(step.blockPos);
                if (pos != null) {
                    scene.effects().indicateSuccess(pos);
                }
            }
            case "encapsulate_bounds" -> {
                // Bounds are resolved before scheduling, so the base plate is correct from frame one.
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

    private static void applyCreateBelt(CreateSceneBuilder scene, SceneBuildingUtil util, DslStep step) {
        BlockPos start = blockPos(step.blockPos);
        BlockPos end = blockPos(step.blockPos2);
        if (start == null || end == null || start.getY() != end.getY()
                || (start.getX() != end.getX() && start.getZ() != end.getZ()) || start.equals(end)) {
            throw new IllegalArgumentException("create_belt requires two distinct, aligned horizontal endpoints");
        }
        Direction facing = start.getX() == end.getX()
                ? (end.getZ() > start.getZ() ? Direction.SOUTH : Direction.NORTH)
                : (end.getX() > start.getX() ? Direction.EAST : Direction.WEST);
        int length = Math.abs(end.getX() - start.getX()) + Math.abs(end.getZ() - start.getZ()) + 1;
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.relative(facing, i);
            BeltPart part = i == 0 ? BeltPart.START : i == length - 1 ? BeltPart.END : BeltPart.MIDDLE;
            scene.world().setBlock(pos, blockState("create:belt", Map.of(
                    "facing", facing.getName(), "part", part.getSerializedName())), false);
        }
        for (int i = 0; i < length; i++) {
            int index = i;
            scene.world().modifyBlockEntity(start.relative(facing, i), BeltBlockEntity.class, belt -> {
                belt.setController(start);
                belt.beltLength = length;
                belt.index = index;
            });
        }
        scene.world().setKineticSpeed(util.select().fromTo(start, end), step.speed == null ? -32 : step.speed);
    }

    private static void applyBeltItemPosition(CreateSceneBuilder scene, DslStep step,
            Map<String, ElementLink<BeltItemElement>> beltItems) {
        ElementLink<BeltItemElement> link = beltItems.get(step.link);
        if (link == null || step.position == null) {
            throw new IllegalArgumentException("Belt movement requires an existing item link and a position");
        }
        int ticks = step.type.equalsIgnoreCase("set_belt_item_position") ? 1 : Math.max(1, duration(step, 20));
        for (int tick = 0; tick < ticks; tick++) {
            int remaining = ticks - tick;
            scene.addInstruction(s -> {
                BeltItemElement element = s.resolve(link);
                if (element != null) {
                    element.ifPresent(item -> {
                        item.prevBeltPosition = item.beltPosition;
                        item.beltPosition += (step.position - item.beltPosition) / remaining;
                        item.sideOffset = item.prevSideOffset = 0;
                        item.locked = true;
                    });
                }
            });
            if (step.type.equalsIgnoreCase("move_belt_item")) {
                scene.idle(1);
            }
        }
    }

    private static ItemStack itemStack(DslStep step) {
        ResourceLocation id = step.item == null ? null : ResourceLocation.tryParse(step.item);
        if (id == null || (step.count != null && step.count <= 0)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), step.count == null ? 1 : step.count);
        if (step.assemblyRecipe != null && step.assemblyStep != null && step.assemblyTotalSteps != null) {
            // Create 1.20.1 stores transitional progress in item NBT, not data components.
            CompoundTag assembly = new CompoundTag();
            assembly.putString("id", step.assemblyRecipe);
            assembly.putInt("Step", step.assemblyStep);
            assembly.putFloat("Progress", step.assemblyStep / (float) Math.max(1, step.assemblyTotalSteps));
            stack.getOrCreateTag().put("SequencedAssembly", assembly);
        }
        return stack;
    }

    private static Vec3 vector(List<Double> point) {
        return new Vec3(point.get(0), point.get(1), point.get(2));
    }

    private static void applyText(CreateSceneBuilder scene, DslStep step) {
        TextElementBuilder builder = scene.overlay().showText(duration(step, 60));
        if (step.keybind == null || step.keybind.isBlank()) {
            builder.text(resolveText(step.text));
        } else {
            // Supply a format parameter so normal-mode I18n text and editor
            // defaults both show the player's actual binding, including remaps.
            String keyName = I18n.get("gui.createpackage.ponder.key_unavailable");
            for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
                if (step.keybind.equals(mapping.getName())) {
                    keyName = mapping.isUnbound() ? keyName : mapping.getTranslatedKeyMessage().getString();
                    break;
                }
            }
            builder.text(resolveText(step.text), keyName);
        }
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
        Integer basePlateSize;
        Float viewScale;
        List<DslStep> steps;
    }

    static final class DslStep {
        String type;
        Integer duration;
        LocalizedText text;
        String keybind;
        List<Double> point;
        List<Double> point2;
        String link;
        Float speed;
        Float distance;
        Float position;
        Integer count;
        Boolean output;
        String assemblyRecipe;
        Integer assemblyStep;
        Integer assemblyTotalSteps;
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
