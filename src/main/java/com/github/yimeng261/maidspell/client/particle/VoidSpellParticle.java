package com.github.yimeng261.maidspell.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 虚空法术粒子。
 *
 * <p>贴图是 {@code textures/particle/void_spell_0..7.png} 八张 16x16 的帧，
 * 由 {@code particles/void_spell.json} 按顺序列出来；{@link #setSpriteFromAge} 再按年龄挑帧，
 * 于是每个粒子各播各的动画，不需要 {@code .mcmeta}（{@code .mcmeta} 的动画是整张图集
 * 全局同步播的，那样同一时刻所有粒子都停在同一帧）。
 *
 * <p><b>不能只写一张 16x128 的竖排图集。</b>{@code ParticleEngine} 只把 JSON 里列出的
 * 每个贴图 id 绑成一个 sprite（{@code MutableSpriteSet.rebind} 一张图一个），整张 PNG
 * 就是一帧 —— 竖排图集写成一个条目，屏幕上看到的就是 8 帧挤在同一个方块里。
 * 一帧一张方图这条约定由 {@code ParticleSpriteResourceTest} 在构建期把着。
 */
public class VoidSpellParticle extends TextureSheetParticle {
    private static final float BASE_QUAD_SIZE = 0.42F;
    private static final float SIZE_VARIATION = 0.35F;
    private static final int MIN_LIFETIME = 12;
    private static final int LIFETIME_VARIATION = 12;
    /** 漂移速度，接近原地悬浮。 */
    private static final double DRIFT = 0.012D;

    private final SpriteSet sprites;

    protected VoidSpellParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;

        this.quadSize = BASE_QUAD_SIZE * (1.0F - SIZE_VARIATION * random.nextFloat());
        this.lifetime = MIN_LIFETIME + random.nextInt(LIFETIME_VARIATION);
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.hasPhysics = false;
        this.xd = dx + (random.nextDouble() - 0.5D) * DRIFT;
        this.yd = dy + random.nextDouble() * DRIFT;
        this.zd = dz + (random.nextDouble() - 0.5D) * DRIFT;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        // 走完一轮 8 帧正好接近寿命终点，配合淡出让粒子自然消散。
        this.setSpriteFromAge(this.sprites);
        if (this.age > this.lifetime / 2) {
            this.alpha = 1.0F - (this.age - this.lifetime / 2.0F) / (this.lifetime / 2.0F);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 虚空粒子自带发光感，不吃世界光照。 */
    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public VoidSpellParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                double x, double y, double z,
                                                double dx, double dy, double dz) {
            return new VoidSpellParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}
