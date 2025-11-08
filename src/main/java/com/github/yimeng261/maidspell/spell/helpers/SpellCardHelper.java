package com.github.yimeng261.maidspell.spell.helpers;

import dev.xkmc.youkaishomecoming.content.spell.game.TouhouSpellCards;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCardWrapper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 符卡辅助类
 * 用于反射访问TouhouSpellCards的私有MAP
 * 以及管理符卡物品到符卡ID的映射
 */
public class SpellCardHelper {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 符卡物品注册名到符卡ID的映射
    private static final Map<String, String> ITEM_TO_SPELL_CARD = new HashMap<>();
    
    static {
        // 初始化符卡物品到符卡ID的映射
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_reimu", "hakurei_reimu");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_sanae", "kochiya_sanae");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_marisa", "kirisame_marisa");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_mystia", "mystia_lorelei");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_remilia", "remilia_scarlet");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_koishi", "komeiji_koishi");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_yukari_butterfly", "yukari_yakumo");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_yukari_laser", "yukari_yakumo");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_clownpiece", "clownpiece");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.custom_spell_ring", "custom");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.custom_spell_homing", "custom");
    }
    private static Map<String, Supplier<SpellCard>> spellCardMap = null;
    
    /**
     * 获取符卡MAP（通过反射）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Supplier<SpellCard>> getSpellCardMap() {
        if (spellCardMap != null) {
            return spellCardMap;
        }
        
        try {
            Field mapField = TouhouSpellCards.class.getDeclaredField("MAP");
            mapField.setAccessible(true);
            spellCardMap = (Map<String, Supplier<SpellCard>>) mapField.get(null);
            LOGGER.info("Successfully accessed TouhouSpellCards MAP via reflection");
            return spellCardMap;
        } catch (Exception e) {
            LOGGER.error("Failed to access TouhouSpellCards MAP: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 创建符卡包装器
     * @param spellCardId 符卡ID
     * @return 符卡包装器，失败返回null
     */
    public static SpellCardWrapper createSpellCard(String spellCardId) {
        Map<String, Supplier<SpellCard>> map = getSpellCardMap();
        if (map == null) {
            LOGGER.error("SpellCard MAP is null, cannot create spell card");
            return null;
        }
        
        Supplier<SpellCard> supplier = map.get(spellCardId);
        if (supplier == null) {
            LOGGER.warn("No spell card found for id: {}", spellCardId);
            return null;
        }
        
        try {
            SpellCardWrapper wrapper = new SpellCardWrapper();
            wrapper.modelId = spellCardId;
            wrapper.card = supplier.get();
            return wrapper;
        } catch (Exception e) {
            LOGGER.error("Failed to create spell card {}: {}", spellCardId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查符卡是否存在
     */
    public static boolean hasSpellCard(String spellCardId) {
        Map<String, Supplier<SpellCard>> map = getSpellCardMap();
        return map != null && map.containsKey(spellCardId);
    }
    
    /**
     * 从物品堆栈获取对应的符卡ID
     * @param itemStack 符卡物品堆栈
     * @return 符卡ID，如果无对应符卡返回null
     */
    public static String getSpellCardIdFromItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        Item item = itemStack.getItem();
        String itemId = item.getDescriptionId();
        return ITEM_TO_SPELL_CARD.get(itemId);
    }
    
    /**
     * 从物品堆栈创建符卡包装器
     * @param itemStack 符卡物品堆栈
     * @return 符卡包装器，失败返回null
     */
    public static SpellCardWrapper createSpellCardFromItem(ItemStack itemStack) {
        String spellCardId = getSpellCardIdFromItem(itemStack);
        if (spellCardId == null) {
            return null;
        }
        
        return createSpellCard(spellCardId);
    }
    
    /**
     * 检查物品是否为符卡物品（可以转换为符卡）
     */
    public static boolean isSpellCardItem(ItemStack itemStack) {
        return getSpellCardIdFromItem(itemStack) != null;
    }
}

