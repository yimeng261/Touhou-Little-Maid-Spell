package com.github.yimeng261.maidspell.effect;

import com.github.yimeng261.maidspell.particle.MaidSpellParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 虚空漫步。星锚珍珠给的状态。
 *
 * <p>表现部分整体照抄 {@code VoidPhaseEffect}：纯增益、不改属性，药水效果自己的水泡粒子
 * 用 {@code visible=false} 关掉（那一位由给状态的调用方设），持续期间由 {@link #applyEffectTick}
 * 在服务端播一圈 {@code void_spell} 粒子 —— 也就是「注册的虚空粒子」。
 *
 * <p><b>免疫不在这里做。</b>{@code MobEffect} 没有「免疫某种伤害」的钩子，而且
 * {@code minecraft:out_of_world} 带着 {@code bypasses_invulnerability}，靠
 * {@code setInvulnerable} 或抗性提升都拦不住。真正生效的是
 * {@code event.StaranchorPearlEvents} 里对 {@code LivingAttackEvent} 的取消。
 * 这里只负责「看见它就知道自己在虚空漫步」这一件事，判断口径见 {@link #isVoidDamage}。
 */
public class VoidWalkEffect extends MobEffect {
    /** 图标底色。取星锚珍珠贴图的主色，和 {@code textures/mob_effect/void_walk.png} 对得上。 */
    private static final int COLOR = 0x7B3FD4;

    /** 环形半径：站在目标腰部扫一圈。 */
    private static final double RING_RADIUS = 0.6D;
    /** 每 tick 转过的圈数。比虚空相变慢一点，毕竟这是个给人挂 30 秒的状态。 */
    private static final double TURNS_PER_SECOND = 0.3D;
    private static final int PARTICLES_PER_TICK = 2;
    private static final double VERTICAL_SPREAD = 0.8D;

    public VoidWalkEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    /**
     * 本模组口径的「虚空伤害」。
     *
     * <p>就是原版 {@code minecraft:out_of_world}（掉出世界底）。选它当唯一的判据，是因为
     * 模组里本来就把这一类当成虚空伤害在用：星影刃光追加的那一发是
     * {@code damageSources().fellOutOfWorld()}（见 {@code StarShadowStrikeSpell}），
     * 虚空相变的额外伤害也是同一个（见 {@code VoidPhaseDamageHandler}），
     * 连 {@code StarShadowStrikeGameTests} 都拿 {@code DamageTypes.FELL_OUT_OF_WORLD} 对账。
     * 所以「免疫虚空伤害」= 免疫这一类，不另开一套自定义伤害类型。
     */
    public static boolean isVoidDamage(DamageSource source) {
        return source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        if (entity.level() instanceof ServerLevel level) {
            spawnRing(level, entity);
        }
    }

    /**
     * 在目标腰上扫一圈虚空粒子。
     *
     * <p>状态的每 tick 表现和「刚挡下一发」「刚喝下珍珠」那两下的表现共用这一个，
     * 免得三处各写一份、转起来还不同步。
     */
    public static void spawnRing(ServerLevel level, LivingEntity entity) {
        RandomSource random = entity.getRandom();
        double halfHeight = entity.getBbHeight() * 0.5D;
        double phase = entity.tickCount * TURNS_PER_SECOND * Mth.TWO_PI / 20.0D;

        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            double angle = phase + i * Math.PI;
            double radius = RING_RADIUS * (0.6D + random.nextDouble() * 0.6D);
            level.sendParticles(MaidSpellParticles.VOID_SPELL.get(),
                    entity.getX() + Math.cos(angle) * radius,
                    entity.getY() + halfHeight + (random.nextDouble() - 0.5D) * VERTICAL_SPREAD,
                    entity.getZ() + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
