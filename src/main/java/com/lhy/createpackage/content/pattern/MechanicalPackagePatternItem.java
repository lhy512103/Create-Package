package com.lhy.createpackage.content.pattern;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterItem;
import com.lhy.createpackage.content.route.RouteMaterialHints;
import com.lhy.createpackage.content.route.RouteTools;
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

public class MechanicalPackagePatternItem extends EncodedPatternItem<RoutedMechanicalPackagePatternDetails> {
    private static final String KEY_PREFIX = "item." + CreatePackage.MODID + ".mechanical_package_pattern.";

    public MechanicalPackagePatternItem(Properties properties,
            EncodedPatternDecoder<RoutedMechanicalPackagePatternDetails> decoder,
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

        // Edit-mode mark edits are handled client-side via RouteMarkEditPayload.
        if (RouteTools.isEditMode(stack)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()) {
            List<BlockPos> route = new ArrayList<>(data.route());
            List<BlockPos> updatedRoute;
            if (player.isShiftKeyDown()) {
                if (!route.contains(clicked)) {
                    msg(player, "not_marked");
                    return InteractionResult.sidedSuccess(false);
                }
                updatedRoute = route.stream().filter(pos -> !pos.equals(clicked)).toList();
                RouteTools.setCursor(stack,
                        RouteTools.clampCursor(RouteTools.getCursor(stack), updatedRoute.size()));
                msg(player, "unmarked", clicked.getX(), clicked.getY(), clicked.getZ(), updatedRoute.size());
            } else {
                if (route.contains(clicked)) {
                    msg(player, "already_marked");
                    return InteractionResult.sidedSuccess(false);
                }
                route.add(clicked.immutable());
                updatedRoute = List.copyOf(route);
                RouteTools.setCursor(stack,
                        RouteTools.clampCursor(RouteTools.getCursor(stack), updatedRoute.size()));
                msg(player, "marked", clicked.getX(), clicked.getY(), clicked.getZ(), updatedRoute.size());
            }
            stack.set(ModComponents.MECHANICAL_PACKAGE_PATTERN.get(),
                    new MechanicalPackagePatternData(data.encodedPattern(), updatedRoute, data.encoder()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag flags) {
        super.appendHoverText(stack, context, lines, flags);
        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
        if (data != null) {
            data.encoder().ifPresent(encoder -> lines.add(Component.translatable(
                            "tooltip.createpackage.mechanical_package_pattern.encoded_by",
                            encoder.name().isBlank() ? encoder.uuid().toString() : encoder.name())
                    .withStyle(ChatFormatting.GRAY)));
            int cursor = RouteTools.clampCursor(RouteTools.getCursor(stack), data.route().size());
            RouteTools.Mode mode = RouteTools.getMode(stack);
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.mode",
                    RouteTools.modeLabel(mode).copy().withStyle(
                            mode == RouteTools.Mode.EDIT ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY));
            MachineRouteTooltip.addRouteLines(lines, context.level(), data.route(), flags.hasShiftDown(), cursor);
            if (mode == RouteTools.Mode.EDIT) {
                for (Component line : RouteMaterialHints.selectedMaterialLines(context.level(), stack, cursor)) {
                    lines.add(line.copy().withStyle(ChatFormatting.YELLOW));
                }
            }
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".route.guide_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void msg(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(KEY_PREFIX + key, args), true);
    }
}
