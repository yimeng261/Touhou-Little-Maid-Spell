package com.github.yimeng261.maidspell;

import net.minecraftforge.common.ForgeConfigSpec;

/** World generation configuration stored in worldgen.toml. */
final class ConfigWorldgen {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.comment("世界生成相关配置",
                    "World generation configurations").push("worldgen");
    }

    private static final ForgeConfigSpec.BooleanValue ENABLE_STARFALL_GARDEN = BUILDER
            .comment("是否允许生成星落之庭；关闭后不再自然生成，也不会向旧存档补生成",
                    "Whether Starfall Garden may generate. When disabled, it will not generate naturally or be retrofitted into old saves.")
            .define("enableStarfallGarden", true);

    static {
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ConfigWorldgen() {
    }

    static void load() {
        Config.enableStarfallGarden = ENABLE_STARFALL_GARDEN.get();
    }
}
