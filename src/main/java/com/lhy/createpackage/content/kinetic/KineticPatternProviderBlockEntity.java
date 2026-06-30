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
        implements PatternProviderLogicHost, IGridTickable, IHaveGoggleInformation {
    private static final String NBT_JOB = "kineticJob";
    private static final String NBT_STATUS = "kineticStatus";
    private static final String NBT_SMART_DOUBLING = "smartDoubling";
    private static final int MAX_JOB_TICKS = 20 * 60 * 5;
    private static final int REFILL_RETRY_TICKS = 20 * 5;
    private static final int ROUND_OUTPUT_TIMEOUT_TICKS = 20 * 60;
    private static final int MAX_SMART_BATCH_PATTERNS = 64;
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
    private final MachineSource actionSource = new MachineSource(this);

    @Nullable
    private KineticJob activeJob;
    private String lastStatusKey = "ready";
    private boolean smartDoubling;
    private boolean aeNodePresent;
    private boolean aePowered;
    private boolean aeChannel;
    private boolean aeActive;

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
        data.putString(NBT_STATUS, lastStatusKey);
        data.putBoolean(NBT_SMART_DOUBLING, smartDoubling);
        if (activeJob != null) {
            data.put(NBT_JOB, activeJob.write(registries));
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        ExtendedAePlusCompat.removeProviderSmartSettings(data);
        logic.readFromNBT(data, registries);
        ExtendedAePlusCompat.disableProviderSmartSettings(logic.getConfigManager());
        lastStatusKey = data.contains(NBT_STATUS) ? data.getString(NBT_STATUS) : "ready";
        smartDoubling = data.contains(NBT_SMART_DOUBLING) && data.getBoolean(NBT_SMART_DOUBLING);
        activeJob = data.contains(NBT_JOB) ? KineticJob.read(data.getCompound(NBT_JOB), registries) : null;
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
        data.writeUtf(lastStatusKey);
        data.writeBoolean(activeJob != null);
        if (activeJob != null) {
            activeJob.primaryOutput.writeToPacket(data);
            data.writeVarLong(activeJob.remaining);
            data.writeBlockPos(activeJob.machinePos);
            data.writeBlockPos(activeJob.outputPos);
            data.writeUtf(activeJob.machineRole);
            data.writeVarInt(activeJob.ticks);
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

        String streamedStatus = data.readUtf();
        if (!Objects.equals(lastStatusKey, streamedStatus)) {
            lastStatusKey = streamedStatus;
            changed = true;
        }

        KineticJob streamedJob = null;
        if (data.readBoolean()) {
            AEItemKey output = AEItemKey.fromPacket(data);
            long remaining = data.readVarLong();
            BlockPos machinePos = data.readBlockPos();
            BlockPos outputPos = data.readBlockPos();
            String machineRole = data.readUtf();
            streamedJob = new KineticJob(output, output, remaining, machinePos, outputPos, machineRole, List.of(),
                    List.of(), false);
            streamedJob.ticks = data.readVarInt();
        }
        if (!sameJob(activeJob, streamedJob)) {
            activeJob = streamedJob;
            changed = true;
        }
        return changed;
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
        activeJob = null;
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
        return new TickingRequest(5, 20, activeJob == null && !hasReturnInventoryWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level == null || level.isClientSide() || !getMainNode().isActive()) {
            return TickRateModulation.SLEEP;
        }
        boolean didWork = returnInventoryToNetwork();
        if (activeJob != null) {
            didWork |= tickJob();
        }
        if (activeJob != null || hasReturnInventoryWork()) {
            return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        return TickRateModulation.SLEEP;
    }

    public String getStatusKey() {
        if (activeJob == null && ("working".equals(lastStatusKey) || "busy".equals(lastStatusKey))) {
            return "ready";
        }
        return lastStatusKey;
    }

    public Component getMachineName() {
        BlockPos pos = targetMachinePos();
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
        BlockPos target = targetMachinePos();
        if (target == null) {
            return Component.translatable("tooltip." + CreatePackage.MODID
                    + ".kinetic_pattern_provider.machine_unconfigured").withStyle(ChatFormatting.RED);
        }
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.machine",
                getMachineName(),
                target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.GRAY);
    }

    public @Nullable KineticJob getActiveJob() {
        return activeJob;
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
        if (activeJob != null && !canAppendToActiveJob(patternDetails, inputs)) {
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

        GenericStack primary = patternDetails.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutput)) {
            rememberStatus("no_primary_output");
            return false;
        }

        List<GenericStack> flattenedInputs = flattenInputs(inputs);
        Plan plan = activeJob != null
                ? createPlanAt(activeJob.machinePos, primaryOutput, primary.amount(), patternDetails.getOutputs(),
                        flattenedInputs)
                : createPlan(primaryOutput, primary.amount(), patternDetails.getOutputs(), flattenedInputs);
        if (plan == null) {
            return false;
        }
        if (activeJob != null && (!Objects.equals(plan.outputPos(), activeJob.outputPos)
                || !Objects.equals(plan.machineRole(), activeJob.machineRole)
                || plan.collectUnexpectedOutputs() != activeJob.collectUnexpectedOutputs)) {
            rememberStatus("busy");
            return false;
        }
        if (!plan.simulate()) {
            rememberStatus("simulate_failed");
            return false;
        }
        if (!plan.execute()) {
            rememberStatus("execute_failed");
            return false;
        }

        consumeInputs(inputs);
        if (activeJob == null) {
            activeJob = new KineticJob(primaryOutput, patternDetails.getDefinition(), primary.amount(),
                    plan.machinePos(), plan.outputPos(), plan.machineRole(), plan.patternOutputs(),
                    plan.refillInputs(), plan.collectUnexpectedOutputs());
            activeJob.roundsStarted = 1;
        } else {
            activeJob.remaining = safeAdd(activeJob.remaining, primary.amount());
            activeJob.queuedPatterns++;
            activeJob.roundsStarted++;
            activeJob.roundHasOutput = false;
            activeJob.roundTicks = 0;
            activeJob.ticks = 0;
        }
        lastStatusKey = "working";
        saveAndSync();
        wakeTicker();
        return true;
    }

    private boolean canAppendToActiveJob(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (activeJob == null || level == null || level.isClientSide()) {
            return activeJob == null;
        }
        if (!smartDoubling) {
            return false;
        }
        GenericStack primary = patternDetails.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryOutput)) {
            return false;
        }
        if (!primaryOutput.equals(activeJob.primaryOutput)) {
            return false;
        }
        if (!Objects.equals(patternDetails.getDefinition(), activeJob.patternDefinition)) {
            return false;
        }
        if (activeJob.queuedPatterns >= MAX_SMART_BATCH_PATTERNS) {
            return false;
        }

        List<GenericStack> flattenedInputs = flattenInputs(inputs);
        Plan plan = createPlanAt(activeJob.machinePos, activeJob.primaryOutput,
                primary.amount(), patternDetails.getOutputs(), flattenedInputs);
        return plan != null
                && Objects.equals(plan.outputPos(), activeJob.outputPos)
                && Objects.equals(plan.machineRole(), activeJob.machineRole)
                && plan.collectUnexpectedOutputs() == activeJob.collectUnexpectedOutputs
                && plan.simulate();
    }

    private boolean activeJobCanAcceptOneMorePattern() {
        if (activeJob == null) {
            return true;
        }
        if (!smartDoubling) {
            return false;
        }
        if (activeJob.queuedPatterns >= MAX_SMART_BATCH_PATTERNS) {
            return false;
        }
        Plan plan = createPlanAt(activeJob.machinePos, activeJob.primaryOutput, activeJob.remaining,
                activeJob.patternOutputs, activeJob.refillInputs);
        return plan != null
                && Objects.equals(plan.outputPos(), activeJob.outputPos)
                && Objects.equals(plan.machineRole(), activeJob.machineRole)
                && plan.collectUnexpectedOutputs() == activeJob.collectUnexpectedOutputs
                && plan.simulate();
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

    private boolean tickJob() {
        if (activeJob == null || level == null || level.isClientSide()) {
            return false;
        }
        activeJob.ticks++;
        activeJob.roundTicks++;
        boolean didWork = collectOutputs(activeJob);
        if (activeJob.remaining <= 0) {
            activeJob = null;
            lastStatusKey = "completed";
            saveAndSync();
            return true;
        }
        if (isCraftingJobNoLongerWaiting(activeJob)) {
            activeJob = null;
            lastStatusKey = "cancelled";
            saveAndSync();
            return true;
        }
        if (activeJob.waitingForRefill()) {
            didWork = retryRefillRound(activeJob) || didWork;
        } else if (activeJob.roundTicks > emptyOutputRefillTimeoutTicks()) {
            didWork = startRefillRound(activeJob, activeJob.roundHasOutput ? "output_timeout" : "empty_output")
                    || didWork;
        }
        if (activeJob.ticks > MAX_JOB_TICKS) {
            CreatePackage.LOGGER.warn("[Kinetic Pattern Provider @ {}] timed out waiting for {} x{} from {} at {}",
                    getBlockPos(), activeJob.primaryOutput, activeJob.remaining, activeJob.machineRole,
                    activeJob.machinePos);
            activeJob = null;
            lastStatusKey = "timeout";
            saveAndSync();
            return true;
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
            case "busy", "waiting_refill_inputs", "refill_target_full" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.status",
                Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.status." + status)
                        .withStyle(color)).withStyle(ChatFormatting.GRAY);
    }

    public List<Component> jobLines() {
        if (activeJob == null) {
            return List.of();
        }
        return List.of(Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.job",
                activeJob.primaryOutput.getDisplayName(),
                activeJob.remaining,
                machineRoleName(activeJob.machineRole),
                activeJob.machinePos.getX(), activeJob.machinePos.getY(), activeJob.machinePos.getZ(),
                activeJob.outputPos.getX(), activeJob.outputPos.getY(), activeJob.outputPos.getZ()));
    }

    public Component smartDoublingLine() {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".kinetic_pattern_provider.smart_doubling",
                boolText(smartDoubling)).withStyle(ChatFormatting.GRAY);
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
        tooltip.add(machineLine());
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
