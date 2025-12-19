package com.github.yimeng261.maidspell.client.gui;

import com.github.yimeng261.maidspell.client.KeyBinds;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.EnderPocketMessage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;

import java.util.List;

/**
 * 末影腰包选择界面
 * 显示所有佩戴末影腰包的女仆列表，点击可打开对应女仆的背包
 */
@OnlyIn(Dist.CLIENT)
public class EnderPocketScreen extends Screen {
    @SuppressWarnings("removal")
    private static final ResourceLocation BACKGROUND_TEXTURE = 
            new ResourceLocation("touhou_little_maid_spell", "textures/gui/ender_pocket.png");
    
    private List<EnderPocketService.EnderPocketMaidInfo> maidInfos;
    private static final int GUI_WIDTH = 90;
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
        this.maidInfos = newMaidInfos;
        // 重新初始化界面以更新按钮
        this.init();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 清除之前的按钮
        this.clearWidgets();
        
        int startX = (this.width - GUI_WIDTH) / 2;
        int startY = (this.height - GUI_HEIGHT) / 2;
        
        // 为每个女仆创建按钮
        for (int i = 0; i < maidInfos.size() && i < 8; i++) { // 最多显示8个女仆
            EnderPocketService.EnderPocketMaidInfo maidInfo = maidInfos.get(i);
            
            int buttonX = startX + 6;
            int buttonHeight = 16;
            int buttonY = startY + 16 + (i * buttonHeight);
            int buttonWidth = GUI_WIDTH - 12;
            
            
            Button maidButton = new TransparentButton(
                buttonX, buttonY, buttonWidth, buttonHeight,
                Component.literal(maidInfo.maidName),
                button -> openMaidInventory(maidInfo.maidEntityId)
            );
            
            this.addRenderableWidget(maidButton);
        }
        
    }
    
    private void openMaidInventory(int maidEntityId) {
        NetworkHandler.CHANNEL.sendToServer(EnderPocketMessage.openMaidInventory(maidEntityId));
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
        
        
        // 渲染背景
        guiGraphics.blit(BACKGROUND_TEXTURE, startX, startY, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        
        // 渲染标题（无阴影）
        int titleX = this.width / 2 - this.font.width(this.title) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, startY + 6, 0x404040, false);
        
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
}
