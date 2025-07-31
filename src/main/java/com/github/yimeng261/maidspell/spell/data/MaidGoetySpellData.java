package com.github.yimeng261.maidspell.spell.data;

import com.Polarice3.Goety.api.magic.ISpell;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆Goety法术数据存储类
 * 集中管理每个女仆的法术相关状态和数据
 */
public class MaidGoetySpellData {
    
    // 全局数据存储，按女仆UUID映射
    private static final Map<UUID, MaidGoetySpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();
    
    // === 基本状态 ===
    private LivingEntity target;
    private ItemStack spellBook = ItemStack.EMPTY;
    
    // === 施法状态 ===
    private boolean isCasting = false;
    private int castingTime = 0;
    private int maxCastingTime = 0;
    private ISpell currentSpell = null;
    private boolean spellUsed = false;
    
    // === 蓄力法术状态 ===
    private int coolCounter = 0;
    private int shotsFired = 0;
    
    // === 聚晶切换状态 ===
    private ItemStack originalFocus = ItemStack.EMPTY;
    
    // === 冷却管理 ===
    private final Map<String, Integer> spellCooldowns = new HashMap<>();
    
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
    
    /**
     * 清理所有数据（用于服务器关闭等场景）
     */
    public static void clearAll() {
        MAID_DATA_MAP.clear();
    }
    
    /**
     * 获取当前存储的女仆数据数量
     */
    public static int getDataCount() {
        return MAID_DATA_MAP.size();
    }
    
    // === 基本状态管理 ===
    
    public LivingEntity getTarget() {
        return target;
    }
    
    public void setTarget(LivingEntity target) {
        this.target = target;
    }
    
    public ItemStack getSpellBook() {
        return spellBook;
    }
    
    public void setSpellBook(ItemStack spellBook) {
        this.spellBook = spellBook != null ? spellBook : ItemStack.EMPTY;
    }
    
    // === 施法状态管理 ===
    
    public boolean isCasting() {
        return isCasting;
    }
    
    public void setCasting(boolean casting) {
        this.isCasting = casting;
    }
    
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
    
    // === 聚晶状态管理 ===
    
    public ItemStack getOriginalFocus() {
        return originalFocus;
    }
    
    public void setOriginalFocus(ItemStack originalFocus) {
        this.originalFocus = originalFocus != null ? originalFocus : ItemStack.EMPTY;
    }
    
    // === 冷却管理 ===
    
    public Map<String, Integer> getSpellCooldowns() {
        return spellCooldowns;
    }
    
    public boolean isSpellOnCooldown(String spellId) {
        return spellCooldowns.getOrDefault(spellId, 0) > 0;
    }
    
    public void setSpellCooldown(String spellId, int cooldown) {
        if (cooldown <= 0) {
            spellCooldowns.remove(spellId);
        } else {
            spellCooldowns.put(spellId, cooldown);
        }
    }
    
    public void updateCooldowns() {
        spellCooldowns.replaceAll((spellId, cooldown) -> Math.max(0, cooldown - 20));
        spellCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
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
     * 重置聚晶状态
     */
    public void resetFocusState() {
        this.originalFocus = ItemStack.EMPTY;
    }
    
    /**
     * 完全重置（除了冷却数据）
     */
    public void reset() {
        this.target = null;
        this.spellBook = ItemStack.EMPTY;
        resetCastingState();
        resetFocusState();
    }
    
    /**
     * 初始化施法状态
     */
    public void initiateCastingState(ISpell spell, int duration) {
        this.currentSpell = spell;
        this.castingTime = 0;
        this.maxCastingTime = duration;
        this.isCasting = true;
    }
    
    /**
     * 设置法术冷却时间（兼容方法）
     */
    public void setCooldown(ISpell spell) {
        if (spell != null) {
            String spellId = spell.getClass().getSimpleName();
            setSpellCooldown(spellId, spell.defaultSpellCooldown());
        }
    }
} 