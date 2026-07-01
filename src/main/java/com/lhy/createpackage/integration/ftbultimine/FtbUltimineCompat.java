package com.lhy.createpackage.integration.ftbultimine;

import com.lhy.createpackage.content.linker.MachineLinkerItem;
import com.lhy.createpackage.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import dev.ftb.mods.ftbultimine.api.rightclick.RegisterRightClickHandlerEvent;
import dev.ftb.mods.ftbultimine.api.shape.ShapeContext;

public final class FtbUltimineCompat {
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        RegisterRightClickHandlerEvent.REGISTER.register(dispatcher ->
                dispatcher.registerHandler(FtbUltimineCompat::handleRightClickBlock));
    }

    private static int handleRightClickBlock(ShapeContext context, InteractionHand hand,
            java.util.Collection<BlockPos> positions) {
        ItemStack stack = context.player().getItemInHand(hand);
        if (!stack.is(ModItems.MACHINE_LINKER.get())) {
            return 0;
        }
        return MachineLinkerItem.linkMany(context.player(), stack, context.origPos(), positions);
    }

    private FtbUltimineCompat() {}
}
