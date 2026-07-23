package com.lhy.createpackage.network;

import java.util.function.Supplier;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AutoRouteService;
import com.lhy.createpackage.content.route.RouteTools;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record AutoRouteSearchPayload() {
    public static void encode(AutoRouteSearchPayload payload, FriendlyByteBuf buffer) {}
    public static AutoRouteSearchPayload decode(FriendlyByteBuf buffer) { return new AutoRouteSearchPayload(); }
    public static void handle(AutoRouteSearchPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (RouteTools.heldRouteTool(player) == null) {
                player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + ".auto_route.no_tool"), true);
                return;
            }
            AutoRouteService.Result result = AutoRouteService.search(player);
            player.displayClientMessage(Component.translatable("message." + CreatePackage.MODID + "." + result.messageKey(), result.linkedCount()), true);
        });
        context.setPacketHandled(true);
    }
}
