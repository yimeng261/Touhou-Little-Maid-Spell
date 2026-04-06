package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆铁魔法数据存储类
 * 集中管理每个女仆的铁魔法相关状态和数据
 */
public class MaidIronsSpellData extends IMaidSpellData {
    
    // 全局数据存储，按女仆UUID映射
    private static final Map<UUID, MaidIronsSpellData> MAID_DATA_MAP = new ConcurrentHashMap<>();
    
    // === 基本状态 ===
    private LivingEntity origin_target = null;
    
    // === 施法状态 ===
    private SpellData currentCastingSpell = null;
    private final MagicData magicData;
    
    // === CastSource 缓存（避免每次施法时重复扫描容器）===
    private CastSource cachedCastSource = null;

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

    public void switchTargetToOwner(EntityMaid maid) {
        origin_target = getTarget();
        setTarget(maid.getOwner());
    }

    public void switchTargetToOrigin(EntityMaid maid) {
        setTarget(origin_target);
    }

    public LivingEntity getOriginTarget() {
        return origin_target;
    }
    
    public void setOriginTarget(LivingEntity originTarget) {
        this.origin_target = originTarget;
    }
    
    // === 施法状态管理 ===
    
    public SpellData getCurrentCastingSpell() {
        return currentCastingSpell;
    }
    
    public void setCurrentCastingSpell(SpellData spell) {
        this.currentCastingSpell = spell;
        if(spell != null && spell.getSpell() != null) {
            setCurrentSpellId(spell.getSpell().getSpellId());
        }
    }
    
    // === CastSource 缓存管理 ===
    
    /**
     * 获取缓存的 CastSource
     * @return 缓存的 CastSource，如果没有缓存返回 null
     */
    public CastSource getCachedCastSource() {
        return cachedCastSource;
    }
    
    /**
     * 设置 CastSource 缓存（在开始施法时调用）
     * @param castSource 施法来源
     */
    public void setCachedCastSource(CastSource castSource) {
        this.cachedCastSource = castSource;
    }
    
    /**
     * 清除 CastSource 缓存（在施法结束时调用）
     */
    public void clearCachedCastSource() {
        this.cachedCastSource = null;
    }
    
    public MagicData getMagicData() {
        return magicData;
    }
    
    // === 冷却管理 ===

    /**
     * 重置施法状态
     */
    @Override
    public void resetCastingState() {
        setCasting(false);
        currentCastingSpell = null;
        clearCachedCastSource();
        magicData.resetCastingState();
    }

} 