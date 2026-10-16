package com.github.yimeng261.maidspell;

import net.minecraftforge.common.ForgeConfigSpec;

/** Optional integration configuration stored in compat.toml. */
final class ConfigCompat {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.comment("联动相关配置",
                    "Integration configurations").push("compat");
    }
    private static final ForgeConfigSpec.BooleanValue AUTO_INSTALL_TLM_MODEL_PACK = BUILDER
            .comment("启动时安装内置 TLM 模型包并覆盖同名文件；自行维护 tlm_custom_pack 时请关闭",
                    "Installs the bundled TLM model pack on launch, overwriting matching files; disable if managed manually.")
            .define("autoInstallTlmModelPack", true);

    static {
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ConfigCompat() {
    }

    static void load() {
        Config.autoInstallTlmModelPack = AUTO_INSTALL_TLM_MODEL_PACK.get();
    }
}
