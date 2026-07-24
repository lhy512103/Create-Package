package com.lhy.createpackage.registry;

import java.util.Optional;
import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** 1.20.1 NBT-backed replacement for 1.21 data components. */
public final class ModComponents {
    private static final String ROOT = CreatePackage.MODID;
    private static final String LINKED_DISTRIBUTOR = "linked_distributor";
    private static final String MECHANICAL_PATTERN = "mechanical_package_pattern";
    private static final String MECHANICAL_ROUTE = "mechanical_route";
    private static final String ROUTE_CURSOR = "route_cursor";
    private static final String ROUTE_EDIT_MODE = "route_edit_mode";

    public static GlobalPos getLinkedDistributor(ItemStack stack) { return read(stack, LINKED_DISTRIBUTOR, GlobalPos.CODEC).orElse(null); }
    public static void setLinkedDistributor(ItemStack stack, GlobalPos value) { write(stack, LINKED_DISTRIBUTOR, GlobalPos.CODEC, value); }
    public static void clearLinkedDistributor(ItemStack stack) { remove(stack, LINKED_DISTRIBUTOR); }

    public static MechanicalPackagePatternData getMechanicalPattern(ItemStack stack) { return read(stack, MECHANICAL_PATTERN, MechanicalPackagePatternData.CODEC).orElse(null); }
    public static void setMechanicalPattern(ItemStack stack, MechanicalPackagePatternData value) { write(stack, MECHANICAL_PATTERN, MechanicalPackagePatternData.CODEC, value); }
    public static void clearMechanicalPattern(ItemStack stack) { remove(stack, MECHANICAL_PATTERN); }

    public static MachineRouteData getMechanicalRoute(ItemStack stack) { return read(stack, MECHANICAL_ROUTE, MachineRouteData.CODEC).orElse(null); }
    public static void setMechanicalRoute(ItemStack stack, MachineRouteData value) { write(stack, MECHANICAL_ROUTE, MachineRouteData.CODEC, value); }
    public static void clearMechanicalRoute(ItemStack stack) { remove(stack, MECHANICAL_ROUTE); }

    public static Integer getRouteCursor(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT);
        return root != null && root.contains(ROUTE_CURSOR, Tag.TAG_INT) ? root.getInt(ROUTE_CURSOR) : null;
    }
    public static void setRouteCursor(ItemStack stack, int value) { stack.getOrCreateTagElement(ROOT).putInt(ROUTE_CURSOR, value); }
    public static void clearRouteCursor(ItemStack stack) { remove(stack, ROUTE_CURSOR); }

    public static Boolean getRouteEditMode(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT);
        return root != null && root.contains(ROUTE_EDIT_MODE, Tag.TAG_BYTE) ? root.getBoolean(ROUTE_EDIT_MODE) : null;
    }
    public static void setRouteEditMode(ItemStack stack, boolean value) { stack.getOrCreateTagElement(ROOT).putBoolean(ROUTE_EDIT_MODE, value); }
    public static void clearRouteEditMode(ItemStack stack) { remove(stack, ROUTE_EDIT_MODE); }

    private static <T> Optional<T> read(ItemStack stack, String key, Codec<T> codec) {
        CompoundTag root = stack.getTagElement(ROOT);
        if (root == null || !root.contains(key)) return Optional.empty();
        return codec.parse(NbtOps.INSTANCE, root.get(key)).resultOrPartial(message ->
                CreatePackage.LOGGER.warn("Failed to decode item NBT {}: {}", key, message));
    }
    private static <T> void write(ItemStack stack, String key, Codec<T> codec, T value) {
        codec.encodeStart(NbtOps.INSTANCE, value).resultOrPartial(message ->
                CreatePackage.LOGGER.warn("Failed to encode item NBT {}: {}", key, message))
                .ifPresent(tag -> stack.getOrCreateTagElement(ROOT).put(key, tag));
    }
    private static void remove(ItemStack stack, String key) {
        CompoundTag root = stack.getTagElement(ROOT);
        if (root == null) return;
        root.remove(key);
        if (root.isEmpty()) stack.removeTagKey(ROOT);
    }
    private ModComponents() {}
}
