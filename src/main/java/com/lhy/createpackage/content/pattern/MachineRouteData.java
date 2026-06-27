package com.lhy.createpackage.content.pattern;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public record MachineRouteData(List<BlockPos> positions) {
    public static final Codec<MachineRouteData> CODEC =
            BlockPos.CODEC.listOf().xmap(MachineRouteData::new, MachineRouteData::positions);

    public static final StreamCodec<ByteBuf, MachineRouteData> STREAM_CODEC =
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(MachineRouteData::new, MachineRouteData::positions);

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
}
