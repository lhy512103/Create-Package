package com.lhy.createpackage.network;

import java.util.function.Supplier;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.route.RouteTools;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record RouteModeTogglePayload() {
    public static void encode(RouteModeTogglePayload payload, FriendlyByteBuf buffer) {}
    public static RouteModeTogglePayload decode(FriendlyByteBuf buffer) { return new RouteModeTogglePayload(); }
    public static void handle(RouteModeTogglePayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ItemStack tool = RouteTools.heldRouteTool(player);
            if (tool == null) return;
            if (!RouteTools.supportsOrderedRouteCursor(player, tool)) {
                player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + ".route_mode.unavailable").withStyle(ChatFormatting.RED), true); return;
            }
            RouteTools.Mode mode = RouteTools.toggleMode(tool);
            ChatFormatting color = mode == RouteTools.Mode.EDIT ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + ".route_mode.switched",
                    RouteTools.modeLabel(mode).copy().withStyle(color)), true);
        });
        context.setPacketHandled(true);
    }
}
