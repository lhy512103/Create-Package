package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * Last failure snapshot shown in goggles / Jade diagnostics.
 */
public final class FailureDiagnostics {
    private static final String NBT_STATUS = "diagStatus";
    private static final String NBT_DETAIL = "diagDetail";
    private static final String NBT_ROUTE = "diagRoute";
    private static final String NBT_SLOT = "diagSlot";

    private String statusKey = "";
    private String detail = "";
    private List<BlockPos> route = List.of();
    private int patternSlot = -1;

    public void clear() {
        statusKey = "";
        detail = "";
        route = List.of();
        patternSlot = -1;
    }

    public void remember(String statusKey, @Nullable String detail, List<BlockPos> route, int patternSlot) {
        this.statusKey = statusKey == null ? "" : statusKey;
        this.detail = detail == null ? "" : detail;
        this.route = route == null ? List.of() : List.copyOf(route);
        this.patternSlot = patternSlot;
    }

    public void remember(String statusKey, @Nullable String detail) {
        remember(statusKey, detail, List.of(), -1);
    }

    public boolean hasData() {
        return !statusKey.isEmpty() || !detail.isEmpty() || !route.isEmpty() || patternSlot >= 0;
    }

    public String statusKey() {
        return statusKey;
    }

    public String detail() {
        return detail;
    }

    public List<BlockPos> route() {
        return route;
    }

    public int patternSlot() {
        return patternSlot;
    }

    public void write(CompoundTag tag) {
        if (!hasData()) {
            return;
        }
        if (!statusKey.isEmpty()) {
            tag.putString(NBT_STATUS, statusKey);
        }
        if (!detail.isEmpty()) {
            tag.putString(NBT_DETAIL, detail);
        }
        if (patternSlot >= 0) {
            tag.putInt(NBT_SLOT, patternSlot);
        }
        if (!route.isEmpty()) {
            long[] packed = new long[route.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = route.get(i).asLong();
            }
            tag.putLongArray(NBT_ROUTE, packed);
        }
    }

    public void read(CompoundTag tag) {
        statusKey = tag.contains(NBT_STATUS) ? tag.getString(NBT_STATUS) : "";
        detail = tag.contains(NBT_DETAIL) ? tag.getString(NBT_DETAIL) : "";
        patternSlot = tag.contains(NBT_SLOT) ? tag.getInt(NBT_SLOT) : -1;
        List<BlockPos> loaded = new ArrayList<>();
        for (long packed : tag.getLongArray(NBT_ROUTE)) {
            loaded.add(BlockPos.of(packed));
        }
        route = List.copyOf(loaded);
    }

    public void writeSync(ListTag routeTag) {
        // kept for compatibility with advanced distributor stream fields
    }

    public List<Component> tooltipLines(String translationPrefix) {
        if (!hasData()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        if (!statusKey.isEmpty()) {
            lines.add(Component.translatable(translationPrefix + ".failure_status",
                    Component.translatable(translationPrefix + ".status." + statusKey)));
        }
        if (!detail.isEmpty()) {
            lines.add(Component.translatable(translationPrefix + ".failure_detail", detail));
        }
        if (patternSlot >= 0 && !route.isEmpty()) {
            BlockPos first = route.get(0);
            BlockPos last = route.get(route.size() - 1);
            lines.add(Component.translatable(translationPrefix + ".failure_route",
                    patternSlot + 1,
                    first.getX(), first.getY(), first.getZ(),
                    last.getX(), last.getY(), last.getZ()));
        } else if (!route.isEmpty()) {
            BlockPos first = route.get(0);
            BlockPos last = route.get(route.size() - 1);
            lines.add(Component.translatable(translationPrefix + ".failure_positions",
                    first.getX(), first.getY(), first.getZ(),
                    last.getX(), last.getY(), last.getZ()));
        }
        return lines;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FailureDiagnostics other)) {
            return false;
        }
        return patternSlot == other.patternSlot
                && Objects.equals(statusKey, other.statusKey)
                && Objects.equals(detail, other.detail)
                && Objects.equals(route, other.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusKey, detail, route, patternSlot);
    }
}
