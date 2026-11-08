package com.lhy.createpackage.content.distributor;

import com.lhy.createpackage.content.CreatePackagePatternProviderMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;

public class AdvancedPackageDistributorMenu extends CreatePackagePatternProviderMenu {
    public AdvancedPackageDistributorMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, AdvancedPackageDistributorBlockEntity host) {
        super(menuType, id, playerInventory, host, host.getUpgrades());
    }
}
