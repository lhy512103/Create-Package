package com.lhy.createpackage.content.distributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.recipe.AssemblyPlan;
import com.lhy.createpackage.content.recipe.AssemblyRecipeMatcher;
import com.lhy.createpackage.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
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
public class PackageDistributorBlockEntity extends AENetworkedBlockEntity implements ICraftingMachine, IActionHost {

    private static final String NBT_LINKED_MACHINES = "linkedMachines";
    private static final String NBT_JOB = "job";
    private static final int MAX_JOB_TICKS = 20 * 60 * 10;

    /**
     * Ordered list of linked Create machines (depot / deployers / spouts), in the physical sequence
     * the player linked them. The order maps directly onto the sequenced-assembly recipe steps.
     * Positions are absolute and share this block entity's dimension.
     */
    private final List<BlockPos> linkedMachines = new ArrayList<>();
    private final MachineSource actionSource = new MachineSource(this);

    private DistributionJob currentJob;

    public PackageDistributorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PACKAGE_DISTRIBUTOR.get(), pos, blockState);

        // Require a channel like most AE2 machines; small idle draw to mark it as an active device.
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(IGridTickable.class, new Ticker())
                .setIdlePowerUsage(1.0);
    }

    // === Linked machine management ===

    /** Immutable view of the linked machines, in link order. */
    public List<BlockPos> getLinkedMachines() {
        return Collections.unmodifiableList(linkedMachines);
    }

    public boolean isLinked(BlockPos pos) {
        return linkedMachines.contains(pos);
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
        setChanged();
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
            setChanged();
        }
        return removed;
    }

    /** Clears all links. Returns the number of machines that were unlinked. */
    public int clearLinks() {
        int count = linkedMachines.size();
        if (count > 0) {
            linkedMachines.clear();
            setChanged();
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
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        linkedMachines.clear();
        for (long packed : data.getLongArray(NBT_LINKED_MACHINES)) {
            linkedMachines.add(BlockPos.of(packed));
        }
        currentJob = data.contains(NBT_JOB) ? DistributionJob.read(data.getCompound(NBT_JOB), registries) : null;
    }

    // === ICraftingMachine ===

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(
                AEItemKey.of(getItemFromBlockEntity()),
                Component.translatable("block." + CreatePackage.MODID + ".package_distributor"),
                java.util.List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        if (!acceptsPlans()) {
            return false;
        }

        if (level == null || level.isClientSide()) {
            return false;
        }

        PlanContext context = createPlan(patternDetails, inputs);
        if (context == null) {
            return false;
        }

        if (!context.simulate()) {
            return false;
        }

        if (!context.execute()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] simulated plan but execution failed; refusing pattern",
                    getBlockPos());
            return false;
        }

        currentJob = new DistributionJob(context.recipeId(), context.primaryOutputKey(),
                context.primaryOutputAmount(), context.outputPos());
        consumeInputs(inputs);
        setChanged();
        wakeTicker();

        CreatePackage.LOGGER.info("[Distributor @ {}] accepted recipe {} waiting for {} x{} at {}",
                getBlockPos(), context.recipeId(), context.primaryOutputKey(), context.primaryOutputAmount(),
                context.outputPos());
        return true;
    }

    @Override
    public boolean acceptsPlans() {
        return currentJob == null && getMainNode().isActive();
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    private PlanContext createPlan(IPatternDetails patternDetails, KeyCounter[] inputs) {
        var patternInputs = flattenInputs(inputs);
        var primary = patternDetails.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutputKey)) {
            return null;
        }

        ItemStack primaryOutput = primaryOutputKey.toStack((int) Math.min(primary.amount(), Integer.MAX_VALUE));
        var candidates = AssemblyRecipeMatcher.findCandidates(level, primaryOutput, itemInputs(patternInputs));
        if (candidates.size() != 1) {
            if (candidates.size() > 1) {
                CreatePackage.LOGGER.warn("[Distributor @ {}] ambiguous sequenced assembly recipes for {}: {}",
                        getBlockPos(), primaryOutput,
                        candidates.stream().map(AssemblyRecipeMatcher.Match::id).toList());
            }
            return null;
        }

        var match = candidates.get(0);
        AssemblyPlan plan = AssemblyPlan.of(match.recipe());
        LinkedMachines machines = LinkedMachines.resolve(level, linkedMachines);

        var inputPos = machines.inputDepot();
        var outputPos = machines.outputDepot();
        if (inputPos == null || outputPos == null) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] needs linked input and output depot/belt", getBlockPos());
            return null;
        }
        if (inputPos.equals(outputPos)) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] input and output depot/belt must be different", getBlockPos());
            return null;
        }

        var inputStack = takeMatchingItem(patternInputs, plan.baseInput(), 1);
        if (inputStack.isEmpty()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] missing base input for {}", getBlockPos(), match.id());
            return null;
        }

        var actions = new ArrayList<SupplyAction>();
        actions.add(new ItemSupplyAction(inputPos, inputStack));

        var deployers = machines.withRole(LinkedMachines.Role.DEPLOYER);
        var spouts = machines.withRole(LinkedMachines.Role.SPOUT);
        int deployIdx = 0;
        int fillIdx = 0;
        for (AssemblyPlan.Step step : plan.consumingSteps()) {
            switch (step.type()) {
                case DEPLOY -> {
                    if (deployIdx >= deployers.size()) {
                        return null;
                    }
                    BlockPos deployer = deployers.get(deployIdx).pos();
                    if (!step.keepHeld() || !hasMatchingItem(deployer, step.heldItem())) {
                        long amount = step.keepHeld() ? 1L : plan.loops();
                        ItemStack held = takeMatchingItem(patternInputs, step.heldItem(), amount);
                        if (held.isEmpty()) {
                            return null;
                        }
                        actions.add(new ItemSupplyAction(deployer, held));
                    }
                    deployIdx++;
                }
                case FILL -> {
                    if (fillIdx >= spouts.size()) {
                        return null;
                    }
                    long amount = (long) plan.loops() * step.fluid().amount();
                    FluidStack fluid = takeMatchingFluid(patternInputs, step.fluid(), amount);
                    if (fluid.isEmpty()) {
                        return null;
                    }
                    actions.add(new FluidSupplyAction(spouts.get(fillIdx).pos(), fluid));
                    fillIdx++;
                }
                default -> {
                }
            }
        }

        if (!patternInputs.isEmpty()) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] pattern has unused input(s): {}", getBlockPos(),
                    patternInputs);
            return null;
        }

        return new PlanContext(match.id(), primaryOutputKey, primary.amount(), outputPos, actions);
    }

    private boolean tickJob() {
        if (currentJob == null || level == null || level.isClientSide()) {
            return false;
        }

        currentJob.ticks++;
        boolean didWork = collectOutput();
        if (currentJob.primaryRemaining <= 0) {
            CreatePackage.LOGGER.info("[Distributor @ {}] completed recipe {}", getBlockPos(), currentJob.recipeId);
            currentJob = null;
            setChanged();
            return true;
        }
        if (currentJob.ticks > MAX_JOB_TICKS) {
            CreatePackage.LOGGER.warn("[Distributor @ {}] timed out waiting for {} x{} from recipe {}",
                    getBlockPos(), currentJob.primaryOutput, currentJob.primaryRemaining, currentJob.recipeId);
            currentJob.ticks = 0;
            setChanged();
        }
        return didWork;
    }

    private boolean collectOutput() {
        IItemHandler handler = itemHandler(currentJob.outputPos);
        if (handler == null) {
            return false;
        }

        boolean didWork = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack available = handler.extractItem(slot, 64, true);
            if (available.isEmpty()) {
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

            if (extractedKey.equals(currentJob.primaryOutput)) {
                currentJob.primaryRemaining -= inserted;
                if (currentJob.primaryRemaining < 0) {
                    currentJob.primaryRemaining = 0;
                }
            }
            didWork = inserted > 0 || didWork;
        }
        if (didWork) {
            setChanged();
        }
        return didWork;
    }

    private long insertIntoNetwork(AEKey what, long amount, Actionable mode) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        return grid.getStorageService().getInventory().insert(what, amount, mode, actionSource);
    }

    private IItemHandler itemHandler(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    }

    private boolean hasMatchingItem(BlockPos pos, net.minecraft.world.item.crafting.Ingredient ingredient) {
        IItemHandler handler = itemHandler(pos);
        if (handler == null) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (ingredient.test(handler.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
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

    private void wakeTicker() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
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
            BlockPos outputPos,
            List<SupplyAction> actions) {

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

    private final class ItemSupplyAction implements SupplyAction {
        private final BlockPos target;
        private final ItemStack stack;

        private ItemSupplyAction(BlockPos target, ItemStack stack) {
            this.target = target;
            this.stack = stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            IItemHandler handler = itemHandler(target);
            if (handler == null) {
                return false;
            }
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, simulate);
            }
            return remaining.isEmpty();
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
        private static final String NBT_OUTPUT_POS = "outputPos";
        private static final String NBT_TICKS = "ticks";

        private final ResourceLocation recipeId;
        private final AEItemKey primaryOutput;
        private long primaryRemaining;
        private final BlockPos outputPos;
        private int ticks;

        private DistributionJob(ResourceLocation recipeId, AEItemKey primaryOutput, long primaryRemaining,
                BlockPos outputPos) {
            this.recipeId = Objects.requireNonNull(recipeId);
            this.primaryOutput = Objects.requireNonNull(primaryOutput);
            this.primaryRemaining = primaryRemaining;
            this.outputPos = outputPos.immutable();
        }

        private CompoundTag write(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_RECIPE, recipeId.toString());
            tag.put(NBT_OUTPUT, primaryOutput.toTag(registries));
            tag.putLong(NBT_REMAINING, primaryRemaining);
            tag.putLong(NBT_OUTPUT_POS, outputPos.asLong());
            tag.putInt(NBT_TICKS, ticks);
            return tag;
        }

        private static DistributionJob read(CompoundTag tag, HolderLookup.Provider registries) {
            ResourceLocation recipe = ResourceLocation.parse(tag.getString(NBT_RECIPE));
            AEItemKey output = AEItemKey.fromTag(registries, tag.getCompound(NBT_OUTPUT));
            if (output == null) {
                return null;
            }
            var job = new DistributionJob(recipe, output, tag.getLong(NBT_REMAINING),
                    BlockPos.of(tag.getLong(NBT_OUTPUT_POS)));
            job.ticks = tag.getInt(NBT_TICKS);
            return job;
        }
    }

    private final class Ticker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(5, 20, currentJob == null);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (currentJob == null) {
                return TickRateModulation.SLEEP;
            }
            return tickJob() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
    }
}
