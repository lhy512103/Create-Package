package com.lhy.createpackage.content.pattern;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * AE2 pattern-details wrapper for a mechanical package pattern.
 */
public final class MechanicalPackagePatternDetails implements RoutedMechanicalPackagePatternDetails {
    private final AEItemKey definition;
    private final IPatternDetails delegate;
    private final List<BlockPos> route;
    private final Optional<MechanicalPackagePatternData.PatternEncoder> encoder;

    private MechanicalPackagePatternDetails(AEItemKey definition, IPatternDetails delegate, List<BlockPos> route,
            Optional<MechanicalPackagePatternData.PatternEncoder> encoder) {
        this.definition = Objects.requireNonNull(definition);
        this.delegate = Objects.requireNonNull(delegate);
        this.route = route.stream().map(BlockPos::immutable).toList();
        this.encoder = encoder == null ? Optional.empty() : encoder;
    }

    @Nullable
    public static RoutedMechanicalPackagePatternDetails decode(AEItemKey what, Level level) {
        var data = MechanicalPackagePatternData.from(what.getReadOnlyStack());
        if (data == null || !data.isValid()) {
            return null;
        }
        AEItemKey innerDefinition = AEItemKey.of(data.encodedPattern());
        if (innerDefinition == null) {
            return null;
        }
        var inner = PatternDetailsHelper.decodePattern(data.encodedPattern(), level);
        if (inner == null) {
            return null;
        }
        if (inner instanceof AEProcessingPattern) {
            return new MechanicalPackageProcessingPatternDetails(what, innerDefinition, data.route(), data.encoder());
        }
        return new MechanicalPackagePatternDetails(what, inner, data.route(), data.encoder());
    }

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
    public IInput[] getInputs() {
        return delegate.getInputs();
    }

    @Override
    public GenericStack getPrimaryOutput() {
        return delegate.getPrimaryOutput();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, delegate, route, encoder);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MechanicalPackagePatternDetails other)) {
            return false;
        }
        return Objects.equals(definition, other.definition)
                && Objects.equals(delegate, other.delegate)
                && Objects.equals(route, other.route)
                && Objects.equals(encoder, other.encoder);
    }
}
