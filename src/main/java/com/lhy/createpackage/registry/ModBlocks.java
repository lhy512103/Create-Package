package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.PackageDistributorBlock;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry for Create Package.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePackage.MODID);

    public static final DeferredBlock<PackageDistributorBlock> PACKAGE_DISTRIBUTOR = BLOCKS.register(
            "package_distributor",
            () -> new PackageDistributorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<PackageDistributorBlock> BASIC_PACKAGE_DISTRIBUTOR = BLOCKS.register(
            "basic_package_distributor",
            () -> new PackageDistributorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops(),
                    BasicPackageDistributorBlockEntity::new));

    public static final DeferredBlock<PackageDistributorBlock> ADVANCED_PACKAGE_DISTRIBUTOR = BLOCKS.register(
            "advanced_package_distributor",
            () -> new PackageDistributorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0f, 8.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops(),
                    AdvancedPackageDistributorBlockEntity::new));

    public static final DeferredBlock<KineticPatternProviderBlock> KINETIC_PATTERN_PROVIDER = BLOCKS.register(
            "kinetic_pattern_provider",
            () -> new KineticPatternProviderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    private ModBlocks() {}
}
