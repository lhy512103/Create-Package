package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterItem;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockItem;
import com.lhy.createpackage.content.linker.MachineLinkerItem;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternItem;
import appeng.api.upgrades.Upgrades;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CreatePackage.MODID);
    public static final RegistryObject<PackageDistributorBlockItem> PACKAGE_DISTRIBUTOR = ITEMS.register("package_distributor",
            () -> new PackageDistributorBlockItem(ModBlocks.PACKAGE_DISTRIBUTOR.get(), new Item.Properties()));
    public static final RegistryObject<PackageDistributorBlockItem> BASIC_PACKAGE_DISTRIBUTOR = ITEMS.register("basic_package_distributor",
            () -> new PackageDistributorBlockItem(ModBlocks.BASIC_PACKAGE_DISTRIBUTOR.get(), new Item.Properties()));
    public static final RegistryObject<PackageDistributorBlockItem> ADVANCED_PACKAGE_DISTRIBUTOR = ITEMS.register("advanced_package_distributor",
            () -> new PackageDistributorBlockItem(ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> KINETIC_PATTERN_PROVIDER = ITEMS.register("kinetic_pattern_provider",
            () -> new BlockItem(ModBlocks.KINETIC_PATTERN_PROVIDER.get(), new Item.Properties()));
    public static final RegistryObject<Item> MACHINE_LINKER = ITEMS.register("machine_linker",
            () -> new MachineLinkerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MECHANICAL_PATTERN_CONVERTER = ITEMS.register("mechanical_pattern_converter",
            () -> new MechanicalPatternConverterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MECHANICAL_PACKAGE_PATTERN = ITEMS.register("mechanical_package_pattern",
            () -> new MechanicalPackagePatternItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PARALLEL_CARD = ITEMS.register("parallel_card",
            () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final RegistryObject<Item> INCOMPLETE_PACKAGE_DISTRIBUTOR = ITEMS.register("incomplete_package_distributor",
            () -> new Item(new Item.Properties()));
    private ModItems() {}
}
