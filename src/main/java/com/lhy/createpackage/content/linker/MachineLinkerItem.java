package com.lhy.createpackage.content.linker;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Machine Linker.
 *
 * <p>Workflow:
 * <ul>
 *   <li>Right-click a Package Distributor to select it (bind the linker to it).</li>
 *   <li>Right-click Create machines (depot / deployer / spout / belt ...) to link them to the
 *       selected distributor, in physical order. The link order maps onto the sequenced-assembly
 *       recipe steps.</li>
 *   <li>Sneak + right-click an already linked machine to unlink it.</li>
 *   <li>Sneak + right-click the distributor to clear all of its links.</li>
 * </ul>
 *
 * <p>All player feedback uses translation keys (see lang files) so it is fully localizable.
 */
public class MachineLinkerItem extends Item {

    private static final String KEY_PREFIX = "item." + CreatePackage.MODID + ".machine_linker.";

    public MachineLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        var player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // Client side: report success so the arm swings, but do all real work on the server.
        if (level.isClientSide() || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        BlockEntity be = level.getBlockEntity(clicked);

        // Case 1: clicked a distributor -> select it, or clear its links when sneaking.
        if (be instanceof PackageDistributorBlockEntity distributor) {
            if (!distributor.usesStoredMachineLinks()) {
                msg(player, "pattern_routed_distributor");
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                int cleared = distributor.clearLinks();
                stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
                msg(player, "cleared", cleared);
            } else {
                stack.set(ModComponents.LINKED_DISTRIBUTOR.get(),
                        GlobalPos.of(level.dimension(), clicked.immutable()));
                msg(player, "selected", clicked.getX(), clicked.getY(), clicked.getZ());
            }
            return InteractionResult.SUCCESS;
        }

        // Case 2: clicked some other block -> link/unlink it to the selected distributor.
        GlobalPos selected = stack.get(ModComponents.LINKED_DISTRIBUTOR.get());
        if (selected == null) {
            msg(player, "no_selection");
            return InteractionResult.SUCCESS;
        }
        if (!selected.dimension().equals(level.dimension())) {
            msg(player, "wrong_dimension");
            return InteractionResult.SUCCESS;
        }

        PackageDistributorBlockEntity distributor = resolveDistributor(level, selected.pos());
        if (distributor == null) {
            stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
            msg(player, "selection_gone");
            return InteractionResult.SUCCESS;
        }
        if (!distributor.usesStoredMachineLinks()) {
            stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
            msg(player, "pattern_routed_distributor");
            return InteractionResult.SUCCESS;
        }

        if (!hasLinkableCapability(level, clicked)) {
            msg(player, "not_linkable");
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            if (distributor.unlinkMachine(clicked)) {
                msg(player, "unlinked", clicked.getX(), clicked.getY(), clicked.getZ(),
                        distributor.getLinkedMachines().size());
            } else {
                msg(player, "not_linked");
            }
        } else {
            if (distributor.linkMachine(clicked)) {
                msg(player, "linked", clicked.getX(), clicked.getY(), clicked.getZ(),
                        distributor.getLinkedMachines().size());
            } else {
                msg(player, "already_linked");
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static PackageDistributorBlockEntity resolveDistributor(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof PackageDistributorBlockEntity d ? d : null;
    }

    /** A block is linkable if it exposes an item or fluid handler that we can feed/drain. */
    private static boolean hasLinkableCapability(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
                || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
    }

    private static void msg(net.minecraft.world.entity.player.Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(KEY_PREFIX + key, args), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos selected = stack.get(ModComponents.LINKED_DISTRIBUTOR.get());
        if (selected == null) {
            tooltip.add(Component.translatable(KEY_PREFIX + "tooltip.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos p = selected.pos();
            tooltip.add(Component.translatable(KEY_PREFIX + "tooltip.bound", p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable(KEY_PREFIX + "tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
