package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
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
    private boolean normalComboCasting = false;
    private int lastTargetEntityId = -1;
    private float lastObservedTargetHealth = Float.NaN;
    private int attackSequence = 0;
    private final List<Integer> pendingDirectSkillEntityIds = new ArrayList<>();
    private int pendingDirectSkillTargetId = -1;
    private float pendingDirectSkillDamage = 0.0F;
    private long pendingDirectSkillExpiryTime = 0L;
    private String pendingDirectSkillName = null;

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
    public boolean isNormalComboCasting() { return normalComboCasting; }
    public void setNormalComboCasting(boolean normalComboCasting) { this.normalComboCasting = normalComboCasting; }
    public int getLastTargetEntityId() { return lastTargetEntityId; }
    public void setLastTargetEntityId(int lastTargetEntityId) { this.lastTargetEntityId = lastTargetEntityId; }
    public float getLastObservedTargetHealth() { return lastObservedTargetHealth; }
    public void setLastObservedTargetHealth(float lastObservedTargetHealth) { this.lastObservedTargetHealth = lastObservedTargetHealth; }
    public int getAttackSequence() { return attackSequence; }
    public void incrementAttackSequence() { this.attackSequence++; }
    public void resetAttackSequence() { this.attackSequence = 0; }
    public List<Integer> getPendingDirectSkillEntityIds() { return pendingDirectSkillEntityIds; }
    public int getPendingDirectSkillTargetId() { return pendingDirectSkillTargetId; }
    public float getPendingDirectSkillDamage() { return pendingDirectSkillDamage; }
    public long getPendingDirectSkillExpiryTime() { return pendingDirectSkillExpiryTime; }
    public String getPendingDirectSkillName() { return pendingDirectSkillName; }
    public boolean hasPendingDirectSkillHit() { return !pendingDirectSkillEntityIds.isEmpty() && pendingDirectSkillTargetId >= 0; }
    public void armPendingDirectSkillHit(List<Integer> entityIds, int targetId, float damage, long expiryTime, String skillName) {
        pendingDirectSkillEntityIds.clear();
        pendingDirectSkillEntityIds.addAll(entityIds);
        pendingDirectSkillTargetId = targetId;
        pendingDirectSkillDamage = damage;
        pendingDirectSkillExpiryTime = expiryTime;
        pendingDirectSkillName = skillName;
    }
    public void clearPendingDirectSkillHit() {
        pendingDirectSkillEntityIds.clear();
        pendingDirectSkillTargetId = -1;
        pendingDirectSkillDamage = 0.0F;
        pendingDirectSkillExpiryTime = 0L;
        pendingDirectSkillName = null;
    }
    @Override
    public void resetCastingState() {
        this.isCasting = false;
        this.saExecutionStartTime = 0;
        this.targetUseTime = 0;
        this.lastComboState = null;
        this.normalComboCasting = false;
        this.clearPendingDirectSkillHit();
    }

    // 冲刺计数器管理
    public int getNonDashSkillCount() { return nonDashSkillCount; }
    public void incrementNonDashSkillCount() { this.nonDashSkillCount++; }
    public void resetNonDashSkillCount() { this.nonDashSkillCount = 0; }


    @Override
    public void updateCooldowns() {
        cooldown -= 20;
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
        this.lastTargetEntityId = -1;
        this.lastObservedTargetHealth = Float.NaN;
        resetAttackSequence();
        resetCastingState();
    }
}
