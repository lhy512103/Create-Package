package com.lhy.createpackage.registry;

import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.api.AECapabilities;

/**
 * Registers AE2 block capabilities for this mod's block entities.
 *
 * <p>AE2 only auto-registers {@code IN_WORLD_GRID_NODE_HOST} for its own block entity types, so a
 * third-party mod must register both the grid-node-host (so adjacent cables/nodes can connect) and
 * {@code CRAFTING_MACHINE} (so pattern providers push patterns here) explicitly.
 */
public final class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {
        registerDistributor(event, ModBlockEntities.PACKAGE_DISTRIBUTOR.get());
        registerDistributor(event, ModBlockEntities.BASIC_PACKAGE_DISTRIBUTOR.get());
    }

    private static <T extends com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity> void registerDistributor(
            RegisterCapabilitiesEvent event, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        // Allow adjacent AE2 cables/nodes to discover and connect to this block in-world.
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type, (be, context) -> be);

        // Allow pattern providers to treat this block as a crafting machine and push patterns to it.
        event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, type, (be, context) -> be);
    }

    private ModCapabilities() {}
}
