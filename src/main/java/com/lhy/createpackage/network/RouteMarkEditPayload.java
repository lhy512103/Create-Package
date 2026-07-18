package com.lhy.createpackage.network;

import java.util.ArrayList;
import java.util.List;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.LinkedMachines;
import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.content.route.RouteTools;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles a right-click on a route-markable block.
 *
 * <p>The client intercepts converter/pattern block clicks before vanilla item use can consume them.
 * That is important because sneaking right-click on <em>air</em> has tool-specific clear behavior,
 * while sneaking right-click on a machine must only remove that clicked route mark.
 */
public record RouteMarkEditPayload(BlockPos pos, boolean sneaking) implements CustomPacketPayload {
    public static final Type<RouteMarkEditPayload> TYPE =
            new Type<>(CreatePackage.id("route_mark_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteMarkEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    RouteMarkEditPayload::pos,
                    ByteBufCodecs.BOOL,
                    RouteMarkEditPayload::sneaking,
                    RouteMarkEditPayload::new);

    public static void handle(RouteMarkEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack tool = RouteTools.heldRouteTool(player);
            if (tool == null) {
                return;
            }
            if (tool.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())) {
                handleConverter(player, tool, payload.pos(), payload.sneaking());
            } else if (tool.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
                handlePattern(player, tool, payload.pos(), payload.sneaking());
            } else if (tool.is(ModItems.MACHINE_LINKER.get())) {
                handleLinkerEdit(player, tool, payload.pos(), payload.sneaking());
            }
        });
    }

    private static void handleConverter(ServerPlayer player, ItemStack tool, BlockPos pos, boolean sneaking) {
        if (!isMarkable(player, pos)) {
            converterMessage(player, "not_markable");
            return;
        }
        MachineRouteData route = tool.get(ModComponents.MECHANICAL_ROUTE.get());
        route = route == null ? new MachineRouteData(List.of()) : route;

        if (!RouteTools.isEditMode(tool)) {
            MachineRouteData updated = sneaking ? route.withRemoved(pos) : route.withAdded(pos);
            if (updated.positions().equals(route.positions())) {
                converterMessage(player, sneaking ? "not_marked" : "already_marked");
                return;
            }
            writeConverterRoute(tool, updated);
            RouteTools.setCursor(tool, RouteTools.clampCursor(RouteTools.getCursor(tool), updated.positions().size()));
            converterMessage(player, sneaking ? "unmarked" : "marked",
                    pos.getX(), pos.getY(), pos.getZ(), updated.positions().size());
            return;
        }

        handleEdit(player, tool, pos, sneaking, "mechanical_pattern_converter");
    }

    private static void handlePattern(ServerPlayer player, ItemStack tool, BlockPos pos, boolean sneaking) {
        if (!isMarkable(player, pos)) {
            patternMessage(player, "not_markable");
            return;
        }
        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(tool);
        if (data == null || data.encodedPattern().isEmpty()) {
            patternMessage(player, "not_markable");
            return;
        }

        if (!RouteTools.isEditMode(tool)) {
            List<BlockPos> route = new ArrayList<>(data.route());
            boolean changed;
            if (sneaking) {
                changed = route.remove(pos);
            } else if (route.contains(pos)) {
                changed = false;
            } else {
                route.add(pos.immutable());
                changed = true;
            }
            if (!changed) {
                patternMessage(player, sneaking ? "not_marked" : "already_marked");
                return;
            }
            if (!RouteTools.writePatternRoute(tool, route)) {
                patternMessage(player, "not_markable");
                return;
            }
            RouteTools.setCursor(tool, RouteTools.clampCursor(RouteTools.getCursor(tool), route.size()));
            patternMessage(player, sneaking ? "unmarked" : "marked",
                    pos.getX(), pos.getY(), pos.getZ(), route.size());
            return;
        }

        handleEdit(player, tool, pos, sneaking, "mechanical_package_pattern");
    }

    private static void handleLinkerEdit(ServerPlayer player, ItemStack tool, BlockPos pos, boolean sneaking) {
        if (!RouteTools.isEditMode(tool) || !RouteTools.supportsOrderedRouteCursor(player, tool)) {
            return;
        }
        if (!isMarkable(player, pos)) {
            linkerMessage(player, "not_linkable");
            return;
        }
        handleEdit(player, tool, pos, sneaking, "machine_linker");
    }

    private static void handleEdit(ServerPlayer player, ItemStack tool, BlockPos pos, boolean sneaking,
            String toolKey) {
        List<BlockPos> route = RouteTools.orderedRouteForCursor(player, tool);
        if (route == null || route.isEmpty()) {
            generic(player, "route_cursor.empty");
            return;
        }
        int cursor = RouteTools.clampCursor(RouteTools.getCursor(tool), route.size());
        if (cursor < 0) {
            generic(player, "route_cursor.need_selection");
            return;
        }

        if (sneaking) {
            if (RouteTools.removeSelectedOrderedMark(player, tool)) {
                generic(player, "route_cursor.removed", cursor + 1);
            } else {
                generic(player, "route_cursor.edit_failed");
            }
            return;
        }

        if (RouteTools.replaceOrderedMark(player, tool, pos)) {
            Component message = Component.translatable("item." + CreatePackage.MODID + "." + toolKey + ".replaced",
                    cursor + 1, pos.getX(), pos.getY(), pos.getZ());
            player.displayClientMessage(message, true);
        } else {
            generic(player, "route_cursor.edit_failed");
        }
    }

    private static boolean isMarkable(ServerPlayer player, BlockPos pos) {
        return player.level().isLoaded(pos) && LinkedMachines.roleOf(player.level(), pos) != LinkedMachines.Role.UNKNOWN;
    }

    private static void writeConverterRoute(ItemStack tool, MachineRouteData route) {
        if (route.isEmpty()) {
            tool.remove(ModComponents.MECHANICAL_ROUTE.get());
        } else {
            tool.set(ModComponents.MECHANICAL_ROUTE.get(), route);
        }
    }

    private static void converterMessage(ServerPlayer player, String key, Object... args) {
        toolMessage(player, "mechanical_pattern_converter", key, args);
    }

    private static void patternMessage(ServerPlayer player, String key, Object... args) {
        toolMessage(player, "mechanical_package_pattern", key, args);
    }

    private static void linkerMessage(ServerPlayer player, String key, Object... args) {
        toolMessage(player, "machine_linker", key, args);
    }

    private static void toolMessage(ServerPlayer player, String tool, String key, Object... args) {
        player.displayClientMessage(Component.translatable(
                "item." + CreatePackage.MODID + "." + tool + "." + key, args), true);
    }

    private static void generic(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(
                "message." + CreatePackage.MODID + "." + key, args), true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
