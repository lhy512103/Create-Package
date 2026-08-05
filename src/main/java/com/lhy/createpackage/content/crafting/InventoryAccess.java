package com.lhy.createpackage.content.crafting;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEFluidKey;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Capability-facing inventory helpers shared by package distributors and kinetic providers.
 */
public final class InventoryAccess {
    private InventoryAccess() {
    }

    @Nullable
    public static IItemHandler itemHandler(@Nullable Level level, BlockPos pos, @Nullable Direction side) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }

    @Nullable
    public static IItemHandler itemHandler(@Nullable Level level, BlockPos pos) {
        IItemHandler handler = itemHandler(level, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction direction : Direction.values()) {
            handler = itemHandler(level, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    @Nullable
    public static IItemHandler itemOutputHandler(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        Object blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        try {
            Object output = blockEntity.getClass().getMethod("getOutputInventory").invoke(blockEntity);
            return output instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    @Nullable
    public static IFluidHandler fluidHandler(@Nullable Level level, BlockPos pos, @Nullable Direction side) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
    }

    @Nullable
    public static IFluidHandler fluidHandler(@Nullable Level level, BlockPos pos) {
        IFluidHandler handler = fluidHandler(level, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction direction : Direction.values()) {
            handler = fluidHandler(level, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    @Nullable
    public static IFluidHandler fluidInputHandler(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        SmartFluidTankBehaviour input = BlockEntityBehaviour.get(level, pos, SmartFluidTankBehaviour.INPUT);
        return input == null ? null : input.getCapability();
    }

    @Nullable
    public static IFluidHandler fluidOutputHandler(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        SmartFluidTankBehaviour output = BlockEntityBehaviour.get(level, pos, SmartFluidTankBehaviour.OUTPUT);
        return output == null ? null : output.getCapability();
    }

    public static ItemStack insertItem(@Nullable Level level, BlockPos target, @Nullable Direction side, ItemStack stack,
            boolean simulate) {
        IItemHandler handler = itemHandler(level, target, side);
        if (handler == null) {
            return stack.copy();
        }
        return insertIntoHandler(handler, stack, simulate);
    }

    public static ItemStack insertItem(@Nullable Level level, BlockPos target, ItemStack stack, boolean simulate) {
        IItemHandler handler = itemHandler(level, target);
        if (handler == null) {
            return stack.copy();
        }
        return insertIntoHandler(handler, stack, simulate);
    }

    public static ItemStack insertIntoHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    public static ItemStack reinsertIntoHandler(IItemHandler handler, int preferredSlot, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if (preferredSlot >= 0 && preferredSlot < handler.getSlots()) {
            remaining = handler.insertItem(preferredSlot, remaining, false);
        }
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            if (slot != preferredSlot) {
                remaining = handler.insertItem(slot, remaining, false);
            }
        }
        return remaining;
    }

    public static ItemStack extractMatching(IItemHandler handler, ItemStack match, int amount, boolean simulate) {
        if (handler == null || match.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack extractedTotal = ItemStack.EMPTY;
        int remaining = amount;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack present = handler.getStackInSlot(slot);
            if (present.isEmpty() || !ItemStack.isSameItemSameComponents(present, match)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, simulate);
            if (extracted.isEmpty()) {
                continue;
            }
            remaining -= extracted.getCount();
            if (extractedTotal.isEmpty()) {
                extractedTotal = extracted.copy();
            } else {
                extractedTotal.grow(extracted.getCount());
            }
        }
        return extractedTotal;
    }

    public static int acceptedItemAmount(@Nullable Level level, BlockPos target, ItemStack stack) {
        ItemStack remaining = insertItem(level, target, stack, true);
        return stack.getCount() - remaining.getCount();
    }

    public static boolean canInsertAll(@Nullable Level level, BlockPos target, ItemStack stack) {
        return insertItem(level, target, stack, true).isEmpty();
    }

    public static boolean itemTargetCouldEventuallyAccept(@Nullable Level level, BlockPos target, ItemStack stack) {
        IItemHandler handler = itemHandler(level, target);
        if (handler == null) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stored = handler.getStackInSlot(slot);
            if (stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, stack)) {
                return true;
            }
        }
        return false;
    }

    public static int acceptedFluidAmount(@Nullable Level level, BlockPos target, FluidStack stack) {
        IFluidHandler handler = fluidHandler(level, target);
        if (handler == null) {
            return 0;
        }
        return handler.fill(stack.copy(), IFluidHandler.FluidAction.SIMULATE);
    }

    public static boolean fluidTargetCouldEventuallyAccept(@Nullable Level level, BlockPos target, FluidStack stack) {
        IFluidHandler handler = fluidHandler(level, target);
        if (handler == null) {
            return false;
        }
        AEFluidKey key = AEFluidKey.of(stack);
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.isFluidValid(tank, stack)) {
                continue;
            }
            FluidStack stored = handler.getFluidInTank(tank);
            if (stored.isEmpty() || key != null && key.equals(AEFluidKey.of(stored))) {
                return true;
            }
        }
        return false;
    }

    public static long countMatchingItem(@Nullable Level level, BlockPos pos,
            net.minecraft.world.item.crafting.Ingredient ingredient) {
        IItemHandler handler = itemHandler(level, pos);
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

    public static FluidStack extractMatchingFluid(@Nullable Level level, BlockPos pos, FluidStack match, int amount,
            boolean simulate) {
        IFluidHandler handler = fluidHandler(level, pos);
        if (handler == null || match == null || match.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack request = match.copyWithAmount(amount);
        return handler.drain(request, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    public static boolean hasAnyFluid(@Nullable Level level, BlockPos pos) {
        IFluidHandler handler = fluidHandler(level, pos);
        if (handler == null) {
            return false;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.getFluidInTank(tank).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static long countMatchingFluid(@Nullable Level level, BlockPos pos,
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient) {
        IFluidHandler handler = fluidHandler(level, pos);
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
}
