package com.lhy.createpackage.network;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AutoRouteService;
import com.lhy.createpackage.content.route.RouteTools;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AutoRouteSearchPayload() implements CustomPacketPayload {
    public static final Type<AutoRouteSearchPayload> TYPE =
            new Type<>(CreatePackage.id("auto_route_search"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutoRouteSearchPayload> STREAM_CODEC =
            StreamCodec.unit(new AutoRouteSearchPayload());

    public static void handle(AutoRouteSearchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (RouteTools.heldRouteTool(player) == null) {
                player.displayClientMessage(Component.translatable(
                        "message." + CreatePackage.MODID + ".auto_route.no_tool"), true);
                return;
            }
            AutoRouteService.Result result = AutoRouteService.search(player);
            player.displayClientMessage(Component.translatable(
                    "message." + CreatePackage.MODID + "." + result.messageKey(),
                    result.linkedCount()), true);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
