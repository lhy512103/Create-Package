package com.lhy.createpackage.content.converter;

import java.util.List;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.LinkedMachines;
import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MachineRouteTooltip;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModMenuTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class MechanicalPatternConverterItem extends Item {
    private static final String KEY_PREFIX = "item." + CreatePackage.MODID + ".mechanical_pattern_converter.";

    public MechanicalPatternConverterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!isMarkableMachine(level, clicked)) {
            if (!level.isClientSide()) {
                msg(player, "not_markable");
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()) {
            MachineRouteData route = route(stack);
            if (player.isShiftKeyDown()) {
                MachineRouteData updated = route.withRemoved(clicked);
                if (updated.positions().size() == route.positions().size()) {
                    msg(player, "not_marked");
                } else {
                    setRoute(stack, updated);
                    msg(player, "unmarked", clicked.getX(), clicked.getY(), clicked.getZ(),
                            updated.positions().size());
                }
            } else {
                MachineRouteData updated = route.withAdded(clicked);
                if (updated.positions().size() == route.positions().size()) {
                    msg(player, "already_marked");
                } else {
                    setRoute(stack, updated);
                    msg(player, "marked", clicked.getX(), clicked.getY(), clicked.getZ(),
                            updated.positions().size());
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != this) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                stack.remove(ModComponents.MECHANICAL_ROUTE.get());
                msg(player, "cleared");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new MechanicalPatternConverterMenu(
                            ModMenuTypes.MECHANICAL_PATTERN_CONVERTER.get(), id, inv, hand),
                    Component.translatable("gui." + CreatePackage.MODID + ".mechanical_pattern_converter"));
            serverPlayer.openMenu(provider, buf -> buf.writeBoolean(hand == InteractionHand.MAIN_HAND));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void msg(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(KEY_PREFIX + key, args), true);
    }

    private static MachineRouteData route(ItemStack stack) {
        MachineRouteData route = stack.get(ModComponents.MECHANICAL_ROUTE.get());
        return route == null ? new MachineRouteData(List.of()) : route;
    }

    private static void setRoute(ItemStack stack, MachineRouteData route) {
        if (route.isEmpty()) {
            stack.remove(ModComponents.MECHANICAL_ROUTE.get());
        } else {
            stack.set(ModComponents.MECHANICAL_ROUTE.get(), route);
        }
    }

    public static boolean isMarkableMachine(Level level, BlockPos pos) {
        return LinkedMachines.roleOf(level, pos) != LinkedMachines.Role.UNKNOWN;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MachineRouteData route = route(stack);
        MachineRouteTooltip.addRouteLines(tooltip, context.level(), route.positions(), flag.hasShiftDown());
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.held_highlight_marked")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY_PREFIX + "tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
