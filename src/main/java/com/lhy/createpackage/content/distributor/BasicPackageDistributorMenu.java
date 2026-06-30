package com.lhy.createpackage.content.distributor;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.implementations.PatternProviderMenu;

public class BasicPackageDistributorMenu extends PatternProviderMenu {
    public BasicPackageDistributorMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, BasicPackageDistributorBlockEntity host) {
        super(menuType, id, playerInventory, host);
    }
}
