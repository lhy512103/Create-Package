package com.lhy.createpackage.registry;

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
        registerDistributor(event, ModBlockEntities.ADVANCED_PACKAGE_DISTRIBUTOR.get());
        registerKineticProvider(event, ModBlockEntities.KINETIC_PATTERN_PROVIDER.get());
        registerKineticProvider(event, ModBlockEntities.EXTENDED_KINETIC_PATTERN_PROVIDER.get());
    }

    private static <T extends com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity> void registerDistributor(
            RegisterCapabilitiesEvent event, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        // Allow adjacent AE2 cables/nodes to discover and connect to this block in-world.
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type, (be, context) -> be);

        // Allow pattern providers to treat this block as a crafting machine and push patterns to it.
        event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, type, (be, context) -> be);
    }

    private static <T extends com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity> void registerKineticProvider(
            RegisterCapabilitiesEvent event, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        // AE2 cables only auto-register this host for AE2's own block-entity types.
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type, (be, context) -> be);

        // Same registration AE2 gives its own Pattern Provider: the return slots. AE adapts a registered generic
        // inventory into item and fluid handlers, so a Mechanical Crafter can hand its product to the provider and AE
        // returns it to the network.
        event.registerBlockEntity(AECapabilities.GENERIC_INTERNAL_INV, type, (be, context) -> be.getLogic().getReturnInv());
    }

    private ModCapabilities() {}
}
