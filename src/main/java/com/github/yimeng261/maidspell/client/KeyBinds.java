package com.github.yimeng261.maidspell.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定定义
 */
@OnlyIn(Dist.CLIENT)
public class KeyBinds {
    
    public static final String CATEGORY = "key.categories.touhou_little_maid_spell";
    
    public static final KeyMapping OPEN_ENDER_POCKET_GUI = new KeyMapping(
            "key.touhou_little_maid_spell.open_ender_pocket",
            GLFW.GLFW_KEY_U,
            CATEGORY
    );
}
