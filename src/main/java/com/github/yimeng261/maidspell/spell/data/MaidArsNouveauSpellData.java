package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 女仆新生魔艺数据存储类
 * 集中管理每个女仆的新生魔艺相关状态和数据
 */
public class MaidArsNouveauSpellData extends IMaidSpellData {
    
    // 全局数据存储，按女仆UUID映射
    private static final Map<UUID, MaidArsNouveauSpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();
    
    // === 施法状态 ===
    private int castingTicks = 0;
    private Spell currentSpell = null;
    // === 施法参数 ===
    private static final int CASTING_DURATION = 10; // 新生魔艺法术施法时间（tick）
    
    // === 构造函数 ===
    private MaidArsNouveauSpellData() {
        // 私有构造函数，通过静态方法获取实例
    }
    
    // === 静态工厂方法 ===
    
    /**
     * 获取指定女仆的法术数据，如果不存在则创建新的
     */
    public static MaidArsNouveauSpellData getOrCreate(UUID maidUuid) {
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidArsNouveauSpellData());
    }

    public static MaidArsNouveauSpellData getOrCreate(EntityMaid maidEntity) {
        UUID maidUuid = maidEntity.getUUID();
        return MAID_DATA_MAP.computeIfAbsent(maidUuid, k -> new MaidArsNouveauSpellData());
    }
    
    /**
     * 获取指定女仆的法术数据，如果不存在返回null
     */
    public static MaidArsNouveauSpellData get(UUID maidUuid) {
        return MAID_DATA_MAP.get(maidUuid);
    }
    
    /**
     * 移除指定女仆的法术数据（当女仆被删除时调用）
     */
    public static void remove(UUID maidUuid) {
        MAID_DATA_MAP.remove(maidUuid);
    }

    public int getCastingTicks() {
        return castingTicks;
    }
    
    public void setCastingTicks(int castingTicks) {
        this.castingTicks = castingTicks;
    }
    
    public void incrementCastingTicks() {
        this.castingTicks++;
    }
    
    public Spell getCurrentSpell() {
        return currentSpell;
    }
    
    public void setCurrentSpell(Spell spell) {
        this.currentSpell = spell;
    }

    // === 施法参数 ===
    
    public int getCastingDuration() {
        return CASTING_DURATION;
    }
    
    /**
     * 检查施法是否完成
     */
    public boolean isCastingComplete() {
        return castingTicks >= CASTING_DURATION;
    }
    
    /**
     * 重置施法状态
     */
    public void resetCastingState() {
        setCasting(false);
        castingTicks = 0;
        currentSpell = null;
        // 保持currentCaster，因为它与spellBook绑定
    }
    

} 