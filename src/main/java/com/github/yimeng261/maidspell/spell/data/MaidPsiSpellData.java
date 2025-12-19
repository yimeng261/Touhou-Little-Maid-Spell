package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆Psi法术数据管理类
 * 管理每个女仆的CAD、法术状态和冷却时间
 */
public class MaidPsiSpellData extends IMaidSpellData {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 全局女仆数据存储
    private static final Map<UUID, MaidPsiSpellData> MAID_DATA = new ConcurrentHashMap<>();


    // 当前目标
    
    // 施法状态
    private Object currentSpell; // 使用Object避免直接依赖Psi类
    private int castingTicks = 0;

    private MaidPsiSpellData(UUID maidUUID) {
        // 女仆UUID
    }

    public static MaidPsiSpellData getOrCreate(UUID maidUUID) {
        return MAID_DATA.computeIfAbsent(maidUUID, MaidPsiSpellData::new);
    }

    public static void remove(UUID maidUUID) {
        MAID_DATA.remove(maidUUID);
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
     * 重置施法状态
     */
    public void resetCastingState() {
        this.setCasting(false);
        this.currentSpell = null;
        this.castingTicks = 0;
    }

} 