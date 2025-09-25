package com.github.yimeng261.maidspell.client.gui;

import com.github.yimeng261.maidspell.service.EnderPocketService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.WeakHashMap;

/**
 * Screen管理器，用于优化Screen的创建、缓存和切换
 * 避免重复创建Screen实例导致的性能问题和画面闪烁
 * 支持鼠标位置保存和平滑过渡效果
 */
@OnlyIn(Dist.CLIENT)
public class ScreenManager {
    
    private static ScreenManager instance;
    private final Minecraft mc = Minecraft.getInstance();
    public Screen currentScreen;
    
    // 鼠标位置保存
    private double savedMouseX = -1;
    private double savedMouseY = -1;
    
    
    private ScreenManager() {}
    
    public static ScreenManager getInstance() {
        if (instance == null) {
            instance = new ScreenManager();
        }
        return instance;
    }
    
    /**
     * 平滑关闭当前Screen，避免画面闪烁和鼠标复位
     */
    public void closeCurrentScreen() {
        if (this.currentScreen != null) {
            this.currentScreen.onClose();
            restoreMousePosition();
        }
    }
    
    /**
     * 保存当前鼠标位置
     */
    public void saveMousePosition() {
        mc.getWindow();
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), xpos, ypos);
        savedMouseX = xpos[0];
        savedMouseY = ypos[0];
    }
    
    /**
     * 恢复鼠标位置
     */
    public void restoreMousePosition() {
        if (savedMouseX >= 0 && savedMouseY >= 0) {
            mc.getWindow();
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), savedMouseX, savedMouseY);
            // 重置保存的位置
            savedMouseX = -1;
            savedMouseY = -1;
        }
    }

}
