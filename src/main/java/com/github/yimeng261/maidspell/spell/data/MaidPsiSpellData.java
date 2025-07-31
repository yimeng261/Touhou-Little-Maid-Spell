package com.github.yimeng261.maidspell.spell.data;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆Psi法术数据管理类
 * 管理每个女仆的CAD、法术状态和冷却时间
 */
public class MaidPsiSpellData {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 全局女仆数据存储
    private static final Map<UUID, MaidPsiSpellData> MAID_DATA = new ConcurrentHashMap<>();
    
    // 女仆UUID
    private final UUID maidUUID;
    
    // CAD物品
    private ItemStack cad = ItemStack.EMPTY;
    
    // 当前目标
    private LivingEntity target;
    
    // 施法状态
    private boolean isCasting = false;
    private Object currentSpell; // 使用Object避免直接依赖Psi类
    private int castingTicks = 0;
    
    // 法术冷却时间映射
    private final Map<String, Integer> spellCooldowns = new HashMap<>();

    /**
     * 私有构造函数
     */
    private MaidPsiSpellData(UUID maidUUID) {
        this.maidUUID = maidUUID;
    }

    /**
     * 获取或创建女仆的Psi法术数据
     */
    public static MaidPsiSpellData getOrCreate(UUID maidUUID) {
        return MAID_DATA.computeIfAbsent(maidUUID, MaidPsiSpellData::new);
    }

    /**
     * 移除女仆的数据
     */
    public static void remove(UUID maidUUID) {
        MAID_DATA.remove(maidUUID);
    }

    // Getters and Setters
    
    public UUID getMaidUUID() {
        return maidUUID;
    }

    public ItemStack getCAD() {
        return cad;
    }

    public void setCAD(ItemStack cad) {
        this.cad = cad != null ? cad : ItemStack.EMPTY;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public boolean isCasting() {
        return isCasting;
    }

    public void setCasting(boolean casting) {
        this.isCasting = casting;
    }

    public Object getCurrentSpell() {
        return currentSpell;
    }

    public void setCurrentSpell(Object spell) {
        this.currentSpell = spell;
    }

    public int getCastingTicks() {
        return castingTicks;
    }

    public void setCastingTicks(int ticks) {
        this.castingTicks = ticks;
    }

    /**
     * 设置法术冷却时间
     */
    public void setSpellCooldown(String spellId, int cooldownTicks) {
        if (spellId != null && cooldownTicks > 0) {
            spellCooldowns.put(spellId, cooldownTicks);
        }
    }

    /**
     * 检查法术是否在冷却中
     */
    public boolean isSpellOnCooldown(String spellId) {
        if (spellId == null) {
            return false;
        }
        Integer cooldown = spellCooldowns.get(spellId);
        return cooldown != null && cooldown > 0;
    }

    /**
     * 获取法术剩余冷却时间
     */
    public int getSpellCooldown(String spellId) {
        if (spellId == null) {
            return 0;
        }
        return spellCooldowns.getOrDefault(spellId, 0);
    }

    /**
     * 更新所有法术的冷却时间
     */
    public void updateCooldowns() {
        spellCooldowns.entrySet().removeIf(entry -> {
            int newCooldown = entry.getValue() - 1;
            if (newCooldown <= 0) {
                return true; // 移除已完成冷却的法术
            } else {
                entry.setValue(newCooldown);
                return false;
            }
        });
    }

    /**
     * 重置施法状态
     */
    public void resetCastingState() {
        this.isCasting = false;
        this.currentSpell = null;
        this.castingTicks = 0;
    }

    /**
     * 清理所有数据
     */
    public void clear() {
        this.cad = ItemStack.EMPTY;
        this.target = null;
        this.resetCastingState();
        this.spellCooldowns.clear();
    }
} 