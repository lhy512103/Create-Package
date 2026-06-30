package com.lhy.createpackage.integration.jade;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class KineticPatternProviderComponentProvider implements IBlockComponentProvider {
    public static final KineticPatternProviderComponentProvider INSTANCE =
            new KineticPatternProviderComponentProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            CreatePackage.MODID, "kinetic_pattern_provider");

    private KineticPatternProviderComponentProvider() {}

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof KineticPatternProviderBlockEntity provider)) {
            return;
        }
        tooltip.add(provider.statusLine());
        tooltip.add(provider.aeLine());
        tooltip.add(provider.smartDoublingLine());
        tooltip.add(provider.machineLine());
        for (Component line : provider.jobLines()) {
            tooltip.add(line.copy().withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
