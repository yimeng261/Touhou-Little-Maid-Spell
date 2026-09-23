package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.github.yimeng261.maidspell.validation.ValidationFixtures.RESOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注入 TLM 手册《记忆中的幻想乡》里的那一块「万法皆通」。
 *
 * <h2>为什么这些文件住在别人的 namespace 里</h2>
 * Patchouli 是这么认书里的东西的：先列出 {@code patchouli_books/<book>/en_us/<type>} 下的
 * 所有 JSON，再 {@code filter(file -> file.getNamespace().equals(book.id.getNamespace()))}。
 * 书的 id 是 {@code touhou_little_maid:memorizable_gensokyo}，所以我们的类别与条目必须放在
 * {@code assets/touhou_little_maid/patchouli_books/memorizable_gensokyo/en_us/} 下面，
 * 条目 id 也就跟着落在 {@code touhou_little_maid:} 里。放进我们自己的 namespace 不会报错，
 * 只是手册里空空如也 —— 这条测试就是钉住这件事。
 *
 * <h2>为什么要盯语言键</h2>
 * TLM 的 {@code book.json} 写着 {@code "i18n": true}，名称与正文都会被当成语言键走一遍
 * {@code Component.translatable}/{@code I18n.get}。键名写错不会报错，手册里就直接显示
 * 一长串键名，所以两份语言文件里都得有。
 *
 * <h2>插画为什么是 256x256</h2>
 * {@code image} 页永远按 uv(0,0)、{@code 200x200} 采样，而那个 {@code blit} 重载把纹理尺寸
 * 写死成 256x256 —— 所以真正显示出来的是「256x256 画布左上角的 200x200」。画布小一圈
 * （比如 200x200），采样范围折算过去只有 0..156，屏幕上就是一张被裁掉右下角的图。
 */
class PatchouliManualResourceTest {
    private static final Path BOOK = RESOURCES.resolve(
        "assets/touhou_little_maid/patchouli_books/memorizable_gensokyo/en_us");
    private static final Path LANG = RESOURCES.resolve("assets/touhou_little_maid_spell/lang");
    private static final String CATEGORY = "touhou_little_maid:wanfa_jietong";
    private static final String IMAGE_PAGE_CANVAS =
        "image 页按 256x256 算 UV，画布必须是 256x256";
    private static final List<String> ENTRIES = List.of("work_modes", "baubles", "structures");

    @Test
    void injectedCategoryAndEntriesCarryBothLanguagesAndRealIcons() throws IOException {
        JsonObject zh = parseObject(LANG.resolve("zh_cn.json"));
        JsonObject en = parseObject(LANG.resolve("en_us.json"));

        List<String> textKeys = new ArrayList<>();
        List<String> icons = new ArrayList<>();

        JsonObject category = parseObject(BOOK.resolve("categories/wanfa_jietong.json"));
        textKeys.add(category.get("name").getAsString());
        textKeys.add(category.get("description").getAsString());
        icons.add(category.get("icon").getAsString());

        for (String entry : ENTRIES) {
            JsonObject json = parseObject(BOOK.resolve("entries/wanfa_jietong/" + entry + ".json"));
            assertEquals(CATEGORY, json.get("category").getAsString(),
                entry + " 的 category 必须写全，并且指回注入的那个类别");
            textKeys.add(json.get("name").getAsString());
            icons.add(json.get("icon").getAsString());
            collectPages(json.getAsJsonArray("pages"), entry, textKeys);
        }

        for (String key : textKeys) {
            assertTrue(zh.has(key), "zh_cn.json 缺少手册键 " + key);
            assertTrue(en.has(key), "en_us.json 缺少手册键 " + key);
        }
        for (String icon : icons) {
            // 图标是物品 id（namespace:path），物品的语言键是 item.namespace.path；缺了就是一片空白图标。
            String key = "item." + icon.replace(':', '.');
            assertTrue(zh.has(key), "手册图标 " + icon + " 不是个有语言键的物品（" + key + "）");
        }

        assertFalse(Files.exists(RESOURCES.resolve(
                "assets/touhou_little_maid_spell/patchouli_books")),
            "手册内容放进自己的 namespace 会被 Patchouli 过滤掉，等于没写");
    }

    private static void collectPages(JsonArray pages, String entry, List<String> textKeys)
            throws IOException {
        assertNotNull(pages, entry + " 没有 pages");
        assertTrue(pages.size() > 0, entry + " 的 pages 是空的");
        for (JsonElement page : pages) {
            JsonObject pageObject = page.getAsJsonObject();
            if (pageObject.has("text")) {
                textKeys.add(pageObject.get("text").getAsString());
            }
            if (!pageObject.has("images")) {
                continue;
            }
            for (JsonElement image : pageObject.getAsJsonArray("images")) {
                String id = image.getAsString();
                int split = id.indexOf(':');
                assertTrue(split > 0, entry + " 的图片 id 不是 namespace:path：" + id);
                Path texture = RESOURCES.resolve(
                    "assets/" + id.substring(0, split) + "/" + id.substring(split + 1));
                assertTrue(Files.isRegularFile(texture), entry + " 的图片不存在：" + texture);
                BufferedImage picture = ImageIO.read(texture.toFile());
                assertNotNull(picture, entry + " 的图片读不出来：" + texture);
                assertEquals(256, picture.getWidth(), IMAGE_PAGE_CANVAS);
                assertEquals(256, picture.getHeight(), IMAGE_PAGE_CANVAS);
                assertContentInsideSampledRegion(picture, texture);
            }
        }
    }

    /**
     * {@code PageImage.render} 调的是七参 {@code blit}，那个重载把纹理尺寸写死成 256x256：
     * UV 只算到 {@code 200/256}，也就是**只采左上 200x200 那一块**。
     *
     * <p>所以画布必须是 256x256、画面必须落在左上 200x200 里。按 200x200 出图的话，
     * 采样范围折算到 200 宽的贴图上只有 0..156，屏幕上就是一张被裁掉右下角、还放大了的图。
     */
    private static void assertContentInsideSampledRegion(BufferedImage picture, Path texture) {
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < picture.getHeight(); y++) {
            for (int x = 0; x < picture.getWidth(); x++) {
                if ((picture.getRGB(x, y) >>> 24) != 0) {
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(maxX >= 0, texture + " 是一张全透明的空图");
        assertTrue(maxX < 200 && maxY < 200,
            texture + " 的画面伸到了 (" + maxX + "," + maxY + ")：image 页只采左上 200x200，多出去的部分会被裁掉");
    }
}
