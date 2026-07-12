package com.github.yimeng261.maidspell.spell.data;

import com.github.yimeng261.maidspell.api.IMaidSpellData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
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
    private String playerTargetWhitelistSpellId = null;
    private boolean currentSpellCanTargetPlayer = false;
    private int cachedSpellWhiteListSlot = -1;
    private MaidRecastSession recastSession = null;

    // === 构造函数 ===
    private MaidIronsSpellData() {
        // Use player-style data without a ServerPlayer so Iron's recast lookup remains persistent for maids.
        this.magicData = new MagicData(false);
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

    public static void clearAll() {
        MAID_DATA_MAP.clear();
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
        } else {
            setCurrentSpellId(null);
        }
    }

    public void setCurrentSpellCanTargetPlayer(String spellId, boolean canTargetPlayer) {
        this.playerTargetWhitelistSpellId = spellId;
        this.currentSpellCanTargetPlayer = canTargetPlayer;
    }

    public boolean canCurrentSpellTargetPlayer(String spellId) {
        return currentSpellCanTargetPlayer && spellId != null && spellId.equals(playerTargetWhitelistSpellId);
    }

    public void clearCurrentSpellPlayerTargetState() {
        this.playerTargetWhitelistSpellId = null;
        this.currentSpellCanTargetPlayer = false;
    }

    public int getCachedSpellWhiteListSlot() {
        return cachedSpellWhiteListSlot;
    }

    public void setCachedSpellWhiteListSlot(int slot) {
        this.cachedSpellWhiteListSlot = slot;
    }

    public void clearCachedSpellWhiteListSlot() {
        this.cachedSpellWhiteListSlot = -1;
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

    public void bindSyncedSpellData(EntityMaid maid) {
        magicData.setSyncedData(new SyncedSpellData(maid));
    }

    public void releaseSyncedSpellData() {
        magicData.setSyncedData(new SyncedSpellData(-1));
    }

    public MaidRecastSession getRecastSession() {
        return recastSession;
    }

    public boolean hasRecastSession() {
        return recastSession != null && !recastSession.isFinished();
    }

    public void startRecastSession(SpellData spell, CastSource castSource, RecastInstance recastInstance, int intervalTicks) {
        this.recastSession = new MaidRecastSession(spell, castSource, recastInstance, intervalTicks);
        setCurrentCastingSpell(spell);
        setCachedCastSource(castSource);
        setCasting(true);
    }

    public void clearRecastSession() {
        this.recastSession = null;
    }
    
    // === 冷却管理 ===

    /**
     * 重置施法状态
     */
    @Override
    public void resetCastingState() {
        super.resetCastingState();
        origin_target = null;
        currentCastingSpell = null;
        clearCurrentSpellPlayerTargetState();
        clearCachedCastSource();
        clearRecastSession();
        magicData.resetCastingState();
    }

    public static class MaidRecastSession {
        private final SpellData spell;
        private final CastSource castSource;
        private final RecastInstance recastInstance;
        private final int intervalTicks;
        private int ticksUntilNextRecast;
        private int remainingRecasts;
        private boolean finished;

        private MaidRecastSession(SpellData spell, CastSource castSource, RecastInstance recastInstance, int intervalTicks) {
            this.spell = spell;
            this.castSource = castSource;
            this.recastInstance = recastInstance;
            this.intervalTicks = Math.max(1, intervalTicks);
            this.ticksUntilNextRecast = this.intervalTicks;
            this.remainingRecasts = recastInstance == null ? 0 : Math.max(0, recastInstance.getRemainingRecasts());
        }

        public SpellData getSpell() {
            return spell;
        }

        public CastSource getCastSource() {
            return castSource;
        }

        public RecastInstance getRecastInstance() {
            return recastInstance;
        }

        public ICastDataSerializable getCastData() {
            return recastInstance == null ? null : recastInstance.getCastData();
        }

        public boolean isFinished() {
            return finished;
        }

        public void markFinished() {
            this.finished = true;
        }

        public int getRemainingRecasts() {
            return remainingRecasts;
        }

        public int consumeRecast() {
            if (remainingRecasts > 0) {
                remainingRecasts--;
            }
            return remainingRecasts;
        }

        public boolean tickReady() {
            if (finished) {
                return false;
            }
            if (ticksUntilNextRecast > 0) {
                ticksUntilNextRecast--;
            }
            if (ticksUntilNextRecast <= 0) {
                ticksUntilNextRecast = intervalTicks;
                return true;
            }
            return false;
        }
    }

}
