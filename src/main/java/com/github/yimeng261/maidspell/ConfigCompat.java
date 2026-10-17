package com.github.yimeng261.maidspell;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 可选联动相关的通用配置，存放在 compat.toml。 */
final class ConfigCompat {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("联动相关配置",
                    "Integration configurations").push("compat");
    }
    private static final ModConfigSpec.BooleanValue AUTO_INSTALL_TLM_MODEL_PACK = BUILDER
            .comment("启动时安装内置 TLM 模型包并覆盖同名文件；自行维护 tlm_custom_pack 时请关闭",
                    "Installs the bundled TLM model pack on launch, overwriting matching files; disable if managed manually.")
            .define("autoInstallTlmModelPack", true);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ConfigCompat() {
    }

    static void load() {
        Config.autoInstallTlmModelPack = AUTO_INSTALL_TLM_MODEL_PACK.get();
    }
}
