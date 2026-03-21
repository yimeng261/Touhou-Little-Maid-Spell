package com.github.yimeng261.maidspell.client.gui;

import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogHaloStyle;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.TransmogNecklaceMessage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 幻化项链样式选择界面
 */
public class TransmogNecklaceScreen extends Screen {
    private static final int PANEL_WIDTH = 196;
    private static final int PANEL_HEIGHT = 104;
    private static final int CARD_SIZE = 42;

    private final int inventorySlot;
    private TransmogHaloStyle selectedStyle;
    private final List<StyleButton> styleButtons = new ArrayList<>();

    public TransmogNecklaceScreen(int inventorySlot, TransmogHaloStyle selectedStyle) {
        super(Component.translatable("gui.maidspell.transmog_necklace.title"));
        this.inventorySlot = inventorySlot;
        this.selectedStyle = selectedStyle;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        styleButtons.clear();

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int startX = left + 20;
        int startY = top + 38;
        int gap = 54;

        for (int i = 0; i < TransmogHaloStyle.values().length; i++) {
            TransmogHaloStyle style = TransmogHaloStyle.byIndex(i);
            StyleButton button = new StyleButton(startX + i * gap, startY, style);
            styleButtons.add(button);
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        guiGraphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE01D1624, 0xF02B2134);
        guiGraphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFCCB8FF);

        guiGraphics.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 10, 0xFFF7E8FF);
        guiGraphics.drawCenteredString(font,
            Component.translatable("gui.maidspell.transmog_necklace.hint"),
            left + PANEL_WIDTH / 2, top + 22, 0xFFD6C4E9);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        for (StyleButton button : styleButtons) {
            if (button.isHovered()) {
                guiGraphics.renderTooltip(font, button.style.getDisplayName(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hasShiftDown()) {
            TransmogHaloStyle next = delta > 0 ? selectedStyle.previous() : selectedStyle.next();
            selectStyle(next);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void selectStyle(TransmogHaloStyle style) {
        if (selectedStyle == style) {
            return;
        }

        selectedStyle = style;
        NetworkHandler.CHANNEL.sendToServer(new TransmogNecklaceMessage(inventorySlot, style.getSerializedIndex()));

        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.translatable("item.touhou_little_maid_spell.transmog_necklace.current_style", style.getDisplayName()),
                true
            );
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.2F);
        }
    }

    private class StyleButton extends Button {
        private final TransmogHaloStyle style;

        private StyleButton(int x, int y, TransmogHaloStyle style) {
            super(x, y, CARD_SIZE, CARD_SIZE, style.getDisplayName(), button -> selectStyle(style), DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        protected void renderWidget(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean isSelected = selectedStyle == style;
            int background = isSelected ? 0xD06C4BB3 : (isHovered() ? 0xB0342B44 : 0xA0201B2D);
            int border = isSelected ? 0xFFF5DE7E : 0xFFAF9CC5;

            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            guiGraphics.renderOutline(getX(), getY(), width, height, border);

            RenderSystem.enableBlend();
            guiGraphics.blit(style.iconTexture(), getX() + 5, getY() + 5, 0, 0, 32, 32, 32, 32);
            RenderSystem.disableBlend();

            if (isSelected) {
                guiGraphics.drawCenteredString(Minecraft.getInstance().font,
                    Component.translatable("gui.maidspell.transmog_necklace.selected"),
                    getX() + width / 2, getY() + height - 10, 0xFFFFF3B0);
            }
        }
    }
}
