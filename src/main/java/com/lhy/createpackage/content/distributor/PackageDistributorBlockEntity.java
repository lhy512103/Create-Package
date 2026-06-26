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

    private DistributionJob currentJob;
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
        return currentJob != null;
    }

    public String getStatusKey() {
        return lastStatusKey;
    }

    public long getPrimaryRemaining() {
        return currentJob == null ? 0 : currentJob.primaryRemaining;
    }

    @Nullable
    public ResourceLocation getCurrentRecipeId() {
        return currentJob == null ? null : currentJob.recipeId;
    }

    public Component getCurrentJobName() {
        return currentJob == null ? CommonComponents.EMPTY : currentJob.primaryOutput.getDisplayName();
    }

    public long getRoundsStarted() {
        return currentJob == null ? 0 : currentJob.roundsStarted;
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
        if (currentJob != null) {
            data.put(NBT_JOB, currentJob.write(registries));
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
        currentJob = data.contains(NBT_JOB) ? DistributionJob.read(data.getCompound(NBT_JOB), registries) : null;
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
        data.writeBoolean(currentJob != null);
        if (currentJob != null) {
            data.writeResourceLocation(currentJob.recipeId);
            currentJob.primaryOutput.writeToPacket(data);
            data.writeVarLong(currentJob.primaryRemaining);
            data.writeBlockPos(currentJob.inputPos);
            data.writeBlockPos(currentJob.outputPos);
            currentJob.transitionalItem.writeToPacket(data);
            data.writeVarInt(currentJob.sequenceLength);
            data.writeVarInt(currentJob.loops);
            data.writeVarInt(currentJob.ticks);
            data.writeVarInt(currentJob.roundTicks);
            data.writeVarLong(currentJob.roundsStarted);
            data.writeBoolean(currentJob.roundHasOutput);
            data.writeBoolean(currentJob.awaitingFinalOutput);
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

        DistributionJob streamedJob = null;
        if (data.readBoolean()) {
            ResourceLocation recipeId = data.readResourceLocation();
            AEItemKey primaryOutput = AEItemKey.fromPacket(data);
            long primaryRemaining = data.readVarLong();
            BlockPos inputPos = data.readBlockPos();
            BlockPos outputPos = data.readBlockPos();
            AEItemKey transitionalItem = AEItemKey.fromPacket(data);
            int sequenceLength = data.readVarInt();
            int loops = data.readVarInt();
            streamedJob = new DistributionJob(recipeId, primaryOutput, primaryRemaining, inputPos, outputPos,
                    transitionalItem, sequenceLength, loops, List.of());
            streamedJob.ticks = data.readVarInt();
            streamedJob.roundTicks = data.readVarInt();
            streamedJob.roundsStarted = data.readVarLong();
            streamedJob.roundHasOutput = data.readBoolean();
            streamedJob.awaitingFinalOutput = data.readBoolean();
        }
        if (!sameJob(currentJob, streamedJob)) {
            currentJob = streamedJob;
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
        if (currentJob != null) {
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

        PlanContext context = createPlan(patternDetails, inputs);
        if (context == null) {
            return false;
        }

        if (!context.simulate()) {
            rememberStatus("simulate_failed");
            return false;
        }

        if (!context.execute()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] simulated plan but execution failed; refusing pattern",
                    getBlockPos());
            rememberStatus("execute_failed");
            return false;
        }

        currentJob = new DistributionJob(context.recipeId(), context.primaryOutputKey(),
                context.primaryOutputAmount(), context.inputPos(), context.outputPos(), context.transitionalItemKey(),
                context.sequenceLength(), context.loops(), context.refillInputs());
        currentJob.roundsStarted = 1;
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
        return currentJob == null;
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

    private PlanContext createPlan(IPatternDetails patternDetails, KeyCounter[] inputs) {
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
        LinkedMachines machines = LinkedMachines.resolve(level, linkedMachines);

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
                        if (existing <= 0 && countMatchingInput(patternInputs, step.heldItem()) <= 0) {
                            rememberStatus("missing_deployer_item");
                            return null;
                        }
                        if (existing <= 0) {
                            ItemStack held = takeMatchingItem(patternInputs, step.heldItem(), 1);
                            if (held.isEmpty()) {
                                rememberStatus("missing_deployer_item");
                                return null;
                            }
                            actions.add(new ItemSupplyAction(deployer, held));
                        }
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

        return new PlanContext(match.id(), primaryOutputKey, primary.amount(), inputPos, outputPos,
                transitionalItemKey, plan.steps().size(), plan.loops(), actions, refillInputs);
    }

    protected final boolean tickDistributorJob() {
        if (currentJob == null || level == null || level.isClientSide()) {
            return false;
        }

        currentJob.ticks++;
        currentJob.roundTicks++;
        boolean waitingForRefill = "waiting_refill_inputs".equals(lastStatusKey)
                || "refill_target_full".equals(lastStatusKey);
        boolean waitingForReflow = "waiting_reflow_input".equals(lastStatusKey);
        boolean didWork = collectOutput();
        if (currentJob.primaryRemaining <= 0) {
            CreatePackage.LOGGER.info("[Distributor @ {}] completed recipe {}", getBlockPos(), currentJob.recipeId);
            currentJob = null;
            lastStatusKey = "completed";
            saveAndSync();
            return true;
        }
        if (waitingForRefill) {
            didWork = retryRefillRound() || didWork;
        } else if (!waitingForReflow && currentJob.awaitingFinalOutput && !currentJob.roundHasOutput
                && currentJob.roundTicks > emptyOutputRefillTimeoutTicks()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] no output appeared for recipe {} after {} ticks; treating "
                    + "this round as an empty probability result", getBlockPos(), currentJob.recipeId,
                    currentJob.roundTicks);
            didWork = startRefillRound("empty_output") || didWork;
        }
        if (currentJob.ticks > MAX_JOB_TICKS) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] timed out waiting for {} x{} from recipe {}",
                    getBlockPos(), currentJob.primaryOutput, currentJob.primaryRemaining, currentJob.recipeId);
            currentJob.ticks = 0;
            lastStatusKey = "timeout";
            saveAndSync();
        }
        return didWork;
    }

    private boolean retryRefillRound() {
        if (currentJob.roundTicks % REFILL_RETRY_TICKS != 0) {
            return false;
        }
        return startRefillRound(lastStatusKey);
    }

    private boolean collectOutput() {
        IItemHandler handler = itemHandler(currentJob.outputPos);
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

            if (isCurrentTransitional(available)) {
                didWork = reflowTransitionalOutput(handler, slot, available) || didWork;
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

            currentJob.roundHasOutput = true;
            if (extractedKey.equals(currentJob.primaryOutput)) {
                currentJob.primaryRemaining -= inserted;
                primaryCollected += inserted;
                if (currentJob.primaryRemaining < 0) {
                    currentJob.primaryRemaining = 0;
                }
            } else {
                otherCollected += inserted;
            }
            didWork = inserted > 0 || didWork;
        }
        if (otherCollected > 0 && primaryCollected == 0 && currentJob.primaryRemaining > 0) {
            didWork = startRefillRound("secondary_output") || didWork;
        }
        if (didWork) {
            saveAndSync();
        }
        return didWork;
    }

    private boolean reflowTransitionalOutput(IItemHandler outputHandler, int slot, ItemStack available) {
        if (currentJob == null) {
            return false;
        }
        if (!canInsertAll(currentJob.inputPos, available)) {
            rememberStatus("waiting_reflow_input");
            return false;
        }

        ItemStack extracted = outputHandler.extractItem(slot, available.getCount(), false);
        if (extracted.isEmpty()) {
            return false;
        }

        if (!isCurrentTransitional(extracted)) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] output slot changed while reflowing sequenced item for {}",
                    getBlockPos(), currentJob.recipeId);
            return false;
        }

        boolean nextPassIsFinal = nextPassIsFinal(extracted);
        ItemStack remaining = insertItem(currentJob.inputPos, extracted, false);
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
            rememberStatus("waiting_reflow_input");
            return true;
        }

        currentJob.awaitingFinalOutput = nextPassIsFinal;
        currentJob.roundHasOutput = false;
        currentJob.roundTicks = 0;
        lastStatusKey = "working";
        CreatePackage.LOGGER.debug("[Distributor @ {}] reflowed transitional item for recipe {} to {} (final pass: {})",
                getBlockPos(), currentJob.recipeId, currentJob.inputPos, nextPassIsFinal);
        return true;
    }

    private boolean isCurrentTransitional(ItemStack stack) {
        if (currentJob == null || stack.isEmpty() || !stack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
            return false;
        }
        if (stack.getItem() != currentJob.transitionalItem.getReadOnlyStack().getItem()) {
            return false;
        }
        SequencedAssemblyRecipe.SequencedAssembly assembly = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        return assembly != null && assembly.id().equals(currentJob.recipeId);
    }

    private boolean nextPassIsFinal(ItemStack stack) {
        if (currentJob == null || currentJob.sequenceLength <= 0 || currentJob.loops <= 1) {
            return true;
        }
        SequencedAssemblyRecipe.SequencedAssembly assembly = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if (assembly == null) {
            return false;
        }
        return assembly.step() >= currentJob.sequenceLength * (currentJob.loops - 1);
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

    private boolean startRefillRound(String reason) {
        if (currentJob == null) {
            return false;
        }
        if (currentJob.refillInputs.isEmpty()) {
            rememberStatus("refill_unavailable");
            return false;
        }

        RefillContext context = createRefillContext(currentJob.refillInputs);
        if (context == null) {
            return false;
        }

        for (GenericStack input : context.inputs()) {
            long available = extractFromNetwork(input.what(), input.amount(), Actionable.SIMULATE);
            if (available < input.amount()) {
                if (!"waiting_refill_inputs".equals(lastStatusKey)) {
                    CreatePackage.LOGGER.warn("[Distributor @ {}] cannot refill recipe {} after {}; missing {} x{}",
                            getBlockPos(), currentJob.recipeId, reason, input.what(), input.amount() - available);
                }
                rememberStatus("waiting_refill_inputs");
                return false;
            }
        }

        if (!context.simulate()) {
            if (!"refill_target_full".equals(lastStatusKey)) {
                CreatePackage.LOGGER.warn("[Distributor @ {}] cannot refill recipe {} after {}; target cannot accept "
                        + "inputs yet", getBlockPos(), currentJob.recipeId, reason);
            }
            rememberStatus("refill_target_full");
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
                rememberStatus("waiting_refill_inputs");
                return false;
            }
        }

        if (!context.execute()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] refill insertion failed after extraction for recipe {}",
                    getBlockPos(), currentJob.recipeId);
            for (GenericStack input : context.inputs()) {
                insertIntoNetwork(input.what(), input.amount(), Actionable.MODULATE);
            }
            rememberStatus("refill_target_full");
            return false;
        }

        currentJob.roundsStarted++;
        currentJob.roundTicks = 0;
        currentJob.roundHasOutput = false;
        currentJob.awaitingFinalOutput = currentJob.loops <= 1;
        lastStatusKey = "working";
        CreatePackage.LOGGER.info("[Distributor @ {}] refilled recipe {} after {} (round {})",
                getBlockPos(), currentJob.recipeId, reason, currentJob.roundsStarted);
        saveAndSync();
        wakeTicker();
        return true;
    }

    @Nullable
    private RefillContext createRefillContext(List<GenericStack> refillInputs) {
        if (level == null) {
            return null;
        }
        var recipe = level.getRecipeManager().byKey(currentJob.recipeId);
        if (recipe.isEmpty() || !(recipe.get().value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe sequenced)) {
            rememberStatus("no_matching_recipe");
            return null;
        }

        AssemblyPlan plan = AssemblyPlan.of(sequenced);
        LinkedMachines machines = LinkedMachines.resolve(level, linkedMachines);
        BlockPos inputPos = machines.inputDepot();
        if (inputPos == null) {
            rememberStatus("missing_io");
            return null;
        }

        List<GenericStack> remaining = copyStacks(refillInputs);
        List<SupplyAction> actions = new ArrayList<>();
        List<GenericStack> inputs = new ArrayList<>();
        ItemStack inputStack = takeMatchingItem(remaining, plan.baseInput(), 1);
        if (inputStack.isEmpty()) {
            rememberStatus("missing_base");
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
                        rememberStatus("missing_deployer");
                        return null;
                    }
                    BlockPos deployer = deployers.get(deployIdx).pos();
                    if (step.keepHeld()) {
                        if (countMatchingItem(deployer, step.heldItem()) <= 0) {
                            rememberStatus("missing_deployer_item");
                            return null;
                        }
                    } else {
                        long amount = plan.loops();
                        long existing = countMatchingItem(deployer, step.heldItem());
                        long supplyAmount = Math.max(0, amount - existing);
                        if (supplyAmount > 0) {
                            ItemStack held = takeMatchingItem(remaining, step.heldItem(), supplyAmount);
                            if (held.isEmpty()) {
                                rememberStatus("missing_deployer_item");
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
                        rememberStatus("missing_spout");
                        return null;
                    }
                    long amount = (long) plan.loops() * step.fluid().amount();
                    long existing = countMatchingFluid(spouts.get(fillIdx).pos(), step.fluid());
                    long supplyAmount = Math.max(0, amount - existing);
                    if (supplyAmount > 0) {
                        FluidStack fluid = takeMatchingFluid(remaining, step.fluid(), supplyAmount);
                        if (fluid.isEmpty()) {
                            rememberStatus("missing_spout_fluid");
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
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
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
        return currentJob != null;
    }

    protected void wakeTicker() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private void clearLastStatus() {
        if (currentJob == null) {
            lastStatusKey = "ready";
        }
    }

    private void rememberStatus(String statusKey) {
        if (!Objects.equals(lastStatusKey, statusKey)) {
            lastStatusKey = statusKey;
            saveAndSync();
        }
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
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.header")
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

        if (currentJob != null) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.job",
                    currentJob.primaryOutput.getDisplayName(),
                    currentJob.primaryRemaining,
                    currentJob.roundsStarted).withStyle(ChatFormatting.AQUA));
        }

        tooltip.add(CommonComponents.EMPTY);
        tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.links",
                linkedMachines.size()).withStyle(ChatFormatting.WHITE));

        if (linkedMachines.isEmpty()) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.no_links")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return true;
        }

        int shown = Math.min(linkedMachines.size(), MAX_TOOLTIP_LINKS);
        for (int i = 0; i < shown; i++) {
            tooltip.add(linkLine(i, linkedMachines.get(i)));
        }
        if (linkedMachines.size() > shown) {
            tooltip.add(Component.translatable("tooltip." + CreatePackage.MODID + ".package_distributor.more_links",
                    linkedMachines.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
        }
        return true;
    }

    private Component statusLine() {
        String status = lastStatusKey;
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
                && left.ticks == right.ticks
                && left.roundTicks == right.roundTicks
                && left.roundsStarted == right.roundsStarted
                && left.roundHasOutput == right.roundHasOutput
                && left.awaitingFinalOutput == right.awaitingFinalOutput;
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
                    result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
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
        private static final String NBT_REFILL_INPUTS = "refillInputs";
        private static final String NBT_REFILL_KEY = "key";
        private static final String NBT_REFILL_AMOUNT = "amount";

        private final ResourceLocation recipeId;
        private final AEItemKey primaryOutput;
        private long primaryRemaining;
        private final BlockPos inputPos;
        private final BlockPos outputPos;
        private final AEItemKey transitionalItem;
        private final int sequenceLength;
        private final int loops;
        private final List<GenericStack> refillInputs;
        private int ticks;
        private int roundTicks;
        private long roundsStarted;
        private boolean roundHasOutput;
        private boolean awaitingFinalOutput;

        private DistributionJob(ResourceLocation recipeId, AEItemKey primaryOutput, long primaryRemaining,
                BlockPos inputPos, BlockPos outputPos, AEItemKey transitionalItem, int sequenceLength, int loops,
                List<GenericStack> refillInputs) {
            this.recipeId = Objects.requireNonNull(recipeId);
            this.primaryOutput = Objects.requireNonNull(primaryOutput);
            this.primaryRemaining = primaryRemaining;
            this.inputPos = inputPos.immutable();
            this.outputPos = outputPos.immutable();
            this.transitionalItem = Objects.requireNonNull(transitionalItem);
            this.sequenceLength = sequenceLength;
            this.loops = loops;
            this.refillInputs = List.copyOf(refillInputs);
            this.awaitingFinalOutput = loops <= 1;
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
            ListTag refillList = new ListTag();
            for (GenericStack input : refillInputs) {
                CompoundTag inputTag = new CompoundTag();
                inputTag.put(NBT_REFILL_KEY, input.what().toTagGeneric(registries));
                inputTag.putLong(NBT_REFILL_AMOUNT, input.amount());
                refillList.add(inputTag);
            }
            tag.put(NBT_REFILL_INPUTS, refillList);
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
            var job = new DistributionJob(recipe, output, tag.getLong(NBT_REMAINING), inputPos, outputPos,
                    transitional,
                    tag.contains(NBT_SEQUENCE_LENGTH) ? tag.getInt(NBT_SEQUENCE_LENGTH) : 1,
                    tag.contains(NBT_LOOPS) ? tag.getInt(NBT_LOOPS) : 1,
                    refillInputs);
            job.ticks = tag.getInt(NBT_TICKS);
            job.roundTicks = tag.getInt(NBT_ROUND_TICKS);
            job.roundsStarted = tag.contains(NBT_ROUNDS_STARTED) ? tag.getLong(NBT_ROUNDS_STARTED) : 1;
            job.roundHasOutput = tag.getBoolean(NBT_ROUND_HAS_OUTPUT);
            job.awaitingFinalOutput = tag.contains(NBT_AWAITING_FINAL_OUTPUT)
                    ? tag.getBoolean(NBT_AWAITING_FINAL_OUTPUT)
                    : job.loops <= 1;
            return job;
        }
    }

    protected class DistributorTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(5, 20, currentJob == null);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (currentJob == null) {
                return TickRateModulation.SLEEP;
            }
            return tickDistributorJob() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
    }
}
