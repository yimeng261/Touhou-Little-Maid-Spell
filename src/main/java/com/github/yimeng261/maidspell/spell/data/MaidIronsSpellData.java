package com.github.yimeng261.maidspell.spell.data;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆铁魔法数据存储类
 * 集中管理每个女仆的铁魔法相关状态和数据
 */
public class MaidIronsSpellData {
    
    // 全局数据存储，按女仆UUID映射
    private static final Map<UUID, MaidIronsSpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();
    
    // === 基本状态 ===
    private LivingEntity target;
    private ItemStack spellBook = ItemStack.EMPTY;
    private ItemStack magicSword = ItemStack.EMPTY;
    private ItemStack staff = ItemStack.EMPTY;
    
    // === 施法状态 ===
    private boolean isCasting = false;
    private SpellData currentCastingSpell = null;
    private final MagicData magicData;
    
    // === 冷却管理 ===
    private final Map<String, Integer> spellCooldowns = new HashMap<>();
    
    // === 构造函数 ===
    private MaidIronsSpellData() {
        this.magicData = new MagicData(true); // true表示这是mob
    }
    
    // === 静态工厂方法 ===
    
    /**
     * 获取指定女仆的法术数据，如果不存在则创建新的
     */
    public static MaidIronsSpellData getOrCreate(UUID maidUuid) {
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidIronsSpellData());
    }

    public static MaidIronsSpellData getOrCreate(EntityMaid maidEntity) {
        UUID maidUuid = maidEntity.getUUID();
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidIronsSpellData());
    }
    
    /**
     * 获取指定女仆的法术数据，如果不存在返回null
     */
    public static MaidIronsSpellData get(UUID maidUuid) {
        return MAID_DATA_MAP.get(maidUuid);
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
    
    public ItemStack getMagicSword() {
        return magicSword;
    }
    
    public void setMagicSword(ItemStack magicSword) {
        this.magicSword = magicSword != null ? magicSword : ItemStack.EMPTY;
    }
    
    public ItemStack getStaff() {
        return staff;
    }
    
    public void setStaff(ItemStack staff) {
        this.staff = staff != null ? staff : ItemStack.EMPTY;
    }
    
    // === 施法状态管理 ===
    
    public boolean isCasting() {
        return isCasting;
    }
    
    public void setCasting(boolean casting) {
        this.isCasting = casting;
    }
    
    public SpellData getCurrentCastingSpell() {
        return currentCastingSpell;
    }
    
    public void setCurrentCastingSpell(SpellData spell) {
        this.currentCastingSpell = spell;
    }
    
    public MagicData getMagicData() {
        return magicData;
    }
    
    // === 冷却管理 ===
    
    /**
     * 检查法术是否在冷却中
     */
    public boolean isSpellOnCooldown(String spellId) {
        if (spellId == null) return true;
        int remainingCooldown = spellCooldowns.getOrDefault(spellId, 0);
        return remainingCooldown > 0;
    }
    
    /**
     * 设置法术冷却
     */
    public void setSpellCooldown(String spellId, int cooldownTicks) {
        if (spellId != null) {
            spellCooldowns.put(spellId, cooldownTicks);
        }
    }
    
    /**
     * 获取法术剩余冷却时间
     */
    public int getSpellCooldown(String spellId) {
        return spellCooldowns.getOrDefault(spellId, 0);
    }
    
    /**
     * 更新所有法术的冷却时间
     */
    public void updateCooldowns() {
        spellCooldowns.replaceAll((spellId, cooldown) -> Math.max(0, cooldown - 20));
        spellCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }
    
    /**
     * 重置施法状态
     */
    public void resetCastingState() {
        isCasting = false;
        currentCastingSpell = null;
        magicData.resetCastingState();
    }
    
    /**
     * 获取所有法术容器
     */
    public ItemStack[] getAllSpellContainers() {
        return new ItemStack[]{spellBook, magicSword, staff};
    }
    
    /**
     * 检查是否有任何法术容器
     */
    public boolean hasAnySpellContainer() {
        return !spellBook.isEmpty() || !magicSword.isEmpty() || !staff.isEmpty();
    }
    
    /**
     * 清理所有数据（女仆被移除时调用）
     */
    public void cleanup() {
        resetCastingState();
        target = null;
        spellBook = ItemStack.EMPTY;
        magicSword = ItemStack.EMPTY;
        staff = ItemStack.EMPTY;
        spellCooldowns.clear();
    }
} 