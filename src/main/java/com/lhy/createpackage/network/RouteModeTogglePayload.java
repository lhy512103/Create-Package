package com.lhy.createpackage.network;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.route.RouteTools;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Toggles ordered-route Append/Edit mode when the player left-clicks air with a supported tool.
 */
public record RouteModeTogglePayload() implements CustomPacketPayload {
    public static final Type<RouteModeTogglePayload> TYPE =
            new Type<>(CreatePackage.id("route_mode_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteModeTogglePayload> STREAM_CODEC =
            StreamCodec.unit(new RouteModeTogglePayload());

    public static void handle(RouteModeTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack tool = RouteTools.heldRouteTool(player);
            if (tool == null) {
                return;
            }
            if (!RouteTools.supportsOrderedRouteCursor(player, tool)) {
                player.displayClientMessage(Component.translatable(
                        "message." + CreatePackage.MODID + ".route_mode.unavailable")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            RouteTools.Mode mode = RouteTools.toggleMode(tool);
            ChatFormatting color = mode == RouteTools.Mode.EDIT ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            player.displayClientMessage(Component.translatable(
                    "message." + CreatePackage.MODID + ".route_mode.switched",
                    RouteTools.modeLabel(mode).copy().withStyle(color)), true);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
