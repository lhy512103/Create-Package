package com.lhy.createpackage.content.pattern;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterItem;
import com.lhy.createpackage.registry.ModComponents;

import appeng.api.crafting.EncodedPatternDecoder;
import appeng.api.crafting.InvalidPatternTooltipStrategy;
import appeng.crafting.pattern.EncodedPatternItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public class MechanicalPackagePatternItem extends EncodedPatternItem<MechanicalPackagePatternDetails> {
    private static final String KEY_PREFIX = "item." + CreatePackage.MODID + ".mechanical_package_pattern.";

    public MechanicalPackagePatternItem(Properties properties,
            EncodedPatternDecoder<MechanicalPackagePatternDetails> decoder,
            @Nullable InvalidPatternTooltipStrategy invalidPatternTooltip) {
        super(properties, decoder, invalidPatternTooltip);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        if (player == null) {
            return InteractionResult.PASS;
        }

        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
        if (data == null || data.encodedPattern().isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!MechanicalPatternConverterItem.isMarkableMachine(level, clicked)) {
            if (!level.isClientSide()) {
                msg(player, "not_markable");
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()) {
            List<BlockPos> route = data.route();
            List<BlockPos> updatedRoute;
            if (player.isShiftKeyDown()) {
                if (!route.contains(clicked)) {
                    msg(player, "not_marked");
                    return InteractionResult.sidedSuccess(false);
                }
                updatedRoute = route.stream()
                        .filter(pos -> !pos.equals(clicked))
                        .toList();
                msg(player, "unmarked", clicked.getX(), clicked.getY(), clicked.getZ(), updatedRoute.size());
            } else {
                if (route.contains(clicked)) {
                    msg(player, "already_marked");
                    return InteractionResult.sidedSuccess(false);
                }
                var copy = new java.util.ArrayList<>(route);
                copy.add(clicked.immutable());
                updatedRoute = copy;
                msg(player, "marked", clicked.getX(), clicked.getY(), clicked.getZ(), updatedRoute.size());
            }
            stack.set(ModComponents.MECHANICAL_PACKAGE_PATTERN.get(),
                    new MechanicalPackagePatternData(data.encodedPattern(), updatedRoute));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag flags) {
        super.appendHoverText(stack, context, lines, flags);
        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
        if (data != null) {
            MachineRouteTooltip.addRouteLines(lines, context.level(), data.route(), flags.hasShiftDown());
            lines.add(Component.translatable("tooltip.createpackage.route.held_highlight")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void msg(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(KEY_PREFIX + key, args), true);
    }
}
