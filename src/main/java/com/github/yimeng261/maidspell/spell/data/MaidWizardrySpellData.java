package com.github.yimeng261.maidspell.spell.data;

import com.binaris.wizardry.api.content.spell.Spell;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆 ElectroblobsWizardryRedux 法术数据存储类
 * 集中管理每个女仆的法术相关状态和数据
 */
public class MaidWizardrySpellData extends IMaidSpellData {

    // 全局数据存储，按女仆UUID映射
    private static final Map<UUID, MaidWizardrySpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();

    // === 施法状态 ===
    private Spell currentSpell = null;
    private ItemStack currentSpellItem = ItemStack.EMPTY;
    private int castingTime = 0;
    private int maxCastingTime = 0;
    private int chargeupTime = 0;
    private boolean charged = false;

    // === 构造函数 ===
    private MaidWizardrySpellData() {
        // 私有构造函数，通过静态方法获取实例
    }

    // === 静态工厂方法 ===

    /**
     * 获取指定女仆的法术数据，如果不存在则创建新的
     */
    public static MaidWizardrySpellData getOrCreate(UUID maidUuid) {
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidWizardrySpellData());
    }

    public static MaidWizardrySpellData getOrCreate(EntityMaid maidEntity) {
        UUID maidUuid = maidEntity.getUUID();
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidWizardrySpellData());
    }

    /**
     * 获取指定女仆的法术数据，如果不存在返回null
     */
    public static MaidWizardrySpellData get(UUID maidUuid) {
        return MAID_DATA_MAP.get(maidUuid);
    }

    /**
     * 移除指定女仆的法术数据（当女仆被删除时调用）
     */
    public static void remove(UUID maidUuid) {
        MAID_DATA_MAP.remove(maidUuid);
    }

    // === 基本状态管理 ===

    public Spell getCurrentSpell() {
        return currentSpell;
    }

    public void setCurrentSpell(Spell currentSpell) {
        this.currentSpell = currentSpell;
        if (currentSpell != null) {
            setCurrentSpellId(currentSpell.getLocation().toString());
        }
    }

    public ItemStack getCurrentSpellItem() {
        return currentSpellItem;
    }

    public void setCurrentSpellItem(ItemStack currentSpellItem) {
        this.currentSpellItem = currentSpellItem;
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

    public int getChargeupTime() {
        return chargeupTime;
    }

    public void setChargeupTime(int chargeupTime) {
        this.chargeupTime = chargeupTime;
    }

    public boolean isCharged() {
        return charged;
    }

    public void setCharged(boolean charged) {
        this.charged = charged;
    }

    // === 状态重置方法 ===

    /**
     * 重置施法状态（保留冷却数据）
     */
    @Override
    public void resetCastingState() {
        this.setCasting(false);
        this.castingTime = 0;
        this.maxCastingTime = 0;
        this.chargeupTime = 0;
        this.charged = false;
        this.currentSpell = null;
        this.currentSpellItem = ItemStack.EMPTY;
    }

    /**
     * 初始化施法状态
     */
    public void initiateCastingState(Spell spell, ItemStack spellItem, int chargeupTime, int maxCastingTime) {
        setCurrentSpell(spell);
        setCurrentSpellItem(spellItem);
        this.castingTime = 0;
        this.chargeupTime = chargeupTime;
        this.maxCastingTime = maxCastingTime;
        this.charged = false;
        this.setCasting(true);
    }
}
