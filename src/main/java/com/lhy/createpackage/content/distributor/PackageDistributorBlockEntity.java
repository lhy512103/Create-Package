package com.lhy.createpackage.content.distributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import com.lhy.createpackage.Config;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.recipe.AssemblyPlan;
import com.lhy.createpackage.content.recipe.AssemblyRecipeMatcher;
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
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
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
    private static final int MAX_JOB_TICKS = 20 * 60 * 10;
    private static final int ROUND_OUTPUT_TIMEOUT_TICKS = 20 * 60;
    private static final int REFILL_RETRY_TICKS = 20 * 5;
    private static final int MAX_TOOLTIP_LINKS = 12;

    /**
     * Ordered list of linked Create machines (depot / deployers / spouts), in the physical sequence
     * the player linked them. The order maps directly onto the sequenced-assembly recipe steps.
     * Positions are absolute and share this block entity's dimension.
     */
    private final List<BlockPos> linkedMachines = new ArrayList<>();
    private final MachineSource actionSource = new MachineSource(this);

    private final List<DistributionJob> activeJobs = new ArrayList<>();
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
                    transitionalItem, sequenceLength, loops, List.of(), route);
            streamedJob.ticks = data.readVarInt();
            streamedJob.roundTicks = data.readVarInt();
            streamedJob.roundsStarted = data.readVarLong();
            streamedJob.roundHasOutput = data.readBoolean();
            streamedJob.awaitingFinalOutput = data.readBoolean();
            streamedJob.statusKey = data.readUtf();
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

        PlanContext context = createPlan(patternDetails, inputs, route);
        if (context == null) {
            return false;
        }

        if (!context.simulate()) {
            rememberStatus("simulate_failed");
            rememberFailureRoute(route);
            return false;
        }

        if (!context.execute()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] simulated plan but execution failed; refusing pattern",
                    getBlockPos());
            rememberStatus("execute_failed");
            rememberFailureRoute(route);
            return false;
        }

        DistributionJob job = new DistributionJob(context.recipeId(), context.primaryOutputKey(),
                context.primaryOutputAmount(), context.inputPos(), context.outputPos(), context.transitionalItemKey(),
                context.sequenceLength(), context.loops(), context.refillInputs(), route);
        job.roundsStarted = 1;
        activeJobs.add(job);
        consumeInputs(inputs);
        lastStatusKey = "working";
        saveAndSync();
        wakeTicker();

        CreatePackage.LOGGER.info("[Distributor @ {}] accepted recipe {} waiting for {} x{} at {}",
                getBlockPos(), context.recipeId(), context.primaryOutputKey(), context.primaryOutputAmount(),
                context.outputPos());
        return true;
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

    private PlanContext createPlan(IPatternDetails patternDetails, KeyCounter[] inputs, List<BlockPos> route) {
        var patternInputs = flattenInputs(inputs);
        var primary = patternDetails.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutputKey)) {
            rememberStatus("no_primary_output");
            return null;
        }

        ItemStack primaryOutput = primaryOutputKey.toStack((int) Math.min(primary.amount(), Integer.MAX_VALUE));
        var candidates = AssemblyRecipeMatcher.findCandidates(level, primaryOutput, itemInputs(patternInputs));
        if (candidates.size() != 1) {
            if (candidates.size() > 1) {
                CreatePackage.LOGGER.warn("[Distributor @ {}] ambiguous sequenced assembly recipes for {}: {}",
                        getBlockPos(), primaryOutput,
                        candidates.stream().map(AssemblyRecipeMatcher.Match::id).toList());
                rememberStatus("ambiguous_recipe");
            } else {
                rememberStatus("no_matching_recipe");
            }
            return null;
        }

        var match = candidates.get(0);
        AssemblyPlan plan = AssemblyPlan.of(match.recipe());
        AEItemKey transitionalItemKey = AEItemKey.of(match.recipe().getTransitionalItem());
        if (transitionalItemKey == null) {
            rememberStatus("no_matching_recipe");
            return null;
        }
        LinkedMachines machines = LinkedMachines.resolve(level, route);

        var inputPos = machines.inputDepot();
        var outputPos = machines.outputDepot();
        if (inputPos == null || outputPos == null) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] needs linked input and output depot/belt", getBlockPos());
            rememberStatus("missing_io");
            return null;
        }
        if (inputPos.equals(outputPos)) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] input and output depot/belt must be different", getBlockPos());
            rememberStatus("same_io");
            return null;
        }

        var inputStack = takeMatchingItem(patternInputs, plan.baseInput(), 1);
        if (inputStack.isEmpty()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] missing base input for {}", getBlockPos(), match.id());
            rememberStatus("missing_base");
            return null;
        }

        var returnActions = new ArrayList<SupplyAction>();
        var actions = new ArrayList<SupplyAction>();
        var refillInputs = new ArrayList<GenericStack>();
        actions.add(new ItemSupplyAction(inputPos, inputStack));
        addRefillInput(refillInputs, inputStack);

        var deployers = machines.withRole(LinkedMachines.Role.DEPLOYER);
        var spouts = machines.withRole(LinkedMachines.Role.SPOUT);
        int deployIdx = 0;
        int fillIdx = 0;
        for (AssemblyPlan.Step step : plan.consumingSteps()) {
            switch (step.type()) {
                case DEPLOY -> {
                    if (deployIdx >= deployers.size()) {
                        rememberStatus("missing_deployer");
                        return null;
                    }
                    BlockPos deployer = deployers.get(deployIdx).pos();
                    long existing = countMatchingItem(deployer, step.heldItem());
                    if (step.keepHeld()) {
                        if (existing <= 0) {
                            ItemStack held = takeMatchingItem(patternInputs, step.heldItem(), 1);
                            boolean supplied = false;
                            if (held.isEmpty()) {
                                NetworkItemStack networkHeld = findNetworkItem(step.heldItem(), 1);
                                if (networkHeld != null) {
                                    actions.add(new NetworkItemSupplyAction(deployer, networkHeld.key(),
                                            networkHeld.stack()));
                                    supplied = true;
                                }
                            } else {
                                actions.add(new ItemSupplyAction(deployer, held));
                                supplied = true;
                            }
                            if (!supplied) {
                                rememberStatus("missing_deployer_item");
                                return null;
                            }
                        }
                        returnAllMatchingInputs(patternInputs, step.heldItem(), returnActions);
                    } else {
                        long amount = plan.loops();
                        long availableInput = countMatchingInput(patternInputs, step.heldItem());
                        if (availableInput + existing < amount) {
                            rememberStatus("missing_deployer_item");
                            return null;
                        }
                        long supplyAmount = availableInput > 0 ? Math.min(availableInput, amount) : 0;
                        if (supplyAmount > 0) {
                            ItemStack held = takeMatchingItem(patternInputs, step.heldItem(), supplyAmount);
                            if (held.isEmpty()) {
                                rememberStatus("missing_deployer_item");
                                return null;
                            }
                            actions.add(new ItemSupplyAction(deployer, held));
                        }
                        ItemStack refill = exampleItemStack(step.heldItem(), amount);
                        if (refill.isEmpty()) {
                            rememberStatus("missing_deployer_item");
                            return null;
                        }
                        addRefillInput(refillInputs, refill);
                    }
                    deployIdx++;
                }
                case FILL -> {
                    if (fillIdx >= spouts.size()) {
                        rememberStatus("missing_spout");
                        return null;
                    }
                    long amount = (long) plan.loops() * step.fluid().amount();
                    BlockPos spout = spouts.get(fillIdx).pos();
                    long availableInput = countMatchingInput(patternInputs, step.fluid());
                    long existing = countMatchingFluid(spout, step.fluid());
                    if (availableInput + existing < amount) {
                        rememberStatus("missing_spout_fluid");
                        return null;
                    }
                    long supplyAmount = availableInput > 0 ? Math.min(availableInput, amount) : 0;
                    if (supplyAmount > 0) {
                        FluidStack fluid = takeMatchingFluid(patternInputs, step.fluid(), supplyAmount);
                        if (fluid.isEmpty()) {
                            rememberStatus("missing_spout_fluid");
                            return null;
                        }
                        actions.add(new FluidSupplyAction(spout, fluid));
                    }
                    FluidStack refill = exampleFluidStack(step.fluid(), amount);
                    if (refill.isEmpty()) {
                        rememberStatus("missing_spout_fluid");
                        return null;
                    }
                    addRefillInput(refillInputs, refill);
                    fillIdx++;
                }
                default -> {
                }
            }
        }

        if (!patternInputs.isEmpty()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] pattern has unused input(s): {}", getBlockPos(),
                    patternInputs);
            rememberStatus("unused_inputs");
            return null;
        }

        var allActions = new ArrayList<SupplyAction>(returnActions.size() + actions.size());
        allActions.addAll(returnActions);
        allActions.addAll(actions);
        return new PlanContext(match.id(), primaryOutputKey, primary.amount(), inputPos, outputPos,
                transitionalItemKey, plan.steps().size(), plan.loops(), allActions, refillInputs);
    }

    protected final boolean tickDistributorJob() {
        if (activeJobs.isEmpty() || level == null || level.isClientSide()) {
            return false;
        }

        boolean didWork = false;
        boolean completed = false;
        for (int i = 0; i < activeJobs.size(); i++) {
            DistributionJob job = activeJobs.get(i);
            didWork = tickJob(job) || didWork;
            if (job.primaryRemaining <= 0) {
                CreatePackage.LOGGER.info("[Distributor @ {}] completed recipe {}", getBlockPos(), job.recipeId);
                activeJobs.remove(i--);
                completed = true;
            }
        }
        if (completed) {
            lastStatusKey = activeJobs.isEmpty() ? "completed" : "working";
            saveAndSync();
            didWork = true;
        }
        return didWork;
    }

    private boolean tickJob(DistributionJob job) {
        job.ticks++;
        job.roundTicks++;
        boolean waitingForRefill = job.waitingForRefill();
        boolean waitingForReflow = job.waitingForReflow();
        boolean didWork = collectOutput(job);
        if (job.primaryRemaining <= 0) {
            return true;
        }
        if (waitingForRefill) {
            didWork = retryRefillRound(job) || didWork;
        } else if (!waitingForReflow && job.awaitingFinalOutput && !job.roundHasOutput
                && job.roundTicks > emptyOutputRefillTimeoutTicks()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] no output appeared for recipe {} after {} ticks; treating "
                    + "this round as an empty probability result", getBlockPos(), job.recipeId,
                    job.roundTicks);
            didWork = startRefillRound(job, "empty_output") || didWork;
        }
        if (job.ticks > MAX_JOB_TICKS) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] timed out waiting for {} x{} from recipe {}",
                    getBlockPos(), job.primaryOutput, job.primaryRemaining, job.recipeId);
            job.ticks = 0;
            job.statusKey = "timeout";
            refreshLastStatus();
            saveAndSync();
        }
        return didWork;
    }

    private boolean retryRefillRound(DistributionJob job) {
        if (job.roundTicks % REFILL_RETRY_TICKS != 0) {
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
        if (otherCollected > 0 && primaryCollected == 0 && job.primaryRemaining > 0) {
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

    @Nullable
    private NetworkItemStack findNetworkItem(net.minecraft.world.item.crafting.Ingredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getLongValue() < amount || !(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            ItemStack stack = itemKey.toStack((int) amount);
            if (ingredient.test(stack)) {
                return new NetworkItemStack(itemKey, stack);
            }
        }
        return null;
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
                if (!"waiting_refill_inputs".equals(job.statusKey)) {
                    CreatePackage.LOGGER.warn("[Distributor @ {}] cannot refill recipe {} after {}; missing {} x{}",
                            getBlockPos(), job.recipeId, reason, input.what(), input.amount() - available);
                }
                rememberJobStatus(job, "waiting_refill_inputs");
                return false;
            }
        }

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
                CreatePackage.LOGGER.warn("[Distributor @ {}] refill extraction changed during execution for {} "
                        + "({} < {})", getBlockPos(), input.what(), extracted, input.amount());
                if (extracted > 0) {
                    insertIntoNetwork(input.what(), extracted, Actionable.MODULATE);
                }
                rememberJobStatus(job, "waiting_refill_inputs");
                return false;
            }
        }

        if (!context.execute()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] refill insertion failed after extraction for recipe {}",
                    getBlockPos(), job.recipeId);
            for (GenericStack input : context.inputs()) {
                insertIntoNetwork(input.what(), input.amount(), Actionable.MODULATE);
            }
            rememberJobStatus(job, "refill_target_full");
            return false;
        }

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
        List<GenericStack> inputs = new ArrayList<>();
        ItemStack inputStack = takeMatchingItem(remaining, plan.baseInput(), 1);
        if (inputStack.isEmpty()) {
            rememberJobStatus(job, "missing_base");
            return null;
        }
        actions.add(new ItemSupplyAction(inputPos, inputStack));
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
                            NetworkItemStack held = findNetworkItem(step.heldItem(), 1);
                            if (held == null) {
                                rememberJobStatus(job, "missing_deployer_item");
                                return null;
                            }
                            actions.add(new ItemSupplyAction(deployer, held.stack()));
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
                            actions.add(new ItemSupplyAction(deployer, held));
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
                        actions.add(new FluidSupplyAction(spouts.get(fillIdx).pos(), fluid));
                        addRefillInput(inputs, fluid);
                    }
                    fillIdx++;
                }
                default -> {
                }
            }
        }

        return new RefillContext(actions, inputs);
    }

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

    private boolean canInsertAll(BlockPos target, ItemStack stack) {
        return insertItem(target, stack, true).isEmpty();
    }

    private ItemStack insertItem(BlockPos target, ItemStack stack, boolean simulate) {
        IItemHandler handler = itemHandler(target);
        if (handler == null) {
            return stack.copy();
        }
        return insertIntoHandler(handler, stack, simulate);
    }

    private ItemStack insertIntoHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private long countMatchingItem(BlockPos pos, net.minecraft.world.item.crafting.Ingredient ingredient) {
        IItemHandler handler = itemHandler(pos);
        if (handler == null || ingredient == null) {
            return 0;
        }
        long amount = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ingredient.test(stack)) {
                amount += stack.getCount();
            }
        }
        return amount;
    }

    private long countMatchingFluid(BlockPos pos,
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient) {
        IFluidHandler handler = fluidHandler(pos);
        if (handler == null || ingredient == null) {
            return 0;
        }
        long amount = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (ingredient.ingredient().test(stack)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

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

    protected final boolean hasDistributorJob() {
        return !activeJobs.isEmpty();
    }

    private boolean routeOverlapsActiveJob(List<BlockPos> route) {
        if (route.isEmpty() || activeJobs.isEmpty()) {
            return false;
        }
        for (DistributionJob job : activeJobs) {
            for (BlockPos pos : route) {
                if (job.route.contains(pos)) {
                    return true;
                }
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
                case "working", "waiting_reflow_input", "waiting_refill_inputs", "refill_target_full", "busy" -> "ready";
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

    private static int emptyOutputRefillTimeoutTicks() {
        try {
            return Config.EMPTY_OUTPUT_REFILL_TIMEOUT_TICKS.get();
        } catch (IllegalStateException ignored) {
            return ROUND_OUTPUT_TIMEOUT_TICKS;
        }
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
            case "busy", "waiting_reflow_input" -> ChatFormatting.YELLOW;
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
        AEItemKey key = AEItemKey.of(stack);
        if (key != null) {
            addRefillInput(inputs, new GenericStack(key, stack.getCount()));
        }
    }

    private static void addRefillInput(List<GenericStack> inputs, FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        if (key != null) {
            addRefillInput(inputs, new GenericStack(key, stack.getAmount()));
        }
    }

    private static void addRefillInput(List<GenericStack> inputs, GenericStack stack) {
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack existing = inputs.get(i);
            if (existing.what().equals(stack.what())) {
                inputs.set(i, new GenericStack(existing.what(), existing.amount() + stack.amount()));
                return;
            }
        }
        inputs.add(stack);
    }

    private static List<GenericStack> copyStacks(List<GenericStack> inputs) {
        return new ArrayList<>(inputs);
    }

    private static ItemStack exampleItemStack(net.minecraft.world.item.crafting.Ingredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = items[0].copy();
        stack.setCount((int) amount);
        return stack;
    }

    private static FluidStack exampleFluidStack(
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return FluidStack.EMPTY;
        }
        FluidStack[] fluids = ingredient.getFluids();
        if (fluids.length == 0) {
            return FluidStack.EMPTY;
        }
        return fluids[0].copyWithAmount((int) amount);
    }

    private static List<GenericStack> flattenInputs(KeyCounter[] inputs) {
        var result = new ArrayList<GenericStack>();
        for (KeyCounter counter : inputs) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0) {
                    addRefillInput(result, new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        return result;
    }

    private static List<ItemStack> itemInputs(List<GenericStack> inputs) {
        var result = new ArrayList<ItemStack>();
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEItemKey itemKey) {
                result.add(itemKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE)));
            }
        }
        return result;
    }

    private static long countMatchingInput(List<GenericStack> inputs,
            net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient == null) {
            return 0;
        }
        long amount = 0;
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEItemKey itemKey && ingredient.test(itemKey.toStack(1))) {
                amount += input.amount();
            }
        }
        return amount;
    }

    private static long countMatchingInput(List<GenericStack> inputs,
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient) {
        if (ingredient == null) {
            return 0;
        }
        long amount = 0;
        for (GenericStack input : inputs) {
            if (input.what() instanceof AEFluidKey fluidKey) {
                FluidStack stack = fluidKey.toStack((int) Math.min(input.amount(), Integer.MAX_VALUE));
                if (ingredient.ingredient().test(stack)) {
                    amount += input.amount();
                }
            }
        }
        return amount;
    }

    private static ItemStack takeMatchingItem(List<GenericStack> inputs,
            net.minecraft.world.item.crafting.Ingredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEItemKey itemKey && input.amount() >= amount) {
                ItemStack stack = itemKey.toStack((int) amount);
                if (ingredient.test(stack)) {
                    removeAmount(inputs, i, amount);
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private void returnAllMatchingInputs(List<GenericStack> inputs,
            net.minecraft.world.item.crafting.Ingredient ingredient, List<SupplyAction> actions) {
        if (ingredient == null) {
            return;
        }
        for (int i = inputs.size() - 1; i >= 0; i--) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEItemKey itemKey && ingredient.test(itemKey.toStack(1))) {
                actions.add(new NetworkReturnAction(input));
                inputs.remove(i);
            }
        }
    }

    private static FluidStack takeMatchingFluid(List<GenericStack> inputs,
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient, long amount) {
        if (ingredient == null || amount <= 0 || amount > Integer.MAX_VALUE) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input.what() instanceof AEFluidKey fluidKey && input.amount() >= amount) {
                FluidStack stack = fluidKey.toStack((int) amount);
                if (ingredient.ingredient().test(stack)) {
                    removeAmount(inputs, i, amount);
                    return stack;
                }
            }
        }
        return FluidStack.EMPTY;
    }

    private static void removeAmount(List<GenericStack> inputs, int index, long amount) {
        GenericStack old = inputs.get(index);
        long remaining = old.amount() - amount;
        if (remaining <= 0) {
            inputs.remove(index);
        } else {
            inputs.set(index, new GenericStack(old.what(), remaining));
        }
    }

    private static void consumeInputs(KeyCounter[] inputs) {
        for (KeyCounter counter : inputs) {
            counter.clear();
        }
    }

    private record PlanContext(
            ResourceLocation recipeId,
            AEItemKey primaryOutputKey,
            long primaryOutputAmount,
            BlockPos inputPos,
            BlockPos outputPos,
            AEItemKey transitionalItemKey,
            int sequenceLength,
            int loops,
            List<SupplyAction> actions,
            List<GenericStack> refillInputs) {

        boolean simulate() {
            return actions.stream().allMatch(action -> action.perform(true));
        }

        boolean execute() {
            return actions.stream().allMatch(action -> action.perform(false));
        }
    }

    private record RefillContext(List<SupplyAction> actions, List<GenericStack> inputs) {
        boolean simulate() {
            return actions.stream().allMatch(action -> action.perform(true));
        }

        boolean execute() {
            return actions.stream().allMatch(action -> action.perform(false));
        }
    }

    private interface SupplyAction {
        boolean perform(boolean simulate);
    }

    private record AeStatus(boolean nodePresent, boolean powered, boolean channel, boolean active) {
    }

    private record NetworkItemStack(AEItemKey key, ItemStack stack) {
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

    private final class NetworkItemSupplyAction implements SupplyAction {
        private final BlockPos target;
        private final AEItemKey key;
        private final ItemStack stack;

        private NetworkItemSupplyAction(BlockPos target, AEItemKey key, ItemStack stack) {
            this.target = target;
            this.key = key;
            this.stack = stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            long amount = stack.getCount();
            if (extractFromNetwork(key, amount, Actionable.SIMULATE) < amount) {
                return false;
            }
            if (!canInsertAll(target, stack)) {
                return false;
            }
            if (simulate) {
                return true;
            }
            long extracted = extractFromNetwork(key, amount, Actionable.MODULATE);
            if (extracted < amount) {
                if (extracted > 0) {
                    insertIntoNetwork(key, extracted, Actionable.MODULATE);
                }
                return false;
            }
            ItemStack remaining = insertItem(target, stack, false);
            if (!remaining.isEmpty()) {
                insertIntoNetwork(key, remaining.getCount(), Actionable.MODULATE);
                return false;
            }
            return true;
        }
    }

    private final class NetworkReturnAction implements SupplyAction {
        private final GenericStack stack;

        private NetworkReturnAction(GenericStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean perform(boolean simulate) {
            var mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
            return insertIntoNetwork(stack.what(), stack.amount(), mode) >= stack.amount();
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
        private final List<BlockPos> route;
        private int ticks;
        private int roundTicks;
        private long roundsStarted;
        private boolean roundHasOutput;
        private boolean awaitingFinalOutput;
        private String statusKey = "working";

        private DistributionJob(ResourceLocation recipeId, AEItemKey primaryOutput, long primaryRemaining,
                BlockPos inputPos, BlockPos outputPos, AEItemKey transitionalItem, int sequenceLength, int loops,
                List<GenericStack> refillInputs, List<BlockPos> route) {
            this.recipeId = Objects.requireNonNull(recipeId);
            this.primaryOutput = Objects.requireNonNull(primaryOutput);
            this.primaryRemaining = primaryRemaining;
            this.inputPos = inputPos.immutable();
            this.outputPos = outputPos.immutable();
            this.transitionalItem = Objects.requireNonNull(transitionalItem);
            this.sequenceLength = sequenceLength;
            this.loops = loops;
            this.refillInputs = List.copyOf(refillInputs);
            this.route = route.stream().map(BlockPos::immutable).toList();
            this.awaitingFinalOutput = loops <= 1;
        }

        private boolean waitingForRefill() {
            return "waiting_refill_inputs".equals(statusKey) || "refill_target_full".equals(statusKey);
        }

        private boolean waitingForReflow() {
            return "waiting_reflow_input".equals(statusKey);
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
            BlockPos outputPos = BlockPos.of(tag.getLong(NBT_OUTPUT_POS));
            BlockPos inputPos = tag.contains(NBT_INPUT_POS) ? BlockPos.of(tag.getLong(NBT_INPUT_POS)) : outputPos;
            List<BlockPos> route = new ArrayList<>();
            for (long packed : tag.getLongArray(NBT_ROUTE)) {
                route.add(BlockPos.of(packed));
            }
            var job = new DistributionJob(recipe, output, tag.getLong(NBT_REMAINING), inputPos, outputPos,
                    transitional,
                    tag.contains(NBT_SEQUENCE_LENGTH) ? tag.getInt(NBT_SEQUENCE_LENGTH) : 1,
                    tag.contains(NBT_LOOPS) ? tag.getInt(NBT_LOOPS) : 1,
                    refillInputs,
                    route);
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
