package com.lhy.createpackage.client.ponder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Checks scene contracts without booting a client or substituting for real machine tests. */
class PonderSceneContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final List<String> FILES = List.of("package_distributors.json", "kinetic_pattern_provider.json",
            "mechanical_route_tools.json", "parallel_card.json", "kinetic_crushing_wheels.json",
            "kinetic_mechanical_crafters.json", "kinetic_machine_workstations.json", "kinetic_batch_linking.json");
    private static final Set<String> SUPPORTED = Set.of("idle", "text", "show_controls", "highlight_section",
            "set_block", "replace_blocks", "show_section_and_merge", "show_section", "hide_section",
            "encapsulate_bounds", "rotate_camera_y", "zoom_scene", "create_belt", "set_kinetic_speed",
            "set_deployer_item", "set_millstone_item", "create_depot_item", "remove_depot_item", "move_deployer",
            "create_belt_item", "move_belt_item", "set_belt_item_position", "change_belt_item", "remove_belt_item",
            "show_line", "indicate_success");

    @Test
    void authoringCopiesMatchRuntimeAndAllScenesHaveUniqueIds() throws IOException {
        Set<String> sceneIds = new HashSet<>();
        for (String file : FILES) {
            String runtime = Files.readString(RESOURCES.resolve("assets/createpackage/ponderer/" + file));
            assertEquals(runtime, Files.readString(RESOURCES.resolve("ponderer_pack/scripts/" + file)), file);
            JsonObject root = JsonParser.parseString(runtime).getAsJsonObject();
            assertFalse(root.getAsJsonArray("items").isEmpty(), file);
            for (JsonElement scene : root.getAsJsonArray("scenes")) {
                assertTrue(sceneIds.add(root.get("id").getAsString() + "/" + scene.getAsJsonObject().get("id").getAsString()));
            }
        }
    }

    @Test
    void normalModeCanResolveEverySceneTitleAndTextInBothLanguages() throws IOException {
        // Ponder's normal mode calls I18n using these keys; inline DSL text is
        // only an editing-mode default. Inspect the actual processed resources.
        for (String language : List.of("en_us", "zh_cn")) {
            try (var stream = getClass().getResourceAsStream("/assets/createpackage/lang/" + language + ".json")) {
                assertNotNull(stream, "Packaged language resource " + language);
                JsonObject translations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Set<String> expectedKeys = new HashSet<>();
                List<String> scenePrefixes = new ArrayList<>();
                for (String file : FILES) {
                    JsonObject document = load(file);
                    String[] id = document.get("id").getAsString().split(":", 2);
                    String prefix = id[0] + ".ponder." + id[1];
                    scenePrefixes.add(prefix);
                    for (JsonElement element : document.getAsJsonArray("scenes")) {
                        JsonObject segment = element.getAsJsonObject();
                        String suffix = segment.has("id") ? segment.get("id").getAsString() : "";
                        String scenePrefix = prefix + (suffix.isBlank() ? "" : "_" + suffix);
                        String header = scenePrefix + ".header";
                        expectedKeys.add(header);
                        assertTrue(translations.has(header), "Missing normal-mode title: " + header);
                        JsonObject title = segment.has("title") ? segment.getAsJsonObject("title") : document.getAsJsonObject("title");
                        assertEquals(title.get(language).getAsString(), translations.get(header).getAsString(), header);
                        int textIndex = 1;
                        for (JsonElement instruction : segment.getAsJsonArray("steps")) {
                            JsonObject step = instruction.getAsJsonObject();
                            if (!step.get("type").getAsString().equalsIgnoreCase("text")) continue;
                            String key = scenePrefix + ".text_" + textIndex++;
                            expectedKeys.add(key);
                            assertTrue(translations.has(key), "Missing normal-mode text: " + key);
                            assertEquals(step.getAsJsonObject("text").get(language).getAsString(),
                                    translations.get(key).getAsString(), key);
                            if (step.has("keybind")) {
                                assertEquals("key.ftbultimine", step.get("keybind").getAsString());
                                String formatted = String.format(translations.get(key).getAsString(), "Remapped Ultimine Key");
                                assertTrue(formatted.contains("Remapped Ultimine Key"), key);
                                assertFalse(formatted.contains("%s"), key);
                                assertTrue(translations.has("gui.createpackage.ponder.key_unavailable"));
                            }
                        }
                    }
                }
                Set<String> actualKeys = new HashSet<>();
                for (String key : translations.keySet()) {
                    if (scenePrefixes.stream().anyMatch(prefix -> key.startsWith(prefix + ".") || key.startsWith(prefix + "_")))
                        actualKeys.add(key);
                }
                assertEquals(expectedKeys, actualKeys, "Remove stale scene keys when renaming segments");
                String screenshotKey = "createpackage.ponder.mechanical_route_tools_linker.text_1";
                assertTrue(translations.has(screenshotKey));
                assertNotEquals(screenshotKey, translations.get(screenshotKey).getAsString());
            }
        }
    }

    @Test
    void instructionsStayWithinBoundsAndOperateOnExistingMachineTypes() throws IOException {
        for (String file : FILES) {
            for (JsonElement segment : load(file).getAsJsonArray("scenes")) {
                JsonObject scene = segment.getAsJsonObject();
                String label = file + "/" + scene.get("id").getAsString();
                int size = scene.has("basePlateSize") ? scene.get("basePlateSize").getAsInt() : 5;
                int height = 5;
                for (JsonElement element : scene.getAsJsonArray("steps")) {
                    JsonObject step = element.getAsJsonObject();
                    if (step.has("bounds")) {
                        JsonArray bounds = step.getAsJsonArray("bounds");
                        size = Math.max(size, Math.max(bounds.get(0).getAsInt(), bounds.get(2).getAsInt()));
                        height = Math.max(height, bounds.get(1).getAsInt());
                    }
                }
                Map<List<Integer>, String> blocks = new HashMap<>();
                Map<String, Integer> belts = new HashMap<>();
                Map<List<Integer>, Integer> beltLengths = new HashMap<>();
                for (JsonElement element : scene.getAsJsonArray("steps")) {
                    JsonObject step = element.getAsJsonObject();
                    String type = step.get("type").getAsString();
                    assertTrue(SUPPORTED.contains(type), label + ": " + type);
                    for (String key : List.of("blockPos", "blockPos2", "point", "point2")) {
                        if (!step.has(key)) continue;
                        JsonArray pos = step.getAsJsonArray(key);
                        assertEquals(3, pos.size(), label + ": " + key);
                        for (int axis = 0; axis < 3; axis++) {
                            double coordinate = pos.get(axis).getAsDouble();
                            assertTrue(coordinate >= 0 && coordinate < (axis == 1 ? height : size), label + ": " + pos);
                        }
                    }
                    if (type.equals("set_block")) blocks.put(pos(step, "blockPos"), step.get("block").getAsString());
                    if (type.equals("create_belt")) {
                        List<Integer> a = pos(step, "blockPos"), b = pos(step, "blockPos2");
                        assertEquals(a.get(1), b.get(1), label);
                        assertTrue(a.get(0).equals(b.get(0)) ^ a.get(2).equals(b.get(2)), label);
                        int length = Math.abs(a.get(0) - b.get(0)) + Math.abs(a.get(2) - b.get(2)) + 1;
                        for (int i = 0; i < length; i++) {
                            List<Integer> p = List.of(a.get(0) + i * Integer.signum(b.get(0) - a.get(0)), a.get(1),
                                    a.get(2) + i * Integer.signum(b.get(2) - a.get(2)));
                            blocks.put(p, "create:belt");
                            beltLengths.put(p, length);
                        }
                    }
                    if (type.equals("set_deployer_item") || type.equals("move_deployer"))
                        assertEquals("create:deployer", blocks.get(pos(step, "blockPos")), label);
                    if (type.equals("set_millstone_item"))
                        assertEquals("create:millstone", blocks.get(pos(step, "blockPos")), label);
                    if (type.equals("create_depot_item") || type.equals("remove_depot_item"))
                        assertEquals("create:depot", blocks.get(pos(step, "blockPos")), label);
                    if (type.equals("create_belt_item")) {
                        assertEquals("create:belt", blocks.get(pos(step, "blockPos")), label);
                        assertNull(belts.put(step.get("link").getAsString(), beltLengths.get(pos(step, "blockPos"))), label);
                    }
                    if (Set.of("move_belt_item", "set_belt_item_position", "change_belt_item", "remove_belt_item").contains(type)) {
                        Integer length = belts.get(step.get("link").getAsString());
                        assertNotNull(length, label + ": missing belt item");
                        if (step.has("position")) {
                            double position = step.get("position").getAsDouble();
                            assertTrue(position >= 0 && position < length, label);
                        }
                    }
                    if (type.equals("text")) {
                        assertFalse(step.getAsJsonObject("text").get("zh_cn").getAsString().isBlank(), label);
                        assertFalse(step.getAsJsonObject("text").get("en_us").getAsString().isBlank(), label);
                    }
                    if (type.equals("show_controls")) {
                        assertTrue(Set.of("left", "right", "scroll").contains(step.get("action").getAsString()), label);
                        if (step.has("item") && step.get("item").getAsString().equals("createpackage:parallel_card")) {
                            assertEquals("right", step.get("action").getAsString(), label);
                            assertTrue(step.get("whileSneaking").getAsBoolean(), label);
                        }
                    }
                }
            }
        }
    }

    @Test
    void assemblyDemonstratesTheActualRecipeAndKeepsProgressAcrossBothReturns() throws IOException {
        JsonObject recipe = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                "data/createpackage/recipe/sequenced_assembly/package_distributor.json"))).getAsJsonObject();
        JsonArray sequence = recipe.getAsJsonArray("sequence");
        int total = sequence.size() * recipe.get("loops").getAsInt();
        JsonObject cycle = segment("package_distributors.json", "assembly_cycle");
        Map<List<Integer>, String> tools = new HashMap<>();
        List<String> usedTools = new ArrayList<>();
        List<Integer> progress = new ArrayList<>();
        int inputs = 0, returns = 0, results = 0;
        String link = null;
        for (JsonElement element : cycle.getAsJsonArray("steps")) {
            JsonObject step = element.getAsJsonObject();
            String type = step.get("type").getAsString();
            if (type.equals("set_deployer_item")) tools.put(pos(step, "blockPos"), step.get("item").getAsString());
            if (type.equals("move_deployer") && step.get("distance").getAsDouble() > 0)
                usedTools.add(tools.get(pos(step, "blockPos")));
            if (type.equals("create_belt_item")) {
                inputs++;
                assertEquals("create:golden_sheet", step.get("item").getAsString());
                link = step.get("link").getAsString();
            }
            if (step.has("assemblyStep")) {
                progress.add(step.get("assemblyStep").getAsInt());
                assertEquals(total, step.get("assemblyTotalSteps").getAsInt());
                assertEquals("createpackage:sequenced_assembly/package_distributor", step.get("assemblyRecipe").getAsString());
                assertEquals(link, step.get("link").getAsString());
            }
            if (type.equals("set_belt_item_position") && step.get("position").getAsDouble() == 0.5) {
                returns++;
                assertEquals(link, step.get("link").getAsString());
            }
            if (type.equals("change_belt_item") && step.get("item").getAsString().equals("createpackage:package_distributor")) results++;
        }
        assertEquals(1, inputs, "Consume the base once, not once per loop");
        assertEquals(recipe.get("loops").getAsInt() - 1, returns);
        assertEquals(1, results);
        assertEquals(total, usedTools.size());
        for (int i = 0; i < total; i++) {
            JsonObject action = sequence.get(i % sequence.size()).getAsJsonObject();
            assertTrue(action.get("keep_held_item").getAsBoolean());
            assertEquals(action.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString(), usedTools.get(i));
        }
        assertEquals(java.util.stream.IntStream.range(1, total).boxed().toList(), progress);
    }

    @Test
    void parallelCardScenesActuallyPopulateAndAnimateTheAdvertisedCapacity() throws IOException {
        assertParallelCapacity("kinetic_before_after", 16, 1);
        assertParallelCapacity("kinetic_max_capacity", 64, 4);
    }

    private static void assertParallelCapacity(String id, int capacity, int cards) throws IOException {
        JsonObject scene = segment("parallel_card.json", id);
        Map<List<Integer>, String> blocks = new HashMap<>();
        Set<List<Integer>> machines = new HashSet<>();
        Set<List<Integer>> tools = new HashSet<>();
        Set<List<Integer>> powered = new HashSet<>();
        Map<Integer, Set<List<Integer>>> suppliedAt = new HashMap<>();
        Map<Integer, Set<List<Integer>>> extendedAt = new HashMap<>();
        Map<Integer, Set<List<Integer>>> collectedAt = new HashMap<>();
        int installedCards = 0, time = 0;
        for (JsonElement element : scene.getAsJsonArray("steps")) {
            JsonObject step = element.getAsJsonObject();
            String type = step.get("type").getAsString();
            if (type.equals("set_block")) {
                List<Integer> p = pos(step, "blockPos");
                String block = step.get("block").getAsString();
                blocks.put(p, block);
                if (block.equals("create:deployer")) {
                    assertEquals("down", step.getAsJsonObject("blockProperties").get("facing").getAsString(), id);
                    assertEquals("true", step.getAsJsonObject("blockProperties").get("axis_along_first").getAsString(), id);
                    machines.add(p);
                }
                if (block.equals("create:creative_motor"))
                    assertEquals("west", step.getAsJsonObject("blockProperties").get("facing").getAsString(), id);
            }
            if (type.equals("set_deployer_item")) {
                assertEquals("create:sand_paper", step.get("item").getAsString(), id);
                tools.add(pos(step, "blockPos"));
            }
            if (type.equals("set_kinetic_speed") && step.get("speed").getAsDouble() != 0) {
                List<Integer> a = pos(step, "blockPos");
                List<Integer> b = step.has("blockPos2") ? pos(step, "blockPos2") : a;
                for (List<Integer> p : machines) {
                    boolean inside = true;
                    for (int axis = 0; axis < 3; axis++)
                        inside &= p.get(axis) >= Math.min(a.get(axis), b.get(axis))
                                && p.get(axis) <= Math.max(a.get(axis), b.get(axis));
                    if (inside) powered.add(p);
                }
            }
            if (type.equals("show_controls") && step.has("item")
                    && step.get("item").getAsString().equals("createpackage:parallel_card")) {
                assertTrue(step.get("whileSneaking").getAsBoolean(), id);
                installedCards++;
            }
            if (type.equals("create_depot_item") && step.get("item").getAsString().equals("create:rose_quartz"))
                suppliedAt.computeIfAbsent(time, ignored -> new HashSet<>()).add(pos(step, "blockPos"));
            if (type.equals("move_deployer") && step.get("distance").getAsDouble() > 0)
                extendedAt.computeIfAbsent(time, ignored -> new HashSet<>()).add(pos(step, "blockPos"));
            if (type.equals("remove_depot_item"))
                collectedAt.computeIfAbsent(time, ignored -> new HashSet<>()).add(pos(step, "blockPos"));
            if (type.equals("idle") || type.equals("move_belt_item")) time += step.get("duration").getAsInt();
        }
        assertEquals(capacity, machines.size(), id + " must show every advertised machine");
        assertEquals(cards, installedCards, id + " must actually show installing the card count");
        assertEquals(machines, tools, id + " must equip every machine");
        assertEquals(machines, powered, id + " must power every machine");
        // Every row is a continuous X-axis shaft through the deployers, driven
        // from its east end. Animation speeds alone must not disguise missing drives.
        Map<Integer, Set<Integer>> rows = new HashMap<>();
        for (List<Integer> p : machines) rows.computeIfAbsent(p.get(2), ignored -> new HashSet<>()).add(p.get(0));
        for (Map.Entry<Integer, Set<Integer>> row : rows.entrySet()) {
            int firstX = row.getValue().stream().mapToInt(Integer::intValue).min().orElseThrow();
            int lastX = row.getValue().stream().mapToInt(Integer::intValue).max().orElseThrow();
            for (int x = firstX; x <= lastX; x++)
                assertEquals("create:deployer", blocks.get(List.of(x, 3, row.getKey())), id + " continuous drive");
            assertEquals("create:creative_motor", blocks.get(List.of(lastX + 1, 3, row.getKey())), id + " row motor");
        }
        Set<List<Integer>> depots = new HashSet<>();
        for (List<Integer> p : machines) {
            List<Integer> workingPosition = List.of(p.get(0), p.get(1) - 2, p.get(2));
            assertEquals("create:depot", blocks.get(workingPosition), id + " working position " + workingPosition);
            depots.add(workingPosition);
        }
        assertTrue(suppliedAt.containsValue(depots), id + " must supply the full capacity in one tick");
        assertTrue(extendedAt.containsValue(machines), id + " must animate the full capacity in one tick");
        assertTrue(collectedAt.containsValue(depots), id + " must collect every working position in one tick");
    }

    @Test
    void allBundledTutorialsAreRegisteredForPlayback() throws IOException {
        try (var files = Files.list(RESOURCES.resolve("assets/createpackage/ponderer"))) {
            assertEquals(new HashSet<>(FILES), files.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        String player = Files.readString(Path.of("src/main/java/com/lhy/createpackage/client/ponder/PondererJsonScenes.java"));
        for (String file : FILES) assertTrue(player.contains("\"" + file + "\""), "Unregistered tutorial: " + file);
    }

    @Test
    void crushingTutorialTargetsTheGapAndRecoversBelowIt() throws IOException {
        JsonObject scene = segment("kinetic_crushing_wheels.json", "controller_target");
        Map<List<Integer>, JsonObject> blocks = finalBlocks(scene);
        JsonObject provider = blocks.get(List.of(3, 3, 2));
        assertEquals("south", provider.getAsJsonObject("blockProperties").get("push_direction").getAsString());
        JsonObject controller = blocks.get(List.of(3, 3, 3));
        assertEquals("create:crushing_wheel_controller", controller.get("block").getAsString());
        assertEquals("down", controller.getAsJsonObject("blockProperties").get("facing").getAsString());
        assertEquals("true", controller.getAsJsonObject("blockProperties").get("valid").getAsString());
        assertEquals("create:depot", blocks.get(List.of(3, 2, 3)).get("block").getAsString());
        for (int x : List.of(2, 4)) {
            JsonObject wheel = blocks.get(List.of(x, 3, 3));
            assertEquals("create:crushing_wheel", wheel.get("block").getAsString());
            assertEquals("z", wheel.getAsJsonObject("blockProperties").get("axis").getAsString());
            assertEquals("create:shaft", blocks.get(List.of(x, 3, 4)).get("block").getAsString());
            assertEquals("create:creative_motor", blocks.get(List.of(x, 3, 5)).get("block").getAsString());
        }
        assertEquals(-64.0, speedAt(scene, List.of(2, 3, 3)));
        assertEquals(64.0, speedAt(scene, List.of(4, 3, 3)));
    }

    @Test
    void crafterTutorialHasTwentyOneDrivenCellsAndValidInPlaneExits() throws IOException {
        for (String id : List.of("auto_layout", "output_and_parallel")) {
            JsonObject scene = segment("kinetic_mechanical_crafters.json", id);
            Map<List<Integer>, JsonObject> blocks = finalBlocks(scene);
            Set<List<Integer>> crafters = new HashSet<>();
            blocks.forEach((p, b) -> { if (b.get("block").getAsString().equals("create:mechanical_crafter")) crafters.add(p); });
            assertEquals(21, crafters.size(), id);
            boolean direct = id.equals("auto_layout");
            List<Integer> outlet = direct ? List.of(1, 3, 4) : List.of(7, 3, 4);
            List<Integer> cog = direct ? List.of(7, 3, 4) : List.of(4, 6, 4);
            List<Integer> motor = List.of(cog.get(0), cog.get(1), cog.get(2) + 1);
            assertEquals("create:cogwheel", blocks.get(cog).get("block").getAsString());
            assertEquals("z", blocks.get(cog).getAsJsonObject("blockProperties").get("axis").getAsString());
            assertEquals("create:creative_motor", blocks.get(motor).get("block").getAsString());
            assertEquals(direct ? "createpackage:kinetic_pattern_provider" : "minecraft:chest", blocks.get(outlet).get("block").getAsString());
            for (List<Integer> p : crafters) {
                assertEquals("north", blocks.get(p).getAsJsonObject("blockProperties").get("facing").getAsString());
                assertEquals((p.get(0) + p.get(1)) % 2 == 0 ? 32.0 : -32.0, speedAt(scene, p));
                // Follow each final arrow to ensure this teaching layout neither loops
                // nor sends the product off the back of the wall.
                Set<List<Integer>> seen = new HashSet<>();
                List<Integer> current = p;
                while (crafters.contains(current)) {
                    assertTrue(seen.add(current), id + " arrow loop at " + current);
                    String pointing = blocks.get(current).getAsJsonObject("blockProperties").get("pointing").getAsString();
                    int dx = pointing.equals("left") ? 1 : pointing.equals("right") ? -1 : 0;
                    int dy = pointing.equals("up") ? 1 : pointing.equals("down") ? -1 : 0;
                    current = List.of(current.get(0) + dx, current.get(1) + dy, current.get(2));
                }
                assertEquals(outlet, current, id + " invalid product exit");
            }
        }
    }

    private static Map<List<Integer>, JsonObject> finalBlocks(JsonObject scene) {
        Map<List<Integer>, JsonObject> result = new HashMap<>();
        for (JsonElement e : scene.getAsJsonArray("steps")) {
            JsonObject step = e.getAsJsonObject();
            if (step.get("type").getAsString().equals("set_block")) result.put(pos(step, "blockPos"), step);
        }
        return result;
    }

    private static double speedAt(JsonObject scene, List<Integer> p) {
        double result = 0;
        for (JsonElement e : scene.getAsJsonArray("steps")) {
            JsonObject step = e.getAsJsonObject();
            if (!step.get("type").getAsString().equals("set_kinetic_speed")) continue;
            List<Integer> a = pos(step, "blockPos");
            List<Integer> b = step.has("blockPos2") ? pos(step, "blockPos2") : a;
            boolean inside = true;
            for (int i = 0; i < 3; i++) inside &= p.get(i) >= Math.min(a.get(i), b.get(i)) && p.get(i) <= Math.max(a.get(i), b.get(i));
            if (inside) result = step.get("speed").getAsDouble();
        }
        return result;
    }

    private static JsonObject load(String file) throws IOException {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve("assets/createpackage/ponderer/" + file))).getAsJsonObject();
    }

    private static JsonObject segment(String file, String id) throws IOException {
        for (JsonElement element : load(file).getAsJsonArray("scenes"))
            if (element.getAsJsonObject().get("id").getAsString().equals(id)) return element.getAsJsonObject();
        throw new AssertionError("Missing scene " + id);
    }

    private static List<Integer> pos(JsonObject step, String key) {
        JsonArray value = step.getAsJsonArray(key);
        return List.of(value.get(0).getAsInt(), value.get(1).getAsInt(), value.get(2).getAsInt());
    }
}
