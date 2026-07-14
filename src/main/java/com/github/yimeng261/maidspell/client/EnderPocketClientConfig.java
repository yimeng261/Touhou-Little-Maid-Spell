package com.github.yimeng261.maidspell.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only placement settings for the Ender Pocket maid HUD. */
public final class EnderPocketClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED = BUILDER
            .comment("Whether the Ender Pocket maid status HUD is visible")
            .define("hudEnabled", true);
    public static final ForgeConfigSpec.IntValue HUD_X = BUILDER
            .comment("HUD X position in scaled GUI pixels")
            .defineInRange("hudX", 6, 0, 8192);
    public static final ForgeConfigSpec.IntValue HUD_Y = BUILDER
            .comment("HUD Y position in scaled GUI pixels")
            .defineInRange("hudY", 6, 0, 8192);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private EnderPocketClientConfig() {
    }

    public static void setPosition(int x, int y) {
        HUD_X.set(Math.max(0, x));
        HUD_Y.set(Math.max(0, y));
    }

    public static void resetPosition() {
        setPosition(6, 6);
    }
}
