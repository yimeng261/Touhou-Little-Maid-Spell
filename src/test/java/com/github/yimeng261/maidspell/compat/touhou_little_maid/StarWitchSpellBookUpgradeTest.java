package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 星之魔女魂符出厂时的法术书容量归一化。
 *
 * <p>这条逻辑只在 {@link StellarWitchStarterMaid#createSoulCharm} 里对"即将写进魂符的序列化数据"跑一次，
 * 不参与女仆入世流程。下面用纯 NBT 覆盖现代/旧版两个容器键、越界过滤器与幂等性，
 * 并核对仓库里的出厂模板本身已经是 20 格。
 */
class StarWitchSpellBookUpgradeTest {
    private static final String MODERN_CONTAINER = "irons_spellbooks:spell_container";
    private static final String LEGACY_CONTAINER = "ISB_Spells";
    private static final String BOOK_ID = "irons_spellbooks:dragonskin_spell_book";

    @Test
    void modernContainerIsRaisedToTwentyWithoutTouchingSpellData() {
        CompoundTag book = spellBook(BOOK_ID, container(MODERN_CONTAINER, 12, 15));
        CompoundTag maidData = maidWith(book);

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        CompoundTag upgraded = containerOf(firstItem(maidData), MODERN_CONTAINER);
        assertEquals(20, upgraded.getInt("maxSpells"), "maxSpells should be raised to the factory capacity");
        assertEquals(15, upgraded.getList("data", Tag.TAG_COMPOUND).size(), "spell entries must be preserved");
        assertTrue(upgraded.getBoolean("mustEquip"), "other container fields must be preserved");
        assertTrue(upgraded.getBoolean("spellWheel"), "other container fields must be preserved");
        assertEquals("irons_spellbooks:magic_missile",
                upgraded.getList("data", Tag.TAG_COMPOUND).getCompound(3).getString("id"));
        assertEquals(10, upgraded.getList("data", Tag.TAG_COMPOUND).getCompound(3).getInt("level"));
    }

    @Test
    void legacyContainerKeyIsRaisedToo() {
        CompoundTag book = spellBook(BOOK_ID, container(LEGACY_CONTAINER, 8, 6));
        CompoundTag maidData = maidWith(book);

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        CompoundTag itemTag = firstItem(maidData).getCompound("tag");
        assertEquals(20, itemTag.getCompound(LEGACY_CONTAINER).getInt("maxSpells"));
        assertFalse(itemTag.contains(MODERN_CONTAINER), "upgrade must not migrate or invent the modern key");
    }

    @Test
    void nonSpellBookContainersAreLeftAlone() {
        // 出厂长剑自带一个 1 格容器（虚空相位）；它不属于 "spell_book" 物品，容量不该被改。
        CompoundTag sword = item("touhou_little_maid_spell:star_shadow_longsword",
                container(MODERN_CONTAINER, 1, 1));
        CompoundTag staff = item("touhou_little_maid_spell:star_shadow_staff", null);
        CompoundTag maidData = maidWith(sword, staff);

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        ListTag items = maidItems(maidData);
        assertEquals(1, items.getCompound(0).getCompound("tag")
                .getCompound(MODERN_CONTAINER).getInt("maxSpells"));
        assertFalse(items.getCompound(1).contains("tag"), "items without a tag must stay untouched");
    }

    @Test
    void booksFromOtherNamespacesAreLeftAlone() {
        CompoundTag book = spellBook("othermod:spell_book", container(MODERN_CONTAINER, 6, 2));
        CompoundTag maidData = maidWith(book);

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        assertEquals(6, containerOf(firstItem(maidData), MODERN_CONTAINER).getInt("maxSpells"));
    }

    @Test
    void missingInventoryOrInventoryItemsAreTolerated() {
        // 没有 MaidInventory 的数据（旧版模板、被裁剪的奖励）必须静默跳过。
        MaidOriginData.upgradeStarWitchSpellBookData(new CompoundTag());

        // 有 MaidInventory 但没有 Items 列表。
        CompoundTag data = new CompoundTag();
        data.put("MaidInventory", new CompoundTag());
        MaidOriginData.upgradeStarWitchSpellBookData(data);

        // Items 里混入没有 tag / 没有 id 的条目。
        CompoundTag broken = new CompoundTag();
        broken.put("MaidInventory", inventory(item(null, null), item(BOOK_ID, null)));
        MaidOriginData.upgradeStarWitchSpellBookData(broken);
        assertFalse(maidItems(broken).getCompound(0).contains("tag"));
    }

    @Test
    void upgradeIsIdempotent() {
        CompoundTag maidData = maidWith(spellBook(BOOK_ID, container(MODERN_CONTAINER, 12, 15)));

        MaidOriginData.upgradeStarWitchSpellBookData(maidData);
        MaidOriginData.upgradeStarWitchSpellBookData(maidData);

        CompoundTag upgraded = containerOf(firstItem(maidData), MODERN_CONTAINER);
        assertEquals(20, upgraded.getInt("maxSpells"));
        assertEquals(15, upgraded.getList("data", Tag.TAG_COMPOUND).size());
    }

    @Test
    void shippedTemplateShipsATwentySlotBook() throws Exception {
        Path template = Path.of(System.getProperty("maidspell.projectDir", "."),
                "src/main/resources/data/touhou_little_maid_spell/maid/stellar_witch_maid.snbt");
        Assumptions.assumeTrue(Files.exists(template), "template resource is only available in the project checkout");
        CompoundTag maidData = TagParser.parseTag(
                Files.readString(template, StandardCharsets.UTF_8));

        CompoundTag book = findSpellBook(maidData);
        assertNotNull(book, "the factory template must ship an Iron's Spellbooks book");
        assertEquals(20, containerOf(book, MODERN_CONTAINER).getInt("maxSpells"),
                "a data fixer or template edit that drops the capacity would break the preset spell list");
        assertEquals(15, containerOf(book, MODERN_CONTAINER).getList("data", Tag.TAG_COMPOUND).size());

        // 出厂路径对模板本体是幂等的：跑一遍升级不会改变结果。
        MaidOriginData.upgradeStarWitchSpellBookData(maidData);
        assertEquals(20, containerOf(findSpellBook(maidData), MODERN_CONTAINER).getInt("maxSpells"));
    }

    /** 模板背包第 0 格是长剑（自带 1 格容器），法术书要按 id 找，不能顺手取第一格。 */
    private static CompoundTag findSpellBook(CompoundTag maidData) {
        for (Tag element : maidItems(maidData)) {
            CompoundTag item = (CompoundTag) element;
            String id = item.getString("id");
            if (id.startsWith("irons_spellbooks:") && id.contains("spell_book")) {
                return item;
            }
        }
        return null;
    }

    // ==================== 构造 NBT 的小工具 ====================

    private static CompoundTag maidWith(CompoundTag... items) {
        CompoundTag maidData = new CompoundTag();
        maidData.put("MaidInventory", inventory(items));
        return maidData;
    }

    private static CompoundTag inventory(CompoundTag... items) {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", 36);
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.length; slot++) {
            CompoundTag item = items[slot].copy();
            item.putByte("Slot", (byte) slot);
            list.add(item);
        }
        inventory.put("Items", list);
        return inventory;
    }

    private static CompoundTag spellBook(String id, CompoundTag itemTag) {
        return item(id, itemTag);
    }

    private static CompoundTag item(String id, CompoundTag itemTag) {
        CompoundTag item = new CompoundTag();
        if (id != null) {
            item.putString("id", id);
        }
        item.putByte("Count", (byte) 1);
        if (itemTag != null) {
            item.put("tag", itemTag);
        }
        return item;
    }

    private static CompoundTag container(String key, int maxSpells, int spellCount) {
        CompoundTag tag = new CompoundTag();
        CompoundTag container = new CompoundTag();
        container.putInt("maxSpells", maxSpells);
        container.putBoolean("mustEquip", true);
        container.putBoolean("spellWheel", true);
        ListTag spells = new ListTag();
        for (int index = 0; index < spellCount; index++) {
            CompoundTag spell = new CompoundTag();
            spell.putInt("index", index);
            spell.putInt("level", index == 3 ? 10 : 5);
            spell.putString("id", index == 3
                    ? "irons_spellbooks:magic_missile" : "touhou_little_maid_spell:test_spell_" + index);
            spells.add(spell);
        }
        container.put("data", spells);
        tag.put(key, container);
        return tag;
    }

    private static ListTag maidItems(CompoundTag maidData) {
        return maidData.getCompound("MaidInventory").getList("Items", Tag.TAG_COMPOUND);
    }

    private static CompoundTag firstItem(CompoundTag maidData) {
        return maidItems(maidData).getCompound(0);
    }

    private static CompoundTag containerOf(CompoundTag item, String key) {
        return item.getCompound("tag").getCompound(key);
    }
}
