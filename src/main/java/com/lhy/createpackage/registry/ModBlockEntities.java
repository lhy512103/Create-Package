package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Block entity type registry for Create Package.
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePackage.MODID);

    public static final RegistryObject<BlockEntityType<PackageDistributorBlockEntity>> PACKAGE_DISTRIBUTOR =
            BLOCK_ENTITIES.register("package_distributor",
                    () -> BlockEntityType.Builder.of(
                            PackageDistributorBlockEntity::new,
                            ModBlocks.PACKAGE_DISTRIBUTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<BasicPackageDistributorBlockEntity>> BASIC_PACKAGE_DISTRIBUTOR =
            BLOCK_ENTITIES.register("basic_package_distributor",
                    () -> BlockEntityType.Builder.of(
                            BasicPackageDistributorBlockEntity::new,
                            ModBlocks.BASIC_PACKAGE_DISTRIBUTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<AdvancedPackageDistributorBlockEntity>> ADVANCED_PACKAGE_DISTRIBUTOR =
            BLOCK_ENTITIES.register("advanced_package_distributor",
                    () -> BlockEntityType.Builder.of(
                            AdvancedPackageDistributorBlockEntity::new,
                            ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<KineticPatternProviderBlockEntity>> KINETIC_PATTERN_PROVIDER =
            BLOCK_ENTITIES.register("kinetic_pattern_provider",
                    () -> BlockEntityType.Builder.of(
                            KineticPatternProviderBlockEntity::new,
                            ModBlocks.KINETIC_PATTERN_PROVIDER.get()).build(null));

    private ModBlockEntities() {}
}
