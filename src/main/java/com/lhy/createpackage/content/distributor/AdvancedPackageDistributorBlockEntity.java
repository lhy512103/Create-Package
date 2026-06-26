package com.lhy.createpackage.content.distributor;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.registry.ModBlockEntities;
import com.lhy.createpackage.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.SettingsFrom;

/**
 * A package distributor with an embedded AE2 pattern provider inventory.
 *
 * <p>The AE2-facing provider service exposes the patterns stored in this block. When the crafting
 * CPU pushes one of those patterns, it is routed directly into the distributor pipeline inherited
 * from {@link PackageDistributorBlockEntity}; no adjacent pattern provider is required.
 */
public class AdvancedPackageDistributorBlockEntity extends PackageDistributorBlockEntity
        implements PatternProviderLogicHost {

    private final InternalPatternProviderLogic logic = new InternalPatternProviderLogic();
    private final CombinedTicker combinedTicker = new CombinedTicker();

    public AdvancedPackageDistributorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ADVANCED_PACKAGE_DISTRIBUTOR.get(),
                ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get(),
                "block." + CreatePackage.MODID + ".advanced_package_distributor",
                pos, blockState, false);
        getMainNode().addService(IGridTickable.class, combinedTicker)
                .addService(ICraftingProvider.class, logic);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        logic.onMainNodeStateChanged();
    }

    @Override
    public void onReady() {
        super.onReady();
        logic.updatePatterns();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        logic.writeToNBT(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        logic.readFromNBT(data, registries);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
    }

    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.noneOf(Direction.class);
    }

    @Override
    public void saveChanges() {
        saveAndSync();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get());
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return logic.getPatternInv();
    }

    @Override
    public @Nullable IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return new PatternContainerGroup(
                getTerminalIcon(),
                ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get().getDescription(),
                List.of());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.toStack();
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(PatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(PatternProviderMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    public void exportSettings(SettingsFrom mode, net.minecraft.core.component.DataComponentMap.Builder builder,
            @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.exportSettings(builder);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, net.minecraft.core.component.DataComponentMap input,
            @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.importSettings(input, player);
        }
    }

    private final class InternalPatternProviderLogic extends PatternProviderLogic {
        private InternalPatternProviderLogic() {
            super(getMainNode(), AdvancedPackageDistributorBlockEntity.this);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            if (!getAvailablePatterns().contains(patternDetails)) {
                return false;
            }
            if (getCraftingLockedReason() != LockCraftingMode.NONE) {
                return false;
            }
            boolean accepted = AdvancedPackageDistributorBlockEntity.super.pushPattern(
                    patternDetails, inputHolder, Direction.UP);
            if (accepted) {
                resetCraftingLock();
            }
            return accepted;
        }

        @Override
        public boolean isBusy() {
            return AdvancedPackageDistributorBlockEntity.this.hasActiveJob();
        }
    }

    private final class CombinedTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(5, 20, !hasDistributorJob());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (hasDistributorJob()) {
                return tickDistributorJob() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
            }
            return TickRateModulation.SLEEP;
        }
    }
}
