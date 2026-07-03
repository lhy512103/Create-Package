package com.lhy.createpackage.client.screen;

import com.mojang.math.Axis;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterMenu;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.core.AppEng;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class MechanicalPatternConverterScreen extends AbstractContainerScreen<MechanicalPatternConverterMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 215;
    private static final Blitter BACKGROUND = Blitter
            .texture(AppEng.makeId("textures/guis/pattern_provider.png"), 256, 256)
            .src(0, 0, WIDTH, HEIGHT);
    private static final int CONVERT_BUTTON_X = 80;
    private static final int CONVERT_BUTTON_Y = 70;

    private ConvertButton convertButton;

    public MechanicalPatternConverterScreen(MechanicalPatternConverterMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = 120;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;

        convertButton = new ConvertButton(leftPos + CONVERT_BUTTON_X, topPos + CONVERT_BUTTON_Y,
                button -> handleConvertButton());
        addRenderableWidget(convertButton);
    }

    private void handleConvertButton() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    MechanicalPatternConverterMenu.BUTTON_CONVERT);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        BACKGROUND.dest(leftPos, topPos).blit(graphics);
        for (int i = 0; i < MechanicalPatternConverterMenu.INTERNAL_SLOT_COUNT; i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) {
                Icon.BACKGROUND_BLANK_PATTERN.getBlitter()
                        .dest(leftPos + slot.x, topPos + slot.y)
                        .blit(graphics);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui." + CreatePackage.MODID + ".mechanical_pattern_converter.input_patterns"),
                8, 34, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui." + CreatePackage.MODID + ".mechanical_pattern_converter.output_patterns"),
                8, 86, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (!slot.isHighlightable()) {
            return;
        }

        int x = slot.x;
        int y = slot.y;
        int width = 16;
        int height = 16;

        graphics.hLine(x, x + width, y - 1, 0xFFdaffff);
        graphics.hLine(x - 1, x + width, y + height, 0xFFdaffff);
        graphics.vLine(x - 1, y - 2, y + height, 0xFFdaffff);
        graphics.vLine(x + width, y - 2, y + height, 0xFFdaffff);
        graphics.fillGradient(RenderType.guiOverlay(), x, y, x + width, y + height, 0x669cd3ff, 0x669cd3ff, 0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (convertButton != null && convertButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font, convertButton.tooltip(), mouseX, mouseY);
        }
    }

    private static final class ConvertButton extends Button {
        private static final Component TOOLTIP = Component.translatable(
                "gui." + CreatePackage.MODID + ".mechanical_pattern_converter.convert");

        private ConvertButton(int x, int y, Button.OnPress onPress) {
            super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        private Component tooltip() {
            return TOOLTIP;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int yOffset = isHovered() ? 1 : 0;
            Icon background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
            background.getBlitter()
                    .dest(getX() - 1, getY() + yOffset, 18, 20)
                    .zOffset(2)
                    .blit(graphics);

            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 8.0F, getY() + 9.0F + yOffset, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
            Icon.ARROW_RIGHT.getBlitter()
                    .dest(-8, -8)
                    .zOffset(3)
                    .blit(graphics);
            graphics.pose().popPose();
        }
    }
}
