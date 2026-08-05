package com.lhy.createpackage.content.kinetic;

import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;

public class ExtendedKineticPatternProviderMenu extends KineticPatternProviderMenu {
    private static final String ACTION_PREVIOUS_PAGE = "previousPatternPage";
    private static final String ACTION_NEXT_PAGE = "nextPatternPage";

    private final ExtendedKineticPatternProviderBlockEntity extendedHost;

    @GuiSync(41)
    private int patternPage;
    @GuiSync(42)
    private int patternPageCount = 1;

    public ExtendedKineticPatternProviderMenu(MenuType<? extends KineticPatternProviderMenu> menuType, int id,
            Inventory playerInventory, ExtendedKineticPatternProviderBlockEntity host) {
        super(menuType, id, playerInventory, host);
        this.extendedHost = host;
        registerClientAction(ACTION_PREVIOUS_PAGE, this::previousPatternPage);
        registerClientAction(ACTION_NEXT_PAGE, this::nextPatternPage);
        updatePatternSlotVisibility();
    }

    @Override
    protected boolean canSlotsBeHidden(appeng.menu.SlotSemantic semantic) {
        return semantic == SlotSemantics.ENCODED_PATTERN || super.canSlotsBeHidden(semantic);
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot candidateSlot, net.minecraft.world.item.ItemStack stackToMove,
            boolean fromPlayerSide) {
        if (getSlotSemantic(candidateSlot) == SlotSemantics.ENCODED_PATTERN
                && !getSlots(SlotSemantics.ENCODED_PATTERN).contains(candidateSlot)) {
            return false;
        }
        return super.isValidQuickMoveDestination(candidateSlot, stackToMove, fromPlayerSide);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            patternPageCount = extendedHost.getAvailablePatternPages();
            patternPage = Math.min(patternPage, patternPageCount - 1);
        }
        updatePatternSlotVisibility();
        super.broadcastChanges();
    }

    @Override
    public List<Slot> getSlots(appeng.menu.SlotSemantic semantic) {
        List<Slot> slots = super.getSlots(semantic);
        if (semantic != SlotSemantics.ENCODED_PATTERN || slots.isEmpty()) {
            return slots;
        }
        int firstVisibleSlot = Math.min(patternPage * ExtendedKineticPatternProviderBlockEntity.SLOTS_PER_PAGE,
                slots.size());
        int lastVisibleSlot = Math.min(firstVisibleSlot + ExtendedKineticPatternProviderBlockEntity.SLOTS_PER_PAGE,
                slots.size());
        return slots.subList(firstVisibleSlot, lastVisibleSlot);
    }

    public List<Slot> getAllPatternSlots() {
        return super.getSlots(SlotSemantics.ENCODED_PATTERN);
    }

    public int getPatternPage() {
        return patternPage;
    }

    public int getPatternPageCount() {
        return patternPageCount;
    }

    public void previousPatternPage() {
        changePatternPage(-1, ACTION_PREVIOUS_PAGE);
    }

    public void nextPatternPage() {
        changePatternPage(1, ACTION_NEXT_PAGE);
    }

    private void changePatternPage(int delta, String action) {
        if (isClientSide()) {
            patternPage = Math.max(0, Math.min(patternPage + delta, patternPageCount - 1));
            updatePatternSlotVisibility();
            sendClientAction(action);
            return;
        }
        int pageCount = extendedHost.getAvailablePatternPages();
        patternPage = Math.max(0, Math.min(patternPage + delta, pageCount - 1));
        patternPageCount = pageCount;
        updatePatternSlotVisibility();
    }

    private void updatePatternSlotVisibility() {
        List<Slot> patternSlots = super.getSlots(SlotSemantics.ENCODED_PATTERN);
        int firstVisibleSlot = patternPage * ExtendedKineticPatternProviderBlockEntity.SLOTS_PER_PAGE;
        int lastVisibleSlot = firstVisibleSlot + ExtendedKineticPatternProviderBlockEntity.SLOTS_PER_PAGE;
        for (int slot = 0; slot < patternSlots.size(); slot++) {
            if (patternSlots.get(slot) instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(slot >= firstVisibleSlot && slot < lastVisibleSlot);
            }
        }
    }
}