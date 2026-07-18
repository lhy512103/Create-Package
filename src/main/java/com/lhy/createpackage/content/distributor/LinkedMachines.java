package com.lhy.createpackage.content.distributor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.lhy.createpackage.content.crafting.CreateMachineIds;

/**
 * Resolves a distributor's linked block positions into typed Create machine roles, in link order.
 *
 * <p>Link order is significant: deployers and spouts are matched onto the recipe's deploying/filling
 * steps in the same order the player linked them, mirroring the physical order items travel along a
 * Create assembly line.
 */
public final class LinkedMachines {

    public enum Role {
        DEPOT,     // depot or belt — item carrier; first one is the input, last is the output
        DEPLOYER,
        SPOUT,
        PRESS,
        MIXER,
        SAW,
        BASIN,
        UNKNOWN
    }

    public record Entry(BlockPos pos, Role role) {
    }

    private final List<Entry> entries = new ArrayList<>();

    private LinkedMachines() {}

    /** Classifies the given positions against the world. Unloaded/foreign blocks become UNKNOWN. */
    public static LinkedMachines resolve(Level level, List<BlockPos> positions) {
        LinkedMachines result = new LinkedMachines();
        for (BlockPos pos : positions) {
            result.entries.add(new Entry(pos, roleOf(level, pos)));
        }
        return result;
    }

    public static Role roleOf(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return Role.UNKNOWN;
        }
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = CreateMachineIds.idOf(state);
        if (CreateMachineIds.isDeployer(id)) {
            return Role.DEPLOYER;
        }
        if (CreateMachineIds.isSpout(id)) {
            return Role.SPOUT;
        }
        if (CreateMachineIds.isMechanicalPress(id)) {
            return Role.PRESS;
        }
        if (CreateMachineIds.isMechanicalMixer(id)) {
            return Role.MIXER;
        }
        if (CreateMachineIds.isMechanicalSaw(id)) {
            return Role.SAW;
        }
        if (CreateMachineIds.isBasin(id)) {
            return Role.BASIN;
        }
        if (CreateMachineIds.isDepotCarrier(id)) {
            return Role.DEPOT;
        }
        return Role.UNKNOWN;
    }

    public List<Entry> all() {
        return entries;
    }

    public List<Entry> withRole(Role role) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.role() == role) {
                result.add(entry);
            }
        }
        return result;
    }

    /** First linked depot/belt in link order, treated as the line's input. Null if none. */
    public BlockPos inputDepot() {
        for (Entry entry : entries) {
            if (entry.role() == Role.DEPOT) {
                return entry.pos();
            }
        }
        return null;
    }

    /** Last linked depot/belt in link order, treated as the line's output. Null if none. */
    public BlockPos outputDepot() {
        BlockPos found = null;
        for (Entry entry : entries) {
            if (entry.role() == Role.DEPOT) {
                found = entry.pos();
            }
        }
        return found;
    }
}
