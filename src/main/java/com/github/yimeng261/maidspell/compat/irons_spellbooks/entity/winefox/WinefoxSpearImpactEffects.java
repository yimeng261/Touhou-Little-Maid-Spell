package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Iron's Spells visual effect used by the Winefox spear impact.
 *
 * <p>The effect follows the old Echoing Strikes impact: a short unstable-ender
 * burst under an expanding, ender-colored blastwave. Keeping this in the
 * compatibility package prevents the common spear entity from resolving ISS
 * particle classes until an ISS-backed projectile actually hits.
 */
public final class WinefoxSpearImpactEffects {
    private WinefoxSpearImpactEffects() {
    }

    public static void spawnEchoBlast(ServerLevel level, Vec3 center, float radius) {
        MagicManager.spawnParticles(level, ParticleHelper.UNSTABLE_ENDER,
            center.x, center.y, center.z, 25, 0, 0, 0, 0.18D, false);
        MagicManager.spawnParticles(level, new BlastwaveParticleOptions(
                SchoolRegistry.ENDER.get().getTargetingColor(), radius * 0.9F),
            center.x, center.y, center.z, 1, 0, 0, 0, 0, true);
    }
}
