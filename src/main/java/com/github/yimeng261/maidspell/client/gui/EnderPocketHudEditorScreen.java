package com.github.yimeng261.maidspell.client.gui;

import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.client.overlay.EnderPocketHudOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nonnull;

/** In-game drag and numeric position editor for the Ender Pocket HUD. */
public final class EnderPocketHudEditorScreen extends Screen {
    private static final int TOOLBAR_WIDTH = 310;
    private static final int TOOLBAR_HEIGHT = 30;

    private final Screen parent;
    private EditBox xInput;
    private EditBox yInput;
    private int hudX;
    private int hudY;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public EnderPocketHudEditorScreen(Screen parent) {
        super(Component.translatable("gui.maidspell.ender_pocket.hud_editor.title"));
        this.parent = parent;
        this.hudX = EnderPocketClientConfig.HUD_X.get();
        this.hudY = EnderPocketClientConfig.HUD_Y.get();
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        int left = (width - TOOLBAR_WIDTH) / 2;
        int top = height - TOOLBAR_HEIGHT + 5;

        xInput = addRenderableWidget(new EditBox(font, left + 14, top, 48, 18,
                Component.translatable("gui.maidspell.ender_pocket.hud_editor.x")));
        yInput = addRenderableWidget(new EditBox(font, left + 82, top, 48, 18,
                Component.translatable("gui.maidspell.ender_pocket.hud_editor.y")));
        xInput.setFilter(EnderPocketHudEditorScreen::isIntegerInput);
        yInput.setFilter(EnderPocketHudEditorScreen::isIntegerInput);
        xInput.setResponder(value -> updateFromInput(value, true));
        yInput.setResponder(value -> updateFromInput(value, false));
        updateInputValues();

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.maidspell.ender_pocket.hud_editor.reset"),
                        button -> resetPosition())
                .bounds(left + 140, top, 50, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.maidspell.ender_pocket.hud_editor.save"),
                        button -> saveAndClose())
                .bounds(left + 196, top, 50, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.maidspell.ender_pocket.hud_editor.cancel"),
                        button -> onClose())
                .bounds(left + 252, top, 54, 18).build());
        clampPosition();
        updateInputValues();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int toolbarTop = height - TOOLBAR_HEIGHT;
        EnderPocketHudOverlay.renderEditorPreview(graphics, hudX, hudY, toolbarTop - 4);

        graphics.fill((width - TOOLBAR_WIDTH) / 2 - 4, toolbarTop,
                (width + TOOLBAR_WIDTH) / 2 + 4, height, 0xD0181822);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        int left = (width - TOOLBAR_WIDTH) / 2;
        graphics.drawString(font, "X", left + 3, toolbarTop + 10, 0xFFFFFFFF, false);
        graphics.drawString(font, "Y", left + 71, toolbarTop + 10, 0xFFFFFFFF, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverPreview(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = (int) mouseX - hudX;
            dragOffsetY = (int) mouseY - hudY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            hudX = (int) mouseX - dragOffsetX;
            hudY = (int) mouseY - dragOffsetY;
            clampPosition();
            updateInputValues();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isOverPreview(double mouseX, double mouseY) {
        int bottom = height - TOOLBAR_HEIGHT - 4;
        int previewHeight = EnderPocketHudOverlay.getEditorPreviewHeight(bottom, hudY);
        return mouseX >= hudX && mouseX < hudX + EnderPocketHudOverlay.ROW_WIDTH
                && mouseY >= hudY && mouseY < hudY + previewHeight;
    }

    private void updateFromInput(String value, boolean xAxis) {
        if (value.isEmpty()) {
            return;
        }
        try {
            if (xAxis) {
                hudX = Integer.parseInt(value);
            } else {
                hudY = Integer.parseInt(value);
            }
            clampPosition();
        } catch (NumberFormatException ignored) {
        }
    }

    private void clampPosition() {
        int bottom = height - TOOLBAR_HEIGHT - 4;
        int previewHeight = EnderPocketHudOverlay.getEditorPreviewHeight(bottom, Math.max(0, hudY));
        hudX = Mth.clamp(hudX, 0, Math.max(0, width - EnderPocketHudOverlay.ROW_WIDTH));
        hudY = Mth.clamp(hudY, 0, Math.max(0, bottom - previewHeight));
    }

    private void updateInputValues() {
        if (xInput != null && !xInput.getValue().equals(Integer.toString(hudX))) {
            xInput.setValue(Integer.toString(hudX));
        }
        if (yInput != null && !yInput.getValue().equals(Integer.toString(hudY))) {
            yInput.setValue(Integer.toString(hudY));
        }
    }

    private void resetPosition() {
        hudX = 6;
        hudY = 6;
        clampPosition();
        updateInputValues();
    }

    private void saveAndClose() {
        EnderPocketClientConfig.setPosition(hudX, hudY);
        onClose();
    }

    private static boolean isIntegerInput(String value) {
        if (value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
