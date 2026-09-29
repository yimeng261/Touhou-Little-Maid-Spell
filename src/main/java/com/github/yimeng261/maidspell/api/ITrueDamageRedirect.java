package com.github.yimeng261.maidspell.api;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 让真伤改走实体指定的伤害流程，保留减伤、无敌窗口和战败结算等契约。
 * 通用工具通过此接口识别目标，避免直接依赖可选模组实体类。
 */
public interface ITrueDamageRedirect {
    /** 真伤请求入队时回调，在延迟结算或同 tick 战败之前记录这次攻击 */
    default void maidspell$onTrueDamageQueued() {
    }

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
