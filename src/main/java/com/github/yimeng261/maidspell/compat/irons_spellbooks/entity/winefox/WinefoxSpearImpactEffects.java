package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** ISS spear impact particles, isolated from common code until the compat effect is used. */
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
