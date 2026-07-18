package com.lhy.createpackage.content.distributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.crafting.ActionList;
import com.lhy.createpackage.content.crafting.AssemblyPlanner;
import com.lhy.createpackage.content.crafting.FailureDiagnostics;
import com.lhy.createpackage.content.crafting.InventoryAccess;
import com.lhy.createpackage.content.crafting.NetworkIO;
import com.lhy.createpackage.content.crafting.PatternStacks;
import com.lhy.createpackage.content.crafting.SupplyAction;
import com.lhy.createpackage.content.crafting.SupplyActions;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.content.pattern.RoutedMechanicalPackagePatternDetails;
import com.lhy.createpackage.content.recipe.AssemblyPlan;
import com.lhy.createpackage.registry.ModBlockEntities;
import com.lhy.createpackage.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;

/**
 * The package distributor block entity.
 *
 * <p>It joins the AE2 grid and exposes {@link ICraftingMachine} so that an adjacent pattern
 * provider treats it as a single crafting machine. When AE2 pushes a pattern, it hands over the
 * full set of inputs at once (all-or-nothing). This block then distributes those inputs across the
 * linked Create sequenced-assembly machines and returns the result to the network.
 *
 * <p>This is the minimal connectivity milestone: it joins the grid and accepts pushes, logging
 * what it receives. Distribution and result recovery are added in later milestones.
 */
public class PackageDistributorBlockEntity extends AENetworkedBlockEntity
        implements ICraftingMachine, IActionHost, IHaveGoggleInformation {

    private static final String NBT_LINKED_MACHINES = "linkedMachines";
    private static final String NBT_JOB = "job";
    private static final String NBT_JOBS = "jobs";
    private static final String NBT_LAST_STATUS = "lastStatus";
    private static final int DEFAULT_MAX_JOB_TICKS = 20 * 60 * 10;
    private static final int DEFAULT_ROUND_OUTPUT_TIMEOUT_TICKS = 20 * 60;
    private static final int DEFAULT_REFILL_RETRY_TICKS = 20 * 5;
    private static final int MAX_TOOLTIP_LINKS = 12;
    private static final Map<UUID, Long> ENCODER_LOGIN_NOTICE_GENERATIONS = new ConcurrentHashMap<>();

    /**
     * Ordered list of linked Create machines (depot / deployers / spouts), in the physical sequence
     * the player linked them. The order maps directly onto the sequenced-assembly recipe steps.
     * Positions are absolute and share this block entity's dimension.
     */
    private final List<BlockPos> linkedMachines = new ArrayList<>();
    private final MachineSource actionSource = new MachineSource(this);

    private final List<DistributionJob> activeJobs = new ArrayList<>();
    private final FailureDiagnostics diagnostics = new FailureDiagnostics();
    private String lastStatusKey = "ready";
    private boolean aeNodePresent;
    private boolean aePowered;
    private boolean aeChannel;
    private boolean aeActive;
    private final Item visualItem;
    private final String descriptionId;

    public PackageDistributorBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.PACKAGE_DISTRIBUTOR.get(), ModItems.PACKAGE_DISTRIBUTOR.get(),
                "block." + CreatePackage.MODID + ".package_distributor", pos, blockState, true);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ENCODER_LOGIN_NOTICE_GENERATIONS.merge(player.getUUID(), 1L, Long::sum);
        }
    }

    protected PackageDistributorBlockEntity(BlockEntityType<?> type, Item visualItem, String descriptionId,
            BlockPos pos, BlockState blockState, boolean installTicker) {
        super(type, pos, blockState);
        this.visualItem = visualItem;
        this.descriptionId = descriptionId;

        // Require a channel like most AE2 machines; small idle draw to mark it as an active device.
        this.getMainNode()
                .setVisualRepresentation(visualItem)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0);
        if (installTicker) {
            this.getMainNode().addService(IGridTickable.class, new DistributorTicker());
        }
    }

    // === Linked machine management ===

    /** Immutable view of the linked machines, in link order. */
    public List<BlockPos> getLinkedMachines() {
        return Collections.unmodifiableList(linkedMachines);
    }

    public boolean isLinked(BlockPos pos) {
        return linkedMachines.contains(pos);
    }

    public boolean hasActiveJob() {
        return !activeJobs.isEmpty();
    }

    public String getStatusKey() {
        return visibleStatusKey();
    }

    public long getPrimaryRemaining() {
        DistributionJob job = firstJob();
        return job == null ? 0 : job.primaryRemaining;
    }

    @Nullable
    public ResourceLocation getCurrentRecipeId() {
        DistributionJob job = firstJob();
        return job == null ? null : job.recipeId;
    }

    public Component getCurrentJobName() {
        DistributionJob job = firstJob();
        return job == null ? CommonComponents.EMPTY : job.primaryOutput.getDisplayName();
    }

    public long getRoundsStarted() {
        DistributionJob job = firstJob();
        return job == null ? 0 : job.roundsStarted;
    }

    public int getActiveJobCount() {
        return activeJobs.size();
    }

    public List<Component> getVisibleJobLines() {
        if (activeJobs.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < activeJobs.size(); i++) {
            DistributionJob job = activeJobs.get(i);
            lines.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.job_entry",
                    i + 1,
                    job.primaryOutput.getDisplayName(),
                    job.primaryRemaining,
                    job.roundsStarted,
                    job.inputPos.getX(), job.inputPos.getY(), job.inputPos.getZ(),
                    job.outputPos.getX(), job.outputPos.getY(), job.outputPos.getZ()));
        }
        return lines;
    }

    public List<Component> getMissingRefillLines() {
        if (activeJobs.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        for (DistributionJob job : activeJobs) {
            if (job.missingRefillInput != null) {
                lines.add(missingRefillLine(job.missingRefillInput));
            }
        }
        return lines;
    }

    private static Component missingRefillLine(GenericStack missing) {
        return Component.translatable("tooltip." + CreatePackage.MODID
                        + ".package_distributor.missing_refill_input",
                stackAmount(missing), missing.what().getDisplayName())
                .withStyle(ChatFormatting.YELLOW);
    }

    private static Component stackAmount(GenericStack stack) {
        return Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL));
    }

    public int getMaxActiveJobs() {
        return 1;
    }

    public boolean canAcceptMoreJobs() {
        return activeJobs.size() < getMaxActiveJobs();
    }

    public Component getLinkedBlockName(BlockPos pos) {
        return linkedBlockName(pos);
    }

    public ItemStack getLinkedBlockIcon(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        Item item = level.getBlockState(pos).getBlock().asItem();
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public String getLinkedRoleKey(BlockPos pos) {
        return roleKey(pos);
    }

    public boolean usesStoredMachineLinks() {
        return true;
    }

    public int getMechanicalPackagePatternCount() {
        return 0;
    }

    /**
     * Links a machine. Re-linking an already linked machine is a no-op.
     *
     * @return true if it was newly added, false if it was already linked.
     */
    public boolean linkMachine(BlockPos pos) {
        if (linkedMachines.contains(pos)) {
            return false;
        }
        linkedMachines.add(pos.immutable());
        clearLastStatus();
        saveAndSync();
        return true;
    }

    /**
     * Unlinks a machine.
     *
     * @return true if it was linked and got removed.
     */
    public boolean unlinkMachine(BlockPos pos) {
        boolean removed = linkedMachines.remove(pos);
        if (removed) {
            clearLastStatus();
            saveAndSync();
        }
        return removed;
    }

    /**
     * Replaces the linked machine at {@code index}. No-op when the index is invalid, the position is
     * already present at another index, or the value is unchanged.
     */
    public boolean replaceLinkedMachine(int index, BlockPos pos) {
        if (index < 0 || index >= linkedMachines.size()) {
            return false;
        }
        BlockPos immutable = pos.immutable();
        int existing = linkedMachines.indexOf(immutable);
        if (existing >= 0 && existing != index) {
            return false;
        }
        if (linkedMachines.get(index).equals(immutable)) {
            return false;
        }
        linkedMachines.set(index, immutable);
        clearLastStatus();
        saveAndSync();
        return true;
    }

    /** Clears all links. Returns the number of machines that were unlinked. */
    public int clearLinks() {
        int count = linkedMachines.size();
        if (count > 0) {
            linkedMachines.clear();
            clearLastStatus();
            saveAndSync();
        }
        return count;
    }

    public boolean replaceAutoRoute(List<BlockPos> positions) {
        List<BlockPos> normalized = positions.stream().map(BlockPos::immutable).toList();
        if (linkedMachines.equals(normalized)) {
            return false;
        }
        linkedMachines.clear();
        linkedMachines.addAll(normalized);
        clearLastStatus();
        saveAndSync();
        return true;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        long[] packed = new long[linkedMachines.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = linkedMachines.get(i).asLong();
        }
        data.putLongArray(NBT_LINKED_MACHINES, packed);
        if (!activeJobs.isEmpty()) {
            ListTag jobs = new ListTag();
            for (DistributionJob job : activeJobs) {
                jobs.add(job.write(registries));
            }
            data.put(NBT_JOBS, jobs);
            data.put(NBT_JOB, activeJobs.get(0).write(registries));
        }
        data.putString(NBT_LAST_STATUS, lastStatusKey);
        diagnostics.write(data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        linkedMachines.clear();
        for (long packed : data.getLongArray(NBT_LINKED_MACHINES)) {
            linkedMachines.add(BlockPos.of(packed));
        }
        activeJobs.clear();
        if (data.contains(NBT_JOBS, Tag.TAG_LIST)) {
            ListTag jobs = data.getList(NBT_JOBS, Tag.TAG_COMPOUND);
            for (int i = 0; i < jobs.size(); i++) {
                DistributionJob job = DistributionJob.read(jobs.getCompound(i), registries);
                if (job != null) {
                    activeJobs.add(job);
                }
            }
        } else if (data.contains(NBT_JOB)) {
            DistributionJob job = DistributionJob.read(data.getCompound(NBT_JOB), registries);
            if (job != null) {
                activeJobs.add(job);
            }
        }
        lastStatusKey = data.contains(NBT_LAST_STATUS) ? data.getString(NBT_LAST_STATUS) : "ready";
        diagnostics.read(data);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        AeStatus aeStatus = currentAeStatus();
        data.writeBoolean(aeStatus.nodePresent());
        data.writeBoolean(aeStatus.powered());
        data.writeBoolean(aeStatus.channel());
        data.writeBoolean(aeStatus.active());
        data.writeVarInt(linkedMachines.size());
        for (BlockPos pos : linkedMachines) {
            data.writeBlockPos(pos);
        }
        data.writeUtf(lastStatusKey);
        data.writeVarInt(activeJobs.size());
        for (DistributionJob job : activeJobs) {
            data.writeResourceLocation(job.recipeId);
            job.primaryOutput.writeToPacket(data);
            data.writeVarLong(job.primaryRemaining);
            data.writeBlockPos(job.inputPos);
            data.writeBlockPos(job.outputPos);
            job.transitionalItem.writeToPacket(data);
            data.writeVarInt(job.sequenceLength);
            data.writeVarInt(job.loops);
            data.writeVarInt(job.route.size());
            for (BlockPos pos : job.route) {
                data.writeBlockPos(pos);
            }
            data.writeVarInt(job.ticks);
            data.writeVarInt(job.roundTicks);
            data.writeVarLong(job.roundsStarted);
            data.writeBoolean(job.roundHasOutput);
            data.writeBoolean(job.awaitingFinalOutput);
            data.writeUtf(job.statusKey);
            data.writeBoolean(job.missingRefillInput != null);
            if (job.missingRefillInput != null) {
                GenericStack.writeBuffer(job.missingRefillInput, data);
            }
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        boolean streamedAeNodePresent = data.readBoolean();
        boolean streamedAePowered = data.readBoolean();
        boolean streamedAeChannel = data.readBoolean();
        boolean streamedAeActive = data.readBoolean();
        if (aeNodePresent != streamedAeNodePresent || aePowered != streamedAePowered
                || aeChannel != streamedAeChannel || aeActive != streamedAeActive) {
            aeNodePresent = streamedAeNodePresent;
            aePowered = streamedAePowered;
            aeChannel = streamedAeChannel;
            aeActive = streamedAeActive;
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

        List<DistributionJob> streamedJobs = new ArrayList<>();
        int jobCount = data.readVarInt();
        for (int jobIndex = 0; jobIndex < jobCount; jobIndex++) {
            ResourceLocation recipeId = data.readResourceLocation();
            AEItemKey primaryOutput = AEItemKey.fromPacket(data);
            long primaryRemaining = data.readVarLong();
            BlockPos inputPos = data.readBlockPos();
            BlockPos outputPos = data.readBlockPos();
            AEItemKey transitionalItem = AEItemKey.fromPacket(data);
            int sequenceLength = data.readVarInt();
            int loops = data.readVarInt();
            List<BlockPos> route = new ArrayList<>();
            int routeSize = data.readVarInt();
            for (int i = 0; i < routeSize; i++) {
                route.add(data.readBlockPos());
            }
            DistributionJob streamedJob = new DistributionJob(recipeId, primaryOutput, primaryRemaining, inputPos, outputPos,
                    transitionalItem, sequenceLength, loops, List.of(), List.of(), null, route);
            streamedJob.ticks = data.readVarInt();
            streamedJob.roundTicks = data.readVarInt();
            streamedJob.roundsStarted = data.readVarLong();
            streamedJob.roundHasOutput = data.readBoolean();
            streamedJob.awaitingFinalOutput = data.readBoolean();
            streamedJob.statusKey = data.readUtf();
            if (data.readBoolean()) {
                streamedJob.missingRefillInput = GenericStack.readBuffer(data);
            }
            streamedJobs.add(streamedJob);
        }
        if (!sameJobs(activeJobs, streamedJobs)) {
            activeJobs.clear();
            activeJobs.addAll(streamedJobs);
            changed = true;
        }

        return changed;
    }

    // === ICraftingMachine ===

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(
                AEItemKey.of(visualItem),
                Component.translatable(descriptionId),
                java.util.List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        return pushPattern(patternDetails, inputs, ejectionDirection, linkedMachines);
    }

    protected boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection,
            List<BlockPos> route) {
        if (!canAcceptMoreJobs()) {
            rememberStatus("busy");
            return false;
        }

        if (level == null || level.isClientSide()) {
            return false;
        }

        if (!getMainNode().isActive()) {
            rememberStatus("ae_inactive");
            return false;
        }

        if (routeOverlapsActiveJob(route)) {
            rememberStatus("busy");
            return false;
        }

        NetworkIO network = networkIO();
        AssemblyPlanner.Plan context = AssemblyPlanner.create(level, network, patternDetails, inputs, route);
        if (!context.isSuccess()) {
            rememberStatus(context.statusKey());
            rememberFailure(context.statusKey(), context.failureDetail(), route, -1);
            if (context.failureDetail() != null && Config.debugLoggingEnabled()) {
                CreatePackage.LOGGER.warn("[Distributor @ {}] plan failed status={} detail={}",
                        getBlockPos(), context.statusKey(), context.failureDetail());
            }
            return false;
        }

        if (!context.simulate()) {
            rememberStatus("simulate_failed");
            rememberFailure("simulate_failed", "simulate rejected route", route, -1);
            rememberFailureRoute(route);
            return false;
        }

        ActionList.Result executed = context.execute();
        if (!executed.success()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] simulated plan but execution failed at action #{} ({}); refusing pattern",
                    getBlockPos(), executed.failedIndex(), executed.failedAction());
            rememberStatus("execute_failed");
            rememberFailure("execute_failed",
                    "action #" + executed.failedIndex() + " " + executed.failedAction(), route, -1);
            rememberFailureRoute(route);
            return false;
        }

        List<AssemblyPlanner.PendingSupply> pending = new ArrayList<>(context.pendingSupplies());
        DistributionJob job = new DistributionJob(context.recipeId(), context.primaryOutputKey(),
                context.primaryOutputAmount(), context.inputPos(), context.outputPos(), context.transitionalItemKey(),
                context.sequenceLength(), context.loops(), context.refillInputs(), pending,
                patternEncoder(patternDetails), route);
        job.roundsStarted = 1;
        activeJobs.add(job);
        PatternStacks.consumeInputs(inputs);
        lastStatusKey = "working";
        diagnostics.clear();
        saveAndSync();
        wakeTicker();

        if (Config.debugLoggingEnabled()) {
            CreatePackage.LOGGER.info("[Distributor @ {}] accepted recipe {} waiting for {} x{} at {}",
                    getBlockPos(), context.recipeId(), context.primaryOutputKey(), context.primaryOutputAmount(),
                    context.outputPos());
        }
        return true;
    }

    @Nullable
    private static MechanicalPackagePatternData.PatternEncoder patternEncoder(IPatternDetails patternDetails) {
        if (patternDetails instanceof RoutedMechanicalPackagePatternDetails routed) {
            return routed.encoder().orElse(null);
        }
        return null;
    }

    @Override
    public boolean acceptsPlans() {
        return canAcceptMoreJobs();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (level != null && !level.isClientSide()) {
            markForUpdate();
        }
    }

    protected final boolean tickDistributorJob() {
        if (activeJobs.isEmpty() || level == null || level.isClientSide()) {
            return false;
        }

        boolean didWork = false;
        boolean finished = false;
        for (int i = 0; i < activeJobs.size(); i++) {
            DistributionJob job = activeJobs.get(i);
            if (isCraftingJobNoLongerWaiting(job)) {
                clearMissingRefillInput(job);
                returnPendingSupplies(job);
                if (Config.debugLoggingEnabled()) {
                    CreatePackage.LOGGER.info("[Distributor @ {}] cancelled recipe {} (AE no longer waiting for {})",
                            getBlockPos(), job.recipeId, job.primaryOutput);
                }
                activeJobs.remove(i--);
                lastStatusKey = activeJobs.isEmpty() ? "cancelled" : "working";
                finished = true;
                didWork = true;
                continue;
            }
            didWork = tickJob(job) || didWork;
            if (job.primaryRemaining <= 0) {
                clearMissingRefillInput(job);
                returnPendingSupplies(job);
                if (Config.debugLoggingEnabled()) CreatePackage.LOGGER.info("[Distributor @ {}] completed recipe {}", getBlockPos(), job.recipeId);
                activeJobs.remove(i--);
                lastStatusKey = activeJobs.isEmpty() ? "completed" : "working";
                finished = true;
            }
        }
        if (finished) {
            saveAndSync();
            didWork = true;
        }
        return didWork;
    }

    private boolean isCraftingJobNoLongerWaiting(DistributionJob job) {
        if (job.ticks < 20) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        return grid != null && grid.getCraftingService().getRequestedAmount(job.primaryOutput) <= 0;
    }

    private boolean tickJob(DistributionJob job) {
        job.ticks++;
        job.roundTicks++;
        boolean waitingForRefill = job.waitingForRefill();
        boolean waitingForReflow = job.waitingForReflow();
        boolean waitingForTargetSpace = job.waitingForTargetSpace();
        boolean didWork = supplyPending(job);
        didWork = collectOutput(job) || didWork;
        notifyEncoderLoginIfMissingRefillInput(job);
        if (job.primaryRemaining <= 0) {
            return true;
        }
        if (waitingForRefill) {
            didWork = retryRefillRound(job) || didWork;
        } else if (!waitingForReflow && !waitingForTargetSpace && job.pendingSupplies.isEmpty()
                && job.awaitingFinalOutput && !job.roundHasOutput
                && Config.enableProbabilisticEmptyRefill()
                && job.roundTicks > emptyOutputRefillTimeoutTicks()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] no output appeared for recipe {} after {} ticks; treating "
                    + "this round as an empty probability result", getBlockPos(), job.recipeId,
                    job.roundTicks);
            didWork = startRefillRound(job, "empty_output") || didWork;
        }
        if (job.ticks > Config.distributorMaxJobTicks()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] timed out waiting for {} x{} from recipe {}",
                    getBlockPos(), job.primaryOutput, job.primaryRemaining, job.recipeId);
            job.ticks = 0;
            job.statusKey = "timeout";
            refreshLastStatus();
            saveAndSync();
        }
        return didWork;
    }

    private boolean supplyPending(DistributionJob job) {
        if (job.pendingSupplies.isEmpty()) {
            if (job.waitingForTargetSpace()) {
                rememberJobStatus(job, "working");
            }
            return false;
        }

        boolean didWork = false;
        boolean blocked = false;
        for (int i = 0; i < job.pendingSupplies.size(); i++) {
            AssemblyPlanner.PendingSupply supply = job.pendingSupplies.get(i);
            long inserted = insertPendingSupply(supply);
            if (inserted <= 0) {
                blocked = true;
                continue;
            }
            didWork = true;
            long remaining = supply.stack().amount() - inserted;
            if (remaining <= 0) {
                job.pendingSupplies.remove(i--);
            } else {
                job.pendingSupplies.set(i, supply.withAmount(remaining));
            }
        }

        if (job.pendingSupplies.isEmpty()) {
            rememberJobStatus(job, "working");
        } else if (blocked) {
            rememberJobStatus(job, "waiting_target_space");
        }
        if (didWork) {
            setChanged();
            wakeTicker();
        }
        return didWork;
    }

    private long insertPendingSupply(AssemblyPlanner.PendingSupply supply) {
        GenericStack stack = supply.stack();
        if (stack.what() instanceof AEItemKey itemKey) {
            int amount = (int) Math.min(stack.amount(), Integer.MAX_VALUE);
            ItemStack itemStack = itemKey.toStack(amount);
            ItemStack remaining = insertItem(supply.target(), itemStack, false);
            return amount - remaining.getCount();
        }
        if (stack.what() instanceof AEFluidKey fluidKey) {
            IFluidHandler handler = fluidHandler(supply.target());
            if (handler == null) {
                return 0;
            }
            int amount = (int) Math.min(stack.amount(), Integer.MAX_VALUE);
            return handler.fill(fluidKey.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    private void returnPendingSupplies(DistributionJob job) {
        for (AssemblyPlanner.PendingSupply supply : job.pendingSupplies) {
            insertIntoNetwork(supply.stack().what(), supply.stack().amount(), Actionable.MODULATE);
        }
        job.pendingSupplies.clear();
    }

    private boolean retryRefillRound(DistributionJob job) {
        if (job.roundTicks % Config.refillRetryTicks() != 0) {
            return false;
        }
        return startRefillRound(job, job.statusKey);
    }

    private boolean collectOutput(DistributionJob job) {
        IItemHandler handler = itemHandler(job.outputPos);
        if (handler == null) {
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

            if (isCurrentTransitional(job, available)) {
                didWork = reflowTransitionalOutput(job, handler, slot, available) || didWork;
                continue;
            }

            AEItemKey key = AEItemKey.of(available);
            if (key == null) {
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
            if (inserted < extracted.getCount()) {
                CreatePackage.LOGGER.warn("[Distributor @ {}] network accepted fewer items than simulated ({} < {})",
                        getBlockPos(), inserted, extracted.getCount());
            }

            job.roundHasOutput = true;
            if (extractedKey.equals(job.primaryOutput)) {
                job.primaryRemaining -= inserted;
                primaryCollected += inserted;
                if (job.primaryRemaining < 0) {
                    job.primaryRemaining = 0;
                }
            } else {
                otherCollected += inserted;
            }
            didWork = inserted > 0 || didWork;
        }
        if (otherCollected > 0 && primaryCollected == 0 && job.primaryRemaining > 0
                && job.pendingSupplies.isEmpty()) {
            didWork = startRefillRound(job, "secondary_output") || didWork;
        }
        if (didWork) {
            saveAndSync();
        }
        return didWork;
    }

    private boolean reflowTransitionalOutput(DistributionJob job, IItemHandler outputHandler, int slot,
            ItemStack available) {
        if (!canInsertAll(job.inputPos, available)) {
            rememberJobStatus(job, "waiting_reflow_input");
            return false;
        }

        ItemStack extracted = outputHandler.extractItem(slot, available.getCount(), false);
        if (extracted.isEmpty()) {
            return false;
        }

        if (!isCurrentTransitional(job, extracted)) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] output slot changed while reflowing sequenced item for {}",
                    getBlockPos(), job.recipeId);
            return false;
        }

        boolean nextPassIsFinal = nextPassIsFinal(job, extracted);
        ItemStack remaining = insertItem(job.inputPos, extracted, false);
        if (!remaining.isEmpty()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] simulated transitional reflow but input accepted only {} "
                    + "of {}; keeping remainder for retry", getBlockPos(),
                    extracted.getCount() - remaining.getCount(), extracted.getCount());
            ItemStack leftover = insertIntoHandler(outputHandler, remaining, false);
            if (!leftover.isEmpty()) {
                AEItemKey key = AEItemKey.of(leftover);
                if (key != null) {
                    insertIntoNetwork(key, leftover.getCount(), Actionable.MODULATE);
                }
            }
            rememberJobStatus(job, "waiting_reflow_input");
            return true;
        }

        job.awaitingFinalOutput = nextPassIsFinal;
        job.roundHasOutput = false;
        job.roundTicks = 0;
        rememberJobStatus(job, "working");
        CreatePackage.LOGGER.debug("[Distributor @ {}] reflowed transitional item for recipe {} to {} (final pass: {})",
                getBlockPos(), job.recipeId, job.inputPos, nextPassIsFinal);
        return true;
    }

    private boolean isCurrentTransitional(DistributionJob job, ItemStack stack) {
        if (stack.isEmpty() || !stack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return false;
        }
        if (stack.getItem() != job.transitionalItem.getReadOnlyStack().getItem()) {
            return false;
        }
        SequencedAssemblyRecipe.SequencedAssembly assembly = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        return assembly != null && assembly.id().equals(job.recipeId);
    }

    private boolean nextPassIsFinal(DistributionJob job, ItemStack stack) {
        if (job.sequenceLength <= 0 || job.loops <= 1) {
            return true;
        }
        SequencedAssemblyRecipe.SequencedAssembly assembly = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if (assembly == null) {
            return false;
        }
        return assembly.step() >= job.sequenceLength * (job.loops - 1);
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

    @Nullable
    private NetworkIO.NetworkItemStack findNetworkItem(net.minecraft.world.item.crafting.Ingredient ingredient, long amount) {
        return networkIO().findItem(ingredient, amount);
    }

    private boolean startRefillRound(DistributionJob job, String reason) {
        if (job.refillInputs.isEmpty()) {
            rememberJobStatus(job, "refill_unavailable");
            return false;
        }

        RefillContext context = createRefillContext(job, job.refillInputs, job.route);
        if (context == null) {
            return false;
        }

        for (GenericStack input : context.inputs()) {
            long available = extractFromNetwork(input.what(), input.amount(), Actionable.SIMULATE);
            if (available < input.amount()) {
                GenericStack missing = new GenericStack(input.what(), input.amount() - available);
                if (!"waiting_refill_inputs".equals(job.statusKey)) {
                    CreatePackage.LOGGER.warn("[Distributor @ {}] cannot refill recipe {} after {}; missing {} x{}",
                            getBlockPos(), job.recipeId, reason, input.what(), missing.amount());
                }
                setMissingRefillInput(job, missing, reason);
                rememberJobStatus(job, "waiting_refill_inputs");
                return false;
            }
        }
        clearMissingRefillInput(job);

        if (!context.simulate()) {
            if (!"refill_target_full".equals(job.statusKey)) {
                CreatePackage.LOGGER.warn("[Distributor @ {}] cannot refill recipe {} after {}; target cannot accept "
                        + "inputs yet", getBlockPos(), job.recipeId, reason);
            }
            rememberJobStatus(job, "refill_target_full");
            return false;
        }

        for (GenericStack input : context.inputs()) {
            long extracted = extractFromNetwork(input.what(), input.amount(), Actionable.MODULATE);
            if (extracted < input.amount()) {
                GenericStack missing = new GenericStack(input.what(), input.amount() - extracted);
                CreatePackage.LOGGER.warn("[Distributor @ {}] refill extraction changed during execution for {} "
                        + "({} < {})", getBlockPos(), input.what(), extracted, input.amount());
                if (extracted > 0) {
                    insertIntoNetwork(input.what(), extracted, Actionable.MODULATE);
                }
                setMissingRefillInput(job, missing, reason);
                rememberJobStatus(job, "waiting_refill_inputs");
                return false;
            }
        }

        ActionList.Result refillExecuted = context.execute();
        if (!refillExecuted.success()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] refill insertion failed after extraction for recipe {} at action #{} ({})",
                    getBlockPos(), job.recipeId, refillExecuted.failedIndex(), refillExecuted.failedAction());
            for (GenericStack input : context.inputs()) {
                insertIntoNetwork(input.what(), input.amount(), Actionable.MODULATE);
            }
            rememberJobStatus(job, "refill_target_full");
            rememberFailure("refill_target_full",
                    "action #" + refillExecuted.failedIndex() + " " + refillExecuted.failedAction(), job.route, -1);
            return false;
        }

        job.pendingSupplies.addAll(context.pendingSupplies());
        job.roundsStarted++;
        job.roundTicks = 0;
        job.roundHasOutput = false;
        job.awaitingFinalOutput = job.loops <= 1;
        rememberJobStatus(job, "working");
        CreatePackage.LOGGER.info("[Distributor @ {}] refilled recipe {} after {} (round {})",
                getBlockPos(), job.recipeId, reason, job.roundsStarted);
        saveAndSync();
        wakeTicker();
        return true;
    }

    private void setMissingRefillInput(DistributionJob job, GenericStack missing, String reason) {
        boolean changed = !Objects.equals(job.missingRefillInput, missing);
        job.missingRefillInput = missing;
        if (changed) {
            job.nextMissingRefillNoticeTick = 0;
            saveAndSync();
        }
        notifyMissingRefillInput(job, missing, reason, changed);
    }

    private void clearMissingRefillInput(DistributionJob job) {
        if (job.missingRefillInput != null) {
            job.missingRefillInput = null;
            saveAndSync();
        }
    }

    private void notifyEncoderLoginIfMissingRefillInput(DistributionJob job) {
        if (job.encoder == null || job.missingRefillInput == null) {
            return;
        }
        long generation = encoderLoginGeneration(job.encoder.uuid());
        if (generation <= job.lastEncoderLoginNoticeGeneration) {
            return;
        }
        if (notifyMissingRefillInput(job, job.missingRefillInput, "login", true)) {
            job.lastEncoderLoginNoticeGeneration = generation;
        }
    }

    private boolean notifyMissingRefillInput(DistributionJob job, GenericStack missing, String reason, boolean force) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        long now = serverLevel.getGameTime();
        if (!force && now < job.nextMissingRefillNoticeTick) {
            return false;
        }

        boolean delivered = false;
        if (job.encoder != null && serverLevel.getServer() != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(job.encoder.uuid());
            if (player != null) {
                player.sendSystemMessage(missingRefillMessage(job, missing, serverLevel, player));
                delivered = true;
            }
        }

        if (!delivered) {
            double radiusSq = Config.missingRefillNoticeRadius() * Config.missingRefillNoticeRadius();
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(getBlockPos().getCenter()) <= radiusSq) {
                    player.sendSystemMessage(missingRefillMessage(job, missing, serverLevel, player));
                    delivered = true;
                }
            }
        }

        if (delivered) {
            job.nextMissingRefillNoticeTick = now + missingRefillNoticeIntervalTicks();
        }
        return delivered;
    }

    private Component missingRefillMessage(DistributionJob job, GenericStack missing, ServerLevel serverLevel,
            ServerPlayer recipient) {
        MutableComponent prefix = Component.translatable("message." + CreatePackage.MODID + ".prefix")
                .withStyle(ChatFormatting.GREEN);
        MutableComponent body = Component.translatable("message." + CreatePackage.MODID
                        + ".package_distributor.missing_refill_input",
                Component.translatable(descriptionId),
                noticeLocation(serverLevel, recipient),
                job.primaryOutput.getDisplayName().copy().withStyle(ChatFormatting.GOLD),
                missingStackText(missing));
        return Component.empty().append(prefix).append(" ").append(body);
    }

    private Component noticeLocation(ServerLevel serverLevel, ServerPlayer recipient) {
        BlockPos pos = getBlockPos();
        MutableComponent location = Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");
        if (!recipient.hasPermissions(2)) {
            return location;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.0D;
        double z = pos.getZ() + 0.5D;
        String command = "/execute in " + serverLevel.dimension().location() + " run tp @s "
                + x + " " + y + " " + z;
        return location.withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message." + CreatePackage.MODID
                                        + ".package_distributor.teleport_to_distributor"))));
    }

    private static Component missingStackText(GenericStack missing) {
        return Component.empty()
                .append(stackAmount(missing).copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" x ").withStyle(ChatFormatting.YELLOW))
                .append(missing.what().getDisplayName().copy().withStyle(ChatFormatting.YELLOW));
    }

    private static long encoderLoginGeneration(UUID playerId) {
        return ENCODER_LOGIN_NOTICE_GENERATIONS.getOrDefault(playerId, 0L);
    }

    private static int missingRefillNoticeIntervalTicks() {
        return Config.missingRefillNoticeIntervalSeconds(30) * 20;
    }

    @Nullable
    private RefillContext createRefillContext(DistributionJob job, List<GenericStack> refillInputs,
            List<BlockPos> route) {
        if (level == null) {
            return null;
        }
        var recipe = level.getRecipeManager().byKey(job.recipeId);
        if (recipe.isEmpty() || !(recipe.get().value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe sequenced)) {
            rememberJobStatus(job, "no_matching_recipe");
            return null;
        }

        AssemblyPlan plan = AssemblyPlan.of(sequenced);
        LinkedMachines machines = LinkedMachines.resolve(level, route);
        BlockPos inputPos = machines.inputDepot();
        if (inputPos == null) {
            rememberJobStatus(job, "missing_io");
            return null;
        }

        List<GenericStack> remaining = copyStacks(refillInputs);
        List<SupplyAction> actions = new ArrayList<>();
        List<AssemblyPlanner.PendingSupply> pendingSupplies = new ArrayList<>();
        List<GenericStack> inputs = new ArrayList<>();
        ItemStack inputStack = takeMatchingItem(remaining, plan.baseInput(), 1);
        if (inputStack.isEmpty()) {
            rememberJobStatus(job, "missing_base");
            return null;
        }
        if (!scheduleItemSupply(inputPos, inputStack, actions, pendingSupplies)) {
            rememberJobStatus(job, "refill_target_full");
            return null;
        }
        addRefillInput(inputs, inputStack);

        var deployers = machines.withRole(LinkedMachines.Role.DEPLOYER);
        var spouts = machines.withRole(LinkedMachines.Role.SPOUT);
        int deployIdx = 0;
        int fillIdx = 0;
        for (AssemblyPlan.Step step : plan.consumingSteps()) {
            switch (step.type()) {
                case DEPLOY -> {
                    if (deployIdx >= deployers.size()) {
                        rememberJobStatus(job, "missing_deployer");
                        return null;
                    }
                    BlockPos deployer = deployers.get(deployIdx).pos();
                    if (step.keepHeld()) {
                        if (countMatchingItem(deployer, step.heldItem()) <= 0) {
                            NetworkIO.NetworkItemStack held = findNetworkItem(step.heldItem(), 1);
                            if (held == null) {
                                rememberJobStatus(job, "missing_deployer_item");
                                return null;
                            }
                            if (!scheduleItemSupply(deployer, held.stack(), actions, pendingSupplies)) {
                                rememberJobStatus(job, "refill_target_full");
                                return null;
                            }
                            addRefillInput(inputs, held.stack());
                        }
                    } else {
                        long amount = plan.loops();
                        long existing = countMatchingItem(deployer, step.heldItem());
                        long supplyAmount = Math.max(0, amount - existing);
                        if (supplyAmount > 0) {
                            ItemStack held = takeMatchingItem(remaining, step.heldItem(), supplyAmount);
                            if (held.isEmpty()) {
                                rememberJobStatus(job, "missing_deployer_item");
                                return null;
                            }
                            if (!scheduleItemSupply(deployer, held, actions, pendingSupplies)) {
                                rememberJobStatus(job, "refill_target_full");
                                return null;
                            }
                            addRefillInput(inputs, held);
                        }
                    }
                    deployIdx++;
                }
                case FILL -> {
                    if (fillIdx >= spouts.size()) {
                        rememberJobStatus(job, "missing_spout");
                        return null;
                    }
                    long amount = (long) plan.loops() * step.fluid().amount();
                    long existing = countMatchingFluid(spouts.get(fillIdx).pos(), step.fluid());
                    long supplyAmount = Math.max(0, amount - existing);
                    if (supplyAmount > 0) {
                        FluidStack fluid = takeMatchingFluid(remaining, step.fluid(), supplyAmount);
                        if (fluid.isEmpty()) {
                            rememberJobStatus(job, "missing_spout_fluid");
                            return null;
                        }
                        if (!scheduleFluidSupply(spouts.get(fillIdx).pos(), fluid, actions, pendingSupplies)) {
                            rememberJobStatus(job, "refill_target_full");
                            return null;
                        }
                        addRefillInput(inputs, fluid);
                    }
                    fillIdx++;
                }
                default -> {
                }
            }
        }

        return new RefillContext(actions, pendingSupplies, inputs);
    }

    private IItemHandler itemHandler(BlockPos pos) {
        return InventoryAccess.itemHandler(level, pos);
    }

    private boolean canInsertAll(BlockPos target, ItemStack stack) {
        return InventoryAccess.canInsertAll(level, target, stack);
    }

    private boolean scheduleItemSupply(BlockPos target, ItemStack stack, List<SupplyAction> actions,
            List<AssemblyPlanner.PendingSupply> pendingSupplies) {
        return AssemblyPlanner.scheduleItemSupply(level, target, stack, actions, pendingSupplies);
    }

    private boolean scheduleFluidSupply(BlockPos target, FluidStack stack, List<SupplyAction> actions,
            List<AssemblyPlanner.PendingSupply> pendingSupplies) {
        return AssemblyPlanner.scheduleFluidSupply(level, target, stack, actions, pendingSupplies);
    }

    private ItemStack insertItem(BlockPos target, ItemStack stack, boolean simulate) {
        return InventoryAccess.insertItem(level, target, stack, simulate);
    }

    private ItemStack insertIntoHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        return InventoryAccess.insertIntoHandler(handler, stack, simulate);
    }

    private long countMatchingItem(BlockPos pos, net.minecraft.world.item.crafting.Ingredient ingredient) {
        return InventoryAccess.countMatchingItem(level, pos, ingredient);
    }

    private long countMatchingFluid(BlockPos pos,
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient) {
        return InventoryAccess.countMatchingFluid(level, pos, ingredient);
    }

    private IFluidHandler fluidHandler(BlockPos pos) {
        return InventoryAccess.fluidHandler(level, pos);
    }

    protected final boolean hasDistributorJob() {
        return !activeJobs.isEmpty();
    }

    protected final boolean routeOverlapsActiveJob(List<BlockPos> route) {
        if (route.isEmpty() || activeJobs.isEmpty()) {
            return false;
        }
        // Any shared route position represents a shared inventory or processing machine.
        // Keep that resource exclusive; only fully disjoint assembly lines may run in parallel.
        for (DistributionJob job : activeJobs) {
            if (!java.util.Collections.disjoint(route, job.route)) {
                return true;
            }
        }
        return false;
    }

    protected void wakeTicker() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private void clearLastStatus() {
        if (activeJobs.isEmpty()) {
            lastStatusKey = "ready";
        }
    }

    protected void rememberStatus(String statusKey) {
        if (!Objects.equals(lastStatusKey, statusKey)) {
            lastStatusKey = statusKey;
            saveAndSync();
        }
    }

    private String visibleStatusKey() {
        if ("busy".equals(lastStatusKey) && canAcceptMoreJobs()) {
            return activeJobs.isEmpty() ? "ready" : "working";
        }
        if (activeJobs.isEmpty()) {
            return switch (lastStatusKey) {
                case "working", "waiting_reflow_input", "waiting_refill_inputs", "waiting_target_space",
                        "refill_target_full", "busy" -> "ready";
                default -> lastStatusKey;
            };
        }
        if ("completed".equals(lastStatusKey)) {
            return "working";
        }
        return lastStatusKey;
    }

    private void rememberJobStatus(DistributionJob job, String statusKey) {
        job.statusKey = statusKey;
        refreshLastStatus();
    }

    private void refreshLastStatus() {
        DistributionJob visible = firstJob();
        String status = visible == null ? lastStatusKey : visible.statusKey;
        if (activeJobs.isEmpty() && !"completed".equals(lastStatusKey)) {
            status = "ready";
        }
        if (!Objects.equals(lastStatusKey, status)) {
            lastStatusKey = status;
            saveAndSync();
        }
    }

    @Nullable
    private DistributionJob firstJob() {
        return activeJobs.isEmpty() ? null : activeJobs.get(0);
    }

    protected void rememberFailureRoute(List<BlockPos> route) {
        rememberFailure(lastStatusKey, null, route, -1);
    }

    protected void rememberFailure(String statusKey, @Nullable String detail, List<BlockPos> route, int patternSlot) {
        diagnostics.remember(statusKey, detail, route, patternSlot);
        saveAndSync();
    }

    public FailureDiagnostics getDiagnostics() {
        return diagnostics;
    }

    protected void saveAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            markForUpdate();
        }
    }

    private AeStatus currentAeStatus() {
        IGridNode node = getMainNode().getNode();
        return new AeStatus(node != null,
                node != null && getMainNode().isPowered(),
                node != null && node.meetsChannelRequirements(),
                node != null && getMainNode().isActive());
    }

    private AeStatus visibleAeStatus() {
        if (level != null && !level.isClientSide()) {
            return currentAeStatus();
        }
        return new AeStatus(aeNodePresent, aePowered, aeChannel, aeActive);
    }

    private record AeStatus(boolean nodePresent, boolean powered, boolean channel, boolean active) {
    }

    private static int emptyOutputRefillTimeoutTicks() {
        return Config.emptyOutputRefillTimeoutTicks(DEFAULT_ROUND_OUTPUT_TIMEOUT_TICKS);
    }

    // === Create goggles ===

    @Override
    public ItemStack getIcon(boolean isPlayerSneaking) {
        return new ItemStack(visualItem);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.header",
                Component.translatable(descriptionId))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(statusLine());

        AeStatus aeStatus = visibleAeStatus();
        if (aeStatus.nodePresent()) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.ae_node",
                    boolText(aeStatus.powered()),
                    boolText(aeStatus.channel()),
                    boolText(aeStatus.active())).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.ae_node_missing")
                    .withStyle(ChatFormatting.RED));
        }

        for (Component line : getVisibleJobLines()) {
            tooltip.add(line.copy().withStyle(ChatFormatting.AQUA));
        }
        tooltip.addAll(getMissingRefillLines());

        addParallelInfoToTooltip(tooltip);

        tooltip.add(CommonComponents.EMPTY);
        if (usesStoredMachineLinks()) {
            addMachineLinksToTooltip(tooltip);
        } else {
            addPatternRoutesToTooltip(tooltip);
        }
        return true;
    }

    private void addMachineLinksToTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.links",
                linkedMachines.size()).withStyle(ChatFormatting.WHITE));

        if (linkedMachines.isEmpty()) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.no_links")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        int shown = Math.min(linkedMachines.size(), MAX_TOOLTIP_LINKS);
        for (int i = 0; i < shown; i++) {
            tooltip.add(linkLine(i, linkedMachines.get(i)));
        }
        if (linkedMachines.size() > shown) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.more_links",
                    linkedMachines.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private void addPatternRoutesToTooltip(List<Component> tooltip) {
        int patterns = getMechanicalPackagePatternCount();
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.pattern_routes",
                patterns).withStyle(patterns > 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.pattern_route_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    protected void addParallelInfoToTooltip(List<Component> tooltip) {
    }

    private Component statusLine() {
        String status = getStatusKey();
        ChatFormatting color = switch (status) {
            case "ready", "completed" -> ChatFormatting.GREEN;
            case "working" -> ChatFormatting.AQUA;
            case "busy", "waiting_reflow_input", "waiting_target_space", "refill_target_full" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.status",
                Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.status." + status)
                        .withStyle(color)).withStyle(ChatFormatting.GRAY);
    }

    private Component linkLine(int index, BlockPos pos) {
        String roleKey = roleKey(pos);
        Component name = linkedBlockName(pos);
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.link_entry",
                index + 1,
                Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.role." + roleKey),
                name,
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY);
    }

    private String roleKey(BlockPos pos) {
        if (level == null) {
            return "unknown";
        }
        return switch (LinkedMachines.roleOf(level, pos)) {
            case DEPOT -> {
                BlockPos input = null;
                BlockPos output = null;
                for (BlockPos linked : linkedMachines) {
                    if (LinkedMachines.roleOf(level, linked) == LinkedMachines.Role.DEPOT) {
                        if (input == null) {
                            input = linked;
                        }
                        output = linked;
                    }
                }
                if (pos.equals(input)) {
                    yield "input";
                }
                if (pos.equals(output)) {
                    yield "output";
                }
                yield "depot";
            }
            case DEPLOYER -> "deployer";
            case SPOUT -> "spout";
            case PRESS -> "press";
            case MIXER -> "mixer";
            case SAW -> "saw";
            case BASIN -> "basin";
            case UNKNOWN -> "unknown";
        };
    }

    private Component linkedBlockName(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.unloaded");
        }
        BlockState state = level.getBlockState(pos);
        Item item = state.getBlock().asItem();
        if (item != net.minecraft.world.item.Items.AIR) {
            return item.getDescription();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? Component.literal("unknown") : Component.literal(id.toString());
    }

    private static Component boolText(boolean value) {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor." + (value ? "yes" : "no"))
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static boolean sameJob(DistributionJob left, DistributionJob right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.recipeId, right.recipeId)
                && Objects.equals(left.primaryOutput, right.primaryOutput)
                && left.primaryRemaining == right.primaryRemaining
                && Objects.equals(left.inputPos, right.inputPos)
                && Objects.equals(left.outputPos, right.outputPos)
                && Objects.equals(left.transitionalItem, right.transitionalItem)
                && left.sequenceLength == right.sequenceLength
                && left.loops == right.loops
                && Objects.equals(left.route, right.route)
                && left.ticks == right.ticks
                && left.roundTicks == right.roundTicks
                && left.roundsStarted == right.roundsStarted
                && left.roundHasOutput == right.roundHasOutput
                && left.awaitingFinalOutput == right.awaitingFinalOutput
                && Objects.equals(left.pendingSupplies, right.pendingSupplies)
                && Objects.equals(left.missingRefillInput, right.missingRefillInput)
                && Objects.equals(left.statusKey, right.statusKey);
    }

    private static boolean sameJobs(List<DistributionJob> left, List<DistributionJob> right) {
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

    private static void addRefillInput(List<GenericStack> inputs, ItemStack stack) {
        PatternStacks.addRefillInput(inputs, stack);
    }

    private static void addRefillInput(List<GenericStack> inputs, FluidStack stack) {
        PatternStacks.addRefillInput(inputs, stack);
    }

    private static void addRefillInput(List<GenericStack> inputs, GenericStack stack) {
        PatternStacks.addRefillInput(inputs, stack);
    }

    private static List<GenericStack> copyStacks(List<GenericStack> inputs) {
        return PatternStacks.copyStacks(inputs);
    }

    private static ItemStack takeMatchingItem(List<GenericStack> inputs,
            net.minecraft.world.item.crafting.Ingredient ingredient, long amount) {
        return PatternStacks.takeMatchingItem(inputs, ingredient, amount);
    }

    private static FluidStack takeMatchingFluid(List<GenericStack> inputs,
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient, long amount) {
        return PatternStacks.takeMatchingFluid(inputs, ingredient, amount);
    }

    private record RefillContext(List<SupplyAction> actions, List<AssemblyPlanner.PendingSupply> pendingSupplies,
            List<GenericStack> inputs) {
        boolean simulate() {
            return new ActionList(actions).simulate();
        }

        ActionList.Result execute() {
            return new ActionList(actions).execute();
        }
    }

    private static final class DistributionJob {
        private static final String NBT_RECIPE = "recipe";
        private static final String NBT_OUTPUT = "output";
        private static final String NBT_REMAINING = "remaining";
        private static final String NBT_INPUT_POS = "inputPos";
        private static final String NBT_OUTPUT_POS = "outputPos";
        private static final String NBT_TRANSITIONAL = "transitional";
        private static final String NBT_SEQUENCE_LENGTH = "sequenceLength";
        private static final String NBT_LOOPS = "loops";
        private static final String NBT_TICKS = "ticks";
        private static final String NBT_ROUND_TICKS = "roundTicks";
        private static final String NBT_ROUNDS_STARTED = "roundsStarted";
        private static final String NBT_ROUND_HAS_OUTPUT = "roundHasOutput";
        private static final String NBT_AWAITING_FINAL_OUTPUT = "awaitingFinalOutput";
        private static final String NBT_STATUS = "status";
        private static final String NBT_REFILL_INPUTS = "refillInputs";
        private static final String NBT_REFILL_KEY = "key";
        private static final String NBT_REFILL_AMOUNT = "amount";
        private static final String NBT_ENCODER = "encoder";
        private static final String NBT_ENCODER_UUID = "uuid";
        private static final String NBT_ENCODER_NAME = "name";
        private static final String NBT_MISSING_REFILL_INPUT = "missingRefillInput";
        private static final String NBT_PENDING_SUPPLIES = "pendingSupplies";
        private static final String NBT_PENDING_TARGET = "target";
        private static final String NBT_PENDING_KEY = "key";
        private static final String NBT_PENDING_AMOUNT = "amount";
        private static final String NBT_ROUTE = "route";

        private final ResourceLocation recipeId;
        private final AEItemKey primaryOutput;
        private long primaryRemaining;
        private final BlockPos inputPos;
        private final BlockPos outputPos;
        private final AEItemKey transitionalItem;
        private final int sequenceLength;
        private final int loops;
        private final List<GenericStack> refillInputs;
        private final List<AssemblyPlanner.PendingSupply> pendingSupplies;
        @Nullable
        private final MechanicalPackagePatternData.PatternEncoder encoder;
        private final List<BlockPos> route;
        private int ticks;
        private int roundTicks;
        private long roundsStarted;
        private boolean roundHasOutput;
        private boolean awaitingFinalOutput;
        @Nullable
        private GenericStack missingRefillInput;
        private long nextMissingRefillNoticeTick;
        private long lastEncoderLoginNoticeGeneration;
        private String statusKey = "working";

        private DistributionJob(ResourceLocation recipeId, AEItemKey primaryOutput, long primaryRemaining,
                BlockPos inputPos, BlockPos outputPos, AEItemKey transitionalItem, int sequenceLength, int loops,
                List<GenericStack> refillInputs, List<AssemblyPlanner.PendingSupply> pendingSupplies,
                @Nullable MechanicalPackagePatternData.PatternEncoder encoder, List<BlockPos> route) {
            this.recipeId = Objects.requireNonNull(recipeId);
            this.primaryOutput = Objects.requireNonNull(primaryOutput);
            this.primaryRemaining = primaryRemaining;
            this.inputPos = inputPos.immutable();
            this.outputPos = outputPos.immutable();
            this.transitionalItem = Objects.requireNonNull(transitionalItem);
            this.sequenceLength = sequenceLength;
            this.loops = loops;
            this.refillInputs = List.copyOf(refillInputs);
            this.pendingSupplies = new ArrayList<>(pendingSupplies);
            this.encoder = encoder;
            this.route = route.stream().map(BlockPos::immutable).toList();
            this.awaitingFinalOutput = loops <= 1;
            this.lastEncoderLoginNoticeGeneration = encoder == null ? 0 : encoderLoginGeneration(encoder.uuid());
        }

        private boolean waitingForRefill() {
            return "waiting_refill_inputs".equals(statusKey) || "refill_target_full".equals(statusKey);
        }

        private boolean waitingForReflow() {
            return "waiting_reflow_input".equals(statusKey);
        }

        private boolean waitingForTargetSpace() {
            return "waiting_target_space".equals(statusKey);
        }

        private CompoundTag write(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_RECIPE, recipeId.toString());
            tag.put(NBT_OUTPUT, primaryOutput.toTag(registries));
            tag.putLong(NBT_REMAINING, primaryRemaining);
            tag.putLong(NBT_INPUT_POS, inputPos.asLong());
            tag.putLong(NBT_OUTPUT_POS, outputPos.asLong());
            tag.put(NBT_TRANSITIONAL, transitionalItem.toTag(registries));
            tag.putInt(NBT_SEQUENCE_LENGTH, sequenceLength);
            tag.putInt(NBT_LOOPS, loops);
            tag.putInt(NBT_TICKS, ticks);
            tag.putInt(NBT_ROUND_TICKS, roundTicks);
            tag.putLong(NBT_ROUNDS_STARTED, roundsStarted);
            tag.putBoolean(NBT_ROUND_HAS_OUTPUT, roundHasOutput);
            tag.putBoolean(NBT_AWAITING_FINAL_OUTPUT, awaitingFinalOutput);
            tag.putString(NBT_STATUS, statusKey);
            ListTag refillList = new ListTag();
            for (GenericStack input : refillInputs) {
                CompoundTag inputTag = new CompoundTag();
                inputTag.put(NBT_REFILL_KEY, input.what().toTagGeneric(registries));
                inputTag.putLong(NBT_REFILL_AMOUNT, input.amount());
                refillList.add(inputTag);
            }
            tag.put(NBT_REFILL_INPUTS, refillList);
            if (encoder != null) {
                CompoundTag encoderTag = new CompoundTag();
                encoderTag.putString(NBT_ENCODER_UUID, encoder.uuid().toString());
                encoderTag.putString(NBT_ENCODER_NAME, encoder.name());
                tag.put(NBT_ENCODER, encoderTag);
            }
            if (missingRefillInput != null) {
                tag.put(NBT_MISSING_REFILL_INPUT, GenericStack.writeTag(registries, missingRefillInput));
            }
            ListTag pendingList = new ListTag();
            for (AssemblyPlanner.PendingSupply supply : pendingSupplies) {
                CompoundTag supplyTag = new CompoundTag();
                supplyTag.putLong(NBT_PENDING_TARGET, supply.target().asLong());
                supplyTag.put(NBT_PENDING_KEY, supply.stack().what().toTagGeneric(registries));
                supplyTag.putLong(NBT_PENDING_AMOUNT, supply.stack().amount());
                pendingList.add(supplyTag);
            }
            tag.put(NBT_PENDING_SUPPLIES, pendingList);
            long[] packedRoute = new long[route.size()];
            for (int i = 0; i < packedRoute.length; i++) {
                packedRoute[i] = route.get(i).asLong();
            }
            tag.putLongArray(NBT_ROUTE, packedRoute);
            return tag;
        }

        private static DistributionJob read(CompoundTag tag, HolderLookup.Provider registries) {
            ResourceLocation recipe = ResourceLocation.parse(tag.getString(NBT_RECIPE));
            AEItemKey output = AEItemKey.fromTag(registries, tag.getCompound(NBT_OUTPUT));
            if (output == null) {
                return null;
            }
            AEItemKey transitional = tag.contains(NBT_TRANSITIONAL)
                    ? AEItemKey.fromTag(registries, tag.getCompound(NBT_TRANSITIONAL))
                    : output;
            if (transitional == null) {
                transitional = output;
            }
            List<GenericStack> refillInputs = new ArrayList<>();
            ListTag refillList = tag.getList(NBT_REFILL_INPUTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < refillList.size(); i++) {
                CompoundTag inputTag = refillList.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(registries, inputTag.getCompound(NBT_REFILL_KEY));
                long amount = inputTag.getLong(NBT_REFILL_AMOUNT);
                if (key != null && amount > 0) {
                    refillInputs.add(new GenericStack(key, amount));
                }
            }
            List<AssemblyPlanner.PendingSupply> pendingSupplies = new ArrayList<>();
            ListTag pendingList = tag.getList(NBT_PENDING_SUPPLIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < pendingList.size(); i++) {
                CompoundTag supplyTag = pendingList.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(registries, supplyTag.getCompound(NBT_PENDING_KEY));
                long amount = supplyTag.getLong(NBT_PENDING_AMOUNT);
                if (key != null && amount > 0) {
                    pendingSupplies.add(new AssemblyPlanner.PendingSupply(BlockPos.of(supplyTag.getLong(NBT_PENDING_TARGET)),
                            new GenericStack(key, amount)));
                }
            }
            BlockPos outputPos = BlockPos.of(tag.getLong(NBT_OUTPUT_POS));
            BlockPos inputPos = tag.contains(NBT_INPUT_POS) ? BlockPos.of(tag.getLong(NBT_INPUT_POS)) : outputPos;
            List<BlockPos> route = new ArrayList<>();
            for (long packed : tag.getLongArray(NBT_ROUTE)) {
                route.add(BlockPos.of(packed));
            }
            MechanicalPackagePatternData.PatternEncoder encoder = readEncoder(tag);
            var job = new DistributionJob(recipe, output, tag.getLong(NBT_REMAINING), inputPos, outputPos,
                    transitional,
                    tag.contains(NBT_SEQUENCE_LENGTH) ? tag.getInt(NBT_SEQUENCE_LENGTH) : 1,
                    tag.contains(NBT_LOOPS) ? tag.getInt(NBT_LOOPS) : 1,
                    refillInputs,
                    pendingSupplies,
                    encoder,
                    route);
            if (tag.contains(NBT_MISSING_REFILL_INPUT, Tag.TAG_COMPOUND)) {
                job.missingRefillInput = GenericStack.readTag(registries, tag.getCompound(NBT_MISSING_REFILL_INPUT));
            }
            job.ticks = tag.getInt(NBT_TICKS);
            job.roundTicks = tag.getInt(NBT_ROUND_TICKS);
            job.roundsStarted = tag.contains(NBT_ROUNDS_STARTED) ? tag.getLong(NBT_ROUNDS_STARTED) : 1;
            job.roundHasOutput = tag.getBoolean(NBT_ROUND_HAS_OUTPUT);
            job.awaitingFinalOutput = tag.contains(NBT_AWAITING_FINAL_OUTPUT)
                    ? tag.getBoolean(NBT_AWAITING_FINAL_OUTPUT)
                    : job.loops <= 1;
            job.statusKey = tag.contains(NBT_STATUS) ? tag.getString(NBT_STATUS) : "working";
            return job;
        }

        @Nullable
        private static MechanicalPackagePatternData.PatternEncoder readEncoder(CompoundTag tag) {
            if (!tag.contains(NBT_ENCODER, Tag.TAG_COMPOUND)) {
                return null;
            }
            CompoundTag encoderTag = tag.getCompound(NBT_ENCODER);
            try {
                UUID uuid = UUID.fromString(encoderTag.getString(NBT_ENCODER_UUID));
                return new MechanicalPackagePatternData.PatternEncoder(uuid,
                        encoderTag.getString(NBT_ENCODER_NAME));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    protected class DistributorTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(5, 20, activeJobs.isEmpty());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (activeJobs.isEmpty()) {
                return TickRateModulation.SLEEP;
            }
            return tickDistributorJob() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
    }
}
