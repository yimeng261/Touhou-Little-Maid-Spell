package com.github.yimeng261.maidspell.client.animation;

import com.github.tartaricacid.touhoulittlemaid.api.animation.IMagicCastingState;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import net.minecraft.world.entity.LivingEntity;

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

    /**
     * @param caster 施法者。形参原先是 {@code EntityMaid}，但方法体只用了它的
     *               {@code level().isClientSide} —— 放宽到 {@code LivingEntity}
     *               之后万法酒狐（{@code AbstractSpellCastingMob}，不是女仆）
     *               也能用同一份状态机。
     */
    public void updateState(LivingEntity caster, SyncedSpellData syncedSpellData) {
        if (!caster.level().isClientSide) {
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
                // castingSpell.getSpell().onClientPreCast(caster.level(), castingSpell.getLevel(), caster, InteractionHand.MAIN_HAND, data.getMagicData());
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
