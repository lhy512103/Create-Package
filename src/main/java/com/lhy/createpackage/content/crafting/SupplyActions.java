package com.lhy.createpackage.content.crafting;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Concrete {@link SupplyAction} implementations shared by craft bridges.
 */
public final class SupplyActions {
    private SupplyActions() {
    }

    public static ItemInsertAction insertItem(Level level, BlockPos target, ItemStack stack) {
        return new ItemInsertAction(level, target, null, stack);
    }

    public static ItemInsertAction insertItem(Level level, MachineEndpoint endpoint, ItemStack stack) {
        return new ItemInsertAction(level, endpoint.pos(), endpoint.side(), stack);
    }

    public static SupplyAction spawnItemEntity(Level level, BlockPos target, ItemStack stack) {
        return new ItemEntityInsertAction(level, target, stack);
    }

    public static FluidInsertAction insertFluid(Level level, BlockPos target, FluidStack stack) {
        return new FluidInsertAction(level, target, null, MachineEndpoint.Medium.FLUID, stack);
    }

    public static FluidInsertAction insertFluid(Level level, MachineEndpoint endpoint, FluidStack stack) {
        return new FluidInsertAction(level, endpoint.pos(), endpoint.side(), endpoint.medium(), stack);
    }

    public static SupplyAction networkItemToTarget(Level level, NetworkIO network, BlockPos target, AEItemKey key,
            ItemStack stack) {
        return new NetworkItemInsertAction(level, network, target, key, stack);
    }

    public static SupplyAction returnToNetwork(NetworkIO network, GenericStack stack) {
        return new NetworkReturnAction(network, stack);
    }

    public static final class ItemInsertAction implements SupplyAction {
        private final Level level;
        private final BlockPos target;
        private final Direction side;
        private final ItemStack stack;
        private int inserted;

        private ItemInsertAction(Level level, BlockPos target, Direction side, ItemStack stack) {
            this.level = level;
            this.target = target.immutable();
            this.side = side;
            this.stack = stack.copy();
        }

        public BlockPos target() {
            return target;
        }

        public Direction side() {
            return side;
        }

        public ItemStack stack() {
            return stack.copy();
        }

        @Nullable
        public IItemHandler handler() {
            return side == null
                    ? InventoryAccess.itemHandler(level, target)
                    : InventoryAccess.itemHandler(level, target, side);
        }

        @Override
        public boolean perform(boolean simulate) {
            ItemStack remaining = side == null
                    ? InventoryAccess.insertItem(level, target, stack, simulate)
                    : InventoryAccess.insertItem(level, target, side, stack, simulate);
            int moved = stack.getCount() - remaining.getCount();
            if (!simulate) {
                inserted = moved;
            }
            return remaining.isEmpty();
        }

        @Override
        public CompensationResult compensate() {
            if (inserted <= 0) {
                return CompensationResult.complete();
            }
            IItemHandler handler = handler();
            if (handler == null) {
                return CompensationResult.failed("item handler unavailable; " + inserted + " item(s) remain inserted");
            }
            ItemStack extracted = InventoryAccess.extractMatching(handler, stack, inserted, false);
            inserted -= extracted.getCount();
            return inserted == 0
                    ? CompensationResult.complete()
                    : CompensationResult.failed(inserted + " item(s) remain inserted");
        }

        @Override
        public String describe() {
            return "item->" + target.toShortString() + " x" + stack.getCount();
        }
    }

    public static final class ItemEntityInsertAction implements SupplyAction {
        private final Level level;
        private final BlockPos target;
        private final ItemStack stack;
        @Nullable
        private ItemEntity spawned;

        private ItemEntityInsertAction(Level level, BlockPos target, ItemStack stack) {
            this.level = level;
            this.target = target.immutable();
            this.stack = stack.copy();
        }

        public ItemStack stack() {
            return stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            if (stack.isEmpty() || !level.isLoaded(target)
                    || !level.getEntitiesOfClass(ItemEntity.class, new AABB(target), entity -> entity.isAlive()).isEmpty()) {
                return false;
            }
            if (simulate) {
                return true;
            }
            ItemEntity entity = new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.1,
                    target.getZ() + 0.5, stack.copy());
            if (!level.addFreshEntity(entity)) {
                return false;
            }
            spawned = entity;
            return true;
        }

        @Override
        public CompensationResult compensate() {
            if (spawned == null) {
                return CompensationResult.complete();
            }
            if (spawned.isAlive()) {
                spawned.discard();
            }
            boolean removed = !spawned.isAlive();
            if (removed) {
                spawned = null;
                return CompensationResult.complete();
            }
            return CompensationResult.failed("spawned item entity could not be removed");
        }

        @Override
        public String describe() {
            return "item-entity->" + target.toShortString() + " x" + stack.getCount();
        }
    }

    public static final class FluidInsertAction implements SupplyAction {
        private final Level level;
        private final BlockPos target;
        private final Direction side;
        private final MachineEndpoint.Medium medium;
        private final FluidStack stack;
        private int inserted;

        private FluidInsertAction(Level level, BlockPos target, Direction side, MachineEndpoint.Medium medium,
                FluidStack stack) {
            this.level = level;
            this.target = target.immutable();
            this.side = side;
            this.medium = medium;
            this.stack = stack.copy();
        }

        public BlockPos target() {
            return target;
        }

        public Direction side() {
            return side;
        }

        public FluidStack stack() {
            return stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            IFluidHandler handler = handler();
            if (handler == null) {
                return false;
            }
            var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
            int filled = handler.fill(stack.copy(), action);
            if (!simulate) {
                inserted = filled;
            }
            return filled >= stack.getAmount();
        }

        @Override
        public CompensationResult compensate() {
            if (inserted <= 0) {
                return CompensationResult.complete();
            }
            IFluidHandler handler = handler();
            if (handler == null) {
                return CompensationResult.failed("fluid handler unavailable; " + inserted + " mB remain inserted");
            }
            FluidStack drained = handler.drain(stack.copyWithAmount(inserted), IFluidHandler.FluidAction.EXECUTE);
            inserted -= drained.getAmount();
            return inserted == 0
                    ? CompensationResult.complete()
                    : CompensationResult.failed(inserted + " mB remain inserted");
        }

        @Nullable
        public IFluidHandler handler() {
            if (medium == MachineEndpoint.Medium.SMART_FLUID_INPUT) {
                return InventoryAccess.fluidInputHandler(level, target);
            }
            return side == null
                    ? InventoryAccess.fluidHandler(level, target)
                    : InventoryAccess.fluidHandler(level, target, side);
        }

        @Override
        public String describe() {
            return "fluid->" + target.toShortString() + " x" + stack.getAmount();
        }
    }

    private static final class NetworkItemInsertAction implements SupplyAction {
        private final Level level;
        private final NetworkIO network;
        private final BlockPos target;
        private final AEItemKey key;
        private final ItemStack stack;
        private long extractedFromNetwork;
        private int insertedIntoWorld;

        private NetworkItemInsertAction(Level level, NetworkIO network, BlockPos target, AEItemKey key,
                ItemStack stack) {
            this.level = level;
            this.network = network;
            this.target = target.immutable();
            this.key = key;
            this.stack = stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            long amount = stack.getCount();
            if (network.extract(key, amount, Actionable.SIMULATE) < amount) {
                return false;
            }
            if (!InventoryAccess.canInsertAll(level, target, stack)) {
                return false;
            }
            if (simulate) {
                return true;
            }
            long extracted = network.extract(key, amount, Actionable.MODULATE);
            extractedFromNetwork = extracted;
            if (extracted < amount) {
                return false;
            }
            ItemStack remaining = InventoryAccess.insertItem(level, target, stack, false);
            insertedIntoWorld = stack.getCount() - remaining.getCount();
            extractedFromNetwork -= insertedIntoWorld;
            return remaining.isEmpty();
        }

        @Override
        public CompensationResult compensate() {
            if (insertedIntoWorld > 0) {
                IItemHandler handler = InventoryAccess.itemHandler(level, target);
                if (handler != null) {
                    ItemStack extracted = InventoryAccess.extractMatching(handler, stack, insertedIntoWorld, false);
                    insertedIntoWorld -= extracted.getCount();
                    extractedFromNetwork += extracted.getCount();
                }
            }
            if (extractedFromNetwork > 0) {
                long returned = network.insert(key, extractedFromNetwork, Actionable.MODULATE);
                extractedFromNetwork -= returned;
            }
            if (insertedIntoWorld == 0 && extractedFromNetwork == 0) {
                return CompensationResult.complete();
            }
            return CompensationResult.failed(insertedIntoWorld + " item(s) remain in target and "
                    + extractedFromNetwork + " item(s) remain outside the network");
        }

        @Override
        public String describe() {
            return "network-item->" + target.toShortString() + " x" + stack.getCount();
        }
    }

    private static final class NetworkReturnAction implements SupplyAction {
        private final NetworkIO network;
        private final GenericStack stack;
        private long inserted;

        private NetworkReturnAction(NetworkIO network, GenericStack stack) {
            this.network = network;
            this.stack = stack;
        }

        @Override
        public boolean perform(boolean simulate) {
            Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
            long amount = network.insert(stack.what(), stack.amount(), mode);
            if (!simulate) {
                inserted = amount;
            }
            return amount >= stack.amount();
        }

        @Override
        public CompensationResult compensate() {
            if (inserted <= 0) {
                return CompensationResult.complete();
            }
            long extracted = network.extract(stack.what(), inserted, Actionable.MODULATE);
            inserted -= extracted;
            return inserted == 0
                    ? CompensationResult.complete()
                    : CompensationResult.failed(inserted + " unit(s) remain in the network");
        }

        @Override
        public String describe() {
            AEKey key = stack.what();
            return "return-network " + key + " x" + stack.amount();
        }
    }
}
