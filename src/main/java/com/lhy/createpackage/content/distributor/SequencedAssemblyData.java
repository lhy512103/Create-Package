package com.lhy.createpackage.content.distributor;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Accesses Create 1.20.1 sequenced-assembly metadata stored in item NBT. */
final class SequencedAssemblyData {
    private static final String ROOT = "SequencedAssembly";
    private static final String ID = "id";
    private static final String STEP = "Step";

    private SequencedAssemblyData() {}

    static boolean has(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains(ROOT, CompoundTag.TAG_COMPOUND);
    }

    @Nullable
    static ResourceLocation recipeId(ItemStack stack) {
        if (!has(stack)) return null;
        String value = stack.getTag().getCompound(ROOT).getString(ID);
        return ResourceLocation.tryParse(value);
    }

    static int step(ItemStack stack) {
        return has(stack) ? stack.getTag().getCompound(ROOT).getInt(STEP) : 0;
    }
}
