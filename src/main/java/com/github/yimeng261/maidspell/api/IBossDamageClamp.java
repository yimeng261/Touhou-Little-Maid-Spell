package com.github.yimeng261.maidspell.api;

import net.minecraft.world.entity.LivingEntity;

/**
 * 单次受击上限的裁决接口：由实体自己决定"这一击最多能打掉多少血"。
 *
 * <p>注入在 {@code LivingEntity.actuallyHurt} 的最终伤害上，所以只裁<b>真正写进血条的那个数</b>，
 * 护甲、抗性、吸收按原版顺序结算；真伤回流 {@code hurt()} 也经过这里。
 *
 * <p>注入点在原版类上，故不能引用任何可选模组的实体类型，否则缺少该模组时类加载失败。
 */
public interface IBossDamageClamp {

    /**
     * @param finalDamage 已过护甲、抗性、吸收的最终伤害，恒为正数
     * @return 允许的最大扣血量；{@link #NO_DAMAGE_CAP} 表示不限制
     */
    float maidspell$maxDamagePerHit(float finalDamage);

    /** 不限制的哨兵值。用 {@link Float#MAX_VALUE} 以免与"配了一个很大的上限"混淆。 */
    float NO_DAMAGE_CAP = Float.MAX_VALUE;
}
