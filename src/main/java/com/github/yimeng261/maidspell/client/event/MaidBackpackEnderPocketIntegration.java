package com.github.yimeng261.maidspell.client.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.backpack.IBackpackContainerScreen;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen.TransparentButton;
import com.github.yimeng261.maidspell.client.gui.ScreenManager;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.EnderPocketMessage;
import com.github.yimeng261.maidspell.service.EnderPocketService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆背包界面中的末影腰包集成
 * 使用 MaidContainerGuiEvent 在背包界面中添加末影腰包功能
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MaidBackpackEnderPocketIntegration {
    
    private static final ResourceLocation ENDER_POCKET_TEXTURE = 
            new ResourceLocation("touhou_little_maid_spell", "textures/gui/ender_pocket.png");
    
    // 末影腰包面板的尺寸
    private static final int PANEL_WIDTH = 90;
    private static final int PANEL_HEIGHT = 153;
    
    // 存储当前显示的女仆信息
    private static List<EnderPocketService.EnderPocketMaidInfo> currentMaidInfos = new ArrayList<>();
    
    // 面板位置计算常量
    private static final int PANEL_OFFSET_X = -110 + 5; // 背包界面左侧偏移量
    private static final int PANEL_OFFSET_Y = 30;       // 背包界面顶部偏移量

    private static final Minecraft mc = Minecraft.getInstance();
    
    /**
     * 计算面板X坐标的统一方法
     * @param guiLeft GUI左侧位置
     * @return 面板X坐标
     */
    private static int calculatePanelX(int guiLeft) {
        return guiLeft + PANEL_OFFSET_X;
    }
    
    /**
     * 计算面板Y坐标的统一方法
     * @param guiTop GUI顶部位置
     * @return 面板Y坐标
     */
    private static int calculatePanelY(int guiTop) {
        return guiTop + PANEL_OFFSET_Y;
    }
    
    /**
     * 在女仆背包界面初始化时添加末影腰包面板
     */
    @SubscribeEvent
    public static void onMaidGuiInit(MaidContainerGuiEvent.Init event) {
        if (event.getGui() instanceof IBackpackContainerScreen) {
            requestEnderPocketData();
            addMaidButtons(event);
            ScreenManager.getInstance().restoreMousePosition();
        }
    }
    
    /**
     * 添加女仆选择按钮
     */
    private static void addMaidButtons(MaidContainerGuiEvent.Init event) {
        int panelX = calculatePanelX(event.getLeftPos());
        int panelY = calculatePanelY(event.getTopPos());
        int buttonWidth = PANEL_WIDTH - 12;
        int buttonHeight = 16;
        int startY = panelY + 16;
        
        for (int i = 0; i < Math.min(currentMaidInfos.size(), 8); i++) {
            EnderPocketService.EnderPocketMaidInfo maidInfo = currentMaidInfos.get(i);
            
            int buttonX = panelX + 6;
            int buttonY = startY + (i * buttonHeight);


            Button maidButton = new TransparentButton(
                buttonX, buttonY, buttonWidth, buttonHeight,
                Component.literal(maidInfo.maidName),
                button -> handleMaidButtonClick(maidInfo.maidEntityId)
            );

            event.addButton("maid_button_" + i, maidButton);
        }
    }
    
    /**
     * 在女仆背包界面渲染时绘制末影腰包面板
     */
    @SubscribeEvent
    public static void onMaidGuiRender(MaidContainerGuiEvent.Render event) {
        if (event.getGui() instanceof IBackpackContainerScreen) {
            renderEnderPocketPanel(event.getGraphics(), event.getLeftPos(), event.getTopPos(), 
                                 event.getMouseX(), event.getMouseY());
        }
    }
    
    /**
     * 在女仆背包界面工具提示渲染时处理末影腰包相关的工具提示
     */
    @SubscribeEvent
    public static void onMaidGuiTooltip(MaidContainerGuiEvent.Tooltip event) {
    }
    
    
    /**
     * 请求末影腰包数据
     */
    private static void requestEnderPocketData() {
        NetworkHandler.CHANNEL.sendToServer(EnderPocketMessage.requestMaidListFromBackpack());
    }
    

    /**
     * 渲染末影腰包面板
     */
    private static void renderEnderPocketPanel(GuiGraphics graphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        // 计算面板位置
        int panelX = calculatePanelX(guiLeft);
        int panelY = calculatePanelY(guiTop);
        
        // 渲染面板背景
        RenderSystem.enableBlend();
        graphics.blit(ENDER_POCKET_TEXTURE, panelX, panelY, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        
        // 渲染标题
        Component title = Component.translatable("gui.maidspell.ender_pocket.title");
        // 使用Minecraft实例获取字体
        
        graphics.drawCenteredString(mc.font, title, panelX + PANEL_WIDTH / 2, panelY + 6, 0x404040);
        
        // 渲染女仆列表
        if (currentMaidInfos.isEmpty()) {
            Component noMaidText = Component.translatable("gui.maidspell.ender_pocket.no_maids");
            graphics.drawCenteredString(mc.font, noMaidText, 
                                      panelX + PANEL_WIDTH / 2, panelY + 50, 0x808080);
        } else {
            renderMaidButtons(graphics, panelX, panelY, mouseX, mouseY);
        }
        
        RenderSystem.disableBlend();
    }
    
    /**
     * 渲染女仆按钮列表
     */
    private static void renderMaidButtons(GuiGraphics graphics, int panelX, int panelY, 
                                        int mouseX, int mouseY) {
        int buttonWidth = PANEL_WIDTH - 12;
        int buttonHeight = 16;
        int startY = panelY + 16;
        
        for (int i = 0; i < Math.min(currentMaidInfos.size(), 8); i++) {
            EnderPocketService.EnderPocketMaidInfo maidInfo = currentMaidInfos.get(i);
            
            int buttonX = panelX + 6;
            int buttonY = startY + (i * buttonHeight);
            
            // 检查鼠标悬停
            boolean isHovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth && 
                               mouseY >= buttonY && mouseY < buttonY + buttonHeight;
            
            // 渲染按钮背景
            if (isHovered) {
                graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0x80FFFFFF);
                graphics.renderOutline(buttonX, buttonY, buttonWidth, buttonHeight, 0xFFFFFFFF);
            }
            
            // 渲染女仆名称
            Component maidName = Component.literal(maidInfo.maidName);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            graphics.drawCenteredString(mc.font, maidName, 
                                      buttonX + buttonWidth / 2, buttonY + (buttonHeight - 8) / 2, 0xFFFFFF);
        }
    }
    
    /**
     * 处理女仆按钮点击（通过事件系统调用）
     */
    private static void handleMaidButtonClick(int maidEntityId) {
        ScreenManager manager = ScreenManager.getInstance();
        manager.saveMousePosition();
        if (mc.screen != null) {
            mc.execute(()->mc.execute(()->mc.execute(()->mc.screen.onClose())));
        }
        NetworkHandler.CHANNEL.sendToServer(EnderPocketMessage.openMaidInventory(maidEntityId));
        //manager.restoreMousePosition();
    }
    
    /**
     * 更新末影腰包数据
     */
    public static void updateEnderPocketData(List<EnderPocketService.EnderPocketMaidInfo> maidInfos) {
        currentMaidInfos.clear();
        currentMaidInfos.addAll(maidInfos);
    }
    
}
