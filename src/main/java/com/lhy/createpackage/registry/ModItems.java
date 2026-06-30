package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterItem;
import com.lhy.createpackage.content.linker.MachineLinkerItem;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternDetails;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.upgrades.Upgrades;

/**
 * Item registry for Create Package.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePackage.MODID);

    public static final DeferredItem<BlockItem> PACKAGE_DISTRIBUTOR = ITEMS.registerSimpleBlockItem(
            "package_distributor", ModBlocks.PACKAGE_DISTRIBUTOR, new Item.Properties());

    public static final DeferredItem<BlockItem> BASIC_PACKAGE_DISTRIBUTOR = ITEMS.registerSimpleBlockItem(
            "basic_package_distributor", ModBlocks.BASIC_PACKAGE_DISTRIBUTOR, new Item.Properties());

    public static final DeferredItem<BlockItem> ADVANCED_PACKAGE_DISTRIBUTOR = ITEMS.registerSimpleBlockItem(
            "advanced_package_distributor", ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR, new Item.Properties());

    public static final DeferredItem<BlockItem> KINETIC_PATTERN_PROVIDER = ITEMS.registerSimpleBlockItem(
            "kinetic_pattern_provider", ModBlocks.KINETIC_PATTERN_PROVIDER, new Item.Properties());

    public static final DeferredItem<Item> MACHINE_LINKER = ITEMS.registerItem(
            "machine_linker", properties -> new MachineLinkerItem(properties.stacksTo(1)));

    public static final DeferredItem<Item> MECHANICAL_PATTERN_CONVERTER = ITEMS.registerItem(
            "mechanical_pattern_converter", properties -> new MechanicalPatternConverterItem(properties.stacksTo(1)));

    public static final DeferredItem<Item> MECHANICAL_PACKAGE_PATTERN = ITEMS.register(
            "mechanical_package_pattern",
            () -> new MechanicalPackagePatternItem(new Item.Properties().stacksTo(1),
                    MechanicalPackagePatternDetails::decode, null));

    public static final DeferredItem<Item> PARALLEL_CARD = ITEMS.register(
            "parallel_card",
            () -> Upgrades.createUpgradeCardItem(new Item.Properties()));

    public static final DeferredItem<Item> INCOMPLETE_PACKAGE_DISTRIBUTOR = ITEMS.registerSimpleItem(
            "incomplete_package_distributor", new Item.Properties());

    private ModItems() {}
}
