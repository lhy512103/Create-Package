package com.lhy.createpackage.content.distributor;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.implementations.PatternProviderMenu;

public class AdvancedPackageDistributorMenu extends PatternProviderMenu {
    public AdvancedPackageDistributorMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, AdvancedPackageDistributorBlockEntity host) {
        super(menuType, id, playerInventory, host);
        setupUpgrades(host.getUpgrades());
    }
}
