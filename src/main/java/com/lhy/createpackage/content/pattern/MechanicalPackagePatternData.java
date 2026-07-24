package com.lhy.createpackage.content.pattern;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.lhy.createpackage.registry.ModComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Data stored on a mechanical package pattern.
 *
 * <p>The original AE2 encoded pattern keeps the crafting inputs/outputs, while the route captures
 * the Create machine links selected during conversion. The route may be empty while the pattern is
 * being prepared, but empty-route patterns are not valid for AE2 autocrafting until marked.
 */
public record MechanicalPackagePatternData(ItemStack encodedPattern, List<BlockPos> route,
        Optional<PatternEncoder> encoder) {
    public static final Codec<MechanicalPackagePatternData> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ItemStack.CODEC.fieldOf("encoded_pattern")
                            .forGetter(MechanicalPackagePatternData::encodedPattern),
                    BlockPos.CODEC.listOf().fieldOf("route")
                            .forGetter(MechanicalPackagePatternData::route),
                    PatternEncoder.CODEC.optionalFieldOf("encoder")
                            .forGetter(MechanicalPackagePatternData::encoder))
            .apply(instance, MechanicalPackagePatternData::new));

    public MechanicalPackagePatternData(ItemStack encodedPattern, List<BlockPos> route) {
        this(encodedPattern, route, Optional.empty());
    }

    public MechanicalPackagePatternData {
        encodedPattern = encodedPattern.copyWithCount(1);
        route = route.stream().map(BlockPos::immutable).toList();
        encoder = encoder == null ? Optional.empty() : encoder;
    }

    public boolean isValid() {
        return !encodedPattern.isEmpty() && !route.isEmpty();
    }

    public static MechanicalPackagePatternData from(ItemStack stack) {
        return ModComponents.getMechanicalPattern(stack);
    }

    public record PatternEncoder(UUID uuid, String name) {
        private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

        public static final Codec<PatternEncoder> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        UUID_CODEC.fieldOf("uuid").forGetter(PatternEncoder::uuid),
                        Codec.STRING.fieldOf("name").forGetter(PatternEncoder::name))
                .apply(instance, PatternEncoder::new));

        public static PatternEncoder of(Player player) {
            return new PatternEncoder(player.getUUID(), player.getGameProfile().getName());
        }

        public PatternEncoder {
            name = name == null ? "" : name;
        }
    }
}
