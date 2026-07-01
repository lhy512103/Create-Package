package com.lhy.createpackage.client.ponder;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.registry.ModItems;

import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class CreatePackagePonderTags {
    public static final ResourceLocation CREATE_PACKAGE = id("create_package");

    private CreatePackagePonderTags() {
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(CREATE_PACKAGE)
                .addToIndex()
                .item(ModItems.PACKAGE_DISTRIBUTOR.get(), true, false)
                .title("Create Package")
                .description("AE2 autocrafting support for Create machines and sequenced assembly")
                .register();

        helper.addToTag(CREATE_PACKAGE)
                .add(id("package_distributor"))
                .add(id("basic_package_distributor"))
                .add(id("advanced_package_distributor"))
                .add(id("kinetic_pattern_provider"))
                .add(id("machine_linker"))
                .add(id("mechanical_pattern_converter"))
                .add(id("mechanical_package_pattern"))
                .add(id("parallel_card"))
                .add(id("incomplete_package_distributor"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreatePackage.MODID, path);
    }
}
