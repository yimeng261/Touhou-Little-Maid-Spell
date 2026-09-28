package com.github.yimeng261.maidspell.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 虚空法术粒子按年龄从八张独立贴图中取帧。
 * 粒子 JSON 须逐帧列出贴图，单张竖排图不会被自动拆帧。
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
