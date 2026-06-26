package com.lhy.createpackage.integration.jade;

import java.util.ArrayList;
import java.util.List;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

public final class PackageDistributorComponentProvider implements IBlockComponentProvider {

    public static final PackageDistributorComponentProvider INSTANCE = new PackageDistributorComponentProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            CreatePackage.MODID, "package_distributor");
    private static final int MAX_LINKS = 12;

    private PackageDistributorComponentProvider() {}

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof PackageDistributorBlockEntity distributor)) {
            return;
        }

        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.status",
                Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.status."
                        + distributor.getStatusKey()).withStyle(statusColor(distributor.getStatusKey()))));

        if (distributor.hasActiveJob()) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.job",
                    distributor.getCurrentJobName(), distributor.getPrimaryRemaining(), distributor.getRoundsStarted())
                    .withStyle(ChatFormatting.AQUA));
        }

        List<BlockPos> links = distributor.getLinkedMachines();
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.links",
                links.size()).withStyle(ChatFormatting.WHITE));

        if (links.isEmpty()) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.no_links")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        IElementHelper elements = IElementHelper.get();
        int shown = Math.min(links.size(), MAX_LINKS);
        for (int i = 0; i < shown; i++) {
            BlockPos pos = links.get(i);
            ItemStack icon = distributor.getLinkedBlockIcon(pos);
            List<IElement> line = new ArrayList<>();
            if (!icon.isEmpty()) {
                line.add(elements.item(icon, 0.75f));
            }
            line.add(elements.text(linkText(distributor, i, pos)));
            tooltip.add(line);
        }

        if (links.size() > shown) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.more_links",
                    links.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component linkText(PackageDistributorBlockEntity distributor, int index, BlockPos pos) {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.link_entry",
                index + 1,
                Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.role."
                        + distributor.getLinkedRoleKey(pos)),
                distributor.getLinkedBlockName(pos),
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY);
    }

    private static ChatFormatting statusColor(String status) {
        return switch (status) {
            case "ready", "completed" -> ChatFormatting.GREEN;
            case "working" -> ChatFormatting.AQUA;
            case "busy" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
