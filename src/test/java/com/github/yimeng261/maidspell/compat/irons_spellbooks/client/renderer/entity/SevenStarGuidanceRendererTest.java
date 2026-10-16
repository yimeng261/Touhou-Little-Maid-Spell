package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SevenStarGuidanceEntity;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SevenStarGuidanceRendererTest {
    private static final ResourceLocation CRYSTAL_FACE = new ResourceLocation(
            "touhou_little_maid_spell", "textures/entity/seven_star_guidance/crystal_face.png");

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        // Only vanilla registries are needed; full Forge bootstrap requires ModLauncher.
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        // Other client tests can already have populated these shared registries.
        if (!BuiltInRegistries.PAINTING_VARIANT.containsKey(new ResourceLocation("minecraft", "kebab"))) {
            BuiltInRegistries.bootStrap();
        }
    }

    @Test
    void lockBracketsSwitchBackToLinesAfterCrystalsUseTheSharedBuilder() {
        HeadlessBuffers buffers = new HeadlessBuffers();
        buffers.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL_FACE));

        assertDoesNotThrow(() -> SevenStarGuidanceRenderer.drawLockBrackets(
                buffers, new Matrix4f(), Vec3.ZERO, 0.72F, 0.5F, 0.75F));
        buffers.endBatch();

        assertLineBatch(buffers.lastBatch(), 16);
    }

    @Test
    void strikeRingsAndFinalReticlesSwitchBackToLinesAfterBeamLayers() {
        HeadlessBuffers buffers = new HeadlessBuffers();
        // Both the newer magic layer and the older eyes fallback use NEW_ENTITY.
        buffers.getBuffer(RenderType.eyes(CRYSTAL_FACE));
        assertDoesNotThrow(() -> SevenStarGuidanceRenderer.drawRing(
                buffers, new Matrix4f(), Vec3.ZERO, 1.8F, 0.5F,
                0.88F, 0.20F, 1.0F, 0.80F, 20));
        buffers.endBatch();
        assertLineBatch(buffers.lastBatch(), 80);

        buffers.getBuffer(RenderType.eyes(CRYSTAL_FACE));
        assertDoesNotThrow(() -> SevenStarGuidanceRenderer.drawReticle(
                buffers, new Matrix4f(), Vec3.ZERO, 0.65F, 0.8F));
        buffers.endBatch();
        assertLineBatch(buffers.lastBatch(), 16);
    }

    @Test
    void finaleCrystalsUseTheirOwnTextureAfterConnectedBeamsAndShatteredEdges() {
        for (int stage : new int[]{1, 2}) {
            for (int mask : new int[]{0, 1, 0b0101010, 0b1111111}) {
                HeadlessBuffers buffers = new HeadlessBuffers();
                float age = stage == 1 ? SevenStarGuidanceEntity.FINAL_TICK + 8.0F
                        : SevenStarGuidanceEntity.FINALE_SHATTER_TICK + 4.0F;

                assertDoesNotThrow(() -> SevenStarGuidanceRenderer.drawFinalConstellation(
                        Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                        1.0D, mask, stage, age, new Matrix4f(), buffers));
                buffers.endBatch();

                Batch crystals = buffers.lastBatch();
                assertSame(RenderType.entityTranslucentEmissive(CRYSTAL_FACE), crystals.type());
                assertEquals(DefaultVertexFormat.NEW_ENTITY, crystals.state().format());
                assertEquals(7 * 8 * 4, crystals.state().vertexCount());
                if (mask == 0b1111111) {
                    assertTrue(buffers.batches.size() > 1, "Connected stars must also render edge effects");
                    assertNotSame(crystals.type(), buffers.batches.get(buffers.batches.size() - 2).type(),
                            "Crystal vertices must not be added to the previous beam or shard batch");
                }
            }
        }
    }

    private static void assertLineBatch(Batch batch, int vertexCount) {
        assertSame(RenderType.lines(), batch.type());
        assertEquals(DefaultVertexFormat.POSITION_COLOR_NORMAL, batch.state().format());
        assertEquals(vertexCount, batch.state().vertexCount());
    }

    private record Batch(RenderType type, BufferBuilder.DrawState state) {}

    /** Uses the real shared builder and format switching, without submitting to OpenGL. */
    private static final class HeadlessBuffers extends MultiBufferSource.BufferSource {
        private final List<Batch> batches = new ArrayList<>();

        private HeadlessBuffers() {
            super(new BufferBuilder(4096), Map.of());
        }

        @Override
        public void endBatch(RenderType type) {
            if (lastState.equals(type.asOptional()) && startedBuffers.remove(builder)) {
                BufferBuilder.RenderedBuffer rendered = builder.end();
                try {
                    if (rendered.drawState().vertexCount() > 0) {
                        batches.add(new Batch(type, rendered.drawState()));
                    }
                } finally {
                    rendered.release();
                }
                lastState = Optional.empty();
            }
        }

        private Batch lastBatch() {
            assertFalse(batches.isEmpty(), "The renderer must submit geometry");
            return batches.get(batches.size() - 1);
        }
    }
}
