package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.api.IBossDamageClamp;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 单次受击上限的<b>兜底</b>注入点。
 *
 * <p>首要防线在 {@code WinefoxBossHealthController.write}（所有写血路径的汇合点），
 * 本 Mixin 只处理一种它管不到的情况：有人绕过我们的 {@code hurt()} 直接调
 * {@code actuallyHurt}。那种调用不经过控制器，于是由这里在最终伤害上兜住。
 * 两条路径共用同一份配置，语义一致。
 *
 * <p>挂在原版 {@code actuallyHurt} 上，裁剪的是 <b>最终伤害</b>（局部变量 3），也就是
 * {@code ForgeHooks.onLivingDamage} 返回之后、写进血条之前的那个数。选择这个位置的理由：
 *
 * <ul>
 *   <li>更早（{@code onLivingHurt} 之前）裁剪<b>不构成保证</b>：后面还有护甲、抗性两轮结算，
 *       它们会把上限再削一次，实际掉血低于配置值；</li>
 *   <li>更晚（{@code setHealth} 之后）已经扣完了，事后裁剪只会覆盖一次已完成的写入。</li>
 * </ul>
 *
 * <p>局部变量 3 在 1.20.1-47.4.0 的方法内只有一处 {@code FSTORE} —— 即
 * {@code onLivingDamage} 的返回值，因此 {@code ordinal = 0} 精确、不会误伤方法的 float 参数
 * （那是局部变量 2）。已核对 Forge 补丁后的字节码。
 *
 * <p>{@code require = 0}：本 Mixin 是兜底而非主防线，若未来 Forge 改变了这段代码形状，
 * 宁可不注入（保护降级为"只挡经 hurt() 的伤害"）也不要因为注入失败而让游戏起不来。
 *
 * <p>目标类是 {@code LivingEntity} 且判据走 {@link IBossDamageClamp} 中立接口，
 * 因此本 Mixin 不依赖任何可选模组。
 */
@Mixin(LivingEntity.class)
public abstract class WinefoxDamageClampMixin {

    @ModifyVariable(
        method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0,
        require = 0
    )
    private float maidspell$clampFinalDamage(float finalDamage) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof IBossDamageClamp clamp)) {
            return finalDamage;
        }
        // 0 是"这一击完全被吸收/取消"，原版靠它跳过整段写血逻辑，不能被抬起来。
        // 非有限值同样原样放行，交给原本的 fcmpl 分支处理，避免把 NaN 变成合法伤害。
        if (finalDamage <= 0.0F || !Float.isFinite(finalDamage)) {
            return finalDamage;
        }
        float max = clamp.maidspell$maxDamagePerHit(finalDamage);
        if (max == IBossDamageClamp.NO_DAMAGE_CAP || max >= finalDamage) {
            return finalDamage;
        }
        return Math.max(max, 0.0F);
    }
}
