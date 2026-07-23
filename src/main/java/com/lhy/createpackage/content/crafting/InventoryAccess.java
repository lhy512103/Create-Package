package com.lhy.createpackage.content.crafting;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEFluidKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Capability-facing inventory helpers shared by package distributors and kinetic providers.
 */
public final class InventoryAccess {
    private InventoryAccess() {
    }

    @Nullable
    public static IItemHandler itemHandler(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler != null) return handler;
        for (Direction direction : Direction.values()) {
            handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElse(null);
            if (handler != null) return handler;
        }
        return null;
    }

    @Nullable
    public static IFluidHandler fluidHandler(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        for (Direction direction : Direction.values()) {
            IFluidHandler handler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction).orElse(null);
            if (handler != null) return handler;
        }
        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
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

    public static ItemStack extractMatching(IItemHandler handler, ItemStack match, int amount, boolean simulate) {
        if (handler == null || match.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack extractedTotal = ItemStack.EMPTY;
        int remaining = amount;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack present = handler.getStackInSlot(slot);
            if (present.isEmpty() || !ItemStack.isSameItemSameTags(present, match)) {
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
            if (stored.isEmpty() || ItemStack.isSameItemSameTags(stored, stack)) {
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
        FluidStack request = new FluidStack(match, amount);
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
            com.simibubi.create.foundation.fluid.FluidIngredient ingredient) {
        IFluidHandler handler = fluidHandler(level, pos);
        if (handler == null || ingredient == null) {
            return 0;
        }
        long amount = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (ingredient.test(stack)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }
}
