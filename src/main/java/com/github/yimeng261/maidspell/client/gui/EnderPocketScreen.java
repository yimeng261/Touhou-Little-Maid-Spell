package com.github.yimeng261.maidspell.client.gui;

import com.github.yimeng261.maidspell.client.KeyBinds;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.EnderPocketRequestMessage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.UUID;

/**
 * 末影腰包选择界面
 * 显示所有佩戴末影腰包的女仆列表，点击可打开对应女仆的背包
 */
@OnlyIn(Dist.CLIENT)
public class EnderPocketScreen extends Screen {
    @SuppressWarnings("removal")
    private static final ResourceLocation BACKGROUND_TEXTURE =
            new ResourceLocation("touhou_little_maid_spell", "textures/gui/ender_pocket.png");
    private static final int ORIGINAL_GUI_WIDTH = 90;
    private static final int LEFT_CAP_WIDTH = 7;
    private static final int RIGHT_CAP_WIDTH = 7;
    private static final int CENTER_SOURCE_WIDTH = ORIGINAL_GUI_WIDTH - LEFT_CAP_WIDTH - RIGHT_CAP_WIDTH;
    private static final int ACTION_SEPARATOR_X = 98;
    private static final int ACTION_SEPARATOR_COLOR = 0xFF544C3B;
    private List<EnderPocketService.EnderPocketMaidInfo> maidInfos;
    private static final int GUI_WIDTH = 150;
    private static final int GUI_HEIGHT = 153;
    

    public EnderPocketScreen(List<EnderPocketService.EnderPocketMaidInfo> maidInfos) {
        super(Component.translatable("gui.maidspell.ender_pocket.title"));
        this.maidInfos = maidInfos;
    }
    
    /**
     * 更新女仆信息数据，避免重新创建Screen
     * @param newMaidInfos 新的女仆信息列表
     */
    public void updateMaidInfos(List<EnderPocketService.EnderPocketMaidInfo> newMaidInfos) {
        boolean widgetsChanged = this.maidInfos.size() != newMaidInfos.size();
        if (!widgetsChanged) {
            for (int i = 0; i < this.maidInfos.size(); i++) {
                EnderPocketService.EnderPocketMaidInfo oldInfo = this.maidInfos.get(i);
                EnderPocketService.EnderPocketMaidInfo newInfo = newMaidInfos.get(i);
                if (!oldInfo.maidUUID().equals(newInfo.maidUUID())
                        || !oldInfo.maidName().equals(newInfo.maidName())
                        || oldInfo.hasAnchorCore() != newInfo.hasAnchorCore()) {
                    widgetsChanged = true;
                    break;
                }
            }
        }
        this.maidInfos = newMaidInfos;
        if (widgetsChanged) {
            this.init();
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 清除之前的按钮
        this.clearWidgets();
        
        int startX = (this.width - GUI_WIDTH) / 2;
        int startY = (this.height - GUI_HEIGHT) / 2;

        this.addRenderableWidget(new TransparentButton(
                startX + 6, startY + 2, 35, 12,
                getHudToggleLabel(), this::toggleHud));
        this.addRenderableWidget(new TransparentButton(
                startX + GUI_WIDTH - 31, startY + 2, 25, 12,
                Component.translatable("gui.maidspell.ender_pocket.hud_settings"),
                button -> openHudEditor()));
        
        // 为每个女仆创建按钮
        for (int i = 0; i < maidInfos.size() && i < 8; i++) { // 最多显示8个女仆
            EnderPocketService.EnderPocketMaidInfo maidInfo = maidInfos.get(i);
            
            int rowX = startX + 6;
            int buttonHeight = 16;
            int buttonY = startY + 16 + (i * buttonHeight);
            int nameButtonX = rowX + 18;
            int nameButtonRight = maidInfo.hasAnchorCore()
                    ? startX + ACTION_SEPARATOR_X - 2
                    : startX + GUI_WIDTH - RIGHT_CAP_WIDTH;

            this.addRenderableWidget(new MaidHudVisibilityButton(
                    rowX, buttonY, maidInfo.maidUUID()));
            Button maidButton = new TransparentButton(
                nameButtonX, buttonY, nameButtonRight - nameButtonX, buttonHeight,
                Component.literal(maidInfo.maidName()),
                button -> openMaidInventory(maidInfo.maidUUID())
            );
            
            this.addRenderableWidget(maidButton);

            if (maidInfo.hasAnchorCore()) {
                Button teleportButton = new TransparentButton(
                        startX + ACTION_SEPARATOR_X + 2, buttonY, 43, buttonHeight,
                        Component.translatable("gui.maidspell.ender_pocket.teleport"),
                        button -> teleportToMaid(maidInfo.maidUUID()));
                this.addRenderableWidget(teleportButton);
            }
        }
        
    }
    
    private void openMaidInventory(java.util.UUID maidUuid) {
        NetworkHandler.CHANNEL.sendToServer(EnderPocketRequestMessage.openMaidInventory(maidUuid));
    }

    private void teleportToMaid(java.util.UUID maidUuid) {
        NetworkHandler.CHANNEL.sendToServer(EnderPocketRequestMessage.teleportToMaid(maidUuid));
        this.onClose();
    }

    private void openHudEditor() {
        if (minecraft != null) {
            minecraft.setScreen(new EnderPocketHudEditorScreen(this));
        }
    }

    private void toggleHud(Button button) {
        EnderPocketClientConfig.HUD_ENABLED.set(!EnderPocketClientConfig.HUD_ENABLED.get());
        button.setMessage(getHudToggleLabel());
    }

    private static Component getHudToggleLabel() {
        String key = EnderPocketClientConfig.HUD_ENABLED.get()
                ? "gui.maidspell.ender_pocket.hud_hide"
                : "gui.maidspell.ender_pocket.hud_show";
        return Component.translatable(key);
    }
    


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || KeyBinds.OPEN_ENDER_POCKET_GUI.getKey().getValue() == keyCode) { // ESC键
                this.onClose();
                return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.renderGui(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        

    }
    
    private void renderGui(GuiGraphics guiGraphics) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int startX = (this.width - GUI_WIDTH) / 2;
        int startY = (this.height - GUI_HEIGHT) / 2;

        renderExtendedBackground(guiGraphics, startX, startY);
        renderActionSeparators(guiGraphics, startX, startY);
        
        // 渲染标题（无阴影）
        int titleX = this.width / 2 - this.font.width(this.title) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, startY + 6, 0x404040, false);
        if (maidInfos.isEmpty()) {
            Component noMaids = Component.translatable("gui.maidspell.ender_pocket.no_maids");
            guiGraphics.drawCenteredString(this.font, noMaids, this.width / 2, startY + 70, 0x404040);
        }
    }

    private static void renderExtendedBackground(GuiGraphics graphics, int x, int y) {
        int targetInnerWidth = GUI_WIDTH - LEFT_CAP_WIDTH - RIGHT_CAP_WIDTH;
        graphics.blit(BACKGROUND_TEXTURE, x, y, 0, 0,
                LEFT_CAP_WIDTH, GUI_HEIGHT);

        int renderedWidth = 0;
        while (renderedWidth < targetInnerWidth) {
            int tileWidth = Math.min(CENTER_SOURCE_WIDTH, targetInnerWidth - renderedWidth);
            graphics.blit(BACKGROUND_TEXTURE, x + LEFT_CAP_WIDTH + renderedWidth, y,
                    LEFT_CAP_WIDTH, 0, tileWidth, GUI_HEIGHT);
            renderedWidth += tileWidth;
        }

        graphics.blit(BACKGROUND_TEXTURE, x + GUI_WIDTH - RIGHT_CAP_WIDTH, y,
                ORIGINAL_GUI_WIDTH - RIGHT_CAP_WIDTH, 0, RIGHT_CAP_WIDTH, GUI_HEIGHT);
    }

    private void renderActionSeparators(GuiGraphics graphics, int x, int y) {
        int visibleMaids = Math.min(maidInfos.size(), 8);
        for (int i = 0; i < visibleMaids; i++) {
            if (!maidInfos.get(i).hasAnchorCore()) {
                continue;
            }
            int rowY = y + 16 + i * 16;
            graphics.fill(x + ACTION_SEPARATOR_X, rowY + 2,
                    x + ACTION_SEPARATOR_X + 1, rowY + 14, ACTION_SEPARATOR_COLOR);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    /**
     * 透明按钮类，背景透明但保留选中时的边框高亮
     */
    public static class TransparentButton extends Button {
        public TransparentButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }
        
        @Override
        public void renderWidget(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (this.isHovered()) {
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x80FFFFFF);
                guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFFFFFFFF);
            }
            
            int textColor = this.active ? 0xFFFFFF : 0xA0A0A0;
            guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, this.getMessage(), 
                this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
        }
    }

    private static class MaidHudVisibilityButton extends Button {
        private final UUID maidUuid;
        private boolean visible;

        private MaidHudVisibilityButton(int x, int y, UUID maidUuid) {
            super(x, y, 16, 16, Component.empty(), button -> { }, DEFAULT_NARRATION);
            this.maidUuid = maidUuid;
            this.visible = EnderPocketClientConfig.isMaidVisible(maidUuid);
            updateLabel();
        }

        @Override
        public void onPress() {
            visible = !visible;
            EnderPocketClientConfig.setMaidVisible(maidUuid, visible);
            updateLabel();
        }

        @Override
        public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (isHovered()) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x50FFFFFF);
            }
            int boxX = getX() + 3;
            int boxY = getY() + 3;
            graphics.fill(boxX, boxY, boxX + 10, boxY + 10, 0xA040392D);
            graphics.renderOutline(boxX, boxY, 10, 10, 0xFFE0CA9F);
            if (visible) {
                graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                        "\u2713", getX() + 8, getY() + 3, 0xFFFFFFFF);
            }
        }

        private void updateLabel() {
            String key = visible
                    ? "gui.maidspell.ender_pocket.hud_maid_visible"
                    : "gui.maidspell.ender_pocket.hud_maid_hidden";
            Component label = Component.translatable(key);
            setMessage(label);
            setTooltip(Tooltip.create(label));
        }
    }
}
