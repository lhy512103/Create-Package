package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative mode tab registry for Create Package.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreatePackage.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CreatePackage.MODID))
                    .icon(() -> new ItemStack(ModItems.PACKAGE_DISTRIBUTOR.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PACKAGE_DISTRIBUTOR.get());
                        output.accept(ModItems.MACHINE_LINKER.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
