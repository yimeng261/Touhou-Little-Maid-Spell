package com.github.yimeng261.maidspell.spell.helpers;

import dev.xkmc.youkaishomecoming.content.spell.game.TouhouSpellCards;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCardWrapper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;
import java.util.*;
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
    
    // 没有对应物品的符卡ID列表（用于随机抽取）
    private static final List<String> ITEM_LESS_SPELL_CARDS = Arrays.asList(
        "touhou_little_maid:cirno",
        "touhou_little_maid:sunny_milk",
        "touhou_little_maid:luna_child",
        "touhou_little_maid:star_sapphire",
        "touhou_little_maid:doremy_sweet",
        "touhou_little_maid:kisin_sagume",
        "touhou_little_maid:eternity_larva"
    );
    
    // 用于自定义符卡的物品标识
    private static final Set<String> CUSTOM_SPELL_ITEMS = new HashSet<>(Arrays.asList(
        "item.youkaishomecoming.custom_spell_ring",
        "item.youkaishomecoming.custom_spell_homing"
    ));
    
    // 随机数生成器
    private static final Random RANDOM = new Random();
    
    static {
        // 初始化符卡物品到符卡ID的映射
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_reimu", "touhou_little_maid:hakurei_reimu");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_sanae", "touhou_little_maid:kochiya_sanae");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_marisa", "touhou_little_maid:kirisame_marisa");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_mystia", "touhou_little_maid:mystia_lorelei");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_remilia", "touhou_little_maid:remilia_scarlet");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_koishi", "touhou_little_maid:komeiji_koishi");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_yukari_butterfly", "touhou_little_maid:yukari_yakumo");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_yukari_laser", "touhou_little_maid:yukari_yakumo");
        ITEM_TO_SPELL_CARD.put("item.youkaishomecoming.spell_clownpiece", "touhou_little_maid:clownpiece");
        // 自定义符卡物品不直接映射到固定ID，而是在使用时随机选择
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
     * 从没有对应物品的符卡列表中随机选择一个符卡ID
     * @return 随机选择的符卡ID
     */
    public static String getRandomItemlessSpellCard() {
        if (ITEM_LESS_SPELL_CARDS.isEmpty()) {
            LOGGER.warn("Item-less spell card list is empty!");
            return null;
        }
        
        int index = RANDOM.nextInt(ITEM_LESS_SPELL_CARDS.size());
        String spellCardId = ITEM_LESS_SPELL_CARDS.get(index);
        LOGGER.debug("Randomly selected spell card: {}", spellCardId);
        return spellCardId;
    }
    
    /**
     * 从物品堆栈获取对应的符卡ID
     * 如果是自定义符卡物品（custom_spell_ring或custom_spell_homing），则随机返回一个没有对应物品的符卡ID
     * @param itemStack 符卡物品堆栈
     * @return 符卡ID，如果无对应符卡返回null
     */
    public static String getSpellCardIdFromItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        Item item = itemStack.getItem();
        String itemId = item.getDescriptionId();
        
        // 检查是否为自定义符卡物品
        if (CUSTOM_SPELL_ITEMS.contains(itemId)) {
            LOGGER.info("Custom spell item detected: {}, selecting random spell card", itemId);
            return getRandomItemlessSpellCard();
        }
        
        // 返回普通符卡映射
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
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        String itemId = itemStack.getItem().getDescriptionId();
        
        // 检查是否为自定义符卡物品或普通符卡物品
        return CUSTOM_SPELL_ITEMS.contains(itemId) || ITEM_TO_SPELL_CARD.containsKey(itemId);
    }
}

