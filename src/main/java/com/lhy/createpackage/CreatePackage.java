package com.lhy.createpackage;

import org.slf4j.Logger;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.integration.ae2.MechanicalPackagePatternEncodingCompat;
import com.lhy.createpackage.network.ModNetwork;
import com.lhy.createpackage.registry.*;
import com.mojang.logging.LogUtils;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(CreatePackage.MODID)
public class CreatePackage {
    public static final String MODID = "createpackage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreatePackage() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(MechanicalPackagePatternEncodingCompat::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(MechanicalPackagePatternEncodingCompat::onContainerClose);
        MinecraftForge.EVENT_BUS.addListener(PackageDistributorBlockEntity::onPlayerLoggedIn);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            CreatePackageClient.init(modEventBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.PACKAGE_DISTRIBUTOR.get(), ModItems.PACKAGE_DISTRIBUTOR.get());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.BASIC_PACKAGE_DISTRIBUTOR.get(), ModItems.BASIC_PACKAGE_DISTRIBUTOR.get());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.ADVANCED_PACKAGE_DISTRIBUTOR.get(), ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.KINETIC_PATTERN_PROVIDER.get(), ModItems.KINETIC_PATTERN_PROVIDER.get());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.EXTENDED_KINETIC_PATTERN_PROVIDER.get(), ModItems.EXTENDED_KINETIC_PATTERN_PROVIDER.get());
            Upgrades.add(ModItems.PARALLEL_CARD.get(), ModItems.ADVANCED_PACKAGE_DISTRIBUTOR.get(), 2);
            Upgrades.add(ModItems.PARALLEL_CARD.get(), ModItems.KINETIC_PATTERN_PROVIDER.get(), 2);
            Upgrades.add(ModItems.PARALLEL_CARD.get(), ModItems.EXTENDED_KINETIC_PATTERN_PROVIDER.get(), 2);
            if (ModList.get().isLoaded("ftbultimine")) {
                com.lhy.createpackage.integration.ftbultimine.FtbUltimineCompat.register();
            }
        });
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(MODID, path); }
}
