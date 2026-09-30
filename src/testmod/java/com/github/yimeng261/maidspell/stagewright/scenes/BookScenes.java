package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构里预置的书（旅行日记、魔女日记、研究记录）：每页、书名都用语言键，两种语言都有；
 * 旅行日记作者是 Stellar_Witch 并带旅行日记标记；书里的页与语言文件里的页一一对应。
 * 以及第 5 批改过的几处文案。
 */
public final class BookScenes {
    private static final String TRAVEL_DIARY = "item.touhou_little_maid_spell.loot.travel_diary.";
    private static final String DIARY_MARKER = MaidSpellMod.MOD_ID + ":travel_diary";
    private static final Pattern PAGE = Pattern.compile("^(.*)\\.page_(\\d+)$");

    private BookScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("books.structureBooksUseLangKeys", 20, BookScenes::structureBooks));
        scenes.add(Checks.scene("books.samePageCountInBothLanguages", 5, BookScenes::pageCounts));
        scenes.add(Checks.scene("lang.batch5Wording", 5, BookScenes::wording));
        return scenes;
    }

    /** 一本在结构模板里找到的书。 */
    private record FoundBook(String template, ItemStack stack) {
    }

    private static List<FoundBook> structureBooks(SceneContext ctx, List<String> templates) {
        StructureTemplateManager manager = ctx.level().getStructureManager();
        List<FoundBook> books = new ArrayList<>();
        manager.listTemplates().filter(id -> id.getNamespace().equals(MaidSpellMod.MOD_ID)).sorted().forEach(id -> {
            StructureTemplate template = manager.get(id).orElse(null);
            if (template == null) {
                return;
            }
            templates.add(id.toString());
            CompoundTag saved = template.save(new CompoundTag());
            collect(saved, tag -> {
                ItemStack stack = ItemStack.parseOptional(ctx.level().registryAccess(), tag);
                if (!stack.isEmpty()) {
                    books.add(new FoundBook(id.toString(), stack));
                }
            });
        });
        return books;
    }

    /** 递归找出 id 为 minecraft:written_book 的物品标签。 */
    private static void collect(Tag tag, java.util.function.Consumer<CompoundTag> found) {
        if (tag instanceof CompoundTag compound) {
            if ("minecraft:written_book".equals(compound.getString("id"))) {
                found.accept(compound);
                return;
            }
            for (String key : compound.getAllKeys()) {
                collect(compound.get(key), found);
            }
        } else if (tag instanceof ListTag list) {
            list.forEach(child -> collect(child, found));
        }
    }

    private static String key(Component component) {
        return component != null && component.getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    private static void structureBooks(SceneContext ctx) {
        Map<String, String> en = Lang.read("en_us");
        Map<String, String> zh = Lang.read("zh_cn");
        Map<String, Integer> pagesInLang = Lang.books(en);
        List<String> templates = new ArrayList<>();
        List<FoundBook> books = structureBooks(ctx, templates);
        ctx.record("templatesScanned", templates.size());
        ctx.check(books).as("结构模板里的成书").isNotEmpty();
        List<String> problems = new ArrayList<>();
        Map<String, Integer> found = new TreeMap<>();
        for (FoundBook book : books) {
            String where = book.template() + " 里的书";
            WrittenBookContent content = book.stack().get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content == null) {
                problems.add(where + "没有成书内容");
                continue;
            }
            List<String> keys = new ArrayList<>();
            for (Filterable<Component> page : content.pages()) {
                String key = key(page.raw());
                if (key == null) {
                    problems.add(where + "有一页不是语言键：" + page.raw().getString());
                } else if (!en.containsKey(key) || !zh.containsKey(key)) {
                    problems.add(where + "的页键 " + key + " 缺翻译");
                }
                keys.add(key);
            }
            String prefix = prefixOf(keys);
            if (prefix == null) {
                problems.add(where + "的页键不属于同一本书或不是连续页：" + keys);
                continue;
            }
            found.put(prefix, keys.size());
            if (!Integer.valueOf(keys.size()).equals(pagesInLang.get(prefix))) {
                problems.add(where + "（" + prefix + "）有 " + keys.size() + " 页，语言文件里有 " + pagesInLang.get(prefix) + " 页");
            }
            String titleKey = prefix + ".title";
            if (en.containsKey(titleKey) && !titleKey.equals(key(book.stack().get(DataComponents.CUSTOM_NAME)))
                    && !titleKey.equals(key(book.stack().get(DataComponents.ITEM_NAME)))) {
                problems.add(where + "（" + prefix + "）的书名不是 " + titleKey);
            }
            if (prefix.startsWith(TRAVEL_DIARY)) {
                if (!"Stellar_Witch".equals(content.author())) {
                    problems.add(where + "（" + prefix + "）的作者是 " + content.author());
                }
                CustomData data = book.stack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                if (!data.copyTag().getBoolean(DIARY_MARKER)) {
                    problems.add(where + "（" + prefix + "）没有 " + DIARY_MARKER + " 标记");
                }
            }
        }
        ctx.record("booksFound", found);
        ctx.check(problems).as("结构里的书的问题").isEmpty();
    }

    /** 页键都是同一前缀、页码从 1 连续；否则返回 null。 */
    private static String prefixOf(List<String> keys) {
        String prefix = null;
        for (int i = 0; i < keys.size(); i++) {
            Matcher m = keys.get(i) == null ? null : PAGE.matcher(keys.get(i));
            if (m == null || !m.matches() || Integer.parseInt(m.group(2)) != i + 1) {
                return null;
            }
            if (prefix != null && !prefix.equals(m.group(1))) {
                return null;
            }
            prefix = m.group(1);
        }
        return prefix;
    }

    private static void pageCounts(SceneContext ctx) {
        ctx.check(Lang.books(Lang.read("zh_cn"))).as("中文各本书的页数（与英文对照）").isEqualTo(Lang.books(Lang.read("en_us")));
    }

    /** 归星说明写明秘银锭；针叶林村庄日记里没有多出来的「欢」；英文里提到星落之庭的地方都是 Starfall Garden。 */
    private static void wording(SceneContext ctx) {
        Map<String, String> en = Lang.read("en_us");
        Map<String, String> zh = Lang.read("zh_cn");
        String returningStar = "item.touhou_little_maid_spell.returning_star.desc";
        ctx.check(en.getOrDefault(returningStar, "").contains("mithril ingots")).as("归星英文说明提到 mithril ingots").isTrue();
        ctx.check(zh.getOrDefault(returningStar, "").contains("星陨石与秘银锭")).as("归星中文说明提到星陨石与秘银锭").isTrue();
        ctx.check(zh.entrySet().stream().filter(e -> e.getValue().contains("欢欢")).map(Map.Entry::getKey).toList())
                .as("中文里含「欢欢」的键").isEmpty();
        ctx.check(zh.getOrDefault(TRAVEL_DIARY + "taiga_village.page_5", "").contains("格外的欢愉")).as("针叶林村庄日记第 5 页写「格外的欢愉」").isTrue();
        List<String> wrong = zh.entrySet().stream().filter(e -> e.getValue().contains("星落之庭"))
                .map(Map.Entry::getKey).filter(k -> !en.getOrDefault(k, "").contains("Starfall Garden")).toList();
        ctx.check(wrong).as("中文提到星落之庭、英文没写 Starfall Garden 的键").isEmpty();
    }
}
