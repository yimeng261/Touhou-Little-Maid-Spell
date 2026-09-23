package com.github.yimeng261.maidspell.particle;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的自定义粒子类型。
 *
 * <p>贴图定义在 {@code assets/<modid>/particles/<name>.json}：里面列一项就是一帧
 * （图集不会自动切），帧由客户端粒子按年龄挑，见 {@code client.particle.VoidSpellParticle}。
 */
public final class MaidSpellParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MaidSpellMod.MOD_ID);

    /** 虚空法术粒子：8 张 16x16 的帧，用作虚空相变的药水效果粒子。 */
    public static final RegistryObject<SimpleParticleType> VOID_SPELL =
            PARTICLES.register("void_spell", () -> new SimpleParticleType(false));

    private MaidSpellParticles() {
    }
}
