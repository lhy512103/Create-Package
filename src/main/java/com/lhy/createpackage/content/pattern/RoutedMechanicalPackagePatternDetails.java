package com.lhy.createpackage.content.pattern;

import java.util.List;
import java.util.Optional;

import appeng.api.crafting.IPatternDetails;

import net.minecraft.core.BlockPos;

public interface RoutedMechanicalPackagePatternDetails extends IPatternDetails {
    IPatternDetails delegate();

    List<BlockPos> route();

    Optional<MechanicalPackagePatternData.PatternEncoder> encoder();
}
