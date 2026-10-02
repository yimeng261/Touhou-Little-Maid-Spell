package com.github.yimeng261.maidspell.stagewright.support;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直接读本模组自带的语言文件（服务端也能读，不经客户端的语言加载）。
 */
public final class Lang {
    private static final Pattern PAGE = Pattern.compile("^(.*)\\.page_(\\d+)$");

    private Lang() {
    }

    /** 读 assets/touhou_little_maid_spell/lang/&lt;code&gt;.json；文件不存在时抛异常。 */
    public static Map<String, String> read(String code) {
        String path = "/assets/" + MaidSpellMod.MOD_ID + "/lang/" + code + ".json";
        try (InputStream in = Lang.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("找不到语言文件 " + path);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> out = new TreeMap<>();
            json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 按 "&lt;书&gt;.page_&lt;n&gt;" 形式的键分组：书的键前缀 → 页数（最大页码）。 */
    public static Map<String, Integer> books(Map<String, String> lang) {
        Map<String, Integer> out = new TreeMap<>();
        for (String key : lang.keySet()) {
            Matcher m = PAGE.matcher(key);
            if (m.matches()) {
                out.merge(m.group(1), Integer.parseInt(m.group(2)), Math::max);
            }
        }
        return out;
    }
}
