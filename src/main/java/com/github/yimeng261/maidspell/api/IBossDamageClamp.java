package com.github.yimeng261.maidspell.api;

import net.minecraft.world.entity.LivingEntity;

/**
 * 单次受击上限的裁决接口。
 *
 * <p>由实体自己决定"这一击最多能打掉多少血"，注入在 {@code LivingEntity.actuallyHurt} 的
 * 最终伤害上。这样护甲、抗性、附魔保护、吸收的原版结算顺序全部保留 —— 上限只裁剪
 * <b>真正会写进血条的那个数</b>，而不是入参伤害。真伤同样会经过这里（它回流到 {@code hurt()}）。
 *
 * <p>接口刻意保持中立：注入点在原版 {@code LivingEntity} 上，判据只能依赖本模组的中立类型，
 * 不能引用任何可选模组的实体类，否则缺少该模组时类加载就会失败。
 */
public interface IBossDamageClamp {

    /**
     * 这一击最多可以扣掉多少血。
     *
     * @param finalDamage 已经过护甲、抗性、吸收的原版最终伤害，恒为正数
     * @return 允许的最大扣血量；返回 {@link #NO_DAMAGE_CAP} 表示不限制
     */
    float maidspell$maxDamagePerHit(float finalDamage);

    /**
     * 不限制时的哨兵值。用 {@link Float#MAX_VALUE} 而不是某个大常数，是为了让"没配上限"
     * 与"配了一个很大的上限"在语义上不会混淆。
     */
    float NO_DAMAGE_CAP = Float.MAX_VALUE;
}
