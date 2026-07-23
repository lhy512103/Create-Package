package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.PackageDistributorBlock;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CreatePackage.MODID);

    public static final RegistryObject<PackageDistributorBlock> PACKAGE_DISTRIBUTOR = BLOCKS.register("package_distributor",
            () -> new PackageDistributorBlock(properties(2.0f, 6.0f)));
    public static final RegistryObject<PackageDistributorBlock> BASIC_PACKAGE_DISTRIBUTOR = BLOCKS.register("basic_package_distributor",
            () -> new PackageDistributorBlock(properties(3.0f, 6.0f), BasicPackageDistributorBlockEntity::new));
    public static final RegistryObject<PackageDistributorBlock> ADVANCED_PACKAGE_DISTRIBUTOR = BLOCKS.register("advanced_package_distributor",
            () -> new PackageDistributorBlock(properties(4.0f, 8.0f), AdvancedPackageDistributorBlockEntity::new));
    public static final RegistryObject<KineticPatternProviderBlock> KINETIC_PATTERN_PROVIDER = BLOCKS.register("kinetic_pattern_provider",
            () -> new KineticPatternProviderBlock(properties(3.0f, 6.0f)));

    private static BlockBehaviour.Properties properties(float hardness, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(hardness, resistance)
                .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops();
    }
    private ModBlocks() {}
}
