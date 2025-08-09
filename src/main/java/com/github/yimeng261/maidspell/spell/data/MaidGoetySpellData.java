package com.github.yimeng261.maidspell.spell.data;

import com.Polarice3.Goety.api.magic.ISpell;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.AbstractSpellData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆Goety法术数据存储类
 * 集中管理每个女仆的法术相关状态和数据
 */
public class MaidGoetySpellData extends AbstractSpellData {
    
    // 全局数据存储，按女仆UUID映射
    private static final Map<UUID, MaidGoetySpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();

    
    // === 施法状态 ===
    private int castingTime = 0;
    private int maxCastingTime = 0;
    private ISpell currentSpell = null;
    private boolean spellUsed = false;
    
    // === 蓄力法术状态 ===
    private int coolCounter = 0;
    private int shotsFired = 0;

    
    // === 构造函数 ===
    private MaidGoetySpellData() {
        // 私有构造函数，通过静态方法获取实例
    }
    
    // === 静态工厂方法 ===
    
    /**
     * 获取指定女仆的法术数据，如果不存在则创建新的
     */
    public static MaidGoetySpellData getOrCreate(UUID maidUuid) {
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidGoetySpellData());
    }

    public static MaidGoetySpellData getOrCreate(EntityMaid maidEntity) {
        UUID maidUuid = maidEntity.getUUID();
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidGoetySpellData());
    }
    
    /**
     * 获取指定女仆的法术数据，如果不存在返回null
     */
    public static MaidGoetySpellData get(UUID maidUuid) {
        return MAID_DATA_MAP.get(maidUuid);
    }

    public boolean spellUsed() {
        return spellUsed;
    }

    public void setSpellUsed(boolean spellUsed) {
        this.spellUsed = spellUsed;
    }
    
    /**
     * 移除指定女仆的法术数据（当女仆被删除时调用）
     */
    public static void remove(UUID maidUuid) {
        MAID_DATA_MAP.remove(maidUuid);
    }
    

    
    // === 基本状态管理 ===
    
    public int getCastingTime() {
        return castingTime;
    }
    
    public void setCastingTime(int castingTime) {
        this.castingTime = castingTime;
    }
    
    public void incrementCastingTime() {
        this.castingTime++;
    }
    
    public int getMaxCastingTime() {
        return maxCastingTime;
    }
    
    public void setMaxCastingTime(int maxCastingTime) {
        this.maxCastingTime = maxCastingTime;
    }
    
    public ISpell getCurrentSpell() {
        return currentSpell;
    }
    
    public void setCurrentSpell(ISpell currentSpell) {
        this.currentSpell = currentSpell;
    }
    
    // === 蓄力法术状态管理 ===
    
    public int getCoolCounter() {
        return coolCounter;
    }
    
    public void setCoolCounter(int coolCounter) {
        this.coolCounter = coolCounter;
    }
    
    public void incrementCoolCounter() {
        this.coolCounter++;
    }
    
    public int getShotsFired() {
        return shotsFired;
    }
    
    public void setShotsFired(int shotsFired) {
        this.shotsFired = shotsFired;
    }
    
    public void incrementShotsFired() {
        this.shotsFired++;
    }

    // === 状态重置方法 ===
    
    /**
     * 重置施法状态（保留冷却数据）
     */
    public void resetCastingState() {
        this.isCasting = false;
        this.castingTime = 0;
        this.maxCastingTime = 0;
        this.currentSpell = null;
        this.coolCounter = 0;
        this.shotsFired = 0;
        this.spellUsed = false;
    }
    
    /**
     * 重置蓄力计数器
     */
    public void resetChargingCounters() {
        this.coolCounter = 0;
        this.shotsFired = 0;
    }
    

    
    /**
     * 初始化施法状态
     */
    public void initiateCastingState(ISpell spell, int duration) {
        setCurrentSpell(spell);
        this.castingTime = 0;
        this.maxCastingTime = duration;
        this.isCasting = true;
    }

} 