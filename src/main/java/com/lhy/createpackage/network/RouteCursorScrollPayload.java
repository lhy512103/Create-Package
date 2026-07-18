package com.lhy.createpackage.network;

import java.util.List;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.route.RouteTools;

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
 * Cycles the selected ordered-route mark while the tool is in Edit mode.
 */
public record RouteCursorScrollPayload(int delta) implements CustomPacketPayload {
    public static final Type<RouteCursorScrollPayload> TYPE =
            new Type<>(CreatePackage.id("route_cursor_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteCursorScrollPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RouteCursorScrollPayload::delta,
                    RouteCursorScrollPayload::new);

    public static void handle(RouteCursorScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack tool = RouteTools.heldRouteTool(player);
            if (tool == null || !RouteTools.supportsOrderedRouteCursor(player, tool)) {
                player.displayClientMessage(Component.translatable(
                        "message." + CreatePackage.MODID + ".route_cursor.unavailable"), true);
                return;
            }
            if (!RouteTools.isEditMode(tool)) {
                player.displayClientMessage(Component.translatable(
                        "message." + CreatePackage.MODID + ".route_mode.need_edit"), true);
                return;
            }
            List<BlockPos> route = RouteTools.orderedRouteForCursor(player, tool);
            if (route == null || route.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "message." + CreatePackage.MODID + ".route_cursor.empty"), true);
                return;
            }
            int next = RouteTools.cycleCursor(RouteTools.getCursor(tool), route.size(), payload.delta());
            RouteTools.setCursor(tool, next);
            BlockPos pos = route.get(next);
            player.displayClientMessage(Component.translatable(
                    "message." + CreatePackage.MODID + ".route_cursor.selected",
                    next + 1, route.size(), pos.getX(), pos.getY(), pos.getZ()), true);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
