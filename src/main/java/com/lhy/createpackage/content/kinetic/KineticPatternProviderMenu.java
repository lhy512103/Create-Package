package com.lhy.createpackage.content.kinetic;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.YesNo;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;

public class KineticPatternProviderMenu extends PatternProviderMenu {
    private static final String ACTION_TOGGLE_SMART_DOUBLING = "toggleSmartDoubling";

    private final KineticPatternProviderBlockEntity host;
    @Nullable
    private final ToolboxMenu toolbox;

    @GuiSync(40)
    public YesNo smartDoubling = YesNo.NO;

    public KineticPatternProviderMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, KineticPatternProviderBlockEntity host) {
        super(menuType, id, playerInventory, host);
        this.host = host;
        bindHostUpgrades(host.getUpgrades());
        this.toolbox = getSlots(SlotSemantics.TOOLBOX).isEmpty() ? new ToolboxMenu(this) : null;
        registerClientAction(ACTION_TOGGLE_SMART_DOUBLING, this::toggleSmartDoubling);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            smartDoubling = host.isSmartDoublingEnabled() ? YesNo.YES : YesNo.NO;
        }
        if (toolbox != null) {
            toolbox.tick();
        }
        super.broadcastChanges();
    }

    public YesNo getSmartDoubling() {
        return smartDoubling;
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

    public void toggleSmartDoubling() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_SMART_DOUBLING);
            return;
        }
        host.toggleSmartDoubling();
    }
}
