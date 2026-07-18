package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;

/**
 * UsefulMagic 生命法术的「主人治疗」判定，供 {@code UsefulMagicProvider}（选球门槛）与
 * {@code HealthMagicMixin}（实际治疗）共用，避免两处各写一份范围常量与存活/同维度/距离判定。
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-07-18
 */
public final class UsefulMagicHealSupport {

    /**
     * 主人治疗/血量判定范围（格）。
     */
    public static final double OWNER_HEAL_RANGE = 32.0;

    private UsefulMagicHealSupport() {
    }

    /**
     * 若女仆的主人存活、与女仆同维度且在 {@link #OWNER_HEAL_RANGE} 格内，返回该主人，否则返回 {@code null}。
     */
    public static LivingEntity ownerInHealRange(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner != null && owner.isAlive()
                && owner.level() == maid.level()
                && owner.distanceTo(maid) <= OWNER_HEAL_RANGE) {
            return owner;
        }
        return null;
    }
}
