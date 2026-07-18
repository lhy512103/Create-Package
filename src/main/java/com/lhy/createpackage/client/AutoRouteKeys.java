package com.lhy.createpackage.client;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.route.RouteTools;
import com.lhy.createpackage.network.AutoRouteSearchPayload;
import com.lhy.createpackage.network.RouteCursorScrollPayload;
import com.lhy.createpackage.network.RouteMarkEditPayload;
import com.lhy.createpackage.network.RouteModeTogglePayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client input for route tools:
 * <ul>
 *   <li>G auto-route only while holding a route tool</li>
 *   <li>Left-click air toggles Append / Edit mode</li>
 *   <li>In Edit mode, mouse wheel cycles the selected ordered-route mark</li>
 *   <li>In Edit mode, right-click replaces the selected mark; sneak + right-click removes it</li>
 * </ul>
 */
public final class AutoRouteKeys {
    private static final KeyMapping AUTO_ROUTE = new KeyMapping(
            "key." + CreatePackage.MODID + ".auto_route",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            "key.categories." + CreatePackage.MODID);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(AUTO_ROUTE);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            while (AUTO_ROUTE.consumeClick()) {
            }
            return;
        }
        while (AUTO_ROUTE.consumeClick()) {
            ItemStack tool = RouteTools.heldRouteTool(minecraft.player);
            if (tool == null || !RouteTools.supportsAutoRoute(tool)) {
                continue;
            }
            PacketDistributor.sendToServer(new AutoRouteSearchPayload());
        }
    }

    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        ItemStack tool = event.getItemStack();
        if (!RouteTools.isRouteTool(tool)) {
            return;
        }
        // Always send: server decides whether ordered modes are available for this host.
        PacketDistributor.sendToServer(new RouteModeTogglePayload());
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        ItemStack tool = RouteTools.heldRouteTool(minecraft.player);
        if (tool == null
                || !RouteTools.supportsOrderedRouteCursor(minecraft.player, tool)
                || !RouteTools.isEditMode(tool)) {
            return;
        }
        double scroll = event.getScrollDeltaY();
        if (scroll == 0.0) {
            return;
        }
        int delta = scroll > 0 ? -1 : 1;
        PacketDistributor.sendToServer(new RouteCursorScrollPayload(delta));
        event.setCanceled(true);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        ItemStack tool = event.getItemStack();

        // Converter/pattern block clicks always use the dedicated route path. This prevents their
        // sneak-right-click-air behavior from leaking into a click on an actual machine.
        boolean converterOrPattern = tool.is(com.lhy.createpackage.registry.ModItems.MECHANICAL_PATTERN_CONVERTER.get())
                || tool.is(com.lhy.createpackage.registry.ModItems.MECHANICAL_PACKAGE_PATTERN.get());
        boolean linkerEdit = tool.is(com.lhy.createpackage.registry.ModItems.MACHINE_LINKER.get())
                && RouteTools.isEditMode(tool)
                && RouteTools.supportsOrderedRouteCursor(minecraft.player, tool);
        if (!converterOrPattern && !linkerEdit) {
            return;
        }

        PacketDistributor.sendToServer(new RouteMarkEditPayload(event.getPos(), minecraft.player.isShiftKeyDown()));
        // A plain cancellation alone can still let the interaction mapper fall through to the
        // item's air-use path on some clients, which opens the converter GUI. Give the click a
        // successful terminal result as well as blocking both block and item use.
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
        event.setUseItem(TriState.FALSE);
        event.setUseBlock(TriState.FALSE);
    }

    private AutoRouteKeys() {}
}
