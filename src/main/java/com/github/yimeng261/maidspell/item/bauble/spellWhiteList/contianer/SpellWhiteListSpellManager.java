package com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * spellWhiteList法术管理器
 * 负责法术和卷轴物品的保存、读取和管理
 */
public class SpellWhiteListSpellManager {
    private static final String SCROLLS_TAG = "StoredScrolls";
    private static final String SPELL_IDS_TAG = "SpellIds";
    
    /**
     * 从spellWhiteList物品中加载卷轴物品
     */
    public static void loadScrollsFromItem(ItemStack spellWhiteListStack, ItemStackHandler scrollHandler) {
        if (spellWhiteListStack.isEmpty()) {
            return;
        }
        
        CompoundTag tag = spellWhiteListStack.getOrCreateTag();
        if (!tag.contains(SCROLLS_TAG)) {
            return;
        }
        
        ListTag scrollsList = tag.getList(SCROLLS_TAG, Tag.TAG_COMPOUND);
        
        // 清空当前槽位
        for (int i = 0; i < scrollHandler.getSlots(); i++) {
            scrollHandler.setStackInSlot(i, ItemStack.EMPTY);
        }
        
        // 恢复保存的卷轴物品
        for (int i = 0; i < scrollsList.size() && i < scrollHandler.getSlots(); i++) {
            CompoundTag scrollTag = scrollsList.getCompound(i);
            ItemStack scroll = ItemStack.of(scrollTag);
            if (!scroll.isEmpty()) {
                scrollHandler.setStackInSlot(i, scroll);
            }
        }
    }
    
    /**
     * 将卷轴物品保存到spellWhiteList物品
     */
    public static void saveScrollsToItem(ItemStack spellWhiteListStack, ItemStackHandler scrollHandler) {
        if (spellWhiteListStack.isEmpty()) {
            return;
        }
        
        CompoundTag tag = spellWhiteListStack.getOrCreateTag();
        ListTag scrollsList = new ListTag();
        
        for (int i = 0; i < scrollHandler.getSlots(); i++) {
            ItemStack scroll = scrollHandler.getStackInSlot(i);
            if (!scroll.isEmpty()) {
                CompoundTag scrollTag = new CompoundTag();
                scroll.save(scrollTag);
                scrollsList.add(scrollTag);
            } else {
                // 为空槽位添加空标记，保持索引一致
                scrollsList.add(new CompoundTag());
            }
        }
        
        tag.put(SCROLLS_TAG, scrollsList);
        
        // 同时保存法术ID列表
        saveSpellIdsToItem(spellWhiteListStack, scrollHandler);
    }
    
    /**
     * 将法术ID列表保存到spellWhiteList物品
     */
    public static void saveSpellIdsToItem(ItemStack spellWhiteListStack, ItemStackHandler scrollHandler) {
        if (spellWhiteListStack.isEmpty()) {
            return;
        }
        
        List<String> spellIds = extractSpellIdsFromScrolls(scrollHandler);
        
        CompoundTag tag = spellWhiteListStack.getOrCreateTag();
        ListTag spellIdsList = new ListTag();
        
        for (String spellId : spellIds) {
            if (spellId != null && !spellId.isEmpty()) {
                spellIdsList.add(StringTag.valueOf(spellId));
            }
        }
        
        tag.put(SPELL_IDS_TAG, spellIdsList);
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
                    for (SpellSlot spellSlot : container.getActiveSpells()) {
                        if (spellSlot != null && spellSlot.spellData() != null && spellSlot.spellData().getSpell() != null) {
                            spellIds.add(spellSlot.spellData().getSpell().getSpellId());
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
    public static List<String> getStoredSpellIds(ItemStack spellWhiteListStack) {
        List<String> spellIds = new ArrayList<>();
        
        if (spellWhiteListStack.isEmpty()) {
            return spellIds;
        }
        
        CompoundTag tag = spellWhiteListStack.getTag();
        if (tag == null || !tag.contains(SPELL_IDS_TAG)) {
            return spellIds;
        }
        
        ListTag spellIdsList = tag.getList(SPELL_IDS_TAG, Tag.TAG_STRING);
        
        for (int i = 0; i < spellIdsList.size(); i++) {
            String spellId = spellIdsList.getString(i);
            if (!spellId.isEmpty()) {
                spellIds.add(spellId);
            }
        }
        
        return spellIds;
    }
    
    /**
     * 清空存储的数据
     */
    public static void clearStoredData(ItemStack spellWhiteListStack) {
        if (!spellWhiteListStack.isEmpty()) {
            CompoundTag tag = spellWhiteListStack.getOrCreateTag();
            tag.remove(SCROLLS_TAG);
            tag.remove(SPELL_IDS_TAG);
        }
    }
} 