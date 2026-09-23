package com.github.yimeng261.maidspell.compat.irons_spellbooks.effect;

import com.github.yimeng261.maidspell.particle.MaidSpellParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 虚空相变。
 *
 * <p>纯增益状态，不改任何属性，效果持续期间由 {@link #applyEffectTick} 在服务端
 * 播一圈虚空法术粒子。药水效果本身用 {@code visible=false} 关掉原版的水泡粒子，
 * 让虚空法术粒子成为这个状态唯一的表现。
 */
public class VoidPhaseEffect extends MobEffect {
    private static final int COLOR = 0x6A35A8;

    /** 环形半径：站在目标腰部扫一圈。 */
    private static final double RING_RADIUS = 0.55D;
    /** 每 tick 转过的圈数（按 20 tick/秒换算），转得慢一点才看得出是环绕而不是抖动。 */
    private static final double TURNS_PER_SECOND = 0.4D;
    private static final int PARTICLES_PER_TICK = 2;
    private static final double VERTICAL_SPREAD = 0.75D;

    public VoidPhaseEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

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
