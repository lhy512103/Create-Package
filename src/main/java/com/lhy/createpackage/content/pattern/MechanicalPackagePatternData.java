package com.lhy.createpackage.content.pattern;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.lhy.createpackage.registry.ModComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Data stored on a mechanical package pattern.
 *
 * <p>The original AE2 encoded pattern keeps the crafting inputs/outputs, while the route captures
 * the Create machine links selected during conversion.
 */
public record MechanicalPackagePatternData(ItemStack encodedPattern, List<BlockPos> route) {
    public static final Codec<MechanicalPackagePatternData> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ItemStack.OPTIONAL_CODEC.fieldOf("encoded_pattern")
                            .forGetter(MechanicalPackagePatternData::encodedPattern),
                    BlockPos.CODEC.listOf().fieldOf("route")
                            .forGetter(MechanicalPackagePatternData::route))
            .apply(instance, MechanicalPackagePatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MechanicalPackagePatternData> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    MechanicalPackagePatternData::encodedPattern,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    MechanicalPackagePatternData::route,
                    MechanicalPackagePatternData::new);

    public MechanicalPackagePatternData {
        encodedPattern = encodedPattern.copyWithCount(1);
        route = route.stream().map(BlockPos::immutable).toList();
    }

    public boolean isValid() {
        return !encodedPattern.isEmpty() && !route.isEmpty();
    }

    public static MechanicalPackagePatternData from(ItemStack stack) {
        return stack.get(ModComponents.MECHANICAL_PACKAGE_PATTERN.get());
    }
}
