package com.lhy.createpackage;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.lhy.createpackage.client.ponder.CreatePackagePonderPlugin;
import com.lhy.createpackage.client.AutoRouteKeys;
import com.lhy.createpackage.client.render.MechanicalPackagePatternHighlighter;
import com.lhy.createpackage.client.screen.AdvancedPackageDistributorScreen;
import com.lhy.createpackage.client.screen.CreatePackagePatternProviderScreen;
import com.lhy.createpackage.client.screen.ExtendedKineticPatternProviderScreen;
import com.lhy.createpackage.client.screen.MechanicalPatternConverterScreen;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorMenu;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorMenu;
import com.lhy.createpackage.content.kinetic.ExtendedKineticPatternProviderMenu;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderMenu;
import com.lhy.createpackage.registry.ModMenuTypes;

import appeng.client.gui.style.StyleManager;
import net.createmod.ponder.foundation.PonderIndex;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CreatePackage.MODID, dist = Dist.CLIENT)
public class CreatePackageClient {
    public CreatePackageClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(CreatePackageClient::onClientSetup);
        modEventBus.addListener(CreatePackageClient::registerScreens);
        modEventBus.addListener(CreatePackageClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(MechanicalPackagePatternHighlighter::render);
        NeoForge.EVENT_BUS.addListener(AutoRouteKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(AutoRouteKeys::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(AutoRouteKeys::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(AutoRouteKeys::onLeftClickEmpty);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new CreatePackagePonderPlugin()));
    }

    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.MECHANICAL_PATTERN_CONVERTER.get(), MechanicalPatternConverterScreen::new);
        event.<BasicPackageDistributorMenu, CreatePackagePatternProviderScreen<BasicPackageDistributorMenu>>register(
                ModMenuTypes.BASIC_PACKAGE_DISTRIBUTOR.get(), (menu, playerInv, title) ->
                new CreatePackagePatternProviderScreen<>(menu, playerInv, title,
                        StyleManager.loadStyleDoc("/screens/createpackage_pattern_provider.json")));
        event.<AdvancedPackageDistributorMenu, AdvancedPackageDistributorScreen>register(
                ModMenuTypes.ADVANCED_PACKAGE_DISTRIBUTOR.get(), (menu, playerInv, title) ->
                new AdvancedPackageDistributorScreen(menu, playerInv, title,
                        StyleManager.loadStyleDoc("/screens/createpackage_pattern_provider.json")));
        event.<KineticPatternProviderMenu, CreatePackagePatternProviderScreen<KineticPatternProviderMenu>>register(
                ModMenuTypes.KINETIC_PATTERN_PROVIDER.get(), (menu, playerInv, title) ->
                new CreatePackagePatternProviderScreen<>(menu, playerInv, title,
                        StyleManager.loadStyleDoc("/screens/createpackage_pattern_provider.json")));
        event.<ExtendedKineticPatternProviderMenu, ExtendedKineticPatternProviderScreen>register(
                ModMenuTypes.EXTENDED_KINETIC_PATTERN_PROVIDER.get(), (menu, playerInv, title) ->
                new ExtendedKineticPatternProviderScreen(menu, playerInv, title,
                        StyleManager.loadStyleDoc("/screens/createpackage_extended_kinetic_pattern_provider.json")));
    }

    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        AutoRouteKeys.register(event);
    }
}
