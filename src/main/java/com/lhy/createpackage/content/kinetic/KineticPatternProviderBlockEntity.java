package com.lhy.createpackage.content.kinetic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.compat.ExtendedAePlusCompat;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

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
    private static final String NBT_LINKED_MACHINES = "linkedMachines";
    private static final int MAX_PARALLEL_CARDS = 2;
    private static final int PARALLEL_MACHINES_PER_CARD = 16;
    private static final int MAX_TOOLTIP_TARGETS = 6;
    private static final int MAX_JOB_TICKS = 20 * 60 * 5;
    private static final int REFILL_RETRY_TICKS = 20 * 5;
    private static final int ROUND_OUTPUT_TIMEOUT_TICKS = 20 * 60;
    private static final int MAX_SMART_BATCH_PATTERNS = 64;
    private static final int PENDING_DISPATCH_RETRY_TICKS = 5;
    private static final ResourceLocation ID_DEPLOYER = ResourceLocation.fromNamespaceAndPath("create", "deployer");
    private static final ResourceLocation ID_SPOUT = ResourceLocation.fromNamespaceAndPath("create", "spout");
    private static final ResourceLocation ID_MECHANICAL_PRESS = ResourceLocation.fromNamespaceAndPath("create", "mechanical_press");
    private static final ResourceLocation ID_MECHANICAL_MIXER = ResourceLocation.fromNamespaceAndPath("create", "mechanical_mixer");
    private static final ResourceLocation ID_BASIN = ResourceLocation.fromNamespaceAndPath("create", "basin");
    private static final ResourceLocation ID_MILLSTONE = ResourceLocation.fromNamespaceAndPath("create", "millstone");
    private static final ResourceLocation ID_MECHANICAL_SAW = ResourceLocation.fromNamespaceAndPath("create", "mechanical_saw");
    private static final ResourceLocation ID_CRUSHING_WHEEL = ResourceLocation.fromNamespaceAndPath("create", "crushing_wheel");
    private static final ResourceLocation ID_CRUSHING_CONTROLLER = ResourceLocation.fromNamespaceAndPath("create", "crushing_wheel_controller");

    private final InternalPatternProviderLogic logic = new InternalPatternProviderLogic();
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            ModItems.KINETIC_PATTERN_PROVIDER.get(), MAX_PARALLEL_CARDS, this::onUpgradesChanged);
    private final MachineSource actionSource = new MachineSource(this);
    private final List<KineticJob> activeJobs = new ArrayList<>();
    private final List<BlockPos> linkedMachines = new ArrayList<>();

    private String lastStatusKey = "ready";
    private boolean smartDoubling;
    private boolean aeNodePresent;
    private boolean aePowered;
    private boolean aeChannel;
    private boolean aeActive;
    private int visibleParallelCards;

    public KineticPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.KINETIC_PATTERN_PROVIDER.get(), pos, blockState);
        getMainNode()
                .setVisualRepresentation(ModItems.KINETIC_PATTERN_PROVIDER.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .addService(IGridTickable.class, this)
                .addService(ICraftingProvider.class, logic);
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
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        logic.writeToNBT(data, registries);
        ExtendedAePlusCompat.removeProviderSmartSettings(data);
        upgrades.writeToNBT(data, NBT_UPGRADES, registries);
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
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        ExtendedAePlusCompat.removeProviderSmartSettings(data);
        logic.readFromNBT(data, registries);
        ExtendedAePlusCompat.disableProviderSmartSettings(logic.getConfigManager());
        upgrades.readFromNBT(data, NBT_UPGRADES, registries);
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
            job.primaryOutput.writeToPacket(data);
            data.writeVarLong(job.remaining);
            data.writeBlockPos(job.machinePos);
            data.writeBlockPos(job.outputPos);
            data.writeUtf(job.machineRole);
            data.writeVarInt(job.ticks);
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
            AEItemKey output = AEItemKey.fromPacket(data);
            long remaining = data.readVarLong();
            BlockPos machinePos = data.readBlockPos();
            BlockPos outputPos = data.readBlockPos();
            String machineRole = data.readUtf();
            if (output != null) {
                KineticJob streamedJob = new KineticJob(output, output, remaining, machinePos, outputPos,
                        machineRole, List.of(), List.of(), false);
                streamedJob.ticks = data.readVarInt();
                streamedJobs.add(streamedJob);
            } else {
                data.readVarInt();
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
        for (ItemStack upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
        upgrades.clear();
        activeJobs.clear();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
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
            return upgrades;
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
        return AEItemKey.of(ModItems.KINETIC_PATTERN_PROVIDER.get());
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
                ModItems.KINETIC_PATTERN_PROVIDER.get().getDescription(),
                List.of());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModItems.KINETIC_PATTERN_PROVIDER.toStack();
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenuTypes.KINETIC_PATTERN_PROVIDER.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(ModMenuTypes.KINETIC_PATTERN_PROVIDER.get(), player, subMenu.getLocator());
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
            didWork |= tickJobs();
        }
        if (!activeJobs.isEmpty() || hasReturnInventoryWork()) {
            return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        return TickRateModulation.SLEEP;
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
        return Math.min(MAX_PARALLEL_CARDS, upgrades.getInstalledUpgrades(ModItems.PARALLEL_CARD.get()));
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

    public boolean linkMachine(BlockPos pos) {
        if (linkedMachines.contains(pos)) {
            return false;
        }
        linkedMachines.add(pos.immutable());
        onLinksChanged();
        return true;
    }

    public boolean unlinkMachine(BlockPos pos) {
        boolean removed = linkedMachines.remove(pos);
        if (removed) {
            onLinksChanged();
        }
        return removed;
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
        return linkedMachines.contains(pos);
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
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutput)) {
            rememberStatus("no_primary_output");
            return false;
        }

        List<GenericStack> flattenedInputs = flattenInputs(inputs);
        DispatchTarget target = selectDispatchTarget(patternDetails, primaryOutput, primary.amount(), flattenedInputs);
        if (target == null) {
            rememberStatus("busy");
            return false;
        }
        Plan plan = target.plan();
        if (!plan.simulate()) {
            if (target.job() == null || !target.allowQueue()) {
                rememberStatus("simulate_failed");
                return false;
            }
            consumeInputs(inputs);
            queuePendingPattern(target.job(), primary.amount(), flattenedInputs);
            lastStatusKey = "working";
            saveAndSync();
            wakeTicker();
            return true;
        }
        if (!plan.execute()) {
            rememberStatus("execute_failed");
            return false;
        }

        consumeInputs(inputs);
        if (target.job() == null) {
            KineticJob job = new KineticJob(primaryOutput, patternDetails.getDefinition(), primary.amount(),
                    plan.machinePos(), plan.outputPos(), plan.machineRole(), plan.patternOutputs(),
                    plan.refillInputs(), plan.collectUnexpectedOutputs());
            job.roundsStarted = 1;
            activeJobs.add(job);
        } else {
            KineticJob job = target.job();
            job.remaining = safeAdd(job.remaining, primary.amount());
            job.queuedPatterns++;
            job.roundsStarted++;
            job.roundHasOutput = false;
            job.roundTicks = 0;
            job.ticks = 0;
        }
        lastStatusKey = "working";
        saveAndSync();
        wakeTicker();
        return true;
    }

    private void queuePendingPattern(KineticJob job, long outputAmount, List<GenericStack> inputs) {
        job.remaining = safeAdd(job.remaining, outputAmount);
        job.queuedPatterns++;
        job.pendingDispatches.add(new KineticJob.PendingDispatch(outputAmount, copyStacks(inputs)));
        job.ticks = 0;
    }

    @Nullable
    private DispatchTarget selectDispatchTarget(IPatternDetails patternDetails, AEItemKey primaryOutput,
            long outputAmount, List<GenericStack> inputs) {
        Plan firstFailure = null;
        if (activeJobs.size() < maxActiveJobsFromLinkedMachines()) {
            for (BlockPos machinePos : targetMachinePositions()) {
                if (isMachineBusy(machinePos)) {
                    continue;
                }
                Plan plan = createPlanAt(machinePos, primaryOutput, outputAmount, patternDetails.getOutputs(), inputs);
                if (plan != null && plan.simulate()) {
                    return new DispatchTarget(null, plan, false);
                }
                if (firstFailure == null) {
                    firstFailure = plan;
                }
            }
        }

        for (KineticJob job : activeJobs) {
            Plan plan = planForAppend(job, patternDetails, outputAmount, inputs);
            if (plan != null) {
                return new DispatchTarget(job, plan, true);
            }
        }
        return firstFailure == null ? null : new DispatchTarget(null, firstFailure, false);
    }

    private boolean canAppendToActiveJob(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (level == null || level.isClientSide()) {
            return activeJobs.isEmpty();
        }
        GenericStack primary = patternDetails.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutput)) {
            return false;
        }
        List<GenericStack> flattenedInputs = flattenInputs(inputs);
        return selectDispatchTarget(patternDetails, primaryOutput, primary.amount(), flattenedInputs) != null;
    }

    @Nullable
    private Plan planForAppend(KineticJob job, IPatternDetails patternDetails, long outputAmount,
            List<GenericStack> inputs) {
        if (!smartDoubling) {
            return null;
        }
        GenericStack primary = patternDetails.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutput)) {
            return null;
        }
        if (!primaryOutput.equals(job.primaryOutput)) {
            return null;
        }
        if (!Objects.equals(patternDetails.getDefinition(), job.patternDefinition)) {
            return null;
        }
        if (job.queuedPatterns >= MAX_SMART_BATCH_PATTERNS) {
            return null;
        }
        Plan plan = createPlanAt(job.machinePos, job.primaryOutput, outputAmount, patternDetails.getOutputs(), inputs);
        if (plan == null
                || !Objects.equals(plan.outputPos(), job.outputPos)
                || !Objects.equals(plan.machineRole(), job.machineRole)
                || plan.collectUnexpectedOutputs() != job.collectUnexpectedOutputs) {
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
            Plan plan = createPlanAt(job.machinePos, job.primaryOutput, 1,
                    job.patternOutputs, job.refillInputs);
            if (job.queuedPatterns < MAX_SMART_BATCH_PATTERNS
                    && plan != null
                    && Objects.equals(plan.outputPos(), job.outputPos)
                    && Objects.equals(plan.machineRole(), job.machineRole)
                    && plan.collectUnexpectedOutputs() == job.collectUnexpectedOutputs) {
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
                if (linked.equals(front) || !level.isLoaded(linked) || !Objects.equals(frontId, blockId(linked))) {
                    continue;
                }
                targets.add(linked.immutable());
            }
        }
        return List.copyOf(targets);
    }

    @Nullable
    private ResourceLocation blockId(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    @Nullable
    private Plan createPlan(AEItemKey primaryOutput, long outputAmount, List<GenericStack> patternOutputs,
            List<GenericStack> inputs) {
        BlockPos machinePos = targetMachinePos();
        if (machinePos == null) {
            rememberStatus("missing_target");
            return null;
        }
        if (level == null || !level.isLoaded(machinePos)) {
            rememberStatus("missing_machine");
            return null;
        }
        return createPlanForMachine(machinePos, primaryOutput, outputAmount, patternOutputs, inputs);
    }

    @Nullable
    private Plan createPlanForMachine(BlockPos machinePos, AEItemKey primaryOutput, long outputAmount,
            List<GenericStack> patternOutputs, List<GenericStack> inputs) {
        if (level == null || !level.isLoaded(machinePos)) {
            rememberStatus("missing_machine");
            return null;
        }
        ResourceLocation machineId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(machinePos).getBlock());
        if (machineId == null) {
            rememberStatus("unsupported_machine");
            return null;
        }

        List<SupplyAction> actions = new ArrayList<>();
        List<GenericStack> remaining = new ArrayList<>(inputs);
        List<GenericStack> refillInputs = copyStacks(inputs);
        List<GenericStack> outputs = new ArrayList<>(patternOutputs);
        if (outputs.stream().noneMatch(output -> output.what().equals(primaryOutput))) {
            outputs.add(new GenericStack(primaryOutput, outputAmount));
        }

        BlockPos outputPos = machinePos;
        String role = roleKey(machineId);
        boolean collectUnexpectedOutputs = false;
        if (ID_DEPLOYER.equals(machineId)) {
            BlockPos itemTarget = deployerItemTarget(machinePos);
            ItemStack processed = takeFirstItem(remaining);
            ItemStack held = takeFirstItem(remaining);
            if (processed.isEmpty()) {
                rememberStatus("missing_item_input");
                return null;
            }
            if (held.isEmpty()) {
                rememberStatus("missing_deployer_item");
                return null;
            }
            actions.add(new ItemSupplyAction(itemTarget, processed));
            actions.add(new ItemSupplyAction(machinePos, held));
            outputPos = itemTarget;
        } else if (ID_SPOUT.equals(machineId)) {
            BlockPos itemTarget = spoutItemTarget(machinePos);
            ItemStack processed = takeFirstItem(remaining);
            FluidStack fluid = takeFirstFluid(remaining);
            if (processed.isEmpty()) {
                rememberStatus("missing_item_input");
                return null;
            }
            if (fluid.isEmpty()) {
                rememberStatus("missing_spout_fluid");
                return null;
            }
            actions.add(new ItemSupplyAction(itemTarget, processed));
            actions.add(new FluidSupplyAction(machinePos, fluid));
            outputPos = itemTarget;
        } else if (ID_MECHANICAL_PRESS.equals(machineId)) {
            BlockPos itemTarget = pressItemTarget(machinePos);
            if (isBasin(itemTarget)) {
                if (!addBasinInputs(actions, remaining, itemTarget)) {
                    return null;
                }
            } else {
                ItemStack input = takeFirstItem(remaining);
                if (input.isEmpty()) {
                    rememberStatus("missing_item_input");
                    return null;
                }
                actions.add(new ItemSupplyAction(itemTarget, input));
            }
            outputPos = itemTarget;
        } else if (ID_MECHANICAL_MIXER.equals(machineId)) {
            BlockPos itemTarget = basinOperatingTarget(machinePos);
            if (!isBasin(itemTarget)) {
                rememberStatus("missing_basin");
                return null;
            }
            if (!addBasinInputs(actions, remaining, itemTarget)) {
                return null;
            }
            outputPos = itemTarget;
        } else if (ID_CRUSHING_WHEEL.equals(machineId)) {
            rememberStatus("face_crushing_controller");
            return null;
        } else if (ID_CRUSHING_CONTROLLER.equals(machineId)) {
            ItemStack input = takeFirstItem(remaining);
            if (input.isEmpty()) {
                rememberStatus("missing_item_input");
                return null;
            }
            BlockPos crushingOutput = crushingOutputTarget(machinePos);
            if (crushingOutput == null || !canRecoverCrushingOutput(machinePos, crushingOutput)) {
                rememberStatus("missing_crushing_output");
                return null;
            }
            actions.add(new ItemSupplyAction(machinePos, input));
            outputPos = crushingOutput;
            collectUnexpectedOutputs = true;
        } else if (ID_MECHANICAL_SAW.equals(machineId)) {
            ItemStack input = takeFirstItem(remaining);
            if (input.isEmpty()) {
                rememberStatus("missing_item_input");
                return null;
            }
            Direction outputDirection = sawOutputDirection(machinePos);
            if (outputDirection == null) {
                return null;
            }
            BlockPos sawOutput = machinePos.relative(outputDirection);
            if (!canRecoverSawOutput(sawOutput, outputDirection)) {
                rememberStatus("missing_saw_output");
                return null;
            }
            if (!canAccessSawFilter(machinePos, primaryOutput.toStack(1))) {
                rememberStatus("missing_saw_filter");
                return null;
            }
            actions.add(new SawFilterAction(machinePos, primaryOutput.toStack(1)));
            actions.add(new ItemSupplyAction(machinePos, input));
            outputPos = sawOutput;
            collectUnexpectedOutputs = true;
        } else if (ID_MILLSTONE.equals(machineId)) {
            ItemStack input = takeFirstItem(remaining);
            if (input.isEmpty()) {
                rememberStatus("missing_item_input");
                return null;
            }
            actions.add(new ItemSupplyAction(machinePos, input));
            collectUnexpectedOutputs = true;
        } else {
            ItemStack input = takeFirstItem(remaining);
            if (input.isEmpty()) {
                rememberStatus("missing_item_input");
                return null;
            }
            actions.add(new ItemSupplyAction(machinePos, input));
        }

        if (!remaining.isEmpty()) {
            rememberStatus("unused_inputs");
            return null;
        }
        return new Plan(machinePos, outputPos, role, actions, outputs, refillInputs, collectUnexpectedOutputs);
    }

    private boolean tickJobs() {
        if (activeJobs.isEmpty() || level == null || level.isClientSide()) {
            return false;
        }
        boolean didWork = false;
        for (int i = 0; i < activeJobs.size(); i++) {
            KineticJob job = activeJobs.get(i);
            job.ticks++;
            job.roundTicks++;
            didWork |= collectOutputs(job);
            if (job.remaining <= 0) {
                activeJobs.remove(i--);
                lastStatusKey = activeJobs.isEmpty() ? "completed" : "working";
                didWork = true;
                continue;
            }
            if (isCraftingJobNoLongerWaiting(job)) {
                job.statusKey = "cancelled";
                didWork = returnPendingDispatches(job) || didWork;
                if (!job.hasPendingDispatches()) {
                    activeJobs.remove(i--);
                    lastStatusKey = activeJobs.isEmpty() ? "cancelled" : "working";
                    didWork = true;
                }
                continue;
            }
            if (job.returningPendingDispatches()) {
                didWork = returnPendingDispatches(job) || didWork;
                if (!job.hasPendingDispatches()) {
                    activeJobs.remove(i--);
                    lastStatusKey = activeJobs.isEmpty() ? job.statusKey : "working";
                    didWork = true;
                }
                continue;
            } else if (job.hasPendingDispatches()) {
                didWork = dispatchPendingPattern(job) || didWork;
            } else if (job.waitingForRefill()) {
                didWork = retryRefillRound(job) || didWork;
            } else if (job.roundTicks > emptyOutputRefillTimeoutTicks()) {
                didWork = startRefillRound(job, job.roundHasOutput ? "output_timeout" : "empty_output") || didWork;
            }
            if (job.ticks > MAX_JOB_TICKS) {
                CreatePackage.LOGGER.warn("[Kinetic Pattern Provider @ {}] timed out waiting for {} x{} from {} at {}",
                        getBlockPos(), job.primaryOutput, job.remaining, job.machineRole, job.machinePos);
                job.statusKey = "timeout";
                didWork = returnPendingDispatches(job) || didWork;
                if (!job.hasPendingDispatches()) {
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
            job.pendingDispatchTicks--;
            return false;
        }
        KineticJob.PendingDispatch pending = job.pendingDispatches.get(0);
        Plan plan = createPlanAt(job.machinePos, job.primaryOutput, pending.outputAmount(),
                job.patternOutputs, pending.inputs());
        if (plan == null
                || !Objects.equals(plan.outputPos(), job.outputPos)
                || !Objects.equals(plan.machineRole(), job.machineRole)
                || plan.collectUnexpectedOutputs() != job.collectUnexpectedOutputs) {
            job.pendingDispatchTicks = PENDING_DISPATCH_RETRY_TICKS;
            rememberJobStatus(job, "waiting_target_space");
            return false;
        }
        if (!plan.simulate()) {
            job.pendingDispatchTicks = PENDING_DISPATCH_RETRY_TICKS;
            rememberJobStatus(job, "waiting_target_space");
            return false;
        }
        if (!plan.execute()) {
            job.pendingDispatchTicks = PENDING_DISPATCH_RETRY_TICKS;
            rememberJobStatus(job, "execute_failed");
            return false;
        }

        job.pendingDispatches.remove(0);
        job.pendingDispatchTicks = 0;
        job.roundsStarted++;
        job.roundTicks = 0;
        job.roundHasOutput = false;
        rememberJobStatus(job, "working");
        saveAndSync();
        wakeTicker();
        return true;
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
        IItemHandler handler = itemHandler(job.outputPos);
        if (handler == null) {
            rememberStatus("missing_output");
            return false;
        }
        boolean didWork = false;
        long primaryCollected = 0;
        long otherCollected = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack available = handler.extractItem(slot, 64, true);
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
            ItemStack extracted = handler.extractItem(slot, (int) Math.min(accepted, Integer.MAX_VALUE), false);
            if (extracted.isEmpty()) {
                continue;
            }
            AEItemKey extractedKey = AEItemKey.of(extracted);
            if (extractedKey == null) {
                continue;
            }
            long inserted = insertIntoNetwork(extractedKey, extracted.getCount(), Actionable.MODULATE);
            if (inserted > 0) {
                job.roundHasOutput = true;
                job.roundTicks = 0;
                if (extractedKey.equals(job.primaryOutput)) {
                    job.remaining = Math.max(0, job.remaining - inserted);
                    primaryCollected += inserted;
                } else {
                    otherCollected += inserted;
                }
                didWork = true;
            }
        }
        if (job.remaining > 0 && job.queuedPatterns <= 1 && primaryCollected == 0 && otherCollected > 0) {
            didWork = startRefillRound(job, "secondary_output") || didWork;
        }
        if (didWork) {
            saveAndSync();
        }
        return didWork;
    }

    private boolean isExpectedOutput(KineticJob job, AEItemKey key) {
        if (key.equals(job.primaryOutput)) {
            return true;
        }
        for (GenericStack output : job.patternOutputs) {
            if (output.what().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean retryRefillRound(KineticJob job) {
        if (job.roundTicks % REFILL_RETRY_TICKS != 0) {
            return false;
        }
        return startRefillRound(job, job.statusKey);
    }

    private boolean startRefillRound(KineticJob job, String reason) {
        if (job.refillInputs.isEmpty()) {
            rememberJobStatus(job, "refill_unavailable");
            return false;
        }

        Plan refill = createPlanAt(job.machinePos, job.primaryOutput, job.remaining, job.patternOutputs,
                job.refillInputs);
        if (refill == null) {
            return false;
        }
        if (!Objects.equals(refill.outputPos(), job.outputPos)) {
            rememberJobStatus(job, "missing_output");
            return false;
        }

        for (GenericStack input : job.refillInputs) {
            long available = extractFromNetwork(input.what(), input.amount(), Actionable.SIMULATE);
            if (available < input.amount()) {
                rememberJobStatus(job, "waiting_refill_inputs");
                return false;
            }
        }
        if (!refill.simulate()) {
            rememberJobStatus(job, "refill_target_full");
            return false;
        }

        for (GenericStack input : job.refillInputs) {
            long extracted = extractFromNetwork(input.what(), input.amount(), Actionable.MODULATE);
            if (extracted < input.amount()) {
                if (extracted > 0) {
                    insertIntoNetwork(input.what(), extracted, Actionable.MODULATE);
                }
                rememberJobStatus(job, "waiting_refill_inputs");
                return false;
            }
        }
        if (!refill.execute()) {
            for (GenericStack input : job.refillInputs) {
                insertIntoNetwork(input.what(), input.amount(), Actionable.MODULATE);
            }
            rememberJobStatus(job, "refill_target_full");
            return false;
        }

        job.roundsStarted++;
        job.roundTicks = 0;
        job.roundHasOutput = false;
        rememberJobStatus(job, "working");
        CreatePackage.LOGGER.info("[Kinetic Pattern Provider @ {}] refilled {} after {} (round {})",
                getBlockPos(), job.primaryOutput, reason, job.roundsStarted);
        saveAndSync();
        wakeTicker();
        return true;
    }

    @Nullable
    private Plan createPlanAt(BlockPos machinePos, AEItemKey primaryOutput, long outputAmount,
            List<GenericStack> patternOutputs, List<GenericStack> inputs) {
        if (level == null || !level.isLoaded(machinePos)) {
            rememberStatus("missing_machine");
            return null;
        }
        return createPlanForMachine(machinePos, primaryOutput, outputAmount, patternOutputs, copyStacks(inputs));
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
        return direction == null ? null : getBlockPos().relative(direction);
    }

    @Nullable
    private Direction targetDirection() {
        return getPushDirection().getDirection();
    }

    private PushDirection getPushDirection() {
        return getBlockState().getValue(KineticPatternProviderBlock.PUSH_DIRECTION);
    }

    private BlockPos deployerItemTarget(BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            return machinePos.below(2);
        }
        BlockState state = level.getBlockState(machinePos);
        if (state.hasProperty(DirectionalKineticBlock.FACING)) {
            return machinePos.relative(state.getValue(DirectionalKineticBlock.FACING), 2);
        }
        return machinePos.below(2);
    }

    private BlockPos spoutItemTarget(BlockPos machinePos) {
        return machinePos.below(2);
    }

    private BlockPos pressItemTarget(BlockPos machinePos) {
        return machinePos.below(2);
    }

    private BlockPos basinOperatingTarget(BlockPos machinePos) {
        return machinePos.below(2);
    }

    @Nullable
    private BlockPos crushingOutputTarget(BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            return null;
        }
        BlockState state = level.getBlockState(machinePos);
        if (!state.hasProperty(DirectionalBlock.FACING)) {
            return null;
        }
        Direction facing = state.getValue(DirectionalBlock.FACING);
        if (facing == Direction.UP) {
            return null;
        }
        return machinePos.below().relative(facing, facing.getAxis() == Direction.Axis.Y ? 0 : 1);
    }

    private boolean canRecoverCrushingOutput(BlockPos machinePos, BlockPos outputPos) {
        if (level == null || !level.isLoaded(machinePos) || !level.isLoaded(outputPos)) {
            return false;
        }
        BlockState state = level.getBlockState(machinePos);
        if (!state.hasProperty(DirectionalBlock.FACING)) {
            return false;
        }
        Direction facing = state.getValue(DirectionalBlock.FACING);
        DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, outputPos,
                DirectBeltInputBehaviour.TYPE);
        return behaviour != null && behaviour.canInsertFromSide(facing) && itemHandler(outputPos) != null;
    }

    @Nullable
    private Direction sawOutputDirection(BlockPos machinePos) {
        if (level == null || !level.isLoaded(machinePos)) {
            rememberStatus("missing_machine");
            return null;
        }
        BlockState state = level.getBlockState(machinePos);
        if (!state.hasProperty(SawBlock.FACING) || state.getValue(SawBlock.FACING) != Direction.UP) {
            rememberStatus("saw_must_face_up");
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(machinePos);
        if (!(blockEntity instanceof KineticBlockEntity kinetic) || kinetic.getSpeed() == 0) {
            rememberStatus("saw_not_powered");
            return null;
        }
        if (!state.hasProperty(SawBlock.AXIS_ALONG_FIRST_COORDINATE)) {
            rememberStatus("unsupported_machine");
            return null;
        }

        boolean alongX = !state.getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE);
        int offset = kinetic.getSpeed() < 0 ? -1 : 1;
        if (alongX) {
            return offset > 0 ? Direction.EAST : Direction.WEST;
        }
        return offset > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private boolean canRecoverSawOutput(BlockPos outputPos, Direction outputDirection) {
        if (level == null || !level.isLoaded(outputPos)) {
            return false;
        }
        DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, outputPos,
                DirectBeltInputBehaviour.TYPE);
        return behaviour != null && behaviour.canInsertFromSide(outputDirection) && itemHandler(outputPos) != null;
    }

    private boolean setSawFilter(BlockPos sawPos, ItemStack filterStack) {
        if (level == null || !level.isLoaded(sawPos) || filterStack.isEmpty()) {
            return false;
        }
        FilteringBehaviour filtering = BlockEntityBehaviour.get(level, sawPos, FilteringBehaviour.TYPE);
        return filtering != null && filtering.setFilter(filterStack);
    }

    private boolean canAccessSawFilter(BlockPos sawPos, ItemStack filterStack) {
        return level != null && level.isLoaded(sawPos) && !filterStack.isEmpty()
                && BlockEntityBehaviour.get(level, sawPos, FilteringBehaviour.TYPE) != null;
    }

    private String roleKey(ResourceLocation id) {
        if (ID_DEPLOYER.equals(id)) {
            return "deployer";
        }
        if (ID_SPOUT.equals(id)) {
            return "spout";
        }
        if (ID_MECHANICAL_PRESS.equals(id)) {
            return "press";
        }
        if (ID_MECHANICAL_MIXER.equals(id)) {
            return "mixer";
        }
        if (ID_CRUSHING_CONTROLLER.equals(id)) {
            return "crushing";
        }
        if (ID_MECHANICAL_SAW.equals(id)) {
            return "saw";
        }
        if (ID_MILLSTONE.equals(id)) {
            return "millstone";
        }
        return "generic";
    }

    private boolean isBasin(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return ID_BASIN.equals(id);
    }

    @Nullable
    private IItemHandler itemHandler(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction direction : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    @Nullable
    private IFluidHandler fluidHandler(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
    }

    private ItemStack insertItem(BlockPos target, ItemStack stack, boolean simulate) {
        IItemHandler handler = itemHandler(target);
        if (handler == null) {
            return stack.copy();
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private boolean addBasinInputs(List<SupplyAction> actions, List<GenericStack> remaining, BlockPos basinPos) {
        boolean supplied = false;
        for (int i = 0; i < remaining.size();) {
            GenericStack input = remaining.get(i);
            if (input.what() instanceof AEItemKey itemKey && input.amount() > 0) {
                actions.add(new ItemSupplyAction(basinPos, itemKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE))));
                supplied = true;
                remaining.remove(i);
                continue;
            }
            if (input.what() instanceof AEFluidKey fluidKey && input.amount() > 0) {
                actions.add(new FluidSupplyAction(basinPos, fluidKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE))));
                supplied = true;
                remaining.remove(i);
                continue;
            }
            i++;
        }
        if (!supplied) {
            rememberStatus("missing_basin_input");
            return false;
        }
        return true;
    }

    private long insertIntoNetwork(AEKey what, long amount, Actionable mode) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        return grid.getStorageService().getInventory().insert(what, amount, mode, actionSource);
    }

    private long extractFromNetwork(AEKey what, long amount, Actionable mode) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        return grid.getStorageService().getInventory().extract(what, amount, mode, actionSource);
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

    private void saveAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            markForUpdate();
        }
    }

    private void wakeTicker() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private void onUpgradesChanged() {
        saveAndSync();
        ICraftingProvider.requestUpdate(getMainNode());
        wakeTicker();
    }

    private void onLinksChanged() {
        saveAndSync();
        ICraftingProvider.requestUpdate(getMainNode());
        wakeTicker();
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
        return new ItemStack(ModItems.KINETIC_PATTERN_PROVIDER.get());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.header",
                ModItems.KINETIC_PATTERN_PROVIDER.get().getDescription()).withStyle(ChatFormatting.GOLD));
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
        return true;
    }

    private static Component boolText(boolean value) {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor."
                + (value ? "yes" : "no")).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static Component machineRoleName(String role) {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.role." + role);
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
                && left.ticks == right.ticks;
    }

    private static List<GenericStack> flattenInputs(KeyCounter[] inputs) {
        List<GenericStack> result = new ArrayList<>();
        for (KeyCounter counter : inputs) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0) {
                    addStack(result, new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        return result;
    }

    private static void addStack(List<GenericStack> stacks, GenericStack stack) {
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack existing = stacks.get(i);
            if (existing.what().equals(stack.what())) {
                stacks.set(i, new GenericStack(existing.what(), safeAdd(existing.amount(), stack.amount())));
                return;
            }
        }
        stacks.add(stack);
    }

    private static long safeAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static List<GenericStack> copyStacks(List<GenericStack> stacks) {
        List<GenericStack> result = new ArrayList<>();
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                addStack(result, new GenericStack(stack.what(), stack.amount()));
            }
        }
        return result;
    }

    private static ItemStack takeFirstItem(List<GenericStack> inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEItemKey itemKey && input.amount() > 0) {
                int amount = (int) Math.min(input.amount(), Integer.MAX_VALUE);
                inputs.remove(i);
                return itemKey.toStack(amount);
            }
        }
        return ItemStack.EMPTY;
    }

    private static FluidStack takeFirstFluid(List<GenericStack> inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEFluidKey fluidKey && input.amount() > 0) {
                int amount = (int) Math.min(input.amount(), Integer.MAX_VALUE);
                inputs.remove(i);
                return fluidKey.toStack(amount);
            }
        }
        return FluidStack.EMPTY;
    }

    private static void consumeInputs(KeyCounter[] inputs) {
        for (KeyCounter counter : inputs) {
            counter.clear();
        }
    }

    private static int emptyOutputRefillTimeoutTicks() {
        try {
            return Config.EMPTY_OUTPUT_REFILL_TIMEOUT_TICKS.get();
        } catch (IllegalStateException ignored) {
            return ROUND_OUTPUT_TIMEOUT_TICKS;
        }
    }

    public record AeStatus(boolean nodePresent, boolean powered, boolean channel, boolean active) {
    }

    public static final class KineticJob {
        private static final String NBT_PRIMARY = "primary";
        private static final String NBT_REMAINING = "remaining";
        private static final String NBT_MACHINE = "machine";
        private static final String NBT_OUTPUT = "output";
        private static final String NBT_ROLE = "role";
        private static final String NBT_TICKS = "ticks";
        private static final String NBT_ROUND_TICKS = "roundTicks";
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

        private final AEItemKey primaryOutput;
        private AEItemKey patternDefinition;
        private long remaining;
        private final BlockPos machinePos;
        private final BlockPos outputPos;
        private final String machineRole;
        private List<GenericStack> patternOutputs;
        private List<GenericStack> refillInputs;
        private final boolean collectUnexpectedOutputs;
        private int ticks;
        private int roundTicks;
        private int roundsStarted;
        private boolean roundHasOutput;
        private int queuedPatterns = 1;
        private String statusKey = "working";
        private final List<PendingDispatch> pendingDispatches = new ArrayList<>();
        private int pendingDispatchTicks;

        private KineticJob(AEItemKey primaryOutput, AEItemKey patternDefinition, long remaining, BlockPos machinePos,
                BlockPos outputPos, String machineRole, List<GenericStack> patternOutputs,
                List<GenericStack> refillInputs, boolean collectUnexpectedOutputs) {
            this.primaryOutput = primaryOutput;
            this.patternDefinition = patternDefinition;
            this.remaining = remaining;
            this.machinePos = machinePos.immutable();
            this.outputPos = outputPos.immutable();
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

        private CompoundTag write(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put(NBT_PRIMARY, primaryOutput.toTag(registries));
            tag.put(NBT_DEFINITION, patternDefinition.toTag(registries));
            tag.putLong(NBT_REMAINING, remaining);
            tag.putLong(NBT_MACHINE, machinePos.asLong());
            tag.putLong(NBT_OUTPUT, outputPos.asLong());
            tag.putString(NBT_ROLE, machineRole);
            tag.putInt(NBT_TICKS, ticks);
            tag.putInt(NBT_ROUND_TICKS, roundTicks);
            tag.putInt(NBT_ROUNDS_STARTED, roundsStarted);
            tag.putInt("queuedPatterns", queuedPatterns);
            tag.putBoolean(NBT_ROUND_HAS_OUTPUT, roundHasOutput);
            tag.putString(NBT_JOB_STATUS, statusKey);
            tag.putBoolean(NBT_COLLECT_UNEXPECTED_OUTPUTS, collectUnexpectedOutputs);
            tag.put(NBT_PATTERN_OUTPUTS, writeStackList(registries, patternOutputs));
            tag.put(NBT_REFILL_INPUTS, writeStackList(registries, refillInputs));
            tag.putInt(NBT_PENDING_DISPATCH_TICKS, pendingDispatchTicks);
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
            AEItemKey primary = AEItemKey.fromTag(registries, tag.getCompound(NBT_PRIMARY));
            if (primary == null) {
                return null;
            }
            List<GenericStack> outputs = readStackList(tag, registries, NBT_PATTERN_OUTPUTS);
            List<GenericStack> refillInputs = readStackList(tag, registries, NBT_REFILL_INPUTS);
            AEItemKey definition = tag.contains(NBT_DEFINITION)
                    ? AEItemKey.fromTag(registries, tag.getCompound(NBT_DEFINITION))
                    : primary;
            if (definition == null) {
                definition = primary;
            }
            KineticJob job = new KineticJob(primary, definition, tag.getLong(NBT_REMAINING),
                    BlockPos.of(tag.getLong(NBT_MACHINE)),
                    BlockPos.of(tag.getLong(NBT_OUTPUT)),
                    tag.getString(NBT_ROLE),
                    outputs,
                    refillInputs,
                    tag.getBoolean(NBT_COLLECT_UNEXPECTED_OUTPUTS));
            job.ticks = tag.getInt(NBT_TICKS);
            job.roundTicks = tag.getInt(NBT_ROUND_TICKS);
            job.roundsStarted = tag.getInt(NBT_ROUNDS_STARTED);
            job.queuedPatterns = tag.contains("queuedPatterns") ? Math.max(1, tag.getInt("queuedPatterns")) : 1;
            job.roundHasOutput = tag.getBoolean(NBT_ROUND_HAS_OUTPUT);
            job.statusKey = tag.contains(NBT_JOB_STATUS) ? tag.getString(NBT_JOB_STATUS) : "working";
            job.pendingDispatchTicks = tag.getInt(NBT_PENDING_DISPATCH_TICKS);
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

    private record Plan(BlockPos machinePos, BlockPos outputPos, String machineRole, List<SupplyAction> actions,
            List<GenericStack> patternOutputs, List<GenericStack> refillInputs, boolean collectUnexpectedOutputs) {
        boolean simulate() {
            return actions.stream().allMatch(action -> action.perform(true));
        }

        boolean execute() {
            return actions.stream().allMatch(action -> action.perform(false));
        }
    }

    private record DispatchTarget(@Nullable KineticJob job, Plan plan, boolean allowQueue) {
    }

    private interface SupplyAction {
        boolean perform(boolean simulate);
    }

    private final class ItemSupplyAction implements SupplyAction {
        private final BlockPos target;
        private final ItemStack stack;

        private ItemSupplyAction(BlockPos target, ItemStack stack) {
            this.target = target;
            this.stack = stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            return insertItem(target, stack, simulate).isEmpty();
        }
    }

    private final class SawFilterAction implements SupplyAction {
        private final BlockPos target;
        private final ItemStack filterStack;

        private SawFilterAction(BlockPos target, ItemStack filterStack) {
            this.target = target;
            this.filterStack = filterStack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            if (level == null || !level.isLoaded(target) || filterStack.isEmpty()) {
                return false;
            }
            FilteringBehaviour filtering = BlockEntityBehaviour.get(level, target, FilteringBehaviour.TYPE);
            if (filtering == null) {
                return false;
            }
            return simulate || setSawFilter(target, filterStack);
        }
    }

    private final class FluidSupplyAction implements SupplyAction {
        private final BlockPos target;
        private final FluidStack stack;

        private FluidSupplyAction(BlockPos target, FluidStack stack) {
            this.target = target;
            this.stack = stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            IFluidHandler handler = fluidHandler(target);
            if (handler == null) {
                return false;
            }
            var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
            return handler.fill(stack.copy(), action) >= stack.getAmount();
        }
    }

    private final class InternalPatternProviderLogic extends PatternProviderLogic {
        private InternalPatternProviderLogic() {
            super(getMainNode(), KineticPatternProviderBlockEntity.this);
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
