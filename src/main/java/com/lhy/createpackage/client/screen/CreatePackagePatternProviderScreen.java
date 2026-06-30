package com.lhy.createpackage.client.screen;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.createpackage.content.kinetic.KineticPatternProviderMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AmountFormat;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Icon;
import appeng.client.gui.Tooltip;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.core.localization.InGameTooltip;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.serverbound.ConfigButtonPacket;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.SlotSemantics;

public class CreatePackagePatternProviderScreen<C extends PatternProviderMenu> extends AEBaseScreen<C> {
    private final SettingToggleButton<YesNo> blockingModeButton;
    @Nullable
    private final ToggleButton smartDoublingButton;
    private final SettingToggleButton<LockCraftingMode> lockCraftingModeButton;
    private final ToggleButton showInPatternAccessTerminalButton;
    private final LockReason lockReason;

    public CreatePackagePatternProviderScreen(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        setTextContent(TEXT_ID_DIALOG_TITLE, title);

        blockingModeButton = new ServerSettingToggleButton<>(Settings.BLOCKING_MODE, YesNo.NO);
        addToLeftToolbar(blockingModeButton);

        if (menu instanceof KineticPatternProviderMenu) {
            smartDoublingButton = new ToggleButton(Icon.BLOCKING_MODE_YES, Icon.BLOCKING_MODE_NO,
                    btn -> toggleSmartDoubling());
            smartDoublingButton.setTooltipOn(List.of(
                    Component.translatable("gui.createpackage.smart_doubling"),
                    Component.translatable("gui.createpackage.smart_doubling.enabled")));
            smartDoublingButton.setTooltipOff(List.of(
                    Component.translatable("gui.createpackage.smart_doubling"),
                    Component.translatable("gui.createpackage.smart_doubling.disabled")));
            addToLeftToolbar(smartDoublingButton);
        } else {
            smartDoublingButton = null;
        }

        lockCraftingModeButton = new ServerSettingToggleButton<>(Settings.LOCK_CRAFTING_MODE,
                LockCraftingMode.NONE);
        addToLeftToolbar(lockCraftingModeButton);

        widgets.addOpenPriorityButton();

        showInPatternAccessTerminalButton = new ToggleButton(Icon.PATTERN_ACCESS_SHOW,
                Icon.PATTERN_ACCESS_HIDE,
                GuiText.PatternAccessTerminal.text(), GuiText.PatternAccessTerminalHint.text(),
                btn -> selectNextPatternProviderMode());
        addToLeftToolbar(showInPatternAccessTerminalButton);

        lockReason = new LockReason(this);
        widgets.add("lockReason", lockReason);

        if (!menu.getSlots(SlotSemantics.UPGRADE).isEmpty() && menu.getTarget() instanceof PatternProviderLogicHost host) {
            widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), () ->
                    appeng.api.upgrades.Upgrades.getTooltipLinesForMachine(host.getTerminalIcon().getItem())));
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        lockReason.setVisible(menu.getLockCraftingMode() != LockCraftingMode.NONE);
        blockingModeButton.set(menu.getBlockingMode());
        if (smartDoublingButton != null && menu instanceof KineticPatternProviderMenu kineticMenu) {
            smartDoublingButton.setState(kineticMenu.getSmartDoubling() == YesNo.YES);
        }
        lockCraftingModeButton.set(menu.getLockCraftingMode());
        showInPatternAccessTerminalButton.setState(menu.getShowInAccessTerminal() == YesNo.YES);
    }

    private void toggleSmartDoubling() {
        if (menu instanceof KineticPatternProviderMenu kineticMenu) {
            kineticMenu.toggleSmartDoubling();
        }
    }

    private void selectNextPatternProviderMode() {
        boolean backwards = isHandlingRightClick();
        ServerboundPacket message = new ConfigButtonPacket(Settings.PATTERN_ACCESS_TERMINAL, backwards);
        PacketDistributor.sendToServer(message);
    }

    private static final class LockReason implements ICompositeWidget {
        private final CreatePackagePatternProviderScreen<?> screen;
        private boolean visible;
        private int x;
        private int y;

        private LockReason(CreatePackagePatternProviderScreen<?> screen) {
            this.screen = screen;
        }

        @Override
        public void setPosition(Point position) {
            x = position.getX();
            y = position.getY();
        }

        @Override
        public void setSize(int width, int height) {
        }

        @Override
        public Rect2i getBounds() {
            return new Rect2i(x, y, 126, 16);
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        private void setVisible(boolean visible) {
            this.visible = visible;
        }

        @Override
        public void drawForegroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
            var menu = screen.getMenu();
            Icon icon;
            Component lockStatusText;
            if (menu.getCraftingLockedReason() == LockCraftingMode.NONE) {
                icon = Icon.UNLOCKED;
                lockStatusText = GuiText.CraftingLockIsUnlocked.text()
                        .setStyle(Style.EMPTY.withColor(Mth.color(125 / 255f, 169 / 255f, 210 / 255f)));
            } else {
                icon = Icon.LOCKED;
                lockStatusText = GuiText.CraftingLockIsLocked.text()
                        .setStyle(Style.EMPTY.withColor(Mth.color(193 / 255f, 66 / 255f, 75 / 255f)));
            }

            icon.getBlitter().dest(x, y).blit(guiGraphics);
            guiGraphics.drawString(Minecraft.getInstance().font, lockStatusText, x + 15, y + 5, -1, false);
        }

        @Nullable
        @Override
        public Tooltip getTooltip(int mouseX, int mouseY) {
            var menu = screen.getMenu();
            var tooltip = switch (menu.getCraftingLockedReason()) {
                case NONE -> null;
                case LOCK_UNTIL_PULSE -> InGameTooltip.CraftingLockedUntilPulse.text();
                case LOCK_WHILE_HIGH -> InGameTooltip.CraftingLockedByRedstoneSignal.text();
                case LOCK_WHILE_LOW -> InGameTooltip.CraftingLockedByLackOfRedstoneSignal.text();
                case LOCK_UNTIL_RESULT -> {
                    var stack = menu.getUnlockStack();
                    Component stackName;
                    Component stackAmount;
                    if (stack != null) {
                        stackName = AEKeyRendering.getDisplayName(stack.what());
                        stackAmount = Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL));
                    } else {
                        stackName = Component.literal("ERROR");
                        stackAmount = Component.literal("ERROR");
                    }
                    yield InGameTooltip.CraftingLockedUntilResult.text(stackName, stackAmount);
                }
            };

            return tooltip != null ? new Tooltip(tooltip) : null;
        }
    }
}
