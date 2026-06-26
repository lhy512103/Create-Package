package com.lhy.createpackage.content.converter;

import java.util.List;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModMenuTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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

        if (level.getBlockEntity(clicked) instanceof PackageDistributorBlockEntity distributor) {
            if (!level.isClientSide()) {
                if (player.isShiftKeyDown()) {
                    stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
                    msg(player, "cleared");
                } else {
                    stack.set(ModComponents.LINKED_DISTRIBUTOR.get(),
                            GlobalPos.of(level.dimension(), clicked.immutable()));
                    msg(player, "selected", clicked.getX(), clicked.getY(), clicked.getZ(),
                            distributor.getLinkedMachines().size());
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != this) {
            return InteractionResultHolder.pass(stack);
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos selected = stack.get(ModComponents.LINKED_DISTRIBUTOR.get());
        if (selected == null) {
            tooltip.add(Component.translatable(KEY_PREFIX + "tooltip.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos pos = selected.pos();
            tooltip.add(Component.translatable(KEY_PREFIX + "tooltip.bound", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable(KEY_PREFIX + "tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
