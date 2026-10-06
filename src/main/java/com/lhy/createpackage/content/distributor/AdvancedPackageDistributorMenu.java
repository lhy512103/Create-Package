package com.lhy.createpackage.content.distributor;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;

public class AdvancedPackageDistributorMenu extends PatternProviderMenu {
    @Nullable
    private final ToolboxMenu toolbox;

    public AdvancedPackageDistributorMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, AdvancedPackageDistributorBlockEntity host) {
        super(menuType, id, playerInventory, host);
        bindHostUpgrades(host.getUpgrades());
        this.toolbox = getSlots(SlotSemantics.TOOLBOX).isEmpty() ? new ToolboxMenu(this) : null;
    }

    @Override
    public void broadcastChanges() {
        if (toolbox != null) {
            toolbox.tick();
        }
        super.broadcastChanges();
    }

    private void bindHostUpgrades(IUpgradeInventory hostUpgrades) {
        for (Slot slot : getSlots(SlotSemantics.UPGRADE)) {
            if (slot instanceof AppEngSlot appEngSlot && appEngSlot.getInventory() != hostUpgrades) {
                appEngSlot.setSlotEnabled(false);
            }
        }
        for (Slot slot : getSlots(SlotSemantics.UPGRADE)) {
            if (slot instanceof AppEngSlot appEngSlot && appEngSlot.isSlotEnabled()
                    && appEngSlot.getInventory() == hostUpgrades) {
                return;
            }
        }
        setupUpgrades(hostUpgrades);
    }
}
