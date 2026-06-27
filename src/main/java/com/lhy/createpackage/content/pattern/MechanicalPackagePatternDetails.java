package com.lhy.createpackage.content.pattern;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * AE2 pattern-details wrapper for a mechanical package pattern.
 */
public final class MechanicalPackagePatternDetails implements IPatternDetails {
    private final AEItemKey definition;
    private final IPatternDetails delegate;
    private final List<BlockPos> route;

    private MechanicalPackagePatternDetails(AEItemKey definition, IPatternDetails delegate, List<BlockPos> route) {
        this.definition = Objects.requireNonNull(definition);
        this.delegate = Objects.requireNonNull(delegate);
        this.route = route.stream().map(BlockPos::immutable).toList();
    }

    @Nullable
    public static MechanicalPackagePatternDetails decode(AEItemKey what, Level level) {
        var data = MechanicalPackagePatternData.from(what.getReadOnlyStack());
        if (data == null || !data.isValid()) {
            return null;
        }
        var inner = PatternDetailsHelper.decodePattern(data.encodedPattern(), level);
        if (inner == null) {
            return null;
        }
        return new MechanicalPackagePatternDetails(what, inner, data.route());
    }

    public IPatternDetails delegate() {
        return delegate;
    }

    public List<BlockPos> route() {
        return route;
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
        return Objects.hash(definition, delegate, route);
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
                && Objects.equals(route, other.route);
    }
}
