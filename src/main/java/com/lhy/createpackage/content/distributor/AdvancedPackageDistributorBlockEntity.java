package com.lhy.createpackage.content.distributor;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.compat.ExtendedAePlusCompat;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternDetails;
import com.lhy.createpackage.registry.ModBlockEntities;
import com.lhy.createpackage.registry.ModItems;
import com.lhy.createpackage.registry.ModMenuTypes;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.SettingsFrom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Advanced distributor: one AE2 pattern-provider inventory can store mechanical package patterns
 * for multiple Create assembly routes. Parallel cards increase how many disjoint routes may run
 * at once.
 */
public class AdvancedPackageDistributorBlockEntity extends PackageDistributorBlockEntity
        implements PatternProviderLogicHost, IUpgradeableObject {
    private static final String NBT_UPGRADES = "upgrades";
    private static final String NBT_LAST_FAILURE_ROUTE = "lastFailureRoute";
    private static final String NBT_LAST_FAILURE_SLOT = "lastFailureSlot";
    private static final int MAX_PARALLEL_CARDS = 2;

    private final InternalPatternProviderLogic logic = new InternalPatternProviderLogic();
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get(), MAX_PARALLEL_CARDS, this::onUpgradesChanged);
    private final CombinedTicker combinedTicker = new CombinedTicker();
    private int visibleMechanicalPackagePatternCount;
    private int visibleParallelCards;
    private List<BlockPos> lastFailureRoute = List.of();
    private int lastFailureSlot = -1;

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
        ExtendedAePlusCompat.disableProviderSmartSettings(logic.getConfigManager());
        logic.updatePatterns();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        logic.writeToNBT(data, registries);
        ExtendedAePlusCompat.removeProviderSmartSettings(data);
        upgrades.writeToNBT(data, NBT_UPGRADES, registries);
        if (!lastFailureRoute.isEmpty()) {
            long[] route = new long[lastFailureRoute.size()];
            for (int i = 0; i < route.length; i++) {
                route[i] = lastFailureRoute.get(i).asLong();
            }
            data.putLongArray(NBT_LAST_FAILURE_ROUTE, route);
            data.putInt(NBT_LAST_FAILURE_SLOT, lastFailureSlot);
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        ExtendedAePlusCompat.removeProviderSmartSettings(data);
        logic.readFromNBT(data, registries);
        ExtendedAePlusCompat.disableProviderSmartSettings(logic.getConfigManager());
        upgrades.readFromNBT(data, NBT_UPGRADES, registries);
        var route = new java.util.ArrayList<BlockPos>();
        for (long packed : data.getLongArray(NBT_LAST_FAILURE_ROUTE)) {
            route.add(BlockPos.of(packed));
        }
        lastFailureRoute = List.copyOf(route);
        lastFailureSlot = data.contains(NBT_LAST_FAILURE_SLOT) ? data.getInt(NBT_LAST_FAILURE_SLOT) : -1;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        logic.addDrops(drops);
        for (ItemStack upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
        upgrades.clear();
    }

    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public @Nullable InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.UPGRADES)) {
            return upgrades;
        }
        return super.getSubInventory(id);
    }

    @Override
    public boolean usesStoredMachineLinks() {
        return false;
    }

    @Override
    public int getMechanicalPackagePatternCount() {
        if (level != null && level.isClientSide()) {
            return visibleMechanicalPackagePatternCount;
        }
        return countMechanicalPackagePatterns();
    }

    @Override
    public int getMaxActiveJobs() {
        return 1 << getParallelCardCount();
    }

    public int getParallelCardCount() {
        if (level != null && level.isClientSide()) {
            return visibleParallelCards;
        }
        return Math.min(MAX_PARALLEL_CARDS, upgrades.getInstalledUpgrades(ModItems.PARALLEL_CARD.get()));
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeVarInt(countMechanicalPackagePatterns());
        data.writeVarInt(getParallelCardCount());
        data.writeVarInt(lastFailureSlot);
        data.writeVarInt(lastFailureRoute.size());
        for (BlockPos pos : lastFailureRoute) {
            data.writeBlockPos(pos);
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int streamedCount = data.readVarInt();
        if (visibleMechanicalPackagePatternCount != streamedCount) {
            visibleMechanicalPackagePatternCount = streamedCount;
            changed = true;
        }
        int streamedParallelCards = data.readVarInt();
        if (visibleParallelCards != streamedParallelCards) {
            visibleParallelCards = streamedParallelCards;
            changed = true;
        }
        int streamedFailureSlot = data.readVarInt();
        List<BlockPos> streamedFailureRoute = new java.util.ArrayList<>();
        int routeSize = data.readVarInt();
        for (int i = 0; i < routeSize; i++) {
            streamedFailureRoute.add(data.readBlockPos());
        }
        if (lastFailureSlot != streamedFailureSlot || !lastFailureRoute.equals(streamedFailureRoute)) {
            lastFailureSlot = streamedFailureSlot;
            lastFailureRoute = List.copyOf(streamedFailureRoute);
            changed = true;
        }
        return changed;
    }

    private int countMechanicalPackagePatterns() {
        int count = 0;
        var patterns = logic.getPatternInv();
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.getStackInSlot(i).is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void addParallelInfoToTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.parallel_cards",
                getParallelCardCount(), MAX_PARALLEL_CARDS, getMaxActiveJobs())
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.parallel_jobs",
                getActiveJobCount(), getMaxActiveJobs())
                .withStyle(getActiveJobCount() > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        if (!lastFailureRoute.isEmpty() && isFailureStatus(getStatusKey())) {
            var input = lastFailureRoute.get(0);
            var output = lastFailureRoute.get(lastFailureRoute.size() - 1);
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID
                    + ".package_distributor.failure_route",
                    lastFailureSlot >= 0 ? lastFailureSlot + 1 : 0,
                    input.getX(), input.getY(), input.getZ(),
                    output.getX(), output.getY(), output.getZ())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    public int getLastFailureSlot() {
        return lastFailureSlot;
    }

    public List<BlockPos> getLastFailureRoute() {
        return lastFailureRoute;
    }

    private static boolean isFailureStatus(String status) {
        return "simulate_failed".equals(status) || "execute_failed".equals(status);
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
        MenuOpener.open(ModMenuTypes.ADVANCED_PACKAGE_DISTRIBUTOR.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(ModMenuTypes.ADVANCED_PACKAGE_DISTRIBUTOR.get(), player, subMenu.getLocator());
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.exportSettings(builder);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.importSettings(input, player);
        }
    }

    private void onUpgradesChanged() {
        saveAndSync();
        ICraftingProvider.requestUpdate(getMainNode());
    }

    @Override
    protected void rememberFailureRoute(List<BlockPos> route) {
        lastFailureRoute = route.stream().map(BlockPos::immutable).toList();
        lastFailureSlot = findPatternSlotForRoute(route);
        saveAndSync();
    }

    private int findPatternSlotForRoute(List<BlockPos> route) {
        var patterns = logic.getPatternInv();
        for (int i = 0; i < patterns.size(); i++) {
            AEItemKey key = AEItemKey.of(patterns.getStackInSlot(i));
            if (key == null || level == null) {
                continue;
            }
            MechanicalPackagePatternDetails details = MechanicalPackagePatternDetails.decode(key, level);
            if (details != null && details.route().equals(route)) {
                return i;
            }
        }
        return -1;
    }

    private final class InternalPatternProviderLogic extends PatternProviderLogic {
        private InternalPatternProviderLogic() {
            super(getMainNode(), AdvancedPackageDistributorBlockEntity.this);
        }

        @Override
        public void updatePatterns() {
            ExtendedAePlusCompat.disableProviderSmartSettings(getConfigManager());
            super.updatePatterns();
            ExtendedAePlusCompat.disableProviderSmartSettings(getConfigManager());
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            if (!getAvailablePatterns().contains(patternDetails)) {
                return false;
            }
            if (getCraftingLockedReason() != LockCraftingMode.NONE) {
                return false;
            }
            if (!(patternDetails instanceof MechanicalPackagePatternDetails mechanical)) {
                rememberStatus("invalid_mechanical_pattern");
                return false;
            }
            boolean accepted = AdvancedPackageDistributorBlockEntity.this.pushPattern(
                    mechanical.delegate(), inputHolder, Direction.UP, mechanical.route());
            if (accepted) {
                resetCraftingLock();
            }
            return accepted;
        }

        @Override
        public boolean isBusy() {
            return !AdvancedPackageDistributorBlockEntity.this.canAcceptMoreJobs();
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
