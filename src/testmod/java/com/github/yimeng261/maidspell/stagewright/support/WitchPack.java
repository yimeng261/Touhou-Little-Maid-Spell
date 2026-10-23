package com.github.yimeng261.maidspell.stagewright.support;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 读星之魔女内置模型包里的动画文件（随模组资源一起打包，服务端也能读到）。 */
public final class WitchPack {
    private static final String ANIMATION_ROOT = "/assets/touhou_little_maid_spell/tlm_custom_pack/"
            + "star_witch_winefox-1.0.0/assets/touhou_little_maid_spell/animation/";

    private WitchPack() {
    }

    /** 动画文件里的 animations 对象，文件名形如 {@code touhou_little_maid_spell.stellar_witch.main.animation.json}。 */
    public static JsonObject animations(String file) {
        try (InputStream stream = MaidSpellMod.class.getResourceAsStream(ANIMATION_ROOT + file)) {
            if (stream == null) {
                throw new IllegalStateException("找不到动画文件 " + file);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
