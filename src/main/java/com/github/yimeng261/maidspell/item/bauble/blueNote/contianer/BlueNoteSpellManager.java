package com.github.yimeng261.maidspell.item.bauble.blueNote.contianer;

import com.github.yimeng261.maidspell.spell.providers.IronsSpellbooksProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;

import java.util.ArrayList;
import java.util.List;

/**
 * BlueNote法术管理器
 * 负责法术和卷轴物品的保存、读取和管理
 */
public class BlueNoteSpellManager {
    private static final String SCROLLS_TAG = "StoredScrolls";
    private static final String SPELL_IDS_TAG = "SpellIds";
    
    /**
     * 从BlueNote物品中加载卷轴物品
     */
    public static void loadScrollsFromItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler) {
        if (blueNoteStack.isEmpty()) {
            return;
        }
        
        CompoundTag tag = blueNoteStack.getOrCreateTag();
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
     * 将卷轴物品保存到BlueNote物品
     */
    public static void saveScrollsToItem(ItemStack blueNoteStack, ItemStackHandler scrollHandler) {
        if (blueNoteStack.isEmpty()) {
            return;
        }
        
        CompoundTag tag = blueNoteStack.getOrCreateTag();
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
        
        CompoundTag tag = blueNoteStack.getOrCreateTag();
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
                    List<SpellData> scrollSpells = IronsSpellbooksProvider.ApiCompatLayer.convertToSpellDataList(container.getActiveSpells());
                    for (SpellData spellData : scrollSpells) {
                        if (spellData != null && spellData.getSpell() != null) {
                            spellIds.add(spellData.getSpell().getSpellId());
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
        
        CompoundTag tag = blueNoteStack.getTag();
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
    public static void clearStoredData(ItemStack blueNoteStack) {
        if (!blueNoteStack.isEmpty()) {
            CompoundTag tag = blueNoteStack.getOrCreateTag();
            tag.remove(SCROLLS_TAG);
            tag.remove(SPELL_IDS_TAG);
        }
    }
} 