package com.lhy.createpackage.content.crafting;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A concrete automation endpoint. Direction is the capability side exposed by the target block.
 */
public record MachineEndpoint(BlockPos pos, @Nullable Direction side, Medium medium, Purpose purpose) {
    public enum Medium {
        ITEM,
        FLUID,
        SMART_ITEM_OUTPUT,
        SMART_FLUID_INPUT,
        SMART_FLUID_OUTPUT,
        CRUCIBLE_ITEM_OUTPUT,
        CRUCIBLE_FLUID_OUTPUT,
        ITEM_ENTITY
    }

    public enum Purpose {
        INPUT,
        OUTPUT
    }

    public MachineEndpoint {
        pos = pos.immutable();
    }

    public static MachineEndpoint itemInput(BlockPos pos, @Nullable Direction side) {
        return new MachineEndpoint(pos, side, Medium.ITEM, Purpose.INPUT);
    }

    public static MachineEndpoint fluidInput(BlockPos pos, @Nullable Direction side) {
        return new MachineEndpoint(pos, side, Medium.FLUID, Purpose.INPUT);
    }

    public static MachineEndpoint smartFluidInput(BlockPos pos) {
        return new MachineEndpoint(pos, null, Medium.SMART_FLUID_INPUT, Purpose.INPUT);
    }

    public static MachineEndpoint itemOutput(BlockPos pos, @Nullable Direction side) {
        return new MachineEndpoint(pos, side, Medium.ITEM, Purpose.OUTPUT);
    }

    public static MachineEndpoint smartItemOutput(BlockPos pos) {
        return new MachineEndpoint(pos, null, Medium.SMART_ITEM_OUTPUT, Purpose.OUTPUT);
    }

    public static MachineEndpoint fluidOutput(BlockPos pos, @Nullable Direction side) {
        return new MachineEndpoint(pos, side, Medium.FLUID, Purpose.OUTPUT);
    }

    public static MachineEndpoint smartFluidOutput(BlockPos pos) {
        return new MachineEndpoint(pos, null, Medium.SMART_FLUID_OUTPUT, Purpose.OUTPUT);
    }

    public static MachineEndpoint crucibleItemOutput(BlockPos pos) {
        return new MachineEndpoint(pos, null, Medium.CRUCIBLE_ITEM_OUTPUT, Purpose.OUTPUT);
    }

    public static MachineEndpoint crucibleFluidOutput(BlockPos pos) {
        return new MachineEndpoint(pos, null, Medium.CRUCIBLE_FLUID_OUTPUT, Purpose.OUTPUT);
    }

    public static MachineEndpoint droppedItemOutput(BlockPos pos) {
        return new MachineEndpoint(pos, null, Medium.ITEM_ENTITY, Purpose.OUTPUT);
    }
}