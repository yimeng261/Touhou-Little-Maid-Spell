package com.github.yimeng261.maidspell.item.bauble.blueNote.contianer;

import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * BlueNote法术管理器
 * 负责法术和卷轴物品的保存、读取和管理
 * 兼容 1.20 旧 NBT 存档并迁移到 1.21 数据组件
 */
public class BlueNoteSpellManager {
    private static final String LEGACY_SCROLLS_TAG = "StoredScrolls";
    private static final String LEGACY_SPELL_IDS_TAG = "SpellIds";

    /**
     * 从BlueNote物品中加载卷轴物品
     */
    public static void loadScrollsFromItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler) {
        loadScrollsFromItem(blueNoteStack, scrollHandler, null);
    }

    public static void loadScrollsFromItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler, HolderLookup.Provider provider) {
        if (blueNoteStack.isEmpty()) {
            return;
        }

        List<ItemStack> scrollsList = blueNoteStack.get(MaidSpellDataComponents.BLUE_NOTE_SCROLLS_TAG);
        boolean migratedLegacyData = false;
        if (scrollsList == null && provider != null) {
            scrollsList = readLegacyScrolls(blueNoteStack, provider);
            migratedLegacyData = scrollsList != null;
        }
        if (scrollsList == null) {
            return;
        }

        // 清空当前槽位
        for (int i = 0; i < scrollHandler.getSlots(); i++) {
            scrollHandler.setStackInSlot(i, ItemStack.EMPTY);
        }

        // 恢复保存的卷轴物品
        for (int i = 0; i < scrollsList.size() && i < scrollHandler.getSlots(); i++) {
            ItemStack scroll = scrollsList.get(i);
            if (!scroll.isEmpty()) {
                scrollHandler.setStackInSlot(i, scroll);
            }
        }

        if (migratedLegacyData) {
            saveScrollsToItem(blueNoteStack, scrollHandler, provider);
        }
    }

    /**
     * 将卷轴物品保存到BlueNote物品
     */
    public static void saveScrollsToItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler) {
        saveScrollsToItem(blueNoteStack, scrollHandler, null);
    }

    public static void saveScrollsToItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler, HolderLookup.Provider provider) {
        if (blueNoteStack.isEmpty()) {
            return;
        }

        List<ItemStack> scrollsList = new ArrayList<>();

        for (int i = 0; i < scrollHandler.getSlots(); i++) {
            ItemStack scroll = scrollHandler.getStackInSlot(i);
            if (!scroll.isEmpty()) {
                scrollsList.add(scroll);
            } else {
                // 为空槽位添加空标记，保持索引一致
                scrollsList.add(ItemStack.EMPTY);
            }
        }

        blueNoteStack.set(MaidSpellDataComponents.BLUE_NOTE_SCROLLS_TAG, scrollsList);
        clearLegacyData(blueNoteStack);

        // 同时保存法术ID列表
        saveSpellIdsToItem(blueNoteStack, scrollHandler);
    }

    /**
     * 将法术ID列表保存到BlueNote物品
     */
    public static void saveSpellIdsToItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler) {
        if (blueNoteStack.isEmpty()) {
            return;
        }

        List<String> spellIds = extractSpellIdsFromScrolls(scrollHandler);
        blueNoteStack.set(MaidSpellDataComponents.BLUE_NOTE_SPELL_IDS_TAG, spellIds);
        clearLegacyData(blueNoteStack);
    }

    /**
     * 从ItemStackHandler中的卷轴提取法术ID
     */
    private static List<String> extractSpellIdsFromScrolls(ItemStackHandler scrollHandler) {
        List<String> spellIds = new ArrayList<>();

        for (int i = 0; i < scrollHandler.getSlots(); i++) {
            ItemStack stack = scrollHandler.getStackInSlot(i);
            if (!stack.isEmpty() && ISpellContainer.isSpellContainer(stack)) {
                ISpellContainer container = ISpellContainer.get(stack);
                if (!container.isEmpty()) {
                    SpellSlot[] scrollSpells = container.getAllSpells();
                    for (SpellSlot spellSlot : scrollSpells) {
                        if (spellSlot != null && spellSlot.getSpell() != null) {
                            spellIds.add(spellSlot.getSpell().getSpellId());
                        }
                    }
                }
            }
        }

        return spellIds;
    }

    /**
     * 获取存储的法术ID列表
     */
    public static List<String> getStoredSpellIds(ItemStack blueNoteStack) {
        return getStoredSpellIds(blueNoteStack, null);
    }

    public static List<String> getStoredSpellIds(ItemStack blueNoteStack, HolderLookup.Provider provider) {
        List<String> spellIds = new ArrayList<>();
        if (blueNoteStack.isEmpty()) {
            return spellIds;
        }

        List<String> storedSpellIds = blueNoteStack.get(MaidSpellDataComponents.BLUE_NOTE_SPELL_IDS_TAG);
        if (storedSpellIds != null) {
            return new ArrayList<>(storedSpellIds);
        }

        spellIds = readLegacySpellIds(blueNoteStack);
        if (!spellIds.isEmpty()) {
            blueNoteStack.set(MaidSpellDataComponents.BLUE_NOTE_SPELL_IDS_TAG, List.copyOf(spellIds));
        }
        return spellIds;
    }

    /**
     * 清空存储的数据
     */
    public static void clearStoredData(ItemStack blueNoteStack) {
        if (!blueNoteStack.isEmpty()) {
            blueNoteStack.remove(MaidSpellDataComponents.BLUE_NOTE_SCROLLS_TAG);
            blueNoteStack.remove(MaidSpellDataComponents.BLUE_NOTE_SPELL_IDS_TAG);
            clearLegacyData(blueNoteStack);
        }
    }

    private static List<ItemStack> readLegacyScrolls(ItemStack blueNoteStack, HolderLookup.Provider provider) {
        CompoundTag customTag = getLegacyDataRoot(blueNoteStack);
        if (customTag == null || !customTag.contains(LEGACY_SCROLLS_TAG, Tag.TAG_LIST)) {
            return null;
        }

        ListTag scrollsList = customTag.getList(LEGACY_SCROLLS_TAG, Tag.TAG_COMPOUND);
        List<ItemStack> result = new ArrayList<>(scrollsList.size());
        for (int i = 0; i < scrollsList.size(); i++) {
            CompoundTag scrollTag = scrollsList.getCompound(i);
            result.add(scrollTag.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(provider, scrollTag));
        }
        return result;
    }

    private static List<String> readLegacySpellIds(ItemStack blueNoteStack) {
        List<String> spellIds = new ArrayList<>();
        CompoundTag customTag = getLegacyDataRoot(blueNoteStack);
        if (customTag == null || !customTag.contains(LEGACY_SPELL_IDS_TAG, Tag.TAG_LIST)) {
            return spellIds;
        }

        ListTag spellIdsList = customTag.getList(LEGACY_SPELL_IDS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < spellIdsList.size(); i++) {
            String spellId = spellIdsList.getString(i);
            if (!spellId.isEmpty()) {
                spellIds.add(spellId);
            }
        }
        return spellIds;
    }

    private static CompoundTag getLegacyDataRoot(ItemStack blueNoteStack) {
        CustomData customData = blueNoteStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }
        return customData.copyTag();
    }

    private static void clearLegacyData(ItemStack blueNoteStack) {
        CustomData.update(DataComponents.CUSTOM_DATA, blueNoteStack, tag -> {
            tag.remove(LEGACY_SCROLLS_TAG);
            tag.remove(LEGACY_SPELL_IDS_TAG);
        });
    }
}
