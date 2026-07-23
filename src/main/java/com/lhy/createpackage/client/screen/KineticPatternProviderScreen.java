package com.lhy.createpackage.client.screen;

import com.lhy.createpackage.content.kinetic.KineticPatternProviderMenu;

import appeng.client.gui.style.StyleManager;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class KineticPatternProviderScreen extends CreatePackagePatternProviderScreen<KineticPatternProviderMenu> {
    public KineticPatternProviderScreen(KineticPatternProviderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, StyleManager.loadStyleDoc("/screens/pattern_provider.json"));
    }
}
