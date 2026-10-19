package com.github.yimeng261.maidspell;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 世界生成配置，存放在 worldgen.toml。 */
final class ConfigWorldgen {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("世界生成相关配置",
                    "World generation configurations").push("worldgen");
    }

    private static final ModConfigSpec.BooleanValue ENABLE_STARFALL_GARDEN = BUILDER
            .comment("是否允许生成星落之庭；关闭后不再自然生成，也不会向旧存档补生成",
                    "Whether Starfall Garden may generate. When disabled, it will not generate naturally or be retrofitted into old saves.")
            .define("enableStarfallGarden", true);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ConfigWorldgen() {
    }

    static void load() {
        Config.enableStarfallGarden = ENABLE_STARFALL_GARDEN.get();
    }
}
