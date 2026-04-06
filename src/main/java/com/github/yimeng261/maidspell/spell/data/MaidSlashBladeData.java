package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆拔刀剑数据管理类 - 简化版本
 */
public class MaidSlashBladeData extends IMaidSpellData {
    private static final Map<UUID, MaidSlashBladeData> DATA_MAP = new ConcurrentHashMap<>();

    
    // 施法状态
    private long saExecutionStartTime = 0;
    private ResourceLocation lastComboState = null;

    private int targetUseTime = 0;
    
    // SA冷却
    private int cooldown = 0;
    
    // 冲刺技能计数器 - 用于保证每3次必有一次冲刺
    private int nonDashSkillCount = 0;


    private MaidSlashBladeData(UUID maidUUID) {
    }
    
    public static MaidSlashBladeData getOrCreate(UUID maidUUID) {
        return DATA_MAP.computeIfAbsent(maidUUID, MaidSlashBladeData::new);
    }
    
    public static void remove(UUID maidUUID) {
        DATA_MAP.remove(maidUUID);
    }
    
    // 施法状态
    public void setSAExecutionStartTime(long time) { this.saExecutionStartTime = time; }
    public long getSAExecutionStartTime() { return saExecutionStartTime; }
    
    public int getTargetUseTime() { return targetUseTime; }
    public void setTargetUseTime(int time) { this.targetUseTime = time; }
    
    public int getCooldown() { return cooldown; }
    public void setCooldown(int time) { this.cooldown = time; }
    public Boolean isOnCooldown() {return cooldown > 0;}
    
    public ResourceLocation getLastComboState() { return lastComboState; }
    public void setLastComboState(ResourceLocation lastComboState) { this.lastComboState = lastComboState; }
    
    // 冲刺计数器管理
    public int getNonDashSkillCount() { return nonDashSkillCount; }
    public void incrementNonDashSkillCount() { this.nonDashSkillCount++; }
    public void resetNonDashSkillCount() { this.nonDashSkillCount = 0; }

    
    public void updateCooldowns() {
        cooldown-=20;
    }

    @Override
    public void removeSpellBook(ItemStack spellBook){
        for(ItemStack oldSpellBook : spellBooks){
            if(ItemStack.isSameItem(oldSpellBook, spellBook)){
                spellBookKinds.remove(spellBook.getItem().getClass());
                spellBooks.remove(oldSpellBook);
                return;
            }
        }
    }

    
    public void reset() {
        this.target = null;
        this.isCasting = false;
        this.saExecutionStartTime = 0;
        this.targetUseTime = 0;
        this.lastComboState = null;
    }
} 