package com.lhy.createpackage.integration.ftbultimine;

import com.lhy.createpackage.content.linker.MachineLinkerItem;
import com.lhy.createpackage.registry.ModItems;

import dev.ftb.mods.ftbultimine.FTBUltimine;
import dev.ftb.mods.ftbultimine.FTBUltiminePlayerData;
import dev.ftb.mods.ftbultimine.config.FTBUltimineServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Forge 1.20.1 bridge for FTB Ultimine 2001.x, which predates the public right-click handler API. */
public final class FtbUltimineCompat {
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false,
                PlayerInteractEvent.RightClickBlock.class, FtbUltimineCompat::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.MACHINE_LINKER.get())) {
            return;
        }

        FTBUltiminePlayerData data = FTBUltimine.instance.getOrCreatePlayerData(player);
        if (!data.isPressed()) {
            return;
        }

        data.clearCache();
        var context = data.updateBlocks(player, event.getPos(), event.getFace(), false,
                FTBUltimineServerConfig.getMaxBlocks(player));
        if (context == null || !data.hasCachedPositions()) {
            return;
        }

        int changed = MachineLinkerItem.linkMany(player, stack, event.getPos(), data.cachedPositions());
        if (changed > 0) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private FtbUltimineCompat() {}
}
