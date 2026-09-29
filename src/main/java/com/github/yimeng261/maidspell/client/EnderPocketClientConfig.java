package com.github.yimeng261.maidspell.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 末影腰包女仆状态栏的客户端位置设置
 */
public final class EnderPocketClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD_ENABLED = BUILDER
            .comment("Whether the Ender Pocket maid status HUD is visible")
            .define("hudEnabled", true);
    public static final ModConfigSpec.IntValue HUD_X = BUILDER
            .comment("HUD X position in scaled GUI pixels")
            .defineInRange("hudX", 6, 0, 8192);
    public static final ModConfigSpec.IntValue HUD_Y = BUILDER
            .comment("HUD Y position in scaled GUI pixels")
            .defineInRange("hudY", 6, 0, 8192);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EnderPocketClientConfig() {
    }

    public static void setPosition(int x, int y) {
        HUD_X.set(Math.max(0, x));
        HUD_Y.set(Math.max(0, y));
        // NeoForge 的 set 只改内存，需要显式写回文件
        SPEC.save();
    }
}
