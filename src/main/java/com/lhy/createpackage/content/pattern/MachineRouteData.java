package com.lhy.createpackage.content.pattern;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;

public record MachineRouteData(List<BlockPos> positions) {
    public static final Codec<MachineRouteData> CODEC =
            BlockPos.CODEC.listOf().xmap(MachineRouteData::new, MachineRouteData::positions);

    public MachineRouteData {
        positions = positions.stream().map(BlockPos::immutable).toList();
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public MachineRouteData withAdded(BlockPos pos) {
        if (positions.contains(pos)) {
            return this;
        }
        var copy = new java.util.ArrayList<>(positions);
        copy.add(pos.immutable());
        return new MachineRouteData(copy);
    }

    public MachineRouteData withRemoved(BlockPos pos) {
        if (!positions.contains(pos)) {
            return this;
        }
        var copy = new java.util.ArrayList<>(positions);
        copy.remove(pos);
        return new MachineRouteData(copy);
    }

    public MachineRouteData withRemovedAt(int index) {
        if (index < 0 || index >= positions.size()) {
            return this;
        }
        var copy = new java.util.ArrayList<>(positions);
        copy.remove(index);
        return new MachineRouteData(copy);
    }

    public MachineRouteData withReplaced(int index, BlockPos pos) {
        if (index < 0 || index >= positions.size()) {
            return this;
        }
        BlockPos immutable = pos.immutable();
        if (positions.get(index).equals(immutable)) {
            return this;
        }
        int existing = positions.indexOf(immutable);
        if (existing >= 0 && existing != index) {
            return this;
        }
        var copy = new java.util.ArrayList<>(positions);
        copy.set(index, immutable);
        return new MachineRouteData(copy);
    }
}
