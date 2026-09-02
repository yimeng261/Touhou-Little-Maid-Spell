package com.github.yimeng261.maidspell.api;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 「别直接写我的血量，走正常伤害流程」。
 *
 * <p>{@code TrueDamageUtil} 实现真伤的办法是绕开 {@code hurt()}，
 * 直接改 {@code SynchedEntityData} 里的 Health 字段（改不动就改 NBT）。
 * 对普通怪物这没问题，但对那些<b>把关键契约挂在 {@code hurt()} 上</b>的实体是致命的 ——
 * 减伤倍率、无敌窗口、血量地板、战败演出、伤害归属统计，全都在 {@code hurt()} 那条链上，
 * 直写血量会一次性把它们全部跳过，而且<b>不报任何错</b>。
 *
 * <p>实现本接口即声明：真伤对我要改道。{@code TrueDamageUtil} 见到实现者就不再直写，
 * 转而调 {@link #maidspell$redirectTrueDamage}，由实体自己决定怎么吃这份伤害。
 *
 * <p><b>放在 api 包而不是 compat 包</b>，是因为 {@code TrueDamageUtil} 属于本模组的通用工具，
 * 不能为了认出某个可选模组下的实体就去 import 它 —— 那会让缺少该模组时整个真伤系统
 * {@code NoClassDefFoundError}。接口在这儿，{@code instanceof} 就够了。
 */
public interface ITrueDamageRedirect {

    /**
     * 吃下一份本该以直写血量方式落下的真伤。
     *
     * @param amount   伤害量，已经是聚合后的总额
     * @param attacker 来源，可能为 null（调试指令、来源实体已卸载）
     * @return 是否吃下。返回 false 只表示这一次没造成伤害，
     *         <b>不代表允许调用方退回去直写血量</b> —— 那条路对实现者永远是关的。
     */
    boolean maidspell$redirectTrueDamage(float amount, @Nullable LivingEntity attacker);
}
