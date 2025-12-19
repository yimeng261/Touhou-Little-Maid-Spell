package com.github.yimeng261.maidspell.client.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.backpack.IBackpackContainerScreen;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen.TransparentButton;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.network.NetworkHandler;
import com.github.yimeng261.maidspell.network.message.EnderPocketMessage;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆背包界面中的末影腰包集成
 * 使用 MaidContainerGuiEvent 在背包界面中添加末影腰包功能
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MaidBackpackEnderPocketIntegration {

    @SuppressWarnings("removal")
    private static final ResourceLocation ENDER_POCKET_TEXTURE =
            new ResourceLocation("touhou_little_maid_spell", "textures/gui/ender_pocket.png");
    
    // 末影腰包面板的尺寸
    private static final int PANEL_WIDTH = 90;
    private static final int PANEL_HEIGHT = 153;
    
    // 存储当前显示的女仆信息
    private static List<EnderPocketService.EnderPocketMaidInfo> currentMaidInfos = new ArrayList<>();
    
    // 面板位置计算常量
    private static final int PANEL_OFFSET_X = -91; // 背包界面左侧偏移量
    private static final int PANEL_OFFSET_Y = 30;       // 背包界面顶部偏移量
    private static final int TASK_LIST_OFFSET_X = -93;  // 任务列表打开时的额外偏移量

    private static final Minecraft mc = Minecraft.getInstance();

    // 数据请求节流参数
    private static long lastRequestTime = 0;
    private static final long REQUEST_COOLDOWN = 1000; // 1秒冷却时间
    private static boolean dataRequested = false; // 标记是否已经请求过数据
    
    // 界面状态跟踪
    private static Screen lastScreen = null;
    
    /**
     * 计算面板X坐标的统一方法
     * @param guiLeft GUI左侧位置
     * @param gui 当前GUI实例
     * @return 面板X坐标
     */
    private static int calculatePanelX(int guiLeft, AbstractMaidContainerGui<?> gui) {
        int baseOffset = PANEL_OFFSET_X;
        // 如果任务列表打开，向左移动以避免重叠
        if (gui.isTaskListOpen()) {
            baseOffset += TASK_LIST_OFFSET_X;
        }
        return guiLeft + baseOffset;
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
            requestEnderPocketDataThrottled();
            addMaidButtons(event);
        }
    }
    
    /**
     * 添加女仆选择按钮
     */
    private static void addMaidButtons(MaidContainerGuiEvent.Init event) {
        int panelX = calculatePanelX(event.getLeftPos(), event.getGui());
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
                button -> handleMaidButtonClick(maidInfo.maidEntityId,event.getGui())
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
                                 event.getMouseX(), event.getMouseY(), event.getGui());
        }
    }
    
    /**
     * 在女仆背包界面工具提示渲染时处理末影腰包相关的工具提示
     */
    @SubscribeEvent
    public static void onMaidGuiTooltip(MaidContainerGuiEvent.Tooltip event) {
    }
    
    /**
     * 客户端tick事件，用于监听界面变化
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Screen currentScreen = mc.screen;
            
            // 检测界面是否从女仆背包界面切换到其他界面
            if (lastScreen instanceof IBackpackContainerScreen && 
                !(currentScreen instanceof IBackpackContainerScreen)) {
                // 界面已关闭，清理缓存
                clearCache();
            }
            
            lastScreen = currentScreen;
        }
    }
    
    
    /**
     * 请求末影腰包数据（节流版本）
     */
    private static void requestEnderPocketDataThrottled() {
        long currentTime = System.currentTimeMillis();
        
        // 如果数据已经存在且在冷却时间内，不重复请求
        if (dataRequested && (currentTime - lastRequestTime) < REQUEST_COOLDOWN) {
            return;
        }
        
        // 只有在数据为空或超过冷却时间时才请求
        if (currentMaidInfos.isEmpty() || (currentTime - lastRequestTime) >= REQUEST_COOLDOWN) {
            requestEnderPocketData();
            lastRequestTime = currentTime;
            dataRequested = true;
        }
    }
    
    /**
     * 请求末影腰包数据（直接版本）
     */
    private static void requestEnderPocketData() {
        NetworkHandler.CHANNEL.sendToServer(EnderPocketMessage.requestMaidListFromBackpack());
    }
    

    /**
     * 渲染末影腰包面板
     */
    private static void renderEnderPocketPanel(GuiGraphics graphics, int guiLeft, int guiTop, int mouseX, int mouseY, AbstractMaidContainerGui<?> gui) {
        // 计算面板位置
        int panelX = calculatePanelX(guiLeft, gui);
        int panelY = calculatePanelY(guiTop);
        
        // 渲染面板背景
        RenderSystem.enableBlend();
        graphics.blit(ENDER_POCKET_TEXTURE, panelX, panelY, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        
        // 渲染标题
        Component title = Component.translatable("gui.maidspell.ender_pocket.title");
        graphics.drawCenteredString(mc.font, title, panelX + PANEL_WIDTH / 2, panelY + 6, 0x404040);
        
        // 渲染女仆列表
        if (!currentMaidInfos.isEmpty()){
            renderMaidButtons(graphics, panelX, panelY, mouseX, mouseY, gui);
        }
        
        RenderSystem.disableBlend();
    }
    
    /**
     * 渲染女仆按钮列表
     */
    private static void renderMaidButtons(GuiGraphics graphics, int panelX, int panelY, 
                                        int mouseX, int mouseY, AbstractMaidContainerGui<?> gui) {
        int buttonWidth = PANEL_WIDTH - 12;
        int buttonHeight = 16;
        int startY = panelY + 16;
        
        // 获取当前打开背包的女仆ID
        int currentMaidId = gui.getMaid().getId();
        
        for (int i = 0; i < Math.min(currentMaidInfos.size(), 8); i++) {
            EnderPocketService.EnderPocketMaidInfo maidInfo = currentMaidInfos.get(i);
            
            int buttonX = panelX + 6;
            int buttonY = startY + (i * buttonHeight);
            
            // 检查鼠标悬停
            boolean isHovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth && 
                               mouseY >= buttonY && mouseY < buttonY + buttonHeight;
            
            // 检查是否是当前打开背包的女仆
            boolean isCurrentMaid = maidInfo.maidEntityId == currentMaidId;
            
            // 渲染按钮背景
            if (isCurrentMaid) {
                // 当前女仆使用黄色高亮背景
                graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0x80FFFF00);
                graphics.renderOutline(buttonX, buttonY, buttonWidth, buttonHeight, 0xFFFFFF00);
            } else if (isHovered) {
                // 悬停时使用白色半透明背景
                graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0x80FFFFFF);
                graphics.renderOutline(buttonX, buttonY, buttonWidth, buttonHeight, 0xFFFFFFFF);
            }
            
            // 渲染女仆名称
            Component maidName = Component.literal(maidInfo.maidName);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            // 当前女仆使用不同的文字颜色
            int textColor = isCurrentMaid ? 0xFFFF00 : 0xFFFFFF;
            graphics.drawCenteredString(mc.font, maidName, 
                                      buttonX + buttonWidth / 2, buttonY + (buttonHeight - 8) / 2, textColor);
        }
    }
    
    /**
     * 处理女仆按钮点击（通过事件系统调用）
     */
    private static void handleMaidButtonClick(int maidEntityId,AbstractMaidContainerGui<?> gui) {
        NetworkHandler.CHANNEL.sendToServer(EnderPocketMessage.openMaidInventory(maidEntityId));
    }

    
    public static void updateEnderPocketData(List<EnderPocketService.EnderPocketMaidInfo> maidInfos) {
        // 检查数据是否真的发生了变化
        if (maidInfos == null) {
            maidInfos = new ArrayList<>();
        }
        
        // 简单的数据比较，避免不必要的更新
        if (currentMaidInfos.size() == maidInfos.size()) {
            boolean dataChanged = false;
            for (int i = 0; i < maidInfos.size(); i++) {
                if (currentMaidInfos.get(i).maidEntityId != maidInfos.get(i).maidEntityId || !currentMaidInfos.get(i).maidName.equals(maidInfos.get(i).maidName)) {
                    dataChanged = true;
                    break;
                }
            }
            if (!dataChanged) {
                return; // 数据没有变化，不需要更新
            }
        }
        
        // 数据发生了变化，进行更新
        currentMaidInfos.clear();
        currentMaidInfos.addAll(maidInfos);
        
        // 更新数据请求状态
        dataRequested = true;
        lastRequestTime = System.currentTimeMillis();
    }
    
    
    /**
     * 清理缓存数据（用于界面关闭时）
     */
    public static void clearCache() {
        dataRequested = false;
        lastRequestTime = 0;
        // 不清理 currentMaidInfos，保持数据以便下次快速加载
    }
    
    /**
     * 强制刷新数据（用于女仆状态变化时）
     */
    public static void forceRefreshData() {
        dataRequested = false;
        lastRequestTime = 0;
        requestEnderPocketData();
    }
}
