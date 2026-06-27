package com.lhy.createpackage.content.pattern;

import java.util.List;

import com.lhy.createpackage.CreatePackage;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class MachineRouteTooltip {
    private static final int DEFAULT_VISIBLE_LINKS = 8;

    public static void addRouteLines(List<Component> tooltip, Level level, List<BlockPos> route, boolean expanded) {
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.count", route.size())
                .withStyle(route.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        if (route.isEmpty()) {
            return;
        }
        if (!expanded) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        int visible = Math.min(DEFAULT_VISIBLE_LINKS, route.size());
        for (int i = 0; i < visible; i++) {
            BlockPos pos = route.get(i);
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.entry",
                    i + 1, blockName(level, pos), pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (route.size() > visible) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.more",
                    route.size() - visible).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component blockName(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.unloaded");
        }
        BlockState state = level.getBlockState(pos);
        Item item = state.getBlock().asItem();
        if (item != net.minecraft.world.item.Items.AIR) {
            return item.getDescription();
        }
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? Component.literal("unknown") : Component.literal(id.toString());
    }

    private MachineRouteTooltip() {}
}
