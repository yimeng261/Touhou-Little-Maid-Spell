package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Youkai-Homecoming弹幕物品数据管理类
 * 管理女仆使用弹幕物品和法术卡的状态
 */
public class MaidYHSpellData extends IMaidSpellData {
    
    // 全局女仆数据存储
    private static final ConcurrentHashMap<UUID, MaidYHSpellData> DATA_MAP = new ConcurrentHashMap<>();

    
    // 当前目标
    private LivingEntity target;
    
    // 施法状态
    private boolean isCasting = false;
    private int castingTime = 0;
    private int maxCastingTime = 0;
    
    // 冷却系统
    private final ConcurrentHashMap<String, Integer> cooldowns = new ConcurrentHashMap<>();
    
    // 弹幕发射计数
    private int shotsFired = 0;
    private int maxShots = 1;

    
    /**
     * 私有构造函数
     */
    private MaidYHSpellData(UUID uuid) {
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
    public void setTarget(LivingEntity target) {
        this.target = target;
    }
    
    @Override
    public LivingEntity getTarget() {
        return target;
    }
    
    @Override
    public boolean isCasting() {
        return false;
    }

    @Override
    public void setCasting(boolean casting) {
    }

    @Override
    public boolean isSpellOnCooldown(String spellId) {
        return false;
    }


    @Override
    public int getSpellCooldown(String spellId) {
        return 0;
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
     * 发射一发弹幕
     */
    public void fireDanmaku() {
        if (shotsFired < maxShots) {
            shotsFired++;
        }
    }
    
    /**
     * 检查是否完成施法
     */
    public boolean isSpellComplete() {
        return shotsFired >= maxShots || castingTime >= maxCastingTime;
    }
    
    /**
     * 重置施法状态
     */
    public void resetCastingState() {
        this.isCasting = false;
        this.castingTime = 0;
        this.maxCastingTime = 0;
        this.shotsFired = 0;
        this.maxShots = 1;
    }
    

    
    /**
     * 安全地比较两个ItemStack是否相同（处理null情况）
     */
    private boolean isSameItemStackSafely(ItemStack stack1, ItemStack stack2) {
        // 处理null情况
        if (stack1 == null && stack2 == null) {
            return true;
        }
        if (stack1 == null || stack2 == null) {
            // 一个为null，另一个不为null，检查非null的是否为空
            ItemStack nonNull = stack1 != null ? stack1 : stack2;
            return nonNull.isEmpty();
        }
        
        // 都不为null，使用原始方法
        return ItemStack.isSameItemSameTags(stack1, stack2);
    }


    /**
     * 检查目标是否有效
     */
    public boolean isValidTarget() {
        return target != null && target.isAlive();
    }
    
    /**
     * 清理无效目标
     */
    public void cleanupTarget() {
        if (target != null && !target.isAlive()) {
            target = null;
        }
    }

}
