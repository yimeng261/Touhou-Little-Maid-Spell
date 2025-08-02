package com.github.yimeng261.maidspell.spell.data;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆拔刀剑数据管理类 - 简化版本
 */
public class MaidSlashBladeData {
    private static final Map<UUID, MaidSlashBladeData> DATA_MAP = new ConcurrentHashMap<>();

    private final UUID maidUUID;
    private ItemStack slashBlade = ItemStack.EMPTY;
    private WeakReference<LivingEntity> target = new WeakReference<>(null);
    
    // 施法状态
    private boolean isCasting = false;
    private long saExecutionStartTime = 0;
    private ResourceLocation lastComboState = null;

    private int targetUseTime = 0;
    
    // SA冷却
    private int cooldown = 0;


    private MaidSlashBladeData(UUID maidUUID) {
        this.maidUUID = maidUUID;
    }
    
    public static MaidSlashBladeData getOrCreate(UUID maidUUID) {
        return DATA_MAP.computeIfAbsent(maidUUID, MaidSlashBladeData::new);
    }
    
    public static void remove(UUID maidUUID) {
        DATA_MAP.remove(maidUUID);
    }
    public void setSlashBlade(ItemStack slashBlade) { 
        this.slashBlade = slashBlade != null ? slashBlade.copy() : ItemStack.EMPTY; 
    }
    
    public LivingEntity getTarget() { return target.get(); }
    public void setTarget(LivingEntity target) { this.target = new WeakReference<>(target); }
    
    // 施法状态
    public boolean isCasting() { return isCasting; }
    public void setCasting(boolean casting) { this.isCasting = casting; }
    public void setSAExecutionStartTime(long time) { this.saExecutionStartTime = time; }
    public long getSAExecutionStartTime() { return saExecutionStartTime; }
    
    public int getTargetUseTime() { return targetUseTime; }
    public void setTargetUseTime(int time) { this.targetUseTime = time; }
    
    public int getCooldown() { return cooldown; }
    public void setCooldown(int time) { this.cooldown = time; }
    public Boolean isOnCooldown() {return cooldown > 0;}
    
    public ResourceLocation getLastComboState() { return lastComboState; }
    public void setLastComboState(ResourceLocation lastComboState) { this.lastComboState = lastComboState; }

    
    public void updateCooldowns() {
        cooldown-=20;
    }

    
    public void reset() {
        this.slashBlade = ItemStack.EMPTY;
        this.target = new WeakReference<>(null);
        this.isCasting = false;
        this.saExecutionStartTime = 0;
        this.targetUseTime = 0;
        this.lastComboState = null;
    }
} 