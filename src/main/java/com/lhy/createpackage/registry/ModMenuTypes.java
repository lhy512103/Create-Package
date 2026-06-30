package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorMenu;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorMenu;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterMenu;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.menu.implementations.MenuTypeBuilder;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreatePackage.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MechanicalPatternConverterMenu>> MECHANICAL_PATTERN_CONVERTER =
            MENUS.register("mechanical_pattern_converter",
                    () -> IMenuTypeExtension.create(MechanicalPatternConverterMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<BasicPackageDistributorMenu>> BASIC_PACKAGE_DISTRIBUTOR =
            MENUS.register("basic_package_distributor",
                    () -> MenuTypeBuilder
                            .create(BasicPackageDistributorMenu::new, BasicPackageDistributorBlockEntity.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "block." + CreatePackage.MODID + ".basic_package_distributor"))
                            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                                    CreatePackage.MODID, "basic_package_distributor")));

    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedPackageDistributorMenu>> ADVANCED_PACKAGE_DISTRIBUTOR =
            MENUS.register("advanced_package_distributor",
                    () -> MenuTypeBuilder
                            .create(AdvancedPackageDistributorMenu::new, AdvancedPackageDistributorBlockEntity.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "block." + CreatePackage.MODID + ".advanced_package_distributor"))
                            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                                    CreatePackage.MODID, "advanced_package_distributor")));

    public static final DeferredHolder<MenuType<?>, MenuType<KineticPatternProviderMenu>> KINETIC_PATTERN_PROVIDER =
            MENUS.register("kinetic_pattern_provider",
                    () -> MenuTypeBuilder
                            .create(KineticPatternProviderMenu::new, KineticPatternProviderBlockEntity.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "block." + CreatePackage.MODID + ".kinetic_pattern_provider"))
                            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                                    CreatePackage.MODID, "kinetic_pattern_provider")));

    private ModMenuTypes() {}
}
