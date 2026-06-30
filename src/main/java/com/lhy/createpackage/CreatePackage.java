package com.lhy.createpackage;

import org.slf4j.Logger;

import com.lhy.createpackage.registry.ModBlockEntities;
import com.lhy.createpackage.registry.ModBlocks;
import com.lhy.createpackage.registry.ModCapabilities;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModCreativeTabs;
import com.lhy.createpackage.registry.ModItems;
import com.lhy.createpackage.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;

/**
 * Create Package — adds AE2 autocrafting support for Create's sequenced assembly.
 *
 * <p>A "package distributor" block sits next to an AE2 pattern provider, exposes the
 * {@code CRAFTING_MACHINE} capability to receive a whole pattern's inputs at once, then
 * distributes each ingredient to the linked Create machines (depot / deployers / spouts)
 * following the sequenced-assembly recipe steps, and returns the result to the network.
 */
@Mod(CreatePackage.MODID)
public class CreatePackage {
    public static final String MODID = "createpackage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreatePackage(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // Register all deferred registers to the mod event bus.
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModComponents.DATA_COMPONENTS.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Register AE2 capabilities for our block entities.
        modEventBus.addListener(ModCapabilities::register);

        // Register the common config.
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.PACKAGE_DISTRIBUTOR.get(),
                    ModItems.PACKAGE_DISTRIBUTOR.get());
            AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.BASIC_PACKAGE_DISTRIBUTOR.get(),
                    ModItems.BASIC_PACKAGE_DISTRIBUTOR.get());
            AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.ADVANCED_PACKAGE_DISTRIBUTOR.get(),
                    ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get());
            AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.KINETIC_PATTERN_PROVIDER.get(),
                    ModItems.KINETIC_PATTERN_PROVIDER.get());
            Upgrades.add(ModItems.PARALLEL_CARD.get(), ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get(), 2);
        });
    }
}
