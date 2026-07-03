package com.lhy.createpackage.content.pattern;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;

import net.minecraft.core.BlockPos;

/**
 * Route-bearing processing pattern that still looks like AE2's native processing pattern to the
 * Pattern Encoding Terminal.
 */
public final class MechanicalPackageProcessingPatternDetails extends AEProcessingPattern
        implements RoutedMechanicalPackagePatternDetails {
    private final AEItemKey definition;
    private final AEProcessingPattern delegate;
    private final List<BlockPos> route;
    private final Optional<MechanicalPackagePatternData.PatternEncoder> encoder;

    public MechanicalPackageProcessingPatternDetails(AEItemKey definition, AEItemKey delegateDefinition,
            List<BlockPos> route, Optional<MechanicalPackagePatternData.PatternEncoder> encoder) {
        super(delegateDefinition);
        this.definition = Objects.requireNonNull(definition);
        this.delegate = new AEProcessingPattern(Objects.requireNonNull(delegateDefinition));
        this.route = route.stream().map(BlockPos::immutable).toList();
        this.encoder = encoder == null ? Optional.empty() : encoder;
    }

    @Override
    public IPatternDetails delegate() {
        return delegate;
    }

    @Override
    public List<BlockPos> route() {
        return route;
    }

    @Override
    public Optional<MechanicalPackagePatternData.PatternEncoder> encoder() {
        return encoder;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, delegate.getDefinition(), route, encoder);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MechanicalPackageProcessingPatternDetails other)) {
            return false;
        }
        return Objects.equals(definition, other.definition)
                && Objects.equals(delegate.getDefinition(), other.delegate.getDefinition())
                && Objects.equals(route, other.route)
                && Objects.equals(encoder, other.encoder);
    }
}
