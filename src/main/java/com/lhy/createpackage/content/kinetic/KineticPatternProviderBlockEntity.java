package com.lhy.createpackage.content.kinetic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.compat.ExtendedAePlusCompat;
import com.lhy.createpackage.content.crafting.ActionList;
import com.lhy.createpackage.content.crafting.CreateMachineIds;
import com.lhy.createpackage.content.crafting.DynamicSegmentWalker;
import com.lhy.createpackage.content.crafting.ExpectedOutputMatcher;
import com.lhy.createpackage.content.crafting.FailureDiagnostics;
import com.lhy.createpackage.content.crafting.InventoryAccess;
import com.lhy.createpackage.content.crafting.MachineEndpoint;
import com.lhy.createpackage.content.crafting.MachinePlanFactory;
import com.lhy.createpackage.content.crafting.NetworkIO;
import com.lhy.createpackage.content.crafting.PatternStacks;
import com.lhy.createpackage.content.crafting.RefillCraftingOrders;
import com.lhy.createpackage.content.crafting.SupplyAction;
import com.lhy.createpackage.content.crafting.SupplyActions;
import com.lhy.createpackage.registry.ModBlockEntities;
import com.lhy.createpackage.registry.ModItems;
import com.lhy.createpackage.registry.ModMenuTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import net.minecraft.world.phys.AABB;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.SettingsFrom;

public class KineticPatternProviderBlockEntity extends AENetworkedBlockEntity
        implements PatternProviderLogicHost, IGridTickable, IHaveGoggleInformation, IUpgradeableObject {
    private static final String NBT_JOB = "kineticJob";
    private static final String NBT_JOBS = "kineticJobs";
    private static final String NBT_STATUS = "kineticStatus";
    private static final String NBT_SMART_DOUBLING = "smartDoubling";
    private static final String NBT_UPGRADES = "upgrades";
    private static final String NBT_PARALLEL_UPGRADES = "parallel_upgrades";
    private static final String NBT_LINKED_MACHINES = "linkedMachines";
    private static final int MAX_PARALLEL_CARDS = 2;
    private static final int PARALLEL_MACHINES_PER_CARD = 16;
    private static final int MAX_TOOLTIP_TARGETS = 6;
    private static final int DEFAULT_MAX_JOB_TICKS = 20 * 60 * 5;
    private static final int DEFAULT_ROUND_OUTPUT_TIMEOUT_TICKS = 20 * 60;
    private static final int SMART_BATCH_GATHER_TICKS = 0;

    private final InternalPatternProviderLogic logic;
    private final IUpgradeInventory upgrades;
    private final Item providerItem;
    private final MenuType<? extends KineticPatternProviderMenu> providerMenuType;
    private final MachineSource actionSource = new MachineSource(this);
    private final RefillCraftingOrders refillCraftingOrders = new RefillCraftingOrders(() -> getMainNode().getNode());
    private final List<KineticJob> activeJobs = new ArrayList<>();
    private final List<BlockPos> linkedMachines = new ArrayList<>();
    private final FailureDiagnostics diagnostics = new FailureDiagnostics();

    private String lastStatusKey = "ready";
    private boolean smartDoubling;
    private boolean aeNodePresent;
    private boolean aePowered;
    private boolean aeChannel;
    private boolean aeActive;
    private int visibleParallelCards;

    public KineticPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.KINETIC_PATTERN_PROVIDER.get(), pos, blockState, ModItems.KINETIC_PATTERN_PROVIDER.get(),
                ModMenuTypes.KINETIC_PATTERN_PROVIDER.get(), 9);
    }

    protected KineticPatternProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
            Item providerItem, MenuType<? extends KineticPatternProviderMenu> providerMenuType, int patternSlots) {
        super(type, pos, blockState);
        this.providerItem = providerItem;
        this.providerMenuType = providerMenuType;
        this.logic = new InternalPatternProviderLogic(patternSlots);
        this.upgrades = UpgradeInventories.forMachine(providerItem, MAX_PARALLEL_CARDS, this::onUpgradesChanged);
        getMainNode()
                .setVisualRepresentation(providerItem)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .addService(IGridTickable.class, this)
                .addService(ICraftingProvider.class, logic)
                .addService(ICraftingWatcherNode.class, refillCraftingOrders);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        logic.onMainNodeStateChanged();
        if (level != null && !level.isClientSide()) {
            markForUpdate();
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        ExtendedAePlusCompat.disableProviderSmartSettings(logic.getConfigManager());
        logic.updatePatterns();
        if (level != null && !level.isClientSide()) {
            migrateHostUpgradesIntoLogic();
            normalizeLinkedMachines();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        logic.writeToNBT(data, registries);
        ExtendedAePlusCompat.removeProviderSmartSettings(data);
        if (getUpgrades() == upgrades) {
            upgrades.writeToNBT(data, NBT_PARALLEL_UPGRADES, registries);
        }
        data.putString(NBT_STATUS, lastStatusKey);
        data.putBoolean(NBT_SMART_DOUBLING, smartDoubling);
        long[] linked = new long[linkedMachines.size()];
        for (int i = 0; i < linked.length; i++) {
            linked[i] = linkedMachines.get(i).asLong();
        }
        data.putLongArray(NBT_LINKED_MACHINES, linked);
        if (!activeJobs.isEmpty()) {
            net.minecraft.nbt.ListTag jobs = new net.minecraft.nbt.ListTag();
            for (KineticJob job : activeJobs) {
                jobs.add(job.write(registries));
            }
            data.put(NBT_JOBS, jobs);
        }
        diagnostics.write(data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        ExtendedAePlusCompat.removeProviderSmartSettings(data);
        logic.readFromNBT(data, registries);
        ExtendedAePlusCompat.disableProviderSmartSettings(logic.getConfigManager());
        if (data.contains(NBT_PARALLEL_UPGRADES)) {
            upgrades.readFromNBT(data, NBT_PARALLEL_UPGRADES, registries);
        } else if (ExtendedAePlusCompat.patternLogicUpgrades(logic) == null) {
            upgrades.readFromNBT(data, NBT_UPGRADES, registries);
        }
        lastStatusKey = data.contains(NBT_STATUS) ? data.getString(NBT_STATUS) : "ready";
        smartDoubling = data.contains(NBT_SMART_DOUBLING) && data.getBoolean(NBT_SMART_DOUBLING);
        linkedMachines.clear();
        for (long packed : data.getLongArray(NBT_LINKED_MACHINES)) {
            linkedMachines.add(BlockPos.of(packed));
        }
        activeJobs.clear();
        if (data.contains(NBT_JOBS)) {
            var jobs = data.getList(NBT_JOBS, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < jobs.size(); i++) {
                KineticJob job = KineticJob.read(jobs.getCompound(i), registries);
                if (job != null) {
                    activeJobs.add(job);
                }
            }
        } else if (data.contains(NBT_JOB)) {
            KineticJob job = KineticJob.read(data.getCompound(NBT_JOB), registries);
            if (job != null) {
                activeJobs.add(job);
            }
        }
        diagnostics.read(data);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        AeStatus status = currentAeStatus();
        data.writeBoolean(status.nodePresent());
        data.writeBoolean(status.powered());
        data.writeBoolean(status.channel());
        data.writeBoolean(status.active());
        data.writeBoolean(smartDoubling);
        data.writeVarInt(getParallelCardCount());
        data.writeVarInt(linkedMachines.size());
        for (BlockPos linked : linkedMachines) {
            data.writeBlockPos(linked);
        }
        data.writeUtf(lastStatusKey);
        data.writeVarInt(activeJobs.size());
        for (KineticJob job : activeJobs) {
            AEKey.writeKey(data, job.primaryOutput);
            data.writeVarLong(job.remaining);
            data.writeBlockPos(job.machinePos);
            data.writeBlockPos(job.outputPos);
            data.writeUtf(job.machineRole);
            data.writeVarInt(job.ticks);
            data.writeUtf(job.statusKey);
            data.writeVarLong(job.nextRefillGameTime);
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean streamedNode = data.readBoolean();
        boolean streamedPowered = data.readBoolean();
        boolean streamedChannel = data.readBoolean();
        boolean streamedActive = data.readBoolean();
        if (aeNodePresent != streamedNode || aePowered != streamedPowered
                || aeChannel != streamedChannel || aeActive != streamedActive) {
            aeNodePresent = streamedNode;
            aePowered = streamedPowered;
            aeChannel = streamedChannel;
            aeActive = streamedActive;
            changed = true;
        }

        boolean streamedSmartDoubling = data.readBoolean();
        if (smartDoubling != streamedSmartDoubling) {
            smartDoubling = streamedSmartDoubling;
            changed = true;
        }

        int streamedParallelCards = data.readVarInt();
        if (visibleParallelCards != streamedParallelCards) {
            visibleParallelCards = streamedParallelCards;
            changed = true;
        }

        List<BlockPos> streamedLinks = new ArrayList<>();
        int linkCount = data.readVarInt();
        for (int i = 0; i < linkCount; i++) {
            streamedLinks.add(data.readBlockPos());
        }
        if (!linkedMachines.equals(streamedLinks)) {
            linkedMachines.clear();
            linkedMachines.addAll(streamedLinks);
            changed = true;
        }

        String streamedStatus = data.readUtf();
        if (!Objects.equals(lastStatusKey, streamedStatus)) {
            lastStatusKey = streamedStatus;
            changed = true;
        }

        List<KineticJob> streamedJobs = new ArrayList<>();
        int jobCount = data.readVarInt();
        for (int i = 0; i < jobCount; i++) {
            AEKey output = AEKey.readKey(data);
            long remaining = data.readVarLong();
            BlockPos machinePos = data.readBlockPos();
            BlockPos outputPos = data.readBlockPos();
            String machineRole = data.readUtf();
            if (output != null) {
                AEItemKey definition = output instanceof AEItemKey itemKey ? itemKey : null;
                KineticJob streamedJob = new KineticJob(output, definition, remaining, machinePos, outputPos,
                        List.of(defaultOutputEndpoint(output, outputPos)), machineRole, List.of(), List.of(), false);
                streamedJob.ticks = data.readVarInt();
                streamedJob.statusKey = data.readUtf();
                streamedJob.nextRefillGameTime = data.readVarLong();
                streamedJobs.add(streamedJob);
            } else {
                data.readVarInt();
                data.readUtf();
                data.readVarLong();
            }
        }
        if (!sameJobs(activeJobs, streamedJobs)) {
            activeJobs.clear();
            activeJobs.addAll(streamedJobs);
            changed = true;
        }
        return changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        logic.addDrops(drops);
        if (getUpgrades() == upgrades) {
            for (ItemStack upgrade : upgrades) {
                if (!upgrade.isEmpty()) {
                    drops.add(upgrade);
                }
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
        upgrades.clear();
        refillCraftingOrders.clear();
        activeJobs.clear();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        // PUSH_DIRECTION is not an AE orientation property, so wrenching must still refresh
        // which sides expose the in-world grid node. Matches AE2 PatternProviderBlockEntity.
        onGridConnectableSidesChanged();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        Direction target = targetDirection();
        if (target == null) {
            return EnumSet.allOf(Direction.class);
        }
        return EnumSet.complementOf(EnumSet.of(target));
    }

    public void updateGridConnectableSides() {
        onGridConnectableSidesChanged();
        if (level != null && !level.isClientSide() && "missing_target".equals(lastStatusKey)
                && targetDirection() != null) {
            lastStatusKey = "ready";
            saveAndSync();
        }
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
            return getUpgrades();
        }
        return super.getSubInventory(id);
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        Direction target = targetDirection();
        return target == null ? EnumSet.noneOf(Direction.class) : EnumSet.of(target);
    }

    @Override
    public void saveChanges() {
        saveAndSync();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(providerItem);
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
        Direction target = targetDirection();
        if (level != null && target != null) {
            BlockPos targetPos = getBlockPos().relative(target);
            if (level.isLoaded(targetPos)) {
                PatternContainerGroup group = PatternContainerGroup.fromMachine(level, targetPos, target.getOpposite());
                if (group != null) {
                    return group;
                }

                ItemStack targetIcon = new ItemStack(level.getBlockState(targetPos).getBlock().asItem());
                AEItemKey targetKey = targetIcon.isEmpty() ? null : AEItemKey.of(targetIcon);
                if (targetKey != null) {
                    return new PatternContainerGroup(targetKey, targetIcon.getHoverName(), List.of());
                }
            }
        }
        return new PatternContainerGroup(
                getTerminalIcon(),
                providerItem.getDescription(),
                List.of());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(providerItem);
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(providerMenuType, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(providerMenuType, player, subMenu.getLocator());
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.exportSettings(builder);
            builder.set(AEComponents.EXPORTED_PUSH_DIRECTION, getPushDirection());
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.importSettings(input, player);
            PushDirection pushDirection = input.get(AEComponents.EXPORTED_PUSH_DIRECTION);
            if (pushDirection != null && level != null) {
                level.setBlockAndUpdate(getBlockPos(),
                        getBlockState().setValue(KineticPatternProviderBlock.PUSH_DIRECTION, pushDirection));
                updateGridConnectableSides();
            }
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(5, 20, activeJobs.isEmpty() && !hasReturnInventoryWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level == null || level.isClientSide() || !getMainNode().isActive()) {
            return TickRateModulation.SLEEP;
        }
        boolean didWork = returnInventoryToNetwork();
        if (!activeJobs.isEmpty()) {
            didWork |= tickJobs(ticksSinceLastCall);
        }
        if (!activeJobs.isEmpty() || hasReturnInventoryWork()) {
            return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        return TickRateModulation.SLEEP;
    }

    public FailureDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public String getStatusKey() {
        if (activeJobs.isEmpty() && ("working".equals(lastStatusKey) || "busy".equals(lastStatusKey))) {
            return "ready";
        }
        return lastStatusKey;
    }

    public Component getMachineName() {
        BlockPos pos = targetMachinePos();
        return machineNameAt(pos);
    }

    private Component machineNameAt(@Nullable BlockPos pos) {
        if (pos == null) {
            return Component.translatable("tooltip." + CreatePackage.MODID
                    + ".kinetic_pattern_provider.not_configured");
        }
        if (level == null || !level.isLoaded(pos)) {
            return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.unloaded");
        }
        ItemStack icon = new ItemStack(level.getBlockState(pos).getBlock().asItem());
        if (!icon.isEmpty()) {
            return icon.getHoverName();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return id == null ? Component.literal("unknown") : Component.literal(id.toString());
    }

    public @Nullable BlockPos getTargetMachinePos() {
        return targetMachinePos();
    }

    public Component machineLine() {
        List<BlockPos> targets = targetMachinePositions();
        if (targets.isEmpty()) {
            return Component.translatable("tooltip." + CreatePackage.MODID
                    + ".kinetic_pattern_provider.machine_unconfigured").withStyle(ChatFormatting.RED);
        }
        BlockPos target = targets.get(0);
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.machine",
                getMachineName(),
                target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.GRAY);
    }

    public @Nullable KineticJob getActiveJob() {
        return activeJobs.isEmpty() ? null : activeJobs.get(0);
    }

    public List<KineticJob> getActiveJobs() {
        return List.copyOf(activeJobs);
    }

    public int getParallelCardCount() {
        if (level != null && level.isClientSide()) {
            return visibleParallelCards;
        }
        return Math.min(MAX_PARALLEL_CARDS, countInstalledParallelCards());
    }

    public int getMaxParallelMachines() {
        int cards = getParallelCardCount();
        if (cards <= 0) {
            return 1;
        }
        return cards * PARALLEL_MACHINES_PER_CARD;
    }

    public int getActiveJobCount() {
        return activeJobs.size();
    }

    public List<BlockPos> getLinkedMachines() {
        return List.copyOf(linkedMachines);
    }

    private BlockPos normalizeLinkedMachinePos(BlockPos pos) {
        return level == null ? pos.immutable() : CreateMachineIds.normalizeKineticMachinePos(level, pos);
    }

    private void normalizeLinkedMachines() {
        List<BlockPos> normalized = new ArrayList<>();
        for (BlockPos linked : linkedMachines) {
            BlockPos master = normalizeLinkedMachinePos(linked);
            if (!normalized.contains(master)) {
                normalized.add(master);
            }
        }
        if (!linkedMachines.equals(normalized)) {
            linkedMachines.clear();
            linkedMachines.addAll(normalized);
            onLinksChanged();
        }
    }

    public boolean linkMachine(BlockPos pos) {
        BlockPos normalized = normalizeLinkedMachinePos(pos);
        if (linkedMachines.contains(normalized)) {
            return false;
        }
        linkedMachines.add(normalized);
        onLinksChanged();
        return true;
    }

    public boolean unlinkMachine(BlockPos pos) {
        BlockPos normalized = normalizeLinkedMachinePos(pos);
        boolean removed = linkedMachines.removeIf(linked -> normalizeLinkedMachinePos(linked).equals(normalized));
        if (removed) {
            onLinksChanged();
        }
        return removed;
    }

    public boolean replaceLinkedMachine(int index, BlockPos pos) {
        if (index < 0 || index >= linkedMachines.size()) {
            return false;
        }
        BlockPos immutable = normalizeLinkedMachinePos(pos);
        int existing = linkedMachines.indexOf(immutable);
        if (existing >= 0 && existing != index) {
            return false;
        }
        if (linkedMachines.get(index).equals(immutable)) {
            return false;
        }
        linkedMachines.set(index, immutable);
        onLinksChanged();
        return true;
    }

    public int clearLinks() {
        int count = linkedMachines.size();
        if (count > 0) {
            linkedMachines.clear();
            onLinksChanged();
        }
        return count;
    }

    public boolean hasLinkedMachine(BlockPos pos) {
        BlockPos normalized = normalizeLinkedMachinePos(pos);
        return linkedMachines.stream().anyMatch(linked -> normalizeLinkedMachinePos(linked).equals(normalized));
    }

    public boolean isSmartDoublingEnabled() {
        return smartDoubling;
    }

    public void toggleSmartDoubling() {
        setSmartDoublingEnabled(!smartDoubling);
    }

    public void setSmartDoublingEnabled(boolean enabled) {
        if (smartDoubling == enabled) {
            return;
        }
        smartDoubling = enabled;
        saveAndSync();
        wakeTicker();
    }

    private boolean pushKineticPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (!getMainNode().isActive()) {
            rememberStatus("ae_inactive");
            return false;
        }

        GenericStack primary = patternDetails.getPrimaryOutput();
        if (primary == null || primary.what() == null) {
            rememberStatus("no_primary_output");
            return false;
        }
        AEKey primaryOutput = primary.what();

        List<GenericStack> flattenedInputs = PatternStacks.flattenInputs(inputs);
        boolean debug = debugLoggingEnabled();
        if (debug) {
            debugLog("push requested output={} amount={} inputs={} active={}/{} smart={} targets={}",
                    primaryOutput, primary.amount(), flattenedInputs, activeJobs.size(),
                    maxActiveJobsFromLinkedMachines(), smartDoubling, targetMachinePositions());
        }
        DispatchTarget target = selectDispatchTarget(patternDetails, primaryOutput, primary.amount(), flattenedInputs);
        if (target == null) {
            if (debug) {
                debugLog("push rejected: no dispatch target output={} amount={} active={}/{}",
                        primaryOutput, primary.amount(), activeJobs.size(), maxActiveJobsFromLinkedMachines());
            }
            rememberStatus("busy");
            return false;
        }
        MachinePlanFactory.Plan plan = target.plan();
        if (!plan.isSuccess()) {
            rememberStatus(plan.statusKey() == null ? "unsupported_machine" : plan.statusKey());
            diagnostics.remember(plan.statusKey(), plan.failureDetail(), List.of(plan.machinePos()), -1);
            return false;
        }
        if (!plan.simulate()) {
            if (target.job() == null || !target.allowQueue()) {
                if (debug) {
                    debugLog("push rejected: target simulation failed machine={} role={} output={} amount={}",
                            plan.machinePos(), plan.machineRole(), primaryOutput, primary.amount());
                }
                rememberStatus("simulate_failed");
                diagnostics.remember("simulate_failed", plan.machinePos().toShortString(), List.of(plan.machinePos()), -1);
                return false;
            }
            PatternStacks.consumeInputs(inputs);
            queuePendingPattern(target.job(), primary.amount(), flattenedInputs);
            lastStatusKey = "working";
            saveAndSync();
            wakeTicker();
            return true;
        }
        if (shouldQueueSmartBatch(plan, flattenedInputs)) {
            PatternStacks.consumeInputs(inputs);
            if (target.job() == null) {
                KineticJob job = new KineticJob(primaryOutput, patternDetails.getDefinition(), primary.amount(),
                        plan.machinePos(), plan.outputPos(), plan.outputEndpoints(), plan.machineRole(), plan.patternOutputs(),
                        plan.refillInputs(), plan.collectUnexpectedOutputs());
                job.pendingDispatches.add(new KineticJob.PendingDispatch(primary.amount(), PatternStacks.copyStacks(flattenedInputs)));
                job.pendingDispatchTicks = SMART_BATCH_GATHER_TICKS;
                activeJobs.add(job);
                if (debug) {
                    debugLog("push queued initial smart batch machine={} role={} output={} amount={} active={}/{} pending={}",
                            job.machinePos, job.machineRole, primaryOutput, primary.amount(), activeJobs.size(),
                            maxActiveJobsFromLinkedMachines(), job.pendingDispatches.size());
                }
            } else {
                queuePendingPattern(target.job(), primary.amount(), flattenedInputs);
                target.job().pendingDispatchTicks = Math.max(target.job().pendingDispatchTicks,
                        SMART_BATCH_GATHER_TICKS);
            }
            lastStatusKey = "working";
            saveAndSync();
            wakeTicker();
            return true;
        }
        ActionList.Result executed = plan.execute();
        if (!executed.success()) {
            String failureDetail = "action #" + executed.failedIndex() + " " + executed.failedAction();
            if (!executed.fullyCompensated()) {
                failureDetail += "; rollback incomplete: " + executed.compensationFailures();
                CreatePackage.LOGGER.error("[Kinetic Provider @ {}] execution failed and rollback was incomplete: {}",
                        getBlockPos(), failureDetail);
                rememberStatus("compensation_failed");
                diagnostics.remember("compensation_failed", failureDetail, List.of(plan.machinePos()), -1);
            } else {
                if (debug) {
                    debugLog("push rejected: target execution failed machine={} role={} output={} amount={} action=#{} {}",
                            plan.machinePos(), plan.machineRole(), primaryOutput, primary.amount(),
                            executed.failedIndex(), executed.failedAction());
                }
                rememberStatus("execute_failed");
                diagnostics.remember("execute_failed", failureDetail, List.of(plan.machinePos()), -1);
            }
            return false;
        }

        PatternStacks.consumeInputs(inputs);
        diagnostics.clear();
        if (target.job() == null) {
            KineticJob job = new KineticJob(primaryOutput, patternDetails.getDefinition(), primary.amount(),
                    plan.machinePos(), plan.outputPos(), plan.outputEndpoints(), plan.machineRole(), plan.patternOutputs(),
                    plan.refillInputs(), plan.collectUnexpectedOutputs());
            job.roundsStarted = 1;
            job.markOutputStart(level.getGameTime());
            activeJobs.add(job);
            if (debug) {
                debugLog("push started job machine={} role={} output={} amount={} active={}/{}",
                        job.machinePos, job.machineRole, primaryOutput, primary.amount(), activeJobs.size(),
                        maxActiveJobsFromLinkedMachines());
            }
        } else {
            KineticJob job = target.job();
            job.remaining = PatternStacks.safeAdd(job.remaining, primary.amount());
            job.queuedPatterns++;
            job.roundsStarted++;
            job.roundHasOutput = false;
            job.roundTicks = 0;
            job.lastRefillAttemptTicks = 0;
            job.nextRefillGameTime = 0;
            job.ticks = 0;
            if (debug) {
                debugLog("push appended immediately machine={} role={} output={} amount={} remaining={} queued={} pending={}",
                        job.machinePos, job.machineRole, primaryOutput, primary.amount(), job.remaining,
                        job.queuedPatterns, job.pendingDispatches.size());
            }
        }
        lastStatusKey = "working";
        saveAndSync();
        wakeTicker();
        return true;
    }

    private void queuePendingPattern(KineticJob job, long outputAmount, List<GenericStack> inputs) {
        job.remaining = PatternStacks.safeAdd(job.remaining, outputAmount);
        job.queuedPatterns++;
        job.pendingDispatches.add(new KineticJob.PendingDispatch(outputAmount, PatternStacks.copyStacks(inputs)));
        job.ticks = 0;
        if (debugLoggingEnabled()) {
            debugLog("push queued pending machine={} role={} output={} amount={} remaining={} queued={} pending={}",
                    job.machinePos, job.machineRole, job.primaryOutput, outputAmount, job.remaining, job.queuedPatterns,
                    job.pendingDispatches.size());
        }
    }

    @Nullable
    private DispatchTarget selectDispatchTarget(IPatternDetails patternDetails, AEKey primaryOutput,
            long outputAmount, List<GenericStack> inputs) {
        MachinePlanFactory.Plan firstFailure = null;
        if (activeJobs.size() < maxActiveJobsFromLinkedMachines()) {
            for (BlockPos machinePos : targetMachinePositions()) {
                if (isMachineBusy(machinePos)) {
                    continue;
                }
                MachinePlanFactory.Plan plan = createPlanAt(machinePos, primaryOutput, outputAmount, patternDetails.getOutputs(), inputs);
                if (plan != null && plan.simulate()) {
                    return new DispatchTarget(null, plan, false);
                }
                if (firstFailure == null) {
                    firstFailure = plan;
                }
            }
        }

        DispatchTarget appendTarget = selectAppendDispatchTarget(patternDetails, outputAmount, inputs);
        if (appendTarget != null) {
            return appendTarget;
        }
        return firstFailure == null ? null : new DispatchTarget(null, firstFailure, false);
    }

    @Nullable
    private DispatchTarget selectAppendDispatchTarget(IPatternDetails patternDetails, long outputAmount,
            List<GenericStack> inputs) {
        KineticJob bestImmediateJob = null;
        MachinePlanFactory.Plan bestImmediatePlan = null;
        KineticJob bestQueuedJob = null;
        MachinePlanFactory.Plan bestQueuedPlan = null;
        for (KineticJob job : activeJobs) {
            MachinePlanFactory.Plan plan = planForAppend(job, patternDetails, outputAmount, inputs);
            if (plan == null) {
                continue;
            }
            if (plan.simulate()) {
                if (isBetterAppendTarget(job, bestImmediateJob)) {
                    bestImmediateJob = job;
                    bestImmediatePlan = plan;
                }
            } else if (isBetterAppendTarget(job, bestQueuedJob)) {
                bestQueuedJob = job;
                bestQueuedPlan = plan;
            }
        }
        if (bestImmediateJob != null && bestImmediatePlan != null) {
            if (debugLoggingEnabled()) {
                debugLog("append target selected immediate machine={} role={} queued={} pending={} remaining={}",
                        bestImmediateJob.machinePos, bestImmediateJob.machineRole, bestImmediateJob.queuedPatterns,
                        bestImmediateJob.pendingDispatches.size(), bestImmediateJob.remaining);
            }
            return new DispatchTarget(bestImmediateJob, bestImmediatePlan, true);
        }
        if (bestQueuedJob != null && bestQueuedPlan != null) {
            if (debugLoggingEnabled()) {
                debugLog("append target selected pending machine={} role={} queued={} pending={} remaining={}",
                        bestQueuedJob.machinePos, bestQueuedJob.machineRole, bestQueuedJob.queuedPatterns,
                        bestQueuedJob.pendingDispatches.size(), bestQueuedJob.remaining);
            }
            return new DispatchTarget(bestQueuedJob, bestQueuedPlan, true);
        }
        return null;
    }

    private boolean isBetterAppendTarget(KineticJob candidate, @Nullable KineticJob currentBest) {
        if (currentBest == null) {
            return true;
        }
        if (candidate.queuedPatterns != currentBest.queuedPatterns) {
            return candidate.queuedPatterns < currentBest.queuedPatterns;
        }
        int candidatePending = candidate.pendingDispatches.size();
        int bestPending = currentBest.pendingDispatches.size();
        if (candidatePending != bestPending) {
            return candidatePending < bestPending;
        }
        if (candidate.remaining != currentBest.remaining) {
            return candidate.remaining < currentBest.remaining;
        }
        return candidate.ticks > currentBest.ticks;
    }

    private boolean canAppendToActiveJob(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (level == null || level.isClientSide()) {
            return activeJobs.isEmpty();
        }
        GenericStack primary = patternDetails.getPrimaryOutput();
        if (primary == null || primary.what() == null) {
            return false;
        }
        List<GenericStack> flattenedInputs = PatternStacks.flattenInputs(inputs);
        return selectDispatchTarget(patternDetails, primary.what(), primary.amount(), flattenedInputs) != null;
    }

    @Nullable
    private MachinePlanFactory.Plan planForAppend(KineticJob job, IPatternDetails patternDetails, long outputAmount,
            List<GenericStack> inputs) {
        if (!smartDoubling) {
            return null;
        }
        GenericStack primary = patternDetails.getPrimaryOutput();
        if (primary == null || primary.what() == null) {
            return null;
        }
        AEKey primaryOutput = primary.what();
        if (!primaryOutput.equals(job.primaryOutput)) {
            return null;
        }
        if (!Objects.equals(patternDetails.getDefinition(), job.patternDefinition)) {
            return null;
        }
        MachinePlanFactory.Plan plan = createPlanAt(job.machinePos, job.primaryOutput, outputAmount, patternDetails.getOutputs(), inputs);
        if (plan == null
                || !Objects.equals(plan.outputEndpoints(), job.outputEndpoints)
                || !Objects.equals(plan.machineRole(), job.machineRole)
                || plan.collectUnexpectedOutputs() != job.collectUnexpectedOutputs) {
            return null;
        }
        if (job.queuedPatterns >= maxAcceptableQueuedPatterns(plan)) {
            return null;
        }
        return plan;
    }

    private boolean activeJobCanAcceptOneMorePattern() {
        if (activeJobs.size() < maxActiveJobsFromLinkedMachines()) {
            return true;
        }
        if (!smartDoubling) {
            return false;
        }
        for (KineticJob job : activeJobs) {
            MachinePlanFactory.Plan plan = createPlanAt(job.machinePos, job.primaryOutput,
                    primaryPatternOutputAmount(job), job.patternOutputs, job.refillInputs);
            if (plan != null
                    && Objects.equals(plan.outputEndpoints(), job.outputEndpoints)
                    && Objects.equals(plan.machineRole(), job.machineRole)
                    && plan.collectUnexpectedOutputs() == job.collectUnexpectedOutputs
                    && job.queuedPatterns < maxAcceptableQueuedPatterns(plan)) {
                return true;
            }
        }
        return false;
    }

    private int maxActiveJobsFromLinkedMachines() {
        return Math.min(getMaxParallelMachines(), targetMachinePositions().size());
    }

    private boolean isMachineBusy(BlockPos machinePos) {
        for (KineticJob job : activeJobs) {
            if (job.machinePos.equals(machinePos)) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> targetMachinePositions() {
        BlockPos front = targetMachinePos();
        if (front == null) {
            return List.of();
        }
        List<BlockPos> targets = new ArrayList<>();
        targets.add(front.immutable());
        if (getParallelCardCount() > 0 && level != null) {
            ResourceLocation frontId = blockId(front);
            for (BlockPos linked : linkedMachines) {
                if (targets.size() >= getMaxParallelMachines()) {
                    break;
                }
                if (!level.isLoaded(linked)) {
                    continue;
                }
                BlockPos normalized = CreateMachineIds.normalizeKineticMachinePos(level, linked);
                if (targets.contains(normalized) || !Objects.equals(frontId, blockId(normalized))) {
                    continue;
                }
                targets.add(normalized.immutable());
            }
        }
        return List.copyOf(targets);
    }

    @Nullable
    private ResourceLocation blockId(BlockPos pos) {
        return CreateMachineIds.idOf(level, pos);
    }

    @Nullable
    private MachinePlanFactory.Plan createPlan(AEKey primaryOutput, long outputAmount, List<GenericStack> patternOutputs,
            List<GenericStack> inputs) {
        BlockPos machinePos = targetMachinePos();
        if (machinePos == null) {
            rememberStatus("missing_target");
            diagnostics.remember("missing_target", null);
            return null;
        }
        return createPlanForMachine(machinePos, primaryOutput, outputAmount, patternOutputs, inputs);
    }

    @Nullable
    private MachinePlanFactory.Plan createPlanForMachine(BlockPos machinePos, AEKey primaryOutput, long outputAmount,
            List<GenericStack> patternOutputs, List<GenericStack> inputs) {
        MachinePlanFactory.Plan plan = MachinePlanFactory.create(level, machinePos, primaryOutput, outputAmount,
                patternOutputs, inputs);
        if (!plan.isSuccess()) {
            rememberStatus(plan.statusKey() == null ? "unsupported_machine" : plan.statusKey());
            diagnostics.remember(plan.statusKey(), plan.failureDetail(), List.of(machinePos), -1);
            return null;
        }
        return plan;
    }

    private boolean tickJobs(int ticksSinceLastCall) {
        if (activeJobs.isEmpty() || level == null || level.isClientSide()) {
            return false;
        }
        int step = Math.max(1, ticksSinceLastCall);
        boolean earlyRefill = refillCraftingOrders.takeCraftFinished();
        boolean didWork = false;
        for (int i = 0; i < activeJobs.size(); i++) {
            KineticJob job = activeJobs.get(i);
            job.ticks += step;
            job.roundTicks += step;
            if (job.pendingDispatchTicks > 0) {
                job.pendingDispatchTicks = Math.max(0, job.pendingDispatchTicks - step);
            }
            boolean collected = collectOutputs(job);
            didWork |= collected;
            if (collected && job.hasPendingDispatches()) {
                job.pendingDispatchTicks = 0;
            }
            if (shouldCompletePartialOutputJob(job)) {
                if (debugLoggingEnabled()) {
                    debugLog("job completed with partial collected output machine={} role={} output={} missing={} queued={}",
                            job.machinePos, job.machineRole, job.primaryOutput, job.remaining, job.queuedPatterns);
                }
                job.remaining = 0;
            }
            if (job.remaining <= 0 && job.pendingNetworkOutputs.isEmpty()) {
                if (debugLoggingEnabled()) {
                    debugLog("job completed machine={} role={} queued={} pending={}",
                            job.machinePos, job.machineRole, job.queuedPatterns, job.pendingDispatches.size());
                }
                activeJobs.remove(i--);
                lastStatusKey = activeJobs.isEmpty() ? "completed" : "working";
                requestCraftingUpdate();
                didWork = true;
                continue;
            }
            if (isCraftingJobNoLongerWaiting(job)) {
                job.statusKey = "cancelled";
                didWork = returnPendingDispatches(job) || didWork;
                if (!job.hasPendingDispatches() && job.pendingNetworkOutputs.isEmpty()) {
                    activeJobs.remove(i--);
                    lastStatusKey = activeJobs.isEmpty() ? "cancelled" : "working";
                    didWork = true;
                }
                continue;
            }
            if (job.returningPendingDispatches()) {
                didWork = returnPendingDispatches(job) || didWork;
                if (!job.hasPendingDispatches() && job.pendingNetworkOutputs.isEmpty()) {
                    activeJobs.remove(i--);
                    lastStatusKey = activeJobs.isEmpty() ? job.statusKey : "working";
                    didWork = true;
                }
                continue;
            } else if (job.hasPendingDispatches()) {
                didWork = dispatchPendingPattern(job) || didWork;
            } else if (job.waitingForRefill()) {
                refillCraftingOrders.tickPending(getMainNode().getGrid(), actionSource);
                didWork = retryRefillRound(job, collected, earlyRefill) || didWork;
            } else if (!job.roundHasOutput
                    && Config.enableProbabilisticEmptyRefill()
                    && job.roundTicks > emptyOutputRefillTimeoutTicks()) {
                didWork = retryRefillRoundNow(job, "empty_output") || didWork;
            }
            if (job.ticks > Config.kineticMaxJobTicks()) {
                CreatePackage.LOGGER.warn("[Kinetic Pattern Provider @ {}] timed out waiting for {} x{} from {} at {}",
                        getBlockPos(), job.primaryOutput, job.remaining, job.machineRole, job.machinePos);
                job.statusKey = "timeout";
                didWork = returnPendingDispatches(job) || didWork;
                if (!job.hasPendingDispatches() && job.pendingNetworkOutputs.isEmpty()) {
                    activeJobs.remove(i--);
                    lastStatusKey = activeJobs.isEmpty() ? "timeout" : "working";
                    didWork = true;
                }
            }
        }
        if (didWork) {
            saveAndSync();
        }
        return didWork;
    }

    private boolean dispatchPendingPattern(KineticJob job) {
        if (job.pendingDispatchTicks > 0) {
            return false;
        }
        KineticJob.PendingDispatch pending = job.pendingDispatches.get(0);
        MachinePlanFactory.Plan plan = createPlanAt(job.machinePos, job.primaryOutput, pending.outputAmount(),
                job.patternOutputs, pending.inputs());
        if (plan == null
                || !Objects.equals(plan.outputEndpoints(), job.outputEndpoints)
                || !Objects.equals(plan.machineRole(), job.machineRole)
                || plan.collectUnexpectedOutputs() != job.collectUnexpectedOutputs) {
            job.pendingDispatchTicks = Config.pendingDispatchRetryTicks();
            rememberJobStatus(job, "waiting_target_space");
            debugPendingBlocked(job, "plan_invalid");
            return false;
        }
        if (!plan.simulate()) {
            // Retry every tick while waiting for basin/tank space; CMM large tanks free up in bursts.
            job.pendingDispatchTicks = 0;
            rememberJobStatus(job, "waiting_target_space");
            debugPendingBlocked(job, "target_full");
            return false;
        }
        PendingBatch batch = expandPendingBatch(job, pending, plan);
        if (!batch.plan().execute().success()) {
            job.pendingDispatchTicks = Config.pendingDispatchRetryTicks();
            rememberJobStatus(job, "execute_failed");
            debugPendingBlocked(job, "execute_failed");
            return false;
        }

        job.markOutputStart(level.getGameTime());
        for (int i = 0; i < batch.patternCount(); i++) {
            job.pendingDispatches.remove(0);
        }
        job.pendingDispatchTicks = 0;
        job.roundsStarted += batch.patternCount();
        job.roundTicks = 0;
        job.lastRefillAttemptTicks = 0;
        job.nextRefillGameTime = 0;
        job.roundHasOutput = false;
        rememberJobStatus(job, "working");
        if (debugLoggingEnabled()) {
            debugLog("pending dispatched machine={} role={} output={} amount={} patterns={} remaining={} queued={} pendingLeft={}",
                    job.machinePos, job.machineRole, job.primaryOutput, batch.outputAmount(), batch.patternCount(),
                    job.remaining, job.queuedPatterns, job.pendingDispatches.size());
        }
        saveAndSync();
        wakeTicker();
        return true;
    }

    private PendingBatch expandPendingBatch(KineticJob job, KineticJob.PendingDispatch first, MachinePlanFactory.Plan firstPlan) {
        if (!shouldQueueSmartBatch(firstPlan, first.inputs())) {
            return new PendingBatch(firstPlan, first.outputAmount(), 1);
        }

        MachinePlanFactory.Plan bestPlan = firstPlan;
        long bestOutputAmount = first.outputAmount();
        int bestPatternCount = 1;
        List<GenericStack> bestInputs = PatternStacks.copyStacks(first.inputs());
        int maxBatchPatterns = maxSmartBatchPatternCount(firstPlan);
        for (int i = 1; i < job.pendingDispatches.size() && bestPatternCount < maxBatchPatterns; i++) {
            KineticJob.PendingDispatch next = job.pendingDispatches.get(i);
            List<GenericStack> mergedInputs = mergeBatchInputs(bestInputs, next.inputs());
            if (mergedInputs == null) {
                break;
            }

            long mergedOutputAmount = PatternStacks.safeAdd(bestOutputAmount, next.outputAmount());
            MachinePlanFactory.Plan mergedPlan = createPlanAt(job.machinePos, job.primaryOutput, mergedOutputAmount,
                    job.patternOutputs, mergedInputs);
            if (!isCompatiblePendingPlan(job, mergedPlan)
                    || !isSmartStackBatchable(mergedPlan, mergedInputs)
                    || !mergedPlan.simulate()) {
                break;
            }

            bestPlan = mergedPlan;
            bestOutputAmount = mergedOutputAmount;
            bestPatternCount++;
            bestInputs = mergedInputs;
        }
        return new PendingBatch(bestPlan, bestOutputAmount, bestPatternCount);
    }

    private boolean isCompatiblePendingPlan(KineticJob job, @Nullable MachinePlanFactory.Plan plan) {
        return plan != null
                && Objects.equals(plan.outputPos(), job.outputPos)
                && Objects.equals(plan.machineRole(), job.machineRole)
                && plan.collectUnexpectedOutputs() == job.collectUnexpectedOutputs;
    }

    private boolean shouldQueueSmartBatch(MachinePlanFactory.Plan plan, List<GenericStack> inputs) {
        return isSmartStackBatchable(plan, inputs) && maxSmartBatchPatternCount(plan) > 1;
    }

    private boolean isSmartStackBatchable(MachinePlanFactory.Plan plan, List<GenericStack> inputs) {
        if (!smartDoubling
                || !supportsSmartStackBatch(plan)
                || inputs.isEmpty()) {
            return false;
        }
        Map<AEKey, Long> expected = new HashMap<>();
        for (GenericStack input : inputs) {
            if (input == null || input.amount() <= 0 || !addBatchAmount(expected, input.what(), input.amount())) {
                return false;
            }
        }
        Map<AEKey, Long> supplied = new HashMap<>();
        for (SupplyAction action : plan.actions().actions()) {
            if (action instanceof SupplyActions.ItemInsertAction itemAction) {
                ItemStack stack = itemAction.stack();
                AEItemKey key = stack.isEmpty() ? null : AEItemKey.of(stack);
                if (key == null || !addBatchAmount(supplied, key, stack.getCount())) {
                    return false;
                }
            } else if (action instanceof SupplyActions.ItemEntityInsertAction entityAction) {
                ItemStack stack = entityAction.stack();
                AEItemKey key = stack.isEmpty() ? null : AEItemKey.of(stack);
                if (key == null || !addBatchAmount(supplied, key, stack.getCount())) {
                    return false;
                }
            } else if (action instanceof SupplyActions.FluidInsertAction fluidAction) {
                FluidStack stack = fluidAction.stack();
                AEFluidKey key = stack.isEmpty() ? null : AEFluidKey.of(stack);
                if (key == null || !addBatchAmount(supplied, key, stack.getAmount())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return expected.equals(supplied);
    }

    private boolean addBatchAmount(Map<AEKey, Long> amounts, AEKey key, long amount) {
        long merged = PatternStacks.safeAdd(amounts.getOrDefault(key, 0L), amount);
        if (merged <= 0) {
            return false;
        }
        amounts.put(key, merged);
        return true;
    }

    private boolean supportsSmartStackBatch(MachinePlanFactory.Plan plan) {
        if ("press".equals(plan.machineRole())) {
            // Depot press: single item. Basin press: multi item/fluid inserts.
            if (isBasin(plan.outputPos())) {
                return isBasinInsertOnlyPlan(plan);
            }
            return plan.actions().actions().size() == 1
                    && plan.actions().actions().get(0) instanceof SupplyActions.ItemInsertAction;
        }
        if ("mixer".equals(plan.machineRole())
                || "compressor".equals(plan.machineRole())
                || "bulk_fermenter".equals(plan.machineRole())
                || "foundry_basin".equals(plan.machineRole())
                || "foundry_mixer".equals(plan.machineRole())
                || "foundry_lid".equals(plan.machineRole())
                || "casting_table".equals(plan.machineRole())
                || "industrial_crucible".equals(plan.machineRole())
                || "grindstone_drain".equals(plan.machineRole())) {
            return isBasinInsertOnlyPlan(plan);
        }
        if ("laser".equals(plan.machineRole())) {
            return plan.actions().actions().size() == 1
                    && (plan.actions().actions().get(0) instanceof SupplyActions.ItemInsertAction
                            || plan.actions().actions().get(0) instanceof SupplyActions.ItemEntityInsertAction);
        }
        if ("tesla_coil".equals(plan.machineRole())
                || "millstone".equals(plan.machineRole())
                || "vibrating_table".equals(plan.machineRole())
                || "rolling_mill".equals(plan.machineRole())
                || "lathe".equals(plan.machineRole())
                || "spring_coiling".equals(plan.machineRole())
                || "curving_press".equals(plan.machineRole())) {
            return plan.actions().actions().size() == 1
                    && plan.actions().actions().get(0) instanceof SupplyActions.ItemInsertAction;
        }
        if ("helve_hammer".equals(plan.machineRole())) {
            return !plan.actions().actions().isEmpty()
                    && plan.actions().actions().stream().allMatch(SupplyActions.ItemInsertAction.class::isInstance);
        }
        if ("deployer".equals(plan.machineRole())) {
            int size = plan.actions().actions().size();
            return size >= 1 && size <= 2
                    && plan.actions().actions().stream().allMatch(SupplyActions.ItemInsertAction.class::isInstance);
        }
        if ("spout".equals(plan.machineRole())) {
            return plan.actions().actions().size() == 2
                    && plan.actions().actions().stream().anyMatch(SupplyActions.ItemInsertAction.class::isInstance)
                    && plan.actions().actions().stream().anyMatch(SupplyActions.FluidInsertAction.class::isInstance);
        }
        return false;
    }

    private boolean isBasinInsertOnlyPlan(MachinePlanFactory.Plan plan) {
        if (plan.actions().actions().isEmpty()) {
            return false;
        }
        for (SupplyAction action : plan.actions().actions()) {
            if (!(action instanceof SupplyActions.ItemInsertAction)
                    && !(action instanceof SupplyActions.FluidInsertAction)) {
                return false;
            }
        }
        return true;
    }

    private int maxSmartBatchPatternCount(MachinePlanFactory.Plan plan) {
        return maxBatchPatternCount(plan, false);
    }

    private int maxTotalSmartBatchPatternCount(MachinePlanFactory.Plan plan) {
        return maxBatchPatternCount(plan, true);
    }

    /**
     * How many same patterns we can pre-queue while a machine is already working.
     * Uses empty-machine capacity so AE can buffer a full fill even when the basin is currently full.
     */
    private int maxAcceptableQueuedPatterns(MachinePlanFactory.Plan plan) {
        int free = maxSmartBatchPatternCount(plan);
        int total = maxTotalSmartBatchPatternCount(plan);
        // Buffer up to two full machine loads so the next fill is ready as soon as space frees.
        long limit = Math.max(free, (long) total * 2L);
        if (limit <= 1) {
            return 1;
        }
        return (int) Math.min(limit, Integer.MAX_VALUE);
    }

    private int maxBatchPatternCount(MachinePlanFactory.Plan plan, boolean totalCapacity) {
        long limit = Long.MAX_VALUE;
        boolean hasBatchAction = false;
        for (SupplyAction action : plan.actions().actions()) {
            long actionLimit;
            if (action instanceof SupplyActions.ItemInsertAction itemAction) {
                actionLimit = maxItemBatchPatternCount(itemAction, totalCapacity);
            } else if (action instanceof SupplyActions.ItemEntityInsertAction entityAction) {
                ItemStack stack = entityAction.stack();
                actionLimit = stack.isEmpty() ? 1 : Math.max(1, stack.getMaxStackSize() / stack.getCount());
            } else if (action instanceof SupplyActions.FluidInsertAction fluidAction) {
                actionLimit = maxFluidBatchPatternCount(fluidAction, totalCapacity);
            } else {
                continue;
            }
            hasBatchAction = true;
            limit = Math.min(limit, actionLimit);
        }
        if (!hasBatchAction || limit <= 1) {
            return 1;
        }
        return (int) Math.min(limit, Integer.MAX_VALUE);
    }

    private long maxItemBatchPatternCount(SupplyActions.ItemInsertAction action, boolean totalCapacity) {
        if (action.stack().isEmpty() || action.stack().getCount() <= 0) {
            return 1;
        }
        IItemHandler handler = action.handler();
        long capacity = totalCapacity
                ? itemTargetTotalCapacity(handler, action.stack())
                : itemTargetCapacity(handler, action.stack());
        if (capacity <= 0) {
            return 1;
        }
        return Math.max(1, capacity / action.stack().getCount());
    }

    private long itemTargetCapacity(@Nullable IItemHandler handler, ItemStack stack) {
        AEItemKey stackKey = AEItemKey.of(stack);
        if (handler == null || stackKey == null) {
            return 0;
        }
        long capacity = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            int slotLimit = handler.getSlotLimit(slot);
            if (slotLimit <= 0) {
                continue;
            }
            ItemStack current = handler.getStackInSlot(slot);
            if (current.isEmpty()) {
                if (canInsertIntoSlot(handler, slot, stack)) {
                    capacity = PatternStacks.safeAdd(capacity, slotLimit);
                }
                continue;
            }
            AEItemKey currentKey = AEItemKey.of(current);
            if (stackKey.equals(currentKey) && canInsertIntoSlot(handler, slot, stack)) {
                capacity = PatternStacks.safeAdd(capacity, Math.max(0, slotLimit - current.getCount()));
            }
        }
        return capacity;
    }

    private long itemTargetTotalCapacity(@Nullable IItemHandler handler, ItemStack stack) {
        AEItemKey stackKey = AEItemKey.of(stack);
        if (handler == null || stackKey == null) {
            return 0;
        }
        long capacity = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            int slotLimit = handler.getSlotLimit(slot);
            if (slotLimit <= 0 || !canInsertIntoSlot(handler, slot, stack)) {
                continue;
            }
            ItemStack current = handler.getStackInSlot(slot);
            if (current.isEmpty() || stackKey.equals(AEItemKey.of(current))) {
                capacity = PatternStacks.safeAdd(capacity, slotLimit);
            }
        }
        return capacity;
    }

    private boolean canInsertIntoSlot(IItemHandler handler, int slot, ItemStack stack) {
        if (handler.isItemValid(slot, stack)) {
            return true;
        }
        ItemStack probe = stack.copy();
        probe.setCount(1);
        return handler.insertItem(slot, probe, true).getCount() < 1;
    }

    private long maxFluidBatchPatternCount(SupplyActions.FluidInsertAction action, boolean totalCapacity) {
        if (action.stack().isEmpty() || action.stack().getAmount() <= 0) {
            return 1;
        }
        IFluidHandler handler = action.handler();
        long capacity = totalCapacity
                ? fluidTargetTotalCapacity(handler, action.stack())
                : fluidTargetCapacity(handler, action.stack());
        if (capacity <= 0) {
            return 1;
        }
        return Math.max(1, capacity / action.stack().getAmount());
    }

    private long fluidTargetCapacity(@Nullable IFluidHandler handler, FluidStack stack) {
        AEFluidKey stackKey = AEFluidKey.of(stack);
        if (handler == null || stackKey == null) {
            return 0;
        }
        long capacity = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            int tankCapacity = handler.getTankCapacity(tank);
            if (tankCapacity <= 0 || !handler.isFluidValid(tank, stack)) {
                continue;
            }
            FluidStack current = handler.getFluidInTank(tank);
            if (current.isEmpty()) {
                capacity = PatternStacks.safeAdd(capacity, tankCapacity);
            } else if (stackKey.equals(AEFluidKey.of(current))) {
                capacity = PatternStacks.safeAdd(capacity, Math.max(0, tankCapacity - current.getAmount()));
            }
        }
        return capacity;
    }

    private long fluidTargetTotalCapacity(@Nullable IFluidHandler handler, FluidStack stack) {
        AEFluidKey stackKey = AEFluidKey.of(stack);
        if (handler == null || stackKey == null) {
            return 0;
        }
        long capacity = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            int tankCapacity = handler.getTankCapacity(tank);
            if (tankCapacity <= 0 || !handler.isFluidValid(tank, stack)) {
                continue;
            }
            FluidStack current = handler.getFluidInTank(tank);
            if (current.isEmpty() || stackKey.equals(AEFluidKey.of(current))) {
                capacity = PatternStacks.safeAdd(capacity, tankCapacity);
            }
        }
        return capacity;
    }

    @Nullable
    private List<GenericStack> mergeBatchInputs(List<GenericStack> left, List<GenericStack> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        List<GenericStack> result = PatternStacks.copyStacks(left);
        for (GenericStack stack : right) {
            if (stack == null || stack.amount() <= 0) {
                return null;
            }
            boolean matched = false;
            for (int i = 0; i < result.size(); i++) {
                GenericStack existing = result.get(i);
                if (!existing.what().equals(stack.what())) {
                    continue;
                }
                long amount = PatternStacks.safeAdd(existing.amount(), stack.amount());
                if (amount <= 0 || amount > Integer.MAX_VALUE) {
                    return null;
                }
                result.set(i, new GenericStack(existing.what(), amount));
                matched = true;
                break;
            }
            if (!matched) {
                return null;
            }
        }
        return List.copyOf(result);
    }

    private void debugPendingBlocked(KineticJob job, String reason) {
        if (job.ticks % 20 != 0) {
            return;
        }
        if (debugLoggingEnabled()) {
            debugLog("pending blocked reason={} machine={} role={} output={} remaining={} queued={} pending={}",
                    reason, job.machinePos, job.machineRole, job.primaryOutput, job.remaining, job.queuedPatterns,
                    job.pendingDispatches.size());
        }
    }

    private boolean returnPendingDispatches(KineticJob job) {
        if (job.pendingDispatches.isEmpty()) {
            return false;
        }
        boolean didWork = false;
        for (int pendingIndex = 0; pendingIndex < job.pendingDispatches.size();) {
            KineticJob.PendingDispatch pending = job.pendingDispatches.get(pendingIndex);
            List<GenericStack> remainingInputs = new ArrayList<>();
            for (GenericStack input : pending.inputs()) {
                long inserted = logic.getReturnInv().insert(input.what(), input.amount(), Actionable.MODULATE,
                        actionSource);
                if (inserted < input.amount()) {
                    remainingInputs.add(new GenericStack(input.what(), input.amount() - inserted));
                }
                didWork |= inserted > 0;
            }
            if (remainingInputs.isEmpty()) {
                job.pendingDispatches.remove(pendingIndex);
                job.remaining = Math.max(0, job.remaining - pending.outputAmount());
                job.queuedPatterns = Math.max(1, job.queuedPatterns - 1);
                didWork = true;
            } else {
                job.pendingDispatches.set(pendingIndex,
                        new KineticJob.PendingDispatch(pending.outputAmount(), remainingInputs));
                pendingIndex++;
            }
        }
        job.pendingDispatchTicks = 0;
        if (didWork) {
            wakeTicker();
        }
        return didWork;
    }

    private boolean collectOutputs(KineticJob job) {
        OutputCollection collected = new OutputCollection();
        flushPendingNetworkOutputs(job, collected);
        if (job.outputEndpoints.isEmpty()) {
            if (!collected.didWork) {
                rememberStatus("missing_output");
            }
            return collected.didWork;
        }
        for (MachineEndpoint endpoint : job.outputEndpoints) {
            if (endpoint.medium() == MachineEndpoint.Medium.ITEM
                    || endpoint.medium() == MachineEndpoint.Medium.SMART_ITEM_OUTPUT
                    || endpoint.medium() == MachineEndpoint.Medium.CRUCIBLE_ITEM_OUTPUT) {
                collectItemOutput(job, endpoint, collected);
            } else if (endpoint.medium() == MachineEndpoint.Medium.FLUID
                    || endpoint.medium() == MachineEndpoint.Medium.SMART_FLUID_OUTPUT
                    || endpoint.medium() == MachineEndpoint.Medium.CRUCIBLE_FLUID_OUTPUT) {
                collectFluidOutput(job, endpoint, collected);
            } else {
                collectDroppedItemOutput(job, endpoint, collected);
            }
        }
        if (!collected.foundHandler) {
            if (!collected.didWork) {
                rememberStatus("missing_output");
            }
            return collected.didWork;
        }
        if (!job.collectUnexpectedOutputs
                && job.remaining > 0
                && job.queuedPatterns <= 1
                && collected.primary == 0
                && collected.other > 0) {
            collected.didWork = startRefillRound(job, "secondary_output") || collected.didWork;
        }
        if (collected.didWork) {
            if (debugLoggingEnabled()) {
                debugLog("collected machine={} role={} primary={} other={} remaining={} queued={} pending={}",
                        job.machinePos, job.machineRole, collected.primary, collected.other, job.remaining,
                        job.queuedPatterns, job.pendingDispatches.size());
            }
            requestCraftingUpdate();
            wakeTicker();
            saveAndSync();
        }
        return collected.didWork;
    }

    private void collectItemOutput(KineticJob job, MachineEndpoint endpoint, OutputCollection collected) {
        IItemHandler handler = itemOutputHandler(endpoint);
        if (handler == null) {
            return;
        }
        collected.foundHandler = true;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack available = handler.extractItem(slot, extractAmountForSlot(handler, slot), true);
            if (available.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(available);
            if (key == null || (!job.collectUnexpectedOutputs && !isExpectedOutput(job, key))) {
                continue;
            }
            long accepted = insertIntoNetwork(key, available.getCount(), Actionable.SIMULATE);
            if (accepted <= 0) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, (int) Math.min(accepted, available.getCount()), false);
            AEItemKey extractedKey = AEItemKey.of(extracted);
            if (extractedKey == null) {
                continue;
            }
            long inserted = insertIntoNetwork(extractedKey, extracted.getCount(), Actionable.MODULATE);
            long committed = Math.max(0, Math.min(inserted, extracted.getCount()));
            if (committed > 0) {
                recordCollectedOutput(job, extractedKey, committed, collected);
            }
            int remainder = extracted.getCount() - (int) committed;
            if (remainder > 0) {
                ItemStack unreturned = InventoryAccess.reinsertIntoHandler(handler, slot,
                        extracted.copyWithCount(remainder));
                bufferNetworkRemainder(job, extractedKey, unreturned.getCount(), 0, collected);
            }
        }
    }

    private void collectDroppedItemOutput(KineticJob job, MachineEndpoint endpoint, OutputCollection collected) {
        collected.foundHandler = true;
        AABB collectionArea = droppedItemCollectionArea(endpoint);
        long maxEntityAge = job.maxCollectableEntityAge(level.getGameTime());
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, collectionArea)) {
            if (entity.tickCount > maxEntityAge) {
                continue;
            }
            ItemStack available = entity.getItem();
            AEItemKey key = AEItemKey.of(available);
            if (key == null || (!job.collectUnexpectedOutputs && !isExpectedOutput(job, key))) {
                continue;
            }
            long accepted = insertIntoNetwork(key, available.getCount(), Actionable.SIMULATE);
            if (accepted <= 0) {
                continue;
            }
            long inserted = insertIntoNetwork(key, Math.min(accepted, available.getCount()), Actionable.MODULATE);
            long committed = Math.max(0, Math.min(inserted, available.getCount()));
            if (committed <= 0) {
                continue;
            }
            if (committed == available.getCount()) {
                entity.discard();
            } else {
                available.shrink((int) committed);
                entity.setItem(available);
            }
            recordCollectedOutput(job, key, committed, collected);
        }
    }

    private static AABB droppedItemCollectionArea(MachineEndpoint endpoint) {
        // Ejected items keep moving; cover enough travel for providers waking a few ticks after the spawn.
        return new AABB(endpoint.pos()).inflate(1.5);
    }

    private void collectFluidOutput(KineticJob job, MachineEndpoint endpoint, OutputCollection collected) {
        IFluidHandler handler = fluidOutputHandler(endpoint);
        if (handler == null) {
            return;
        }
        collected.foundHandler = true;
        if (endpoint.medium() == MachineEndpoint.Medium.CRUCIBLE_FLUID_OUTPUT) {
            collectDynamicFluidSegments(job, handler, collected);
            return;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack available = handler.getFluidInTank(tank);
            if (available.isEmpty()) {
                continue;
            }
            AEFluidKey key = AEFluidKey.of(available);
            if (key == null || (!job.collectUnexpectedOutputs && !isExpectedOutput(job, key))) {
                continue;
            }
            long accepted = insertIntoNetwork(key, available.getAmount(), Actionable.SIMULATE);
            if (accepted <= 0) {
                continue;
            }
            FluidStack extracted = handler.drain(available.copyWithAmount((int) Math.min(accepted, available.getAmount())),
                    IFluidHandler.FluidAction.EXECUTE);
            AEFluidKey extractedKey = AEFluidKey.of(extracted);
            if (extractedKey == null) {
                continue;
            }
            long inserted = insertIntoNetwork(extractedKey, extracted.getAmount(), Actionable.MODULATE);
            long committed = Math.max(0, Math.min(inserted, extracted.getAmount()));
            if (committed > 0) {
                recordCollectedOutput(job, extractedKey, committed, collected);
            }
            int remainder = extracted.getAmount() - (int) committed;
            if (remainder > 0) {
                int returned = handler.fill(extracted.copyWithAmount(remainder), IFluidHandler.FluidAction.EXECUTE);
                bufferNetworkRemainder(job, extractedKey, remainder - Math.max(0, Math.min(returned, remainder)), 0,
                        collected);
            }
        }
    }

    private void collectDynamicFluidSegments(KineticJob job, IFluidHandler handler, OutputCollection collected) {
        DynamicSegmentWalker.walk(handler::getTanks, tank -> {
            FluidStack available = handler.getFluidInTank(tank);
            if (available.isEmpty()) {
                return false;
            }
            AEFluidKey key = AEFluidKey.of(available);
            if (key == null || (!job.collectUnexpectedOutputs && !isExpectedOutput(job, key))) {
                return false;
            }
            long accepted = insertIntoNetwork(key, available.getAmount(), Actionable.SIMULATE);
            if (accepted <= 0) {
                return false;
            }
            FluidStack extracted = handler.drain(available.copyWithAmount((int) Math.min(accepted, available.getAmount())),
                    IFluidHandler.FluidAction.EXECUTE);
            AEFluidKey extractedKey = AEFluidKey.of(extracted);
            if (extractedKey == null) {
                return false;
            }
            long inserted = insertIntoNetwork(extractedKey, extracted.getAmount(), Actionable.MODULATE);
            long committed = Math.max(0, Math.min(inserted, extracted.getAmount()));
            if (committed > 0) {
                recordCollectedOutput(job, extractedKey, committed, collected);
            }
            int remainder = extracted.getAmount() - (int) committed;
            if (remainder > 0) {
                int returned = handler.fill(extracted.copyWithAmount(remainder), IFluidHandler.FluidAction.EXECUTE);
                bufferNetworkRemainder(job, extractedKey, remainder - Math.max(0, Math.min(returned, remainder)), 0,
                        collected);
            }
            return !extracted.isEmpty() && extracted.getAmount() >= available.getAmount();
        });
    }

    @Nullable
    private IItemHandler itemOutputHandler(MachineEndpoint endpoint) {
        if (endpoint.medium() == MachineEndpoint.Medium.SMART_ITEM_OUTPUT) {
            return InventoryAccess.itemOutputHandler(level, endpoint.pos());
        }
        return endpoint.side() == null
                ? InventoryAccess.itemHandler(level, endpoint.pos())
                : InventoryAccess.itemHandler(level, endpoint.pos(), endpoint.side());
    }

    @Nullable
    private IFluidHandler fluidOutputHandler(MachineEndpoint endpoint) {
        if (endpoint.medium() == MachineEndpoint.Medium.SMART_FLUID_OUTPUT) {
            return InventoryAccess.fluidOutputHandler(level, endpoint.pos());
        }
        return endpoint.side() == null
                ? InventoryAccess.fluidHandler(level, endpoint.pos())
                : InventoryAccess.fluidHandler(level, endpoint.pos(), endpoint.side());
    }

    private void flushPendingNetworkOutputs(KineticJob job, OutputCollection collected) {
        for (int i = 0; i < job.pendingNetworkOutputs.size();) {
            GenericStack pending = job.pendingNetworkOutputs.get(i);
            long inserted = insertIntoNetwork(pending.what(), pending.amount(), Actionable.MODULATE);
            long committed = Math.max(0, Math.min(inserted, pending.amount()));
            if (committed > 0) {
                recordCollectedOutput(job, pending.what(), committed, collected);
            }
            if (committed == pending.amount()) {
                job.pendingNetworkOutputs.remove(i);
            } else {
                job.pendingNetworkOutputs.set(i,
                        new GenericStack(pending.what(), pending.amount() - committed));
                i++;
            }
        }
    }

    private static void bufferNetworkRemainder(KineticJob job, AEKey key, long extracted, long inserted,
            OutputCollection collected) {
        long remainder = extracted - Math.max(0, inserted);
        if (remainder <= 0) {
            return;
        }
        PatternStacks.addStack(job.pendingNetworkOutputs, new GenericStack(key, remainder));
        collected.didWork = true;
    }

    private static void recordCollectedOutput(KineticJob job, AEKey key, long amount, OutputCollection collected) {
        job.roundHasOutput = true;
        job.roundTicks = 0;
        job.lastRefillAttemptTicks = 0;
        job.nextRefillGameTime = 0;
        if (key.equals(job.primaryOutput)) {
            job.remaining = Math.max(0, job.remaining - amount);
            collected.primary += amount;
        } else {
            collected.other += amount;
        }
        collected.didWork = true;
    }

    private static final class OutputCollection {
        private boolean foundHandler;
        private boolean didWork;
        private long primary;
        private long other;
    }

    private boolean isExpectedOutput(KineticJob job, AEKey key) {
        return ExpectedOutputMatcher.matches(job.primaryOutput, job.patternOutputs, GenericStack::what, key);
    }

    private boolean shouldCompletePartialOutputJob(KineticJob job) {
        return job.collectUnexpectedOutputs
                && job.remaining > 0
                && job.roundHasOutput
                && job.roundTicks > 0
                && !job.hasPendingDispatches()
                && job.roundsStarted >= job.queuedPatterns
                && !InventoryAccess.hasAnyItem(level, job.machinePos)
                && !InventoryAccess.hasAnyFluid(level, job.machinePos)
                && !hasCollectableOutput(job);
    }

    private boolean hasCollectableOutput(KineticJob job) {
        for (MachineEndpoint endpoint : job.outputEndpoints) {
            if (endpoint.medium() == MachineEndpoint.Medium.ITEM
                    || endpoint.medium() == MachineEndpoint.Medium.SMART_ITEM_OUTPUT
                    || endpoint.medium() == MachineEndpoint.Medium.CRUCIBLE_ITEM_OUTPUT) {
                IItemHandler handler = itemOutputHandler(endpoint);
                if (handler == null) {
                    continue;
                }
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack available = handler.extractItem(slot, extractAmountForSlot(handler, slot), true);
                    AEItemKey key = AEItemKey.of(available);
                    if (key != null && (job.collectUnexpectedOutputs || isExpectedOutput(job, key))) {
                        return true;
                    }
                }
            } else if (endpoint.medium() == MachineEndpoint.Medium.FLUID
                    || endpoint.medium() == MachineEndpoint.Medium.SMART_FLUID_OUTPUT
                    || endpoint.medium() == MachineEndpoint.Medium.CRUCIBLE_FLUID_OUTPUT) {
                IFluidHandler handler = fluidOutputHandler(endpoint);
                if (handler == null) {
                    continue;
                }
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    AEFluidKey key = AEFluidKey.of(handler.getFluidInTank(tank));
                    if (key != null && (job.collectUnexpectedOutputs || isExpectedOutput(job, key))) {
                        return true;
                    }
                }
            } else {
                long maxEntityAge = job.maxCollectableEntityAge(level.getGameTime());
                for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
                        droppedItemCollectionArea(endpoint))) {
                    if (entity.tickCount > maxEntityAge) {
                        continue;
                    }
                    AEItemKey key = AEItemKey.of(entity.getItem());
                    if (key != null && (job.collectUnexpectedOutputs || isExpectedOutput(job, key))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int extractAmountForSlot(IItemHandler handler, int slot) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return 64;
        }
        return Math.max(64, stack.getCount());
    }

    private boolean retryRefillRound(KineticJob job, boolean targetChanged, boolean earlyRefill) {
        if (targetChanged && "refill_target_full".equals(job.statusKey)) {
            return retryRefillRoundNow(job, "target_freed");
        }
        if (!earlyRefill && job.roundTicks - job.lastRefillAttemptTicks < Config.refillRetryTicks()) {
            return false;
        }
        return retryRefillRoundNow(job, job.statusKey);
    }

    private boolean retryRefillRoundNow(KineticJob job, String reason) {
        boolean started = startRefillRound(job, reason);
        if (!started && "waiting_refill_inputs".equals(job.statusKey)) {
            refillCraftingOrders.requestMissing(level, getMainNode().getGrid(), actionSource, job.refillInputs,
                    job.primaryOutput);
        }
        if (!started) {
            saveAndSync();
        }
        return started;
    }

    private void markRefillAttempt(KineticJob job) {
        job.lastRefillAttemptTicks = job.roundTicks;
        if (level != null) {
            job.nextRefillGameTime = level.getGameTime() + Config.refillRetryTicks();
        }
    }

    private boolean startRefillRound(KineticJob job, String reason) {
        if (job.refillInputs.isEmpty()) {
            rememberJobStatus(job, "refill_unavailable");
            markRefillAttempt(job);
            return false;
        }

        MachinePlanFactory.Plan refill = createPlanAt(job.machinePos, job.primaryOutput,
                primaryPatternOutputAmount(job), job.patternOutputs, job.refillInputs);
        if (refill == null) {
            markRefillAttempt(job);
            return false;
        }
        if (!Objects.equals(refill.outputEndpoints(), job.outputEndpoints)) {
            rememberJobStatus(job, "missing_output");
            markRefillAttempt(job);
            return false;
        }

        for (GenericStack input : job.refillInputs) {
            long available = extractFromNetwork(input.what(), input.amount(), Actionable.SIMULATE);
            if (available < input.amount()) {
                rememberJobStatus(job, "waiting_refill_inputs");
                markRefillAttempt(job);
                return false;
            }
        }
        if (!refill.simulate()) {
            rememberJobStatus(job, "refill_target_full");
            markRefillAttempt(job);
            return false;
        }

        for (GenericStack input : job.refillInputs) {
            long extracted = extractFromNetwork(input.what(), input.amount(), Actionable.MODULATE);
            if (extracted < input.amount()) {
                if (extracted > 0) {
                    insertIntoNetwork(input.what(), extracted, Actionable.MODULATE);
                }
                rememberJobStatus(job, "waiting_refill_inputs");
                markRefillAttempt(job);
                return false;
            }
        }
        if (!refill.execute().success()) {
            for (GenericStack input : job.refillInputs) {
                insertIntoNetwork(input.what(), input.amount(), Actionable.MODULATE);
            }
            rememberJobStatus(job, "refill_target_full");
            markRefillAttempt(job);
            return false;
        }

        job.markOutputStart(level.getGameTime());
        job.roundsStarted++;
        job.roundTicks = 0;
        job.lastRefillAttemptTicks = 0;
        job.nextRefillGameTime = 0;
        job.roundHasOutput = false;
        rememberJobStatus(job, "working");
        if (!hasWaitingRefillJob()) {
            refillCraftingOrders.watchNone();
        }
        CreatePackage.LOGGER.info("[Kinetic Pattern Provider @ {}] refilled {} after {} (round {})",
                getBlockPos(), job.primaryOutput, reason, job.roundsStarted);
        saveAndSync();
        wakeTicker();
        return true;
    }

    private boolean hasWaitingRefillJob() {
        for (KineticJob job : activeJobs) {
            if (job.waitingForRefill()) {
                return true;
            }
        }
        return false;
    }

    private long primaryPatternOutputAmount(KineticJob job) {
        for (GenericStack output : job.patternOutputs) {
            if (output != null && job.primaryOutput.equals(output.what()) && output.amount() > 0) {
                return output.amount();
            }
        }
        return 1;
    }

    @Nullable
    private MachinePlanFactory.Plan createPlanAt(BlockPos machinePos, AEKey primaryOutput, long outputAmount,
            List<GenericStack> patternOutputs, List<GenericStack> inputs) {
        if (level == null || !level.isLoaded(machinePos)) {
            return MachinePlanFactory.Plan.failure("missing_machine");
        }
        return MachinePlanFactory.create(level, machinePos, primaryOutput, outputAmount, patternOutputs,
                PatternStacks.copyStacks(inputs));
    }

    private boolean isCraftingJobNoLongerWaiting(KineticJob job) {
        if (job.ticks < 20) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        return grid != null && grid.getCraftingService().getRequestedAmount(job.primaryOutput) <= 0;
    }

    @Nullable
    private BlockPos targetMachinePos() {
        Direction direction = targetDirection();
        if (direction == null) {
            return null;
        }
        BlockPos observed = getBlockPos().relative(direction);
        return level == null ? observed : CreateMachineIds.normalizeKineticMachinePos(level, observed);
    }

    @Nullable
    private Direction targetDirection() {
        return getPushDirection().getDirection();
    }

    private PushDirection getPushDirection() {
        return getBlockState().getValue(KineticPatternProviderBlock.PUSH_DIRECTION);
    }

    @Nullable
    private IItemHandler itemHandler(BlockPos pos) {
        return InventoryAccess.itemHandler(level, pos);
    }

    @Nullable
    private IFluidHandler fluidHandler(BlockPos pos) {
        return InventoryAccess.fluidHandler(level, pos);
    }

    private ItemStack insertItem(BlockPos target, ItemStack stack, boolean simulate) {
        return InventoryAccess.insertItem(level, target, stack, simulate);
    }

    private NetworkIO networkIO() {
        return new NetworkIO(getMainNode().getGrid(), actionSource);
    }

    private long insertIntoNetwork(AEKey what, long amount, Actionable mode) {
        return networkIO().insert(what, amount, mode);
    }

    private long extractFromNetwork(AEKey what, long amount, Actionable mode) {
        return networkIO().extract(what, amount, mode);
    }

    private String roleKey(ResourceLocation id) {
        return CreateMachineIds.kineticRoleKey(id);
    }

    private boolean isBasin(BlockPos pos) {
        return MachinePlanFactory.isBasin(level, pos);
    }

    private void rememberJobStatus(KineticJob job, String statusKey) {
        job.statusKey = statusKey;
        rememberStatus(statusKey);
    }

    private void rememberStatus(String statusKey) {
        if (!Objects.equals(lastStatusKey, statusKey)) {
            lastStatusKey = statusKey;
            saveAndSync();
        }
    }

    private void debugLog(String message, Object... args) {
        if (!debugLoggingEnabled()) {
            return;
        }
        Object[] fullArgs = new Object[args.length + 1];
        fullArgs[0] = getBlockPos();
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        CreatePackage.LOGGER.info("[Kinetic Pattern Provider Debug @ {}] " + message, fullArgs);
    }

    private static boolean debugLoggingEnabled() {
        return Config.debugLoggingEnabled();
    }

    private void saveAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            markForUpdate();
        }
    }

    private void wakeTicker() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private int countInstalledParallelCards() {
        int count = getUpgrades().getInstalledUpgrades(ModItems.PARALLEL_CARD.get());
        if (getUpgrades() != upgrades) {
            count += upgrades.getInstalledUpgrades(ModItems.PARALLEL_CARD.get());
        }
        return count;
    }

    private void migrateHostUpgradesIntoLogic() {
        IUpgradeInventory fromLogic = ExtendedAePlusCompat.patternLogicUpgrades(logic);
        if (fromLogic == null || fromLogic == upgrades) {
            return;
        }
        for (int slot = 0; slot < fromLogic.size(); slot++) {
            ItemStack stack = fromLogic.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != ModItems.PARALLEL_CARD.get()) {
                continue;
            }
            ItemStack leftover = upgrades.addItems(stack);
            fromLogic.setItemDirect(slot, leftover);
        }
    }

    private void onUpgradesChanged() {
        saveAndSync();
        requestCraftingUpdate();
        wakeTicker();
    }

    private void onLinksChanged() {
        saveAndSync();
        requestCraftingUpdate();
        wakeTicker();
    }

    private void requestCraftingUpdate() {
        ICraftingProvider.requestUpdate(getMainNode());
    }

    private boolean hasReturnInventoryWork() {
        return !logic.getReturnInv().isEmpty();
    }

    private boolean returnInventoryToNetwork() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        boolean didWork = logic.getReturnInv().injectIntoNetwork(
                grid.getStorageService().getInventory(),
                actionSource,
                returned -> {
                });
        if (didWork) {
            saveAndSync();
        }
        return didWork;
    }

    private AeStatus currentAeStatus() {
        IGridNode node = getMainNode().getNode();
        return new AeStatus(node != null,
                node != null && getMainNode().isPowered(),
                node != null && node.meetsChannelRequirements(),
                node != null && getMainNode().isActive());
    }

    public AeStatus visibleAeStatus() {
        if (level != null && !level.isClientSide()) {
            return currentAeStatus();
        }
        return new AeStatus(aeNodePresent, aePowered, aeChannel, aeActive);
    }

    public Component statusLine() {
        String status = getStatusKey();
        ChatFormatting color = switch (status) {
            case "ready", "completed" -> ChatFormatting.GREEN;
            case "working" -> ChatFormatting.AQUA;
            case "busy", "waiting_refill_inputs", "refill_target_full", "waiting_target_space" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.status",
                Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.status." + status)
                        .withStyle(color)).withStyle(ChatFormatting.GRAY);
    }

    public List<Component> jobLines() {
        if (activeJobs.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < activeJobs.size(); i++) {
            KineticJob job = activeJobs.get(i);
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.job_entry",
                    i + 1,
                    job.primaryOutput.getDisplayName(),
                    job.remaining,
                    machineRoleName(job.machineRole),
                    job.machinePos.getX(), job.machinePos.getY(), job.machinePos.getZ(),
                    job.outputPos.getX(), job.outputPos.getY(), job.outputPos.getZ()));
        }
        return lines;
    }

    public List<Component> refillRetryLines() {
        if (activeJobs.isEmpty() || level == null) {
            return List.of();
        }
        long now = level.getGameTime();
        List<Component> lines = new ArrayList<>();
        for (KineticJob job : activeJobs) {
            if (!job.waitingForRefill() || job.nextRefillGameTime <= 0) {
                continue;
            }
            lines.add(RefillCraftingOrders.retryLine(
                    "tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.refill_retry",
                    job.nextRefillGameTime, now));
        }
        return lines;
    }

    public Component smartDoublingLine() {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.smart_doubling",
                boolText(smartDoubling)).withStyle(ChatFormatting.GRAY);
    }

    public Component parallelLine() {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.parallel",
                getParallelCardCount(), MAX_PARALLEL_CARDS, activeJobs.size(), maxActiveJobsFromLinkedMachines())
                .withStyle(ChatFormatting.GRAY);
    }

    public List<Component> linkedMachineLines() {
        List<BlockPos> targets = targetMachinePositions();
        if (targets.size() <= 1 && linkedMachines.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.linked_machines",
                linkedMachines.size()).withStyle(ChatFormatting.WHITE));
        int shown = Math.min(linkedMachines.size(), MAX_TOOLTIP_TARGETS);
        for (int i = 0; i < shown; i++) {
            BlockPos pos = linkedMachines.get(i);
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID
                    + ".kinetic_pattern_provider.linked_machine_entry",
                    i + 1, machineNameAt(pos), pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (linkedMachines.size() > shown) {
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID
                    + ".package_distributor.more_links", linkedMachines.size() - shown)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    public Component aeLine() {
        AeStatus status = visibleAeStatus();
        if (!status.nodePresent()) {
            return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.ae_node_missing")
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.ae_node",
                boolText(status.powered()),
                boolText(status.channel()),
                boolText(status.active())).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public ItemStack getIcon(boolean isPlayerSneaking) {
        return new ItemStack(providerItem);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.header",
                providerItem.getDescription()).withStyle(ChatFormatting.GOLD));
        tooltip.add(statusLine());
        tooltip.add(aeLine());
        tooltip.add(smartDoublingLine());
        tooltip.add(parallelLine());
        tooltip.add(machineLine());
        for (Component line : linkedMachineLines()) {
            tooltip.add(line);
        }
        for (Component line : jobLines()) {
            tooltip.add(line.copy().withStyle(ChatFormatting.AQUA));
        }
        tooltip.addAll(refillRetryLines());
        for (Component line : diagnostics.tooltipLines("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider")) {
            tooltip.add(line.copy().withStyle(ChatFormatting.RED));
        }
        return true;
    }

    private static Component boolText(boolean value) {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor."
                + (value ? "yes" : "no")).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static Component machineRoleName(String role) {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.role." + role);
    }

    private static MachineEndpoint defaultOutputEndpoint(AEKey output, BlockPos outputPos) {
        MachineEndpoint.Medium medium = output instanceof AEFluidKey
                ? MachineEndpoint.Medium.FLUID
                : MachineEndpoint.Medium.ITEM;
        return new MachineEndpoint(outputPos, null, medium, MachineEndpoint.Purpose.OUTPUT);
    }

    private static boolean sameJobs(List<KineticJob> left, List<KineticJob> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!sameJob(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameJob(@Nullable KineticJob left, @Nullable KineticJob right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.primaryOutput, right.primaryOutput)
                && left.remaining == right.remaining
                && Objects.equals(left.machinePos, right.machinePos)
                && Objects.equals(left.outputPos, right.outputPos)
                && Objects.equals(left.machineRole, right.machineRole)
                && left.ticks == right.ticks
                && Objects.equals(left.statusKey, right.statusKey)
                && left.nextRefillGameTime == right.nextRefillGameTime;
    }

    private static int emptyOutputRefillTimeoutTicks() {
        return Config.emptyOutputRefillTimeoutTicks(DEFAULT_ROUND_OUTPUT_TIMEOUT_TICKS);
    }

    public record AeStatus(boolean nodePresent, boolean powered, boolean channel, boolean active) {
    }

    public static final class KineticJob {
        private static final String NBT_PRIMARY = "primary";
        private static final String NBT_REMAINING = "remaining";
        private static final String NBT_MACHINE = "machine";
        private static final String NBT_OUTPUT = "output";
        private static final String NBT_OUTPUT_ENDPOINTS = "outputEndpoints";
        private static final String NBT_ENDPOINT_POS = "pos";
        private static final String NBT_ENDPOINT_SIDE = "side";
        private static final String NBT_ENDPOINT_MEDIUM = "medium";
        private static final String NBT_ROLE = "role";
        private static final String NBT_TICKS = "ticks";
        private static final String NBT_ROUND_TICKS = "roundTicks";
        private static final String NBT_LAST_REFILL_ATTEMPT_TICKS = "lastRefillAttemptTicks";
        private static final String NBT_NEXT_REFILL_GAME_TIME = "nextRefillGameTime";
        private static final String NBT_ROUNDS_STARTED = "roundsStarted";
        private static final String NBT_ROUND_HAS_OUTPUT = "roundHasOutput";
        private static final String NBT_JOB_STATUS = "jobStatus";
        private static final String NBT_REFILL_INPUTS = "refillInputs";
        private static final String NBT_PATTERN_OUTPUTS = "patternOutputs";
        private static final String NBT_COLLECT_UNEXPECTED_OUTPUTS = "collectUnexpectedOutputs";
        private static final String NBT_DEFINITION = "definition";
        private static final String NBT_PENDING_DISPATCHES = "pendingDispatches";
        private static final String NBT_PENDING_OUTPUT = "outputAmount";
        private static final String NBT_PENDING_INPUTS = "inputs";
        private static final String NBT_PENDING_DISPATCH_TICKS = "pendingDispatchTicks";
        private static final String NBT_OUTPUT_START_TIME = "outputStartTime";
        private static final String NBT_PENDING_NETWORK_OUTPUTS = "pendingNetworkOutputs";

        private final AEKey primaryOutput;
        @Nullable
        private AEItemKey patternDefinition;
        private long remaining;
        private final BlockPos machinePos;
        // Kept for old client stream and old save compatibility; authoritative collection uses endpoints.
        private final BlockPos outputPos;
        private final List<MachineEndpoint> outputEndpoints;
        private final String machineRole;
        private List<GenericStack> patternOutputs;
        private List<GenericStack> refillInputs;
        private final boolean collectUnexpectedOutputs;
        private int ticks;
        private int roundTicks;
        private int lastRefillAttemptTicks;
        private long nextRefillGameTime;
        private int roundsStarted;
        private boolean roundHasOutput;
        private int queuedPatterns = 1;
        private String statusKey = "working";
        private final List<PendingDispatch> pendingDispatches = new ArrayList<>();
        private final List<GenericStack> pendingNetworkOutputs = new ArrayList<>();
        private int pendingDispatchTicks;
        private long outputStartTime = -1;

        private KineticJob(AEKey primaryOutput, @Nullable AEItemKey patternDefinition, long remaining, BlockPos machinePos,
                BlockPos outputPos, List<MachineEndpoint> outputEndpoints, String machineRole,
                List<GenericStack> patternOutputs,
                List<GenericStack> refillInputs, boolean collectUnexpectedOutputs) {
            this.primaryOutput = primaryOutput;
            this.patternDefinition = patternDefinition;
            this.remaining = remaining;
            this.machinePos = machinePos.immutable();
            this.outputPos = outputPos.immutable();
            this.outputEndpoints = List.copyOf(outputEndpoints);
            this.machineRole = machineRole;
            this.patternOutputs = List.copyOf(patternOutputs);
            this.refillInputs = List.copyOf(refillInputs);
            this.collectUnexpectedOutputs = collectUnexpectedOutputs;
        }

        private boolean waitingForRefill() {
            return "waiting_refill_inputs".equals(statusKey) || "refill_target_full".equals(statusKey);
        }

        private boolean returningPendingDispatches() {
            return "cancelled".equals(statusKey) || "timeout".equals(statusKey);
        }

        private boolean hasPendingDispatches() {
            return !pendingDispatches.isEmpty();
        }

        private void markOutputStart(long gameTime) {
            outputStartTime = gameTime;
        }

        private long maxCollectableEntityAge(long gameTime) {
            if (outputStartTime < 0 || gameTime < outputStartTime) {
                return Math.max(1, ticks + 1L);
            }
            return Math.max(1, gameTime - outputStartTime + 1L);
        }

        private CompoundTag write(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put(NBT_PRIMARY, primaryOutput.toTagGeneric(registries));
            if (patternDefinition != null) {
                tag.put(NBT_DEFINITION, patternDefinition.toTag(registries));
            }
            tag.putLong(NBT_REMAINING, remaining);
            tag.putLong(NBT_MACHINE, machinePos.asLong());
            tag.putLong(NBT_OUTPUT, outputPos.asLong());
            tag.put(NBT_OUTPUT_ENDPOINTS, writeEndpoints(outputEndpoints));
            tag.putString(NBT_ROLE, machineRole);
            tag.putInt(NBT_TICKS, ticks);
            tag.putInt(NBT_ROUND_TICKS, roundTicks);
            tag.putInt(NBT_LAST_REFILL_ATTEMPT_TICKS, lastRefillAttemptTicks);
            tag.putLong(NBT_NEXT_REFILL_GAME_TIME, nextRefillGameTime);
            tag.putInt(NBT_ROUNDS_STARTED, roundsStarted);
            tag.putInt("queuedPatterns", queuedPatterns);
            tag.putBoolean(NBT_ROUND_HAS_OUTPUT, roundHasOutput);
            tag.putString(NBT_JOB_STATUS, statusKey);
            tag.putBoolean(NBT_COLLECT_UNEXPECTED_OUTPUTS, collectUnexpectedOutputs);
            tag.put(NBT_PATTERN_OUTPUTS, writeStackList(registries, patternOutputs));
            tag.put(NBT_REFILL_INPUTS, writeStackList(registries, refillInputs));
            tag.putInt(NBT_PENDING_DISPATCH_TICKS, pendingDispatchTicks);
            tag.putLong(NBT_OUTPUT_START_TIME, outputStartTime);
            tag.put(NBT_PENDING_NETWORK_OUTPUTS, writeStackList(registries, pendingNetworkOutputs));
            if (!pendingDispatches.isEmpty()) {
                net.minecraft.nbt.ListTag pendingTags = new net.minecraft.nbt.ListTag();
                for (PendingDispatch pending : pendingDispatches) {
                    CompoundTag pendingTag = new CompoundTag();
                    pendingTag.putLong(NBT_PENDING_OUTPUT, pending.outputAmount());
                    pendingTag.put(NBT_PENDING_INPUTS, writeStackList(registries, pending.inputs()));
                    pendingTags.add(pendingTag);
                }
                tag.put(NBT_PENDING_DISPATCHES, pendingTags);
            }
            return tag;
        }

        @Nullable
        private static KineticJob read(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag primaryTag = tag.getCompound(NBT_PRIMARY);
            AEKey primary = AEKey.fromTagGeneric(registries, primaryTag);
            if (primary == null) {
                primary = AEItemKey.fromTag(registries, primaryTag);
            }
            if (primary == null) {
                return null;
            }
            List<GenericStack> outputs = readStackList(tag, registries, NBT_PATTERN_OUTPUTS);
            List<GenericStack> refillInputs = readStackList(tag, registries, NBT_REFILL_INPUTS);
            AEItemKey definition = tag.contains(NBT_DEFINITION)
                    ? AEItemKey.fromTag(registries, tag.getCompound(NBT_DEFINITION))
                    : (primary instanceof AEItemKey itemKey ? itemKey : null);
            BlockPos outputPos = BlockPos.of(tag.getLong(NBT_OUTPUT));
            List<MachineEndpoint> outputEndpoints = tag.contains(NBT_OUTPUT_ENDPOINTS, net.minecraft.nbt.Tag.TAG_LIST)
                    ? readEndpoints(tag)
                    : List.of(defaultOutputEndpoint(primary, outputPos));
            KineticJob job = new KineticJob(primary, definition, tag.getLong(NBT_REMAINING),
                    BlockPos.of(tag.getLong(NBT_MACHINE)),
                    outputPos,
                    outputEndpoints,
                    tag.getString(NBT_ROLE),
                    outputs,
                    refillInputs,
                    tag.getBoolean(NBT_COLLECT_UNEXPECTED_OUTPUTS));
            job.ticks = tag.getInt(NBT_TICKS);
            job.roundTicks = tag.getInt(NBT_ROUND_TICKS);
            job.lastRefillAttemptTicks = tag.getInt(NBT_LAST_REFILL_ATTEMPT_TICKS);
            job.nextRefillGameTime = tag.getLong(NBT_NEXT_REFILL_GAME_TIME);
            job.roundsStarted = tag.getInt(NBT_ROUNDS_STARTED);
            job.queuedPatterns = tag.contains("queuedPatterns") ? Math.max(1, tag.getInt("queuedPatterns")) : 1;
            job.roundHasOutput = tag.getBoolean(NBT_ROUND_HAS_OUTPUT);
            job.statusKey = tag.contains(NBT_JOB_STATUS) ? tag.getString(NBT_JOB_STATUS) : "working";
            job.pendingDispatchTicks = tag.getInt(NBT_PENDING_DISPATCH_TICKS);
            job.outputStartTime = tag.contains(NBT_OUTPUT_START_TIME) ? tag.getLong(NBT_OUTPUT_START_TIME) : -1;
            job.pendingNetworkOutputs.addAll(readStackList(tag, registries, NBT_PENDING_NETWORK_OUTPUTS));
            var pendingTags = tag.getList(NBT_PENDING_DISPATCHES, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < pendingTags.size(); i++) {
                CompoundTag pendingTag = pendingTags.getCompound(i);
                long outputAmount = pendingTag.getLong(NBT_PENDING_OUTPUT);
                List<GenericStack> pendingInputs = readStackList(pendingTag, registries, NBT_PENDING_INPUTS);
                if (outputAmount > 0 && !pendingInputs.isEmpty()) {
                    job.pendingDispatches.add(new PendingDispatch(outputAmount, pendingInputs));
                }
            }
            return job;
        }

        private static net.minecraft.nbt.ListTag writeEndpoints(List<MachineEndpoint> endpoints) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (MachineEndpoint endpoint : endpoints) {
                CompoundTag endpointTag = new CompoundTag();
                endpointTag.putLong(NBT_ENDPOINT_POS, endpoint.pos().asLong());
                if (endpoint.side() != null) {
                    endpointTag.putString(NBT_ENDPOINT_SIDE, endpoint.side().getSerializedName());
                }
                endpointTag.putString(NBT_ENDPOINT_MEDIUM, endpoint.medium().name());
                list.add(endpointTag);
            }
            return list;
        }

        private static List<MachineEndpoint> readEndpoints(CompoundTag tag) {
            List<MachineEndpoint> endpoints = new ArrayList<>();
            var endpointTags = tag.getList(NBT_OUTPUT_ENDPOINTS, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < endpointTags.size(); i++) {
                CompoundTag endpointTag = endpointTags.getCompound(i);
                MachineEndpoint.Medium medium;
                try {
                    medium = MachineEndpoint.Medium.valueOf(endpointTag.getString(NBT_ENDPOINT_MEDIUM));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                Direction side = endpointTag.contains(NBT_ENDPOINT_SIDE)
                        ? Direction.byName(endpointTag.getString(NBT_ENDPOINT_SIDE))
                        : null;
                endpoints.add(new MachineEndpoint(BlockPos.of(endpointTag.getLong(NBT_ENDPOINT_POS)), side, medium,
                        MachineEndpoint.Purpose.OUTPUT));
            }
            return List.copyOf(endpoints);
        }

        private static net.minecraft.nbt.ListTag writeStackList(HolderLookup.Provider registries,
                List<GenericStack> stacks) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (GenericStack stack : stacks) {
                list.add(GenericStack.writeTag(registries, stack));
            }
            return list;
        }

        private static List<GenericStack> readStackList(CompoundTag tag, HolderLookup.Provider registries,
                String key) {
            List<GenericStack> stacks = new ArrayList<>();
            var stackTags = tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < stackTags.size(); i++) {
                GenericStack stack = GenericStack.readTag(registries, stackTags.getCompound(i));
                if (stack != null) {
                    stacks.add(stack);
                }
            }
            return stacks;
        }

        private record PendingDispatch(long outputAmount, List<GenericStack> inputs) {
            private PendingDispatch {
                inputs = List.copyOf(inputs);
            }
        }
    }

    private record DispatchTarget(@Nullable KineticJob job, MachinePlanFactory.Plan plan, boolean allowQueue) {
    }

    private record PendingBatch(MachinePlanFactory.Plan plan, long outputAmount, int patternCount) {
    }

    private final class InternalPatternProviderLogic extends PatternProviderLogic {
        private InternalPatternProviderLogic(int patternSlots) {
            super(getMainNode(), KineticPatternProviderBlockEntity.this, patternSlots);
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
            boolean accepted = KineticPatternProviderBlockEntity.this.pushKineticPattern(patternDetails, inputHolder);
            if (accepted) {
                resetCraftingLock();
            }
            return accepted;
        }

        @Override
        public boolean isBusy() {
            return !KineticPatternProviderBlockEntity.this.activeJobCanAcceptOneMorePattern();
        }
    }
}
