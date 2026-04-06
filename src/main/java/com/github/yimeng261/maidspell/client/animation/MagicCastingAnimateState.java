package com.github.yimeng261.maidspell.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;

/**
 * 简单的魔法咏唱状态实现
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03
 */
public class MagicCastingAnimateState implements IMagicCastingState {
    private CastingPhase phase;
    private boolean cancelled;

    private SpellData castingSpell = SpellData.EMPTY;

    private AbstractSpell instantCastSpellType = SpellRegistry.none();

    private boolean clientIsCasting = false;

    /**
     * 创建一个新的魔法咏唱状态
     *
     * @param phase 当前咏唱阶段
     */
    public MagicCastingAnimateState(CastingPhase phase) {
        this(phase, false);
    }

    /**
     * 创建一个新的魔法咏唱状态
     *
     * @param phase     当前咏唱阶段
     * @param cancelled 是否已取消
     */
    public MagicCastingAnimateState(CastingPhase phase, boolean cancelled) {
        this.phase = phase;
        this.cancelled = cancelled;
    }

    @Override
    public CastingPhase getCurrentPhase() {
        return phase;
    }

    public void setCurrentPhase(CastingPhase phase) {
        this.phase = phase;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 设置咏唱取消状态
     *
     * @param cancelled 是否已取消
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public AbstractSpell getInstantCastSpellType() {
        return instantCastSpellType;
    }

    public void clearInstantCastSpellType() {
        this.instantCastSpellType = SpellRegistry.none();
        this.phase = CastingPhase.NONE;
    }

    public SpellData getCastingSpell() {
        return castingSpell;
    }

    public void updateState(EntityMaid maid, SyncedSpellData syncedSpellData) {
        if (!maid.level().isClientSide) {
            return;
        }

        boolean oldIsCasting = clientIsCasting;
        SpellData lastCastingSpell = castingSpell;
        castingSpell = new SpellData(SpellRegistry.getSpell(syncedSpellData.getCastingSpellId()), syncedSpellData.getCastingSpellLevel());
        clientIsCasting = syncedSpellData.isCasting();

        if (castingSpell.getSpell() == SpellRegistry.none() && lastCastingSpell.getSpell() == SpellRegistry.none()) {
            if (phase != CastingPhase.INSTANT) {
                phase = CastingPhase.NONE;
            }
            return;
        }

        if (!clientIsCasting && oldIsCasting) {
            castingSpell = lastCastingSpell;
            phase = CastingPhase.END;
            instantCastSpellType = lastCastingSpell.getSpell();
        } else if (clientIsCasting && !oldIsCasting) {
            phase = CastingPhase.START;
            if (castingSpell.getSpell().getCastType() == CastType.INSTANT) {
                instantCastSpellType = castingSpell.getSpell();
                // castingSpell.getSpell().onClientPreCast(maid.level(), castingSpell.getLevel(), maid, InteractionHand.MAIN_HAND, data.getMagicData());
                castingSpell = SpellData.EMPTY;
                phase = CastingPhase.INSTANT;
            } else  {
                instantCastSpellType = SpellRegistry.none();
            }
        } else if (clientIsCasting) {
            phase = CastingPhase.CASTING;
        } else if (phase != CastingPhase.END) {
            castingSpell = SpellData.EMPTY;
            phase = CastingPhase.NONE;
            instantCastSpellType = SpellRegistry.none();
        }
    }
}
