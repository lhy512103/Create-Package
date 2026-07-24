package com.lhy.createpackage.client.screen;

import com.lhy.createpackage.content.distributor.BasicPackageDistributorMenu;

import appeng.client.gui.style.StyleManager;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BasicPackageDistributorScreen extends CreatePackagePatternProviderScreen<BasicPackageDistributorMenu> {
    public BasicPackageDistributorScreen(BasicPackageDistributorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, StyleManager.loadStyleDoc("/screens/pattern_provider.json"));
    }
}
