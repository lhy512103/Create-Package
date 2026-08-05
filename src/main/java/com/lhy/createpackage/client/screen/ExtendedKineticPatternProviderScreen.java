package com.lhy.createpackage.client.screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.lhy.createpackage.content.kinetic.ExtendedKineticPatternProviderMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;

public class ExtendedKineticPatternProviderScreen
        extends CreatePackagePatternProviderScreen<ExtendedKineticPatternProviderMenu> {
    private final List<ToolbarAction> toolbarActions;
    private final List<ActionToolbarButton> toolbarButtons;

    public ExtendedKineticPatternProviderScreen(ExtendedKineticPatternProviderMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        toolbarActions = createToolbarActions();
        toolbarButtons = toolbarActions.stream()
                .map(action -> addToLeftToolbar(new ActionToolbarButton(action)))
                .toList();
    }

    protected List<ToolbarAction> createToolbarActions() {
        return List.of(
                new ToolbarAction(
                        Icon.ARROW_RIGHT,
                        menu::nextPatternPage,
                        () -> Component.translatable("gui.createpackage.next_pattern_page"),
                        () -> menu.getPatternPageCount() > 1,
                        () -> menu.getPatternPage() + 1 < menu.getPatternPageCount()),
                new ToolbarAction(
                        Icon.ARROW_LEFT,
                        menu::previousPatternPage,
                        () -> Component.translatable("gui.createpackage.previous_pattern_page"),
                        () -> menu.getPatternPageCount() > 1,
                        () -> menu.getPatternPage() > 0));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        positionPatternPage();
        toolbarButtons.forEach(ActionToolbarButton::refresh);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        Component patterns = Component.translatable("gui.ae2.Patterns");
        Component page = Component.translatable("gui.createpackage.pattern_page_compact",
                menu.getPatternPage() + 1, menu.getPatternPageCount());
        guiGraphics.drawString(font, page, 14 + font.width(patterns), 31, 0x404040, false);
    }

    private void positionPatternPage() {
        repositionSlots(SlotSemantics.ENCODED_PATTERN);
        List<Slot> visibleSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        for (Slot slot : menu.getAllPatternSlots()) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(visibleSlots.contains(slot));
            }
        }
    }

    protected record ToolbarAction(Icon icon, Runnable action, Supplier<Component> tooltip,
            BooleanSupplier visible, BooleanSupplier active) {
    }

    private static final class ActionToolbarButton extends IconButton {
        private final ToolbarAction action;

        private ActionToolbarButton(ToolbarAction action) {
            super(button -> action.action().run());
            this.action = action;
            refresh();
        }

        private void refresh() {
            visible = action.visible().getAsBoolean();
            active = visible && action.active().getAsBoolean();
            setMessage(action.tooltip().get());
        }

        @Override
        protected Icon getIcon() {
            return action.icon();
        }
    }
}