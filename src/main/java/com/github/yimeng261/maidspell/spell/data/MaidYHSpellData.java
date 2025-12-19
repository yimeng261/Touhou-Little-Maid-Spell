package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCardWrapper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Youkai-Homecoming弹幕物品数据管理类
 * 管理女仆使用弹幕物品和法术卡的状态
 */
public class MaidYHSpellData extends IMaidSpellData {
    
    // 全局女仆数据存储
    private static final ConcurrentHashMap<UUID, MaidYHSpellData> DATA_MAP = new ConcurrentHashMap<>();

    private int castingTime = 0;
    private final int maxCastingTime = 400;
    private SpellCardWrapper activeSpellCard = null;
    
    /**
     * 私有构造函数
     */
    private MaidYHSpellData(UUID uuid) {
    }

    @Override
    protected boolean canAddSpellBook(ItemStack spellBook){
        return true;
    }

    /**
     * 获取或创建女仆的弹幕数据
     */
    public static MaidYHSpellData getOrCreate(UUID maidUUID) {
        return DATA_MAP.computeIfAbsent(maidUUID, MaidYHSpellData::new);
    }
    
    /**
     * 移除女仆数据
     */
    public static void remove(UUID maidUUID) {
        DATA_MAP.remove(maidUUID);
    }
    
    @Override
    public LivingEntity getTarget() {
        return target;
    }
    
    @Override
    public boolean isCasting() {
        return isCasting;
    }

    
    /**
     * 更新施法状态
     */
    public void updateCasting() {
        if (isCasting) {
            castingTime++;
        }
    }

    /**
     * 检查是否完成施法
     */
    public boolean isSpellComplete() {
        return castingTime >= maxCastingTime;
    }
    
    /**
     * 重置施法状态
     */
    public void resetCastingState() {
        this.isCasting = false;
        this.castingTime = 0;
    }


    /**
     * 检查目标是否有效
     */
    public boolean isValidTarget() {
        return target != null && target.isAlive();
    }

    
    // === 符卡系统方法 ===
    
    /**
     * 激活符卡
     * @param spellCard 符卡包装器
     */
    public void activateSpellCard(SpellCardWrapper spellCard) {
        this.isCasting = true;
        this.activeSpellCard = spellCard;
        
        // 重置符卡状态
        if (spellCard != null && spellCard.card != null) {
            spellCard.card.reset();
        }
    }
    
    /**
     * 获取当前激活的符卡
     */
    @Nullable
    public SpellCardWrapper getActiveSpellCard() {
        return activeSpellCard;
    }
    
    /**
     * 检查是否有激活的符卡
     */
    public boolean hasActiveSpellCard() {
        return activeSpellCard != null;
    }
    
    /**
     * 停用符卡
     */
    public void deactivateSpellCard() {
        if (activeSpellCard != null && activeSpellCard.card != null) {
            activeSpellCard.card.reset();
        }
        activeSpellCard = null;
    }
}
