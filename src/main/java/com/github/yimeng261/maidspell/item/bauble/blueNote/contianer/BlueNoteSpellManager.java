package com.github.yimeng261.maidspell.item.bauble.blueNote.contianer;

import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * BlueNote法术管理器
 * 负责法术和卷轴物品的保存、读取和管理
 * 旧存档升级过来包不兼容的
 */
public class BlueNoteSpellManager {
    /**
     * 从BlueNote物品中加载卷轴物品
     */
    public static void loadScrollsFromItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler) {
        if (blueNoteStack.isEmpty()) {
            return;
        }

        List<ItemStack> scrollsList = blueNoteStack.get(MaidSpellDataComponents.BLUE_NOTE_SCROLLS_TAG);
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
    }

    /**
     * 将卷轴物品保存到BlueNote物品
     */
    public static void saveScrollsToItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler) {
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
        List<String> spellIds = new ArrayList<>();

        if (blueNoteStack.isEmpty()) {
            return spellIds;
        }
        return blueNoteStack.getOrDefault(MaidSpellDataComponents.BLUE_NOTE_SPELL_IDS_TAG, spellIds);
    }

    /**
     * 清空存储的数据
     */
    public static void clearStoredData(ItemStack blueNoteStack) {
        if (!blueNoteStack.isEmpty()) {
            blueNoteStack.remove(MaidSpellDataComponents.BLUE_NOTE_SCROLLS_TAG);
            blueNoteStack.remove(MaidSpellDataComponents.BLUE_NOTE_SPELL_IDS_TAG);
        }
    }
}