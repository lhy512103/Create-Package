package com.lhy.createpackage.content.kinetic;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.config.YesNo;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.guisync.GuiSync;

public class KineticPatternProviderMenu extends PatternProviderMenu {
    private static final String ACTION_TOGGLE_SMART_DOUBLING = "toggleSmartDoubling";

    private final KineticPatternProviderBlockEntity host;

    @GuiSync(40)
    public YesNo smartDoubling = YesNo.NO;

    public KineticPatternProviderMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, KineticPatternProviderBlockEntity host) {
        super(menuType, id, playerInventory, host);
        this.host = host;
        setupUpgrades(host.getUpgrades());
        registerClientAction(ACTION_TOGGLE_SMART_DOUBLING, this::toggleSmartDoubling);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            smartDoubling = host.isSmartDoublingEnabled() ? YesNo.YES : YesNo.NO;
        }
        super.broadcastChanges();
    }

    public YesNo getSmartDoubling() {
        return smartDoubling;
    }

    public void toggleSmartDoubling() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_SMART_DOUBLING);
            return;
        }
        host.toggleSmartDoubling();
    }
}
