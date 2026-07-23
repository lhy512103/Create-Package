package com.lhy.createpackage.network;

import java.util.List;
import java.util.function.Supplier;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.route.RouteTools;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record RouteCursorScrollPayload(int delta) {
    public static void encode(RouteCursorScrollPayload payload, FriendlyByteBuf buffer) { buffer.writeVarInt(payload.delta); }
    public static RouteCursorScrollPayload decode(FriendlyByteBuf buffer) { return new RouteCursorScrollPayload(buffer.readVarInt()); }
    public static void handle(RouteCursorScrollPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ItemStack tool = RouteTools.heldRouteTool(player);
            if (tool == null || !RouteTools.supportsOrderedRouteCursor(player, tool)) {
                player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + ".route_cursor.unavailable"), true); return;
            }
            if (!RouteTools.isEditMode(tool)) {
                player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + ".route_mode.need_edit"), true); return;
            }
            List<BlockPos> route = RouteTools.orderedRouteForCursor(player, tool);
            if (route == null || route.isEmpty()) {
                player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + ".route_cursor.empty"), true); return;
            }
            int next = RouteTools.cycleCursor(RouteTools.getCursor(tool), route.size(), payload.delta());
            RouteTools.setCursor(tool, next);
            BlockPos pos = route.get(next);
            player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + ".route_cursor.selected",
                    next + 1, route.size(), pos.getX(), pos.getY(), pos.getZ()), true);
        });
        context.setPacketHandled(true);
    }
}
