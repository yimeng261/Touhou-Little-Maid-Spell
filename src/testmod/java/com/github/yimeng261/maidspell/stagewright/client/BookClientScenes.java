package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 书的每页在成书界面里放得下：按客户端字体折行后不超过一页的行数，中英文都一样。
 */
public final class BookClientScenes {
    /** 成书界面的文字区宽 114 像素、高 128 像素，一行 9 像素。 */
    private static final int PAGE_WIDTH = 114;
    private static final int PAGE_LINES = 128 / 9;

    private BookClientScenes() {
    }

    public static List<Scene> integratedServer() {
        return List.of(Players.hostScene("client.books.everyPageFits", 10, BookClientScenes::pagesFit));
    }

    private static void pagesFit(SceneContext ctx, ServerPlayer player) {
        Map<String, Integer> books = Lang.books(Lang.read("en_us"));
        ClientSide.call(ctx, () -> {
            Font font = Minecraft.getInstance().font;
            List<String> overflow = new ArrayList<>();
            for (String code : List.of("en_us", "zh_cn")) {
                ClientLanguage language = ClientSide.language(code);
                books.forEach((book, pages) -> {
                    for (int page = 1; page <= pages; page++) {
                        String key = book + ".page_" + page;
                        int lines = font.split(FormattedText.of(language.getOrDefault(key)), PAGE_WIDTH).size();
                        if (lines > PAGE_LINES) {
                            overflow.add(code + " " + key + "：" + lines + " 行");
                        }
                    }
                });
            }
            return overflow;
        }, overflow -> {
            ctx.record("books", books);
            ctx.check(overflow).as("折行后超过 " + PAGE_LINES + " 行的页").isEmpty();
        });
    }
}
