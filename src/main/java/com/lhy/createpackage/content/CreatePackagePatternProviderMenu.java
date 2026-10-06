package com.lhy.createpackage.content;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;

/**
 * Shared pattern-provider menu: bind this machine's upgrade inventory (hiding addon-injected
 * slots that point elsewhere) and attach AE2's network-tool toolbox so MEST toolkit slots appear.
 */
public abstract class CreatePackagePatternProviderMenu extends PatternProviderMenu {
    @Nullable
    private final ToolboxMenu toolbox;

    protected CreatePackagePatternProviderMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, PatternProviderLogicHost host, @Nullable IUpgradeInventory hostUpgrades) {
        super(menuType, id, playerInventory, host);
        if (hostUpgrades != null) {
            bindHostUpgrades(hostUpgrades);
        }
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
