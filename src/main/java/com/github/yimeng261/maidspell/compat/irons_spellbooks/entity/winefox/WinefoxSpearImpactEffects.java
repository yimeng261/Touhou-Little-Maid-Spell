package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.VoidPhaseSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** ISS spear impact particles, isolated from common code until the compat effect is used. */
public final class WinefoxSpearImpactEffects {
    private WinefoxSpearImpactEffects() {
    }

    public static void applyImpactDamage(Entity target, Entity spear, @Nullable Entity owner, float damage) {
        DamageSource source = target.damageSources().indirectMagic(spear, owner);
        if (!target.hurt(source, damage) || !(target instanceof LivingEntity victim)
            || !IAuthoritativeHealth.combatAlive(victim) || !(owner instanceof LivingEntity caster)
            || WinefoxBossSpells.hasVoidPhase(caster)) {
            return;
        }
        VoidPhaseSpell spell = (VoidPhaseSpell) IronsSpellbooksCompatSpells.VOID_PHASE.get();
        // The primary hit has already committed; the follow-up shares the remaining duel health.
        float bonus = WinefoxNonLethalGuard.duelFollowUpLimit(victim, source, 0.0F,
            spell.getBonusDamage(1, caster));
        if (bonus <= 0.0F) return;
        int previousInvulnerabilityTime = victim.invulnerableTime;
        Vec3 previousMotion = victim.getDeltaMovement();
        try {
            victim.invulnerableTime = 0;
            victim.hurt(new DamageSource(victim.damageSources().fellOutOfWorld().typeHolder(), spear, owner), bonus);
        } finally {
            victim.invulnerableTime = previousInvulnerabilityTime;
            victim.setDeltaMovement(previousMotion);
        }
    }

    public static void spawnEchoBlast(ServerLevel level, Vec3 center, float radius) {
        MagicManager.spawnParticles(level, ParticleHelper.UNSTABLE_ENDER,
            center.x, center.y, center.z, 25, 0, 0, 0, 0.18D, false);
        MagicManager.spawnParticles(level, new BlastwaveParticleOptions(
                SchoolRegistry.ENDER.get().getTargetingColor(), radius * 0.9F),
            center.x, center.y, center.z, 1, 0, 0, 0, 0, true);
    }
}
