package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreatePackage.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MechanicalPatternConverterMenu>> MECHANICAL_PATTERN_CONVERTER =
            MENUS.register("mechanical_pattern_converter",
                    () -> IMenuTypeExtension.create(MechanicalPatternConverterMenu::fromNetwork));

    private ModMenuTypes() {}
}
