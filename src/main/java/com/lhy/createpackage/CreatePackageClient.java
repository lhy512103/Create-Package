package com.lhy.createpackage;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.lhy.createpackage.client.render.MechanicalPackagePatternHighlighter;
import com.lhy.createpackage.client.screen.AdvancedPackageDistributorScreen;
import com.lhy.createpackage.client.screen.MechanicalPatternConverterScreen;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorMenu;
import com.lhy.createpackage.registry.ModMenuTypes;

import appeng.client.gui.style.StyleManager;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CreatePackage.MODID, dist = Dist.CLIENT)
public class CreatePackageClient {
    public CreatePackageClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(CreatePackageClient::onClientSetup);
        modEventBus.addListener(CreatePackageClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(MechanicalPackagePatternHighlighter::render);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        CreatePackage.LOGGER.info("HELLO FROM CLIENT SETUP");
        CreatePackage.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.MECHANICAL_PATTERN_CONVERTER.get(), MechanicalPatternConverterScreen::new);
        event.<AdvancedPackageDistributorMenu, AdvancedPackageDistributorScreen>register(
                ModMenuTypes.ADVANCED_PACKAGE_DISTRIBUTOR.get(), (menu, playerInv, title) ->
                new AdvancedPackageDistributorScreen(menu, playerInv, title,
                        StyleManager.loadStyleDoc("/screens/pattern_provider.json")));
    }
}
