package com.lhy.createpackage.content.crafting;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Concrete {@link SupplyAction} implementations shared by craft bridges.
 */
public final class SupplyActions {
    private SupplyActions() {
    }

    public static ItemInsertAction insertItem(Level level, BlockPos target, ItemStack stack) {
        return new ItemInsertAction(level, target, stack);
    }

    public static FluidInsertAction insertFluid(Level level, BlockPos target, FluidStack stack) {
        return new FluidInsertAction(level, target, stack);
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
        private final ItemStack stack;
        private int inserted;

        private ItemInsertAction(Level level, BlockPos target, ItemStack stack) {
            this.level = level;
            this.target = target.immutable();
            this.stack = stack.copy();
        }

        public BlockPos target() {
            return target;
        }

        public ItemStack stack() {
            return stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            ItemStack remaining = InventoryAccess.insertItem(level, target, stack, simulate);
            int moved = stack.getCount() - remaining.getCount();
            if (!simulate) {
                inserted = moved;
            }
            return remaining.isEmpty();
        }

        @Override
        public void compensate() {
            if (inserted <= 0) {
                return;
            }
            IItemHandler handler = InventoryAccess.itemHandler(level, target);
            if (handler == null) {
                return;
            }
            InventoryAccess.extractMatching(handler, stack, inserted, false);
            inserted = 0;
        }

        @Override
        public String describe() {
            return "item->" + target.toShortString() + " x" + stack.getCount();
        }
    }

    public static final class FluidInsertAction implements SupplyAction {
        private final Level level;
        private final BlockPos target;
        private final FluidStack stack;
        private int inserted;

        private FluidInsertAction(Level level, BlockPos target, FluidStack stack) {
            this.level = level;
            this.target = target.immutable();
            this.stack = stack.copy();
        }

        public BlockPos target() {
            return target;
        }

        public FluidStack stack() {
            return stack.copy();
        }

        @Override
        public boolean perform(boolean simulate) {
            IFluidHandler handler = InventoryAccess.fluidHandler(level, target);
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
        public void compensate() {
            if (inserted <= 0) {
                return;
            }
            IFluidHandler handler = InventoryAccess.fluidHandler(level, target);
            if (handler == null) {
                return;
            }
            handler.drain(new FluidStack(stack, inserted), IFluidHandler.FluidAction.EXECUTE);
            inserted = 0;
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
                if (extracted > 0) {
                    network.insert(key, extracted, Actionable.MODULATE);
                    extractedFromNetwork = 0;
                }
                return false;
            }
            ItemStack remaining = InventoryAccess.insertItem(level, target, stack, false);
            insertedIntoWorld = stack.getCount() - remaining.getCount();
            if (!remaining.isEmpty()) {
                network.insert(key, remaining.getCount(), Actionable.MODULATE);
                extractedFromNetwork = insertedIntoWorld;
                // partial world insert still counts as failure for all-or-nothing plans
                compensate();
                return false;
            }
            return true;
        }

        @Override
        public void compensate() {
            if (insertedIntoWorld > 0) {
                IItemHandler handler = InventoryAccess.itemHandler(level, target);
                if (handler != null) {
                    InventoryAccess.extractMatching(handler, stack, insertedIntoWorld, false);
                }
                network.insert(key, insertedIntoWorld, Actionable.MODULATE);
                insertedIntoWorld = 0;
                extractedFromNetwork = 0;
                return;
            }
            if (extractedFromNetwork > 0) {
                network.insert(key, extractedFromNetwork, Actionable.MODULATE);
                extractedFromNetwork = 0;
            }
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
        public void compensate() {
            if (inserted <= 0) {
                return;
            }
            network.extract(stack.what(), inserted, Actionable.MODULATE);
            inserted = 0;
        }

        @Override
        public String describe() {
            AEKey key = stack.what();
            return "return-network " + key + " x" + stack.amount();
        }
    }
}
