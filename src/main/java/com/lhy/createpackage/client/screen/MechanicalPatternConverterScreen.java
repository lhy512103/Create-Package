package com.lhy.createpackage.client.screen;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.converter.MechanicalPatternConverterMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MechanicalPatternConverterScreen extends AbstractContainerScreen<MechanicalPatternConverterMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;

    public MechanicalPatternConverterScreen(MechanicalPatternConverterMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = 73;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + CreatePackage.MODID + ".mechanical_pattern_converter.convert"),
                        button -> {
                            if (minecraft != null && minecraft.gameMode != null) {
                                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                            }
                        })
                .bounds(leftPos + 72, topPos + 35, 28, 20)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        slotBox(graphics, leftPos + 43, topPos + 34);
        slotBox(graphics, leftPos + 115, topPos + 34);
        graphics.fill(leftPos + 63, topPos + 43, leftPos + 70, topPos + 45, 0xFF555555);
        graphics.fill(leftPos + 102, topPos + 43, leftPos + 109, topPos + 45, 0xFF555555);

        for (var slot : menu.slots) {
            if (slot.index <= MechanicalPatternConverterMenu.OUTPUT_SLOT) {
                continue;
            }
            slotBox(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x333333, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui." + CreatePackage.MODID + ".mechanical_pattern_converter.route_hint"),
                8, 60, 0x666666, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);
        graphics.fill(x, y, x + width - 1, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + height - 1, 0xFFFFFFFF);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF555555);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
    }

    private static void slotBox(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x, y, x + 17, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + 17, 0xFF373737);
        graphics.fill(x + 17, y, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x, y + 17, x + 18, y + 18, 0xFFFFFFFF);
    }
}
