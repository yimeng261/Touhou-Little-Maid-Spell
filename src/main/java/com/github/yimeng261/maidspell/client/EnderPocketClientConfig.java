package com.github.yimeng261.maidspell.client;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HUD_HIDDEN_MAIDS = BUILDER
            .comment("Maid UUIDs hidden from the Ender Pocket HUD")
            .defineListAllowEmpty(List.of("hudHiddenMaidUuids"), List::of,
                    value -> value instanceof String text && isUuid(text));

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

    public static boolean isMaidVisible(UUID maidUuid) {
        String id = maidUuid.toString();
        return HUD_HIDDEN_MAIDS.get().stream().noneMatch(id::equalsIgnoreCase);
    }

    public static void setMaidVisible(UUID maidUuid, boolean visible) {
        String id = maidUuid.toString();
        List<String> hiddenMaids = new ArrayList<>(HUD_HIDDEN_MAIDS.get());
        hiddenMaids.removeIf(id::equalsIgnoreCase);
        if (!visible) {
            hiddenMaids.add(id);
        }
        HUD_HIDDEN_MAIDS.set(hiddenMaids);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
