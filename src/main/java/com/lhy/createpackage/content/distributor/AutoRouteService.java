package com.lhy.createpackage.content.distributor;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.content.route.RouteTools;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;
import static com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Rebuilds a package-distributor / mechanical-route from manually selected endpoints.
 *
 * <p>Primary path: straight same-Y corridor. Optional secondary path: one-bend L-shaped same-Y
 * corridor when enabled in server config.
 *
 * <p>Supported held tools:
 * <ul>
 *   <li>Machine Linker bound to a Package Distributor that stores links</li>
 *   <li>Mechanical Pattern Converter with at least two marked route endpoints</li>
 *   <li>Mechanical Package Pattern with at least two marked route endpoints</li>
 * </ul>
 */
public final class AutoRouteService {
    public record Result(boolean success, String messageKey, int linkedCount) {}

    private static final String MESSAGE_PREFIX = "auto_route";

    public static Result search(ServerPlayer player) {
        ItemStack tool = routeToolInHand(player);
        if (tool.isEmpty()) {
            return new Result(false, MESSAGE_PREFIX + ".no_tool", 0);
        }
        if (tool.is(ModItems.MACHINE_LINKER.get())) {
            return searchLinker(player, tool);
        }
        if (tool.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())) {
            return searchConverter(player, tool);
        }
        if (tool.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            return searchPattern(player, tool);
        }
        return new Result(false, MESSAGE_PREFIX + ".no_tool", 0);
    }

    private static Result searchLinker(ServerPlayer player, ItemStack linker) {
        GlobalPos selected = linker.get(ModComponents.LINKED_DISTRIBUTOR.get());
        if (selected == null) {
            return new Result(false, MESSAGE_PREFIX + ".no_selection", 0);
        }
        if (!selected.dimension().equals(player.level().dimension())) {
            return new Result(false, MESSAGE_PREFIX + ".wrong_dimension", 0);
        }

        if (player.level().getBlockEntity(selected.pos()) instanceof KineticPatternProviderBlockEntity) {
            return new Result(false, MESSAGE_PREFIX + ".not_distributor", 0);
        }
        if (!(player.level().getBlockEntity(selected.pos()) instanceof PackageDistributorBlockEntity distributor)) {
            return new Result(false, MESSAGE_PREFIX + ".not_distributor", 0);
        }
        if (!distributor.usesStoredMachineLinks()) {
            return new Result(false, MESSAGE_PREFIX + ".pattern_routed_distributor", 0);
        }

        List<BlockPos> route = distributor.getLinkedMachines();
        if (route.size() < 2) {
            return new Result(false, MESSAGE_PREFIX + ".need_endpoints", 0);
        }

        List<BlockPos> rebuilt = buildRoute(player.level(), route.get(0), route.get(route.size() - 1));
        if (rebuilt == null) {
            return new Result(false, MESSAGE_PREFIX + ".no_route", 0);
        }
        if (!distributor.replaceAutoRoute(rebuilt)) {
            return new Result(false, MESSAGE_PREFIX + ".no_change", rebuilt.size());
        }
        RouteTools.setCursor(linker, RouteTools.clampCursor(RouteTools.getCursor(linker), rebuilt.size()));
        return new Result(true, MESSAGE_PREFIX + ".success", rebuilt.size());
    }

    private static Result searchConverter(ServerPlayer player, ItemStack converter) {
        List<BlockPos> route = RouteTools.routePositions(converter);
        if (route.size() < 2) {
            return new Result(false, MESSAGE_PREFIX + ".need_endpoints", 0);
        }
        List<BlockPos> rebuilt = buildRoute(player.level(), route.get(0), route.get(route.size() - 1));
        if (rebuilt == null) {
            return new Result(false, MESSAGE_PREFIX + ".no_route", 0);
        }
        if (route.equals(rebuilt)) {
            return new Result(false, MESSAGE_PREFIX + ".no_change", rebuilt.size());
        }
        RouteTools.writeConverterRoute(converter, rebuilt);
        return new Result(true, MESSAGE_PREFIX + ".success", rebuilt.size());
    }

    private static Result searchPattern(ServerPlayer player, ItemStack pattern) {
        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(pattern);
        if (data == null || data.encodedPattern().isEmpty()) {
            return new Result(false, MESSAGE_PREFIX + ".invalid_pattern", 0);
        }
        List<BlockPos> route = data.route();
        if (route.size() < 2) {
            return new Result(false, MESSAGE_PREFIX + ".need_endpoints", 0);
        }
        List<BlockPos> rebuilt = buildRoute(player.level(), route.get(0), route.get(route.size() - 1));
        if (rebuilt == null) {
            return new Result(false, MESSAGE_PREFIX + ".no_route", 0);
        }
        if (route.equals(rebuilt)) {
            return new Result(false, MESSAGE_PREFIX + ".no_change", rebuilt.size());
        }
        if (!RouteTools.writePatternRoute(pattern, rebuilt)) {
            return new Result(false, MESSAGE_PREFIX + ".invalid_pattern", 0);
        }
        return new Result(true, MESSAGE_PREFIX + ".success", rebuilt.size());
    }

    private static ItemStack routeToolInHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (RouteTools.supportsAutoRoute(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (RouteTools.supportsAutoRoute(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    static List<BlockPos> buildRoute(Level level, BlockPos input, BlockPos output) {
        List<BlockPos> straight = buildCorridorRoute(level, input, output);
        if (straight != null) {
            return straight;
        }
        if (!Config.autoRouteAllowLShaped()) {
            return null;
        }
        return buildLShapedRoute(level, input, output);
    }

    @Nullable
    private static List<BlockPos> buildCorridorRoute(Level level, BlockPos input, BlockPos output) {
        Direction travelDirection = corridorDirection(input, output);
        if (travelDirection == null) {
            return null;
        }
        if (manhattan(input, output) > Config.autoRouteMaxLength()) {
            return null;
        }
        if (LinkedMachines.roleOf(level, input) != LinkedMachines.Role.DEPOT
                || LinkedMachines.roleOf(level, output) != LinkedMachines.Role.DEPOT) {
            return null;
        }

        List<BlockPos> result = new ArrayList<>();
        result.add(input.immutable());

        BlockPos corridorPos = input;
        while (true) {
            if (!level.isLoaded(corridorPos)) {
                return null;
            }
            appendStationMachines(level, result, corridorPos);
            if (corridorPos.equals(output)) {
                break;
            }
            corridorPos = corridorPos.relative(travelDirection);
        }

        addIfMissing(result, output);
        return List.copyOf(result);
    }

    @Nullable
    private static List<BlockPos> buildLShapedRoute(Level level, BlockPos input, BlockPos output) {
        if (input.equals(output) || input.getY() != output.getY()) {
            return null;
        }
        if (manhattan(input, output) > Config.autoRouteMaxLength()) {
            return null;
        }
        if (LinkedMachines.roleOf(level, input) != LinkedMachines.Role.DEPOT
                || LinkedMachines.roleOf(level, output) != LinkedMachines.Role.DEPOT) {
            return null;
        }
        int dx = output.getX() - input.getX();
        int dz = output.getZ() - input.getZ();
        if (dx == 0 || dz == 0) {
            return null;
        }

        BlockPos cornerA = new BlockPos(output.getX(), input.getY(), input.getZ());
        BlockPos cornerB = new BlockPos(input.getX(), input.getY(), output.getZ());
        List<BlockPos> viaA = mergeCorridors(level, input, cornerA, output);
        if (viaA != null) {
            return viaA;
        }
        return mergeCorridors(level, input, cornerB, output);
    }

    @Nullable
    private static List<BlockPos> mergeCorridors(Level level, BlockPos start, BlockPos mid, BlockPos end) {
        List<BlockPos> first = buildCorridorRoute(level, start, mid);
        List<BlockPos> second = buildCorridorRoute(level, mid, end);
        if (first == null || second == null) {
            return null;
        }
        List<BlockPos> merged = new ArrayList<>(first);
        for (int i = 1; i < second.size(); i++) {
            addIfMissing(merged, second.get(i));
        }
        return List.copyOf(merged);
    }

    private static void appendStationMachines(Level level, List<BlockPos> result, BlockPos corridorPos) {
        appendDeployers(level, result, corridorPos);
        appendSpout(level, result, corridorPos);
    }

    @Nullable
    private static Direction corridorDirection(BlockPos input, BlockPos output) {
        if (input.equals(output) || input.getY() != output.getY()) {
            return null;
        }
        return axisAlignedDirection(output.getX() - input.getX(), output.getZ() - input.getZ());
    }

    @Nullable
    static Direction axisAlignedDirection(int dx, int dz) {
        if (dx != 0 && dz != 0) {
            return null;
        }
        if (dx == 0 && dz == 0) {
            return null;
        }
        Direction.Axis axis = dx != 0 ? Direction.Axis.X : Direction.Axis.Z;
        int step = dx != 0 ? Integer.signum(dx) : Integer.signum(dz);
        Direction.AxisDirection axisDirection = step > 0
                ? Direction.AxisDirection.POSITIVE
                : Direction.AxisDirection.NEGATIVE;
        return Direction.get(axisDirection, axis);
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static void addIfMissing(List<BlockPos> result, BlockPos pos) {
        if (!result.contains(pos)) {
            result.add(pos.immutable());
        }
    }

    private static void appendDeployers(Level level, List<BlockPos> result, BlockPos corridorPos) {
        for (Direction facing : Direction.values()) {
            BlockPos machinePos = corridorPos.relative(facing.getOpposite(), 2);
            if (!level.isLoaded(machinePos)) {
                continue;
            }
            var state = level.getBlockState(machinePos);
            if (LinkedMachines.roleOf(level, machinePos) == LinkedMachines.Role.DEPLOYER
                    && state.hasProperty(FACING) && state.getValue(FACING) == facing) {
                addIfMissing(result, machinePos);
            }
        }
    }

    private static void appendSpout(Level level, List<BlockPos> result, BlockPos corridorPos) {
        BlockPos machinePos = corridorPos.above(2);
        if (!level.isLoaded(machinePos)) {
            return;
        }
        if (LinkedMachines.roleOf(level, machinePos) == LinkedMachines.Role.SPOUT) {
            addIfMissing(result, machinePos);
        }
    }

    private AutoRouteService() {}
}
