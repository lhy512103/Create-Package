package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entity type registry for Create Package.
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePackage.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PackageDistributorBlockEntity>> PACKAGE_DISTRIBUTOR =
            BLOCK_ENTITIES.register("package_distributor",
                    () -> BlockEntityType.Builder.of(
                            PackageDistributorBlockEntity::new,
                            ModBlocks.PACKAGE_DISTRIBUTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicPackageDistributorBlockEntity>> BASIC_PACKAGE_DISTRIBUTOR =
            BLOCK_ENTITIES.register("basic_package_distributor",
                    () -> BlockEntityType.Builder.of(
                            BasicPackageDistributorBlockEntity::new,
                            ModBlocks.BASIC_PACKAGE_DISTRIBUTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedPackageDistributorBlockEntity>> ADVANCED_PACKAGE_DISTRIBUTOR =
            BLOCK_ENTITIES.register("advanced_package_distributor",
                    () -> BlockEntityType.Builder.of(
                            AdvancedPackageDistributorBlockEntity::new,
                            ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get()).build(null));

    private ModBlockEntities() {}
}
