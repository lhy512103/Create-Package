package com.lhy.createpackage.content;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.compat.Ae2UtilityCompat;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;

/**
 * Shared pattern-provider menu: bind this machine's upgrade inventory (hiding addon-injected
 * slots that point elsewhere) and attach AE2's network-tool toolbox so MEST toolkit slots appear.
 */
public abstract class CreatePackagePatternProviderMenu extends PatternProviderMenu {
    @Nullable
    private final ToolboxMenu toolbox;
    private final IUpgradeInventory hostUpgrades;

    protected CreatePackagePatternProviderMenu(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, PatternProviderLogicHost host, @Nullable IUpgradeInventory hostUpgrades) {
        super(menuType, id, playerInventory, host);
        this.hostUpgrades = hostUpgrades;
        if (hostUpgrades != null) {
            bindHostUpgrades(hostUpgrades);
        }
        this.toolbox = getSlots(SlotSemantics.TOOLBOX).isEmpty() ? new ToolboxMenu(this) : null;
    }

    @Override
    protected Slot addSlot(Slot slot, appeng.menu.SlotSemantic semantic) {
        // Utility injects a separate slot during the superclass constructor. Our machines
        // use the four host slots instead, so never add that extra slot to the menu.
        if (slot.getClass().getName().equals("com.lhy.ae2utility.menu.PatternProviderTearSlot")) {
            return slot;
        }
        return super.addSlot(slot, semantic);
    }

    @Override
    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        if (Ae2UtilityCompat.isTearCard(stack)) {
            if (!(slot instanceof AppEngSlot appSlot) || !appSlot.isSlotEnabled()
                    || appSlot.getInventory() != hostUpgrades) {
                return false;
            }
            for (Slot other : getSlots(SlotSemantics.UPGRADE)) {
                if (other != slot && Ae2UtilityCompat.isTearCard(other.getItem())) {
                    return false;
                }
            }
        }
        return super.isValidForSlot(slot, stack);
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot candidate, ItemStack stack, boolean fromPlayerSide) {
        return candidate.isActive() && super.isValidQuickMoveDestination(candidate, stack, fromPlayerSide);
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
        // ExtendedAE Plus cancels AEBaseMenu.setupUpgrades once any upgrade slot exists.
        // Add this machine's slots directly so the panel is not left empty.
        for (int slot = 0; slot < hostUpgrades.size(); slot++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, hostUpgrades, slot),
                    SlotSemantics.UPGRADE);
        }
    }
}
