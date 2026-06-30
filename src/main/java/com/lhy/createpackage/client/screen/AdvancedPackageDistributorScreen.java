package com.lhy.createpackage.client.screen;

import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorMenu;

import appeng.client.gui.style.ScreenStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AdvancedPackageDistributorScreen extends CreatePackagePatternProviderScreen<AdvancedPackageDistributorMenu> {
    public AdvancedPackageDistributorScreen(AdvancedPackageDistributorMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
