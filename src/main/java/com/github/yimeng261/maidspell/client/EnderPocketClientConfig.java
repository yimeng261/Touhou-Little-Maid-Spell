package com.github.yimeng261.maidspell.client;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HUD_HIDDEN_MAIDS = BUILDER
            .comment("Maid UUIDs hidden from the Ender Pocket HUD")
            .defineListAllowEmpty("hudHiddenMaidUuids", List::of, () -> new UUID(0L, 0L).toString(),
                    value -> value instanceof String text && isUuid(text));

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EnderPocketClientConfig() {
    }

    public static void setPosition(int x, int y) {
        HUD_X.set(Math.max(0, x));
        HUD_Y.set(Math.max(0, y));
        // NeoForge 的 set 只改内存，需要显式写回文件
        SPEC.save();
    }

    public static void setHudEnabled(boolean enabled) {
        HUD_ENABLED.set(enabled);
        SPEC.save();
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
        SPEC.save();
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
