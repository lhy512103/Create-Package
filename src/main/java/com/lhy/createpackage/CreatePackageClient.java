package com.lhy.createpackage;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.createmod.ponder.foundation.PonderIndex;

import com.lhy.createpackage.client.AutoRouteKeys;
import com.lhy.createpackage.client.render.MechanicalPackagePatternHighlighter;
import com.lhy.createpackage.client.screen.AdvancedPackageDistributorScreen;
import com.lhy.createpackage.client.screen.BasicPackageDistributorScreen;
import com.lhy.createpackage.client.screen.KineticPatternProviderScreen;
import com.lhy.createpackage.client.screen.MechanicalPatternConverterScreen;
import com.lhy.createpackage.client.ponder.CreatePackagePonderPlugin;
import com.lhy.createpackage.registry.ModMenuTypes;


// This class will not load on dedicated servers. Accessing client side code from here is safe.
public class CreatePackageClient {
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CreatePackageClient::onClientSetup);
        modEventBus.addListener(CreatePackageClient::registerKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(MechanicalPackagePatternHighlighter::render);
        MinecraftForge.EVENT_BUS.addListener(AutoRouteKeys::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(AutoRouteKeys::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(AutoRouteKeys::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(AutoRouteKeys::onLeftClickEmpty);


    }

    static void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new CreatePackagePonderPlugin());
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.MECHANICAL_PATTERN_CONVERTER.get(), MechanicalPatternConverterScreen::new);
            MenuScreens.register(ModMenuTypes.BASIC_PACKAGE_DISTRIBUTOR.get(), BasicPackageDistributorScreen::new);
            MenuScreens.register(ModMenuTypes.ADVANCED_PACKAGE_DISTRIBUTOR.get(), AdvancedPackageDistributorScreen::new);
            MenuScreens.register(ModMenuTypes.KINETIC_PATTERN_PROVIDER.get(), KineticPatternProviderScreen::new);
            });
    }

    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        AutoRouteKeys.register(event);
    }
}
