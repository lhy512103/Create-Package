package com.lhy.createpackage.content.kinetic;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.registry.ModBlockEntities;
import com.lhy.createpackage.registry.ModItems;
import com.lhy.createpackage.registry.ModMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ExtendedKineticPatternProviderBlockEntity extends KineticPatternProviderBlockEntity {
    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_PAGES = 9;
    public static final int PATTERN_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;

    public ExtendedKineticPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.EXTENDED_KINETIC_PATTERN_PROVIDER.get(), pos, blockState,
                ModItems.EXTENDED_KINETIC_PATTERN_PROVIDER.get(),
                ModMenuTypes.EXTENDED_KINETIC_PATTERN_PROVIDER.get(), PATTERN_SLOTS);
    }

    public int getAvailablePatternPages() {
        int configuredPages = Math.min(MAX_PAGES, Math.max(1, Config.extendedKineticProviderPageMultiplier()));
        int highestUsedSlot = -1;
        var patterns = getLogic().getPatternInv();
        for (int slot = patterns.size() - 1; slot >= 0; slot--) {
            if (!patterns.getStackInSlot(slot).isEmpty()) {
                highestUsedSlot = slot;
                break;
            }
        }
        int pagesRequiredByContents = highestUsedSlot < 0 ? 1 : highestUsedSlot / SLOTS_PER_PAGE + 1;
        return Math.max(configuredPages, pagesRequiredByContents);
    }
}