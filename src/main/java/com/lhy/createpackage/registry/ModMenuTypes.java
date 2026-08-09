package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorMenu;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorMenu;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterMenu;
import com.lhy.createpackage.content.kinetic.ExtendedKineticPatternProviderBlockEntity;
import com.lhy.createpackage.content.kinetic.ExtendedKineticPatternProviderMenu;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;



public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreatePackage.MODID);

    public static final RegistryObject<MenuType<MechanicalPatternConverterMenu>> MECHANICAL_PATTERN_CONVERTER =
            MENUS.register("mechanical_pattern_converter",
                    () -> IForgeMenuType.create(MechanicalPatternConverterMenu::fromNetwork));

    public static final RegistryObject<MenuType<BasicPackageDistributorMenu>> BASIC_PACKAGE_DISTRIBUTOR =
            MENUS.register("basic_package_distributor",
                    () -> ModMenuTypeBuilder
                            .create(BasicPackageDistributorMenu::new, BasicPackageDistributorBlockEntity.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "block." + CreatePackage.MODID + ".basic_package_distributor"))
                            .buildUnregistered(new ResourceLocation(
                                    CreatePackage.MODID, "basic_package_distributor")));

    public static final RegistryObject<MenuType<AdvancedPackageDistributorMenu>> ADVANCED_PACKAGE_DISTRIBUTOR =
            MENUS.register("advanced_package_distributor",
                    () -> ModMenuTypeBuilder
                            .create(AdvancedPackageDistributorMenu::new, AdvancedPackageDistributorBlockEntity.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "block." + CreatePackage.MODID + ".advanced_package_distributor"))
                            .buildUnregistered(new ResourceLocation(
                                    CreatePackage.MODID, "advanced_package_distributor")));

    public static final RegistryObject<MenuType<KineticPatternProviderMenu>> KINETIC_PATTERN_PROVIDER =
            MENUS.register("kinetic_pattern_provider",
                    () -> ModMenuTypeBuilder
                            .create(KineticPatternProviderMenu::new, KineticPatternProviderBlockEntity.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "block." + CreatePackage.MODID + ".kinetic_pattern_provider"))
                            .buildUnregistered(new ResourceLocation(
                                    CreatePackage.MODID, "kinetic_pattern_provider")));

    public static final RegistryObject<MenuType<ExtendedKineticPatternProviderMenu>> EXTENDED_KINETIC_PATTERN_PROVIDER =
            MENUS.register("extended_kinetic_pattern_provider",
                    () -> ModMenuTypeBuilder
                            .create(ExtendedKineticPatternProviderMenu::new, ExtendedKineticPatternProviderBlockEntity.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "block." + CreatePackage.MODID + ".extended_kinetic_pattern_provider"))
                            .buildUnregistered(new ResourceLocation(
                                    CreatePackage.MODID, "extended_kinetic_pattern_provider")));

    private ModMenuTypes() {}
}
