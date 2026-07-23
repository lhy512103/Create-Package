package com.lhy.createpackage.integration.ae2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.TickEvent;

public final class MechanicalPackagePatternEncodingCompat {
    private static final Map<UUID, RememberedRoute> REMEMBERED_ROUTES = new HashMap<>();

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide() || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
            REMEMBERED_ROUTES.remove(player.getUUID());
            return;
        }

        List<Slot> encodedSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        if (encodedSlots.isEmpty()) {
            REMEMBERED_ROUTES.remove(player.getUUID());
            return;
        }

        Slot encodedSlot = encodedSlots.get(0);
        ItemStack stack = encodedSlot.getItem();
        if (rememberMechanicalRoute(player, menu, encodedSlot, stack)) {
            return;
        }

        RememberedRoute remembered = REMEMBERED_ROUTES.get(player.getUUID());
        if (remembered == null || remembered.containerId() != menu.containerId
                || remembered.slotIndex() != encodedSlot.index) {
            return;
        }
        if (stack.isEmpty()) {
            REMEMBERED_ROUTES.remove(player.getUUID());
            return;
        }
        if (!stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get()) && PatternDetailsHelper.isEncodedPattern(stack)) {
            encodedSlot.set(wrap(stack, remembered.route(), player));
            menu.broadcastChanges();
        } else {
            REMEMBERED_ROUTES.remove(player.getUUID());
        }
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        REMEMBERED_ROUTES.remove(event.getEntity().getUUID());
    }

    private static boolean rememberMechanicalRoute(ServerPlayer player, PatternEncodingTermMenu menu, Slot slot,
            ItemStack stack) {
        if (!stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            return false;
        }

        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
        if (data != null && data.isValid() && PatternDetailsHelper.isEncodedPattern(data.encodedPattern())) {
            REMEMBERED_ROUTES.put(player.getUUID(), new RememberedRoute(menu.containerId, slot.index, data.route()));
        } else {
            REMEMBERED_ROUTES.remove(player.getUUID());
        }
        return true;
    }

    private static ItemStack wrap(ItemStack encodedPattern, List<BlockPos> route, ServerPlayer player) {
        ItemStack result = new ItemStack(ModItems.MECHANICAL_PACKAGE_PATTERN.get());
        ModComponents.setMechanicalPattern(result, new MechanicalPackagePatternData(encodedPattern.copyWithCount(1), route,
                        java.util.Optional.of(MechanicalPackagePatternData.PatternEncoder.of(player))));
        return result;
    }

    private record RememberedRoute(int containerId, int slotIndex, List<BlockPos> route) {
        private RememberedRoute {
            route = route.stream().map(BlockPos::immutable).toList();
        }
    }

    private MechanicalPackagePatternEncodingCompat() {}
}
