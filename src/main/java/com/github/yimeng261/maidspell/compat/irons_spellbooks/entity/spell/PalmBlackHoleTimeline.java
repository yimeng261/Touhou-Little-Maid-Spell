package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

/** Server ticks are shared by the spell, carrier and visual wave. */
public final class PalmBlackHoleTimeline {
    public static final int TOP_TICK = 260;
    public static final int DROP_TICK = 320;
    public static final int IMPACT_TICK = 340;
    public static final int WARNING_TICKS = 5 * 20;
    public static final int WARNING_START_TICK = IMPACT_TICK - WARNING_TICKS;
    public static final int WAVE_TICKS = 20;
    public static final float BLAST_RADIUS = 16.0F;
    public static final int PULSE_START_TICK = 50;
    public static final int PULSE_INTERVAL = 20;
    public static final float PULSE_RADIUS = 10.0F;
    public static final float EXPLOSION_BASE_DAMAGE = 10.0F;
    public static final float MAX_MODEL_SCALE = 3.2F;
    public static final float LOCATOR_SCALE_MULTIPLIER = 1.6F;

    private PalmBlackHoleTimeline() {
    }

    public static boolean isWarningActive(double age) {
        return age >= WARNING_START_TICK && age < IMPACT_TICK;
    }

    public static boolean isPulseTick(int age) {
        return age >= PULSE_START_TICK && age < IMPACT_TICK
                && (age - PULSE_START_TICK) % PULSE_INTERVAL == 0;
    }

    public static float pulseDamage(float maxHealth) {
        return 2.0F + maxHealth * 0.10F;
    }

    public static boolean insidePulse(double dx, double dz) {
        return dx * dx + dz * dz <= PULSE_RADIUS * PULSE_RADIUS;
    }

    public static float explosionDamage(float maxHealth) {
        return EXPLOSION_BASE_DAMAGE + maxHealth * 0.20F;
    }

    public static double dropHeight(double age, double topHeight) {
        double progress = Math.max(0, Math.min(1, (age - DROP_TICK) / (IMPACT_TICK - DROP_TICK)));
        return topHeight * (1 - progress * progress);
    }

    public static float waveRadius(double age) {
        return BLAST_RADIUS * (float) Math.max(0, Math.min(1, (age - IMPACT_TICK) / WAVE_TICKS));
    }

    public static boolean crossedWave(double distance, float previousRadius, float radius) {
        return distance >= Math.max(0, previousRadius - 0.8F)
                && distance <= Math.min(BLAST_RADIUS, radius + 0.8F);
    }
}
