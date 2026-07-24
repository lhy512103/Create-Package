package com.lhy.createpackage.network;

import com.lhy.createpackage.CreatePackage;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CreatePackage.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, AutoRouteSearchPayload.class, AutoRouteSearchPayload::encode,
                AutoRouteSearchPayload::decode, AutoRouteSearchPayload::handle);
        CHANNEL.registerMessage(id++, RouteCursorScrollPayload.class, RouteCursorScrollPayload::encode,
                RouteCursorScrollPayload::decode, RouteCursorScrollPayload::handle);
        CHANNEL.registerMessage(id++, RouteMarkEditPayload.class, RouteMarkEditPayload::encode,
                RouteMarkEditPayload::decode, RouteMarkEditPayload::handle);
        CHANNEL.registerMessage(id, RouteModeTogglePayload.class, RouteModeTogglePayload::encode,
                RouteModeTogglePayload::decode, RouteModeTogglePayload::handle);
    }
    public static void sendToServer(Object message) { CHANNEL.sendToServer(message); }
    private ModNetwork() {}
}
