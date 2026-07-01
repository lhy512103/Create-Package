package com.lhy.createpackage.content.linker;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
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
                play(level, clicked, ModSounds.MACHINE_LINKER_ERROR.get());
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                int cleared = distributor.clearLinks();
                stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
                msg(player, "cleared", cleared);
                play(level, clicked, ModSounds.MACHINE_LINKER_SUCCESS.get());
            } else {
                stack.set(ModComponents.LINKED_DISTRIBUTOR.get(),
                        GlobalPos.of(level.dimension(), clicked.immutable()));
                msg(player, "selected", clicked.getX(), clicked.getY(), clicked.getZ());
                play(level, clicked, ModSounds.MACHINE_LINKER_THUD.get());
            }
            return InteractionResult.SUCCESS;
        }

        // Case 1b: clicked a Kinetic Pattern Provider -> select it, or clear parallel machine links.
        if (be instanceof KineticPatternProviderBlockEntity provider) {
            if (player.isShiftKeyDown()) {
                int cleared = provider.clearLinks();
                stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
                msg(player, "kinetic_cleared", cleared);
                play(level, clicked, ModSounds.MACHINE_LINKER_SUCCESS.get());
            } else {
                stack.set(ModComponents.LINKED_DISTRIBUTOR.get(),
                        GlobalPos.of(level.dimension(), clicked.immutable()));
                msg(player, "kinetic_selected", clicked.getX(), clicked.getY(), clicked.getZ());
                play(level, clicked, ModSounds.MACHINE_LINKER_THUD.get());
            }
            return InteractionResult.SUCCESS;
        }

        // Case 2: clicked some other block -> link/unlink it to the selected host.
        linkSingle(level, player, stack, clicked);
        return InteractionResult.SUCCESS;
    }

    public static int linkMany(ServerPlayer player, ItemStack stack, BlockPos origin, Collection<BlockPos> positions) {
        Level level = player.level();
        LinkTarget target = resolveTarget(level, stack, player, origin);
        if (target == null) {
            return 0;
        }

        ResourceLocation originId = blockId(level, origin);
        boolean unlink = player.isShiftKeyDown();
        int changed = positions.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator
                        .comparingInt((BlockPos pos) -> pos.distManhattan(origin))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX))
                .mapToInt(pos -> tryChangeLink(target, originId, pos, unlink))
                .sum();

        if (changed > 0) {
            if (target.distributor != null) {
                msg(player, unlink ? "batch_unlinked" : "batch_linked", changed,
                        target.distributor.getLinkedMachines().size());
            } else {
                msg(player, unlink ? "kinetic_batch_unlinked" : "kinetic_batch_linked", changed,
                        target.provider.getLinkedMachines().size());
            }
            play(level, origin, ModSounds.MACHINE_LINKER_SUCCESS.get());
        } else {
            msg(player, "batch_none");
            play(level, origin, ModSounds.MACHINE_LINKER_ERROR.get());
        }
        return changed;
    }

    private static void linkSingle(Level level, net.minecraft.world.entity.player.Player player, ItemStack stack,
            BlockPos clicked) {
        LinkTarget target = resolveTarget(level, stack, player, clicked);
        if (target == null) {
            return;
        }

        boolean unlink = player.isShiftKeyDown();
        int changed = tryChangeLink(target, blockId(level, clicked), clicked, unlink);
        if (target.distributor != null) {
            if (changed > 0) {
                if (unlink) {
                    msg(player, "unlinked", clicked.getX(), clicked.getY(), clicked.getZ(),
                            target.distributor.getLinkedMachines().size());
                } else {
                    msg(player, "linked", clicked.getX(), clicked.getY(), clicked.getZ(),
                            target.distributor.getLinkedMachines().size());
                }
                play(level, clicked, ModSounds.MACHINE_LINKER_SUCCESS.get());
            } else {
                msg(player, unlink ? "not_linked" : existingOrInvalidDistributorMessage(level, target.distributor,
                        clicked));
                play(level, clicked, ModSounds.MACHINE_LINKER_ERROR.get());
            }
        } else {
            if (changed > 0) {
                if (unlink) {
                    msg(player, "kinetic_unlinked", clicked.getX(), clicked.getY(), clicked.getZ(),
                            target.provider.getLinkedMachines().size());
                } else {
                    msg(player, "kinetic_linked", clicked.getX(), clicked.getY(), clicked.getZ(),
                            target.provider.getLinkedMachines().size());
                }
                play(level, clicked, ModSounds.MACHINE_LINKER_SUCCESS.get());
            } else {
                msg(player, unlink ? "not_linked" : existingOrInvalidKineticMessage(level, target.provider, clicked));
                play(level, clicked, ModSounds.MACHINE_LINKER_ERROR.get());
            }
        }
    }

    @Nullable
    private static LinkTarget resolveTarget(Level level, ItemStack stack,
            net.minecraft.world.entity.player.Player player, BlockPos feedbackPos) {
        GlobalPos selected = stack.get(ModComponents.LINKED_DISTRIBUTOR.get());
        if (selected == null) {
            msg(player, "no_selection");
            play(level, feedbackPos, ModSounds.MACHINE_LINKER_ERROR.get());
            return null;
        }
        if (!selected.dimension().equals(level.dimension())) {
            msg(player, "wrong_dimension");
            play(level, feedbackPos, ModSounds.MACHINE_LINKER_ERROR.get());
            return null;
        }

        BlockEntity selectedBe = level.getBlockEntity(selected.pos());
        if (selectedBe == null) {
            stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
            msg(player, "selection_gone");
            play(level, feedbackPos, ModSounds.MACHINE_LINKER_ERROR.get());
            return null;
        }

        if (selectedBe instanceof PackageDistributorBlockEntity distributor) {
            if (!distributor.usesStoredMachineLinks()) {
                stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
                msg(player, "pattern_routed_distributor");
                play(level, feedbackPos, ModSounds.MACHINE_LINKER_ERROR.get());
                return null;
            }
            return new LinkTarget(level, distributor, null);
        }

        if (selectedBe instanceof KineticPatternProviderBlockEntity provider) {
            return new LinkTarget(level, null, provider);
        }

        stack.remove(ModComponents.LINKED_DISTRIBUTOR.get());
        msg(player, "selection_gone");
        play(level, feedbackPos, ModSounds.MACHINE_LINKER_ERROR.get());
        return null;
    }

    private static int tryChangeLink(LinkTarget target, @Nullable ResourceLocation originId, BlockPos pos,
            boolean unlink) {
        if (!target.level.isLoaded(pos) || !Objects.equals(originId, blockId(target.level, pos))) {
            return 0;
        }
        if (target.distributor != null) {
            if (!unlink && !hasLinkableCapability(target.level, pos)) {
                return 0;
            }
            return (unlink ? target.distributor.unlinkMachine(pos) : target.distributor.linkMachine(pos)) ? 1 : 0;
        }
        if (!isSameKineticMachine(target.level, target.provider, pos)) {
            return 0;
        }
        return (unlink ? target.provider.unlinkMachine(pos) : target.provider.linkMachine(pos)) ? 1 : 0;
    }

    private static String existingOrInvalidDistributorMessage(Level level, PackageDistributorBlockEntity distributor,
            BlockPos clicked) {
        if (!hasLinkableCapability(level, clicked)) {
            return "not_linkable";
        }
        return distributor.isLinked(clicked) ? "already_linked" : "not_linkable";
    }

    private static String existingOrInvalidKineticMessage(Level level, KineticPatternProviderBlockEntity provider,
            BlockPos clicked) {
        if (!isSameKineticMachine(level, provider, clicked)) {
            return "kinetic_wrong_machine";
        }
        return provider.hasLinkedMachine(clicked) ? "already_linked" : "kinetic_wrong_machine";
    }

    /** A block is linkable if it exposes an item or fluid handler that we can feed/drain. */
    public static boolean hasLinkableCapability(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
                || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
    }

    public static boolean isSameKineticMachine(Level level, KineticPatternProviderBlockEntity provider,
            BlockPos clicked) {
        BlockPos target = provider.getTargetMachinePos();
        if (target == null || !level.isLoaded(target) || !level.isLoaded(clicked)) {
            return false;
        }
        ResourceLocation targetId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock());
        ResourceLocation clickedId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(clicked).getBlock());
        return targetId != null && targetId.equals(clickedId);
    }

    @Nullable
    private static ResourceLocation blockId(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    private static void msg(net.minecraft.world.entity.player.Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(KEY_PREFIX + key, args), true);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.PLAYERS, 0.45f, 1.0f);
    }

    private record LinkTarget(Level level, @Nullable PackageDistributorBlockEntity distributor,
            @Nullable KineticPatternProviderBlockEntity provider) {}

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
