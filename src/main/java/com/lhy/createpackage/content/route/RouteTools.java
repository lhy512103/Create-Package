package com.lhy.createpackage.content.route;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Shared helpers for route tools that support auto-route and ordered-route editing.
 *
 * <p>Two explicit modes, toggled by left-clicking air while holding a supported tool:
 * <ul>
 *   <li><b>Append</b> (default): right-click appends, sneak-right-click removes the clicked mark</li>
 *   <li><b>Edit</b>: scroll selects a mark, right-click replaces it, sneak-right-click removes it</li>
 * </ul>
 * Kinetic Pattern Provider hosts stay set-like and never enter ordered edit mode.
 */
public final class RouteTools {
    public enum Mode {
        APPEND,
        EDIT
    }

    private RouteTools() {
    }

    public static boolean isRouteTool(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ModItems.MACHINE_LINKER.get())
                || stack.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())
                || stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get()));
    }

    public static boolean supportsAutoRoute(ItemStack stack) {
        return isRouteTool(stack);
    }

    /**
     * Whether this held tool currently exposes ordered-route modes/cursor.
     * Kinetic Pattern Provider links are a parallel machine set, not an ordered line.
     */
    public static boolean supportsOrderedRouteCursor(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())
                || stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            return true;
        }
        if (!stack.is(ModItems.MACHINE_LINKER.get()) || player == null) {
            return false;
        }
        return resolveOrderedLinkerDistributor(player.level(), stack) != null;
    }

    @Nullable
    public static ItemStack heldRouteTool(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isRouteTool(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isRouteTool(off)) {
            return off;
        }
        return null;
    }

    public static Mode getMode(ItemStack stack) {
        Boolean edit = ModComponents.getRouteEditMode(stack);
        return edit != null && edit ? Mode.EDIT : Mode.APPEND;
    }

    public static boolean isEditMode(ItemStack stack) {
        return getMode(stack) == Mode.EDIT;
    }

    public static void setMode(ItemStack stack, Mode mode) {
        if (mode == Mode.EDIT) {
            ModComponents.setRouteEditMode(stack, true);
        } else {
            ModComponents.clearRouteEditMode(stack);
        }
    }

    public static Mode toggleMode(ItemStack stack) {
        Mode next = isEditMode(stack) ? Mode.APPEND : Mode.EDIT;
        setMode(stack, next);
        if (next == Mode.APPEND) {
            // Leave any previous highlight cleared so append feels clean.
            setCursor(stack, -1);
        }
        return next;
    }

    public static Component modeLabel(Mode mode) {
        return Component.translatable("tooltip." + CreatePackage.MODID + ".route.mode."
                + (mode == Mode.EDIT ? "edit" : "append"));
    }

    public static int getCursor(ItemStack stack) {
        Integer cursor = ModComponents.getRouteCursor(stack);
        return cursor == null ? -1 : cursor;
    }

    public static void setCursor(ItemStack stack, int cursor) {
        if (cursor < 0) {
            ModComponents.clearRouteCursor(stack);
        } else {
            ModComponents.setRouteCursor(stack, cursor);
        }
    }

    /**
     * Keeps an existing selection in range. Does <strong>not</strong> promote -1 to 0.
     */
    public static int clampCursor(int cursor, int routeSize) {
        if (routeSize <= 0 || cursor < 0) {
            return -1;
        }
        return Math.min(cursor, routeSize - 1);
    }

    public static int cycleCursor(int current, int routeSize, int delta) {
        if (routeSize <= 0) {
            return -1;
        }
        int start = current < 0 ? (delta >= 0 ? 0 : routeSize - 1) : current;
        return Math.floorMod(start + delta, routeSize);
    }

    public static List<BlockPos> routePositions(ItemStack stack) {
        if (stack.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())) {
            MachineRouteData route = ModComponents.getMechanicalRoute(stack);
            return route == null ? List.of() : route.positions();
        }
        if (stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
            return data == null ? List.of() : data.route();
        }
        return List.of();
    }

    @Nullable
    public static List<BlockPos> orderedRouteForCursor(Player player, ItemStack stack) {
        if (stack.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())
                || stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            return routePositions(stack);
        }
        if (stack.is(ModItems.MACHINE_LINKER.get()) && player != null) {
            PackageDistributorBlockEntity distributor = resolveOrderedLinkerDistributor(player.level(), stack);
            return distributor == null ? null : new ArrayList<>(distributor.getLinkedMachines());
        }
        return null;
    }

    @Nullable
    public static PackageDistributorBlockEntity resolveOrderedLinkerDistributor(Level level, ItemStack stack) {
        GlobalPos selected = ModComponents.getLinkedDistributor(stack);
        if (selected == null || level == null || !selected.dimension().equals(level.dimension())
                || !level.isLoaded(selected.pos())) {
            return null;
        }
        if (level.getBlockEntity(selected.pos()) instanceof PackageDistributorBlockEntity distributor
                && distributor.usesStoredMachineLinks()) {
            return distributor;
        }
        return null;
    }

    public static void writeConverterRoute(ItemStack stack, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            ModComponents.clearMechanicalRoute(stack);
        } else {
            ModComponents.setMechanicalRoute(stack, new MachineRouteData(positions));
        }
        setCursor(stack, clampCursor(getCursor(stack), positions.size()));
    }

    public static boolean writePatternRoute(ItemStack stack, List<BlockPos> positions) {
        MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
        if (data == null || data.encodedPattern().isEmpty()) {
            return false;
        }
        ModComponents.setMechanicalPattern(stack, new MechanicalPackagePatternData(data.encodedPattern(), positions, data.encoder()));
        setCursor(stack, clampCursor(getCursor(stack), positions.size()));
        return true;
    }

    public static boolean replaceOrderedMark(Player player, ItemStack stack, BlockPos pos) {
        int cursor = getCursor(stack);
        if (stack.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())) {
            MachineRouteData current = ModComponents.getMechanicalRoute(stack);
            if (current == null) {
                return false;
            }
            cursor = clampCursor(cursor, current.positions().size());
            if (cursor < 0) {
                return false;
            }
            MachineRouteData updated = current.withReplaced(cursor, pos);
            if (updated.positions().equals(current.positions())) {
                return false;
            }
            writeConverterRoute(stack, updated.positions());
            setCursor(stack, cursor);
            return true;
        }
        if (stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
            if (data == null) {
                return false;
            }
            List<BlockPos> copy = new ArrayList<>(data.route());
            cursor = clampCursor(cursor, copy.size());
            if (cursor < 0) {
                return false;
            }
            BlockPos immutable = pos.immutable();
            int existing = copy.indexOf(immutable);
            if (existing >= 0 && existing != cursor) {
                return false;
            }
            if (copy.get(cursor).equals(immutable)) {
                return false;
            }
            copy.set(cursor, immutable);
            if (!writePatternRoute(stack, copy)) {
                return false;
            }
            setCursor(stack, cursor);
            return true;
        }
        if (stack.is(ModItems.MACHINE_LINKER.get())) {
            PackageDistributorBlockEntity distributor = resolveOrderedLinkerDistributor(player.level(), stack);
            if (distributor == null) {
                return false;
            }
            cursor = clampCursor(cursor, distributor.getLinkedMachines().size());
            if (cursor < 0) {
                return false;
            }
            boolean changed = distributor.replaceLinkedMachine(cursor, pos);
            if (changed) {
                setCursor(stack, cursor);
            }
            return changed;
        }
        return false;
    }

    public static boolean removeSelectedOrderedMark(Player player, ItemStack stack) {
        int cursor = getCursor(stack);
        if (stack.is(ModItems.MECHANICAL_PATTERN_CONVERTER.get())) {
            MachineRouteData current = ModComponents.getMechanicalRoute(stack);
            if (current == null) {
                return false;
            }
            cursor = clampCursor(cursor, current.positions().size());
            if (cursor < 0) {
                return false;
            }
            MachineRouteData updated = current.withRemovedAt(cursor);
            if (updated.positions().size() == current.positions().size()) {
                return false;
            }
            writeConverterRoute(stack, updated.positions());
            setCursor(stack, clampCursor(cursor, updated.positions().size()));
            return true;
        }
        if (stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
            if (data == null) {
                return false;
            }
            List<BlockPos> copy = new ArrayList<>(data.route());
            cursor = clampCursor(cursor, copy.size());
            if (cursor < 0) {
                return false;
            }
            copy.remove(cursor);
            writePatternRoute(stack, copy);
            setCursor(stack, clampCursor(cursor, copy.size()));
            return true;
        }
        if (stack.is(ModItems.MACHINE_LINKER.get())) {
            PackageDistributorBlockEntity distributor = resolveOrderedLinkerDistributor(player.level(), stack);
            if (distributor == null) {
                return false;
            }
            List<BlockPos> linked = distributor.getLinkedMachines();
            cursor = clampCursor(cursor, linked.size());
            if (cursor < 0) {
                return false;
            }
            boolean removed = distributor.unlinkMachine(linked.get(cursor));
            setCursor(stack, clampCursor(cursor, distributor.getLinkedMachines().size()));
            return removed;
        }
        return false;
    }
}
