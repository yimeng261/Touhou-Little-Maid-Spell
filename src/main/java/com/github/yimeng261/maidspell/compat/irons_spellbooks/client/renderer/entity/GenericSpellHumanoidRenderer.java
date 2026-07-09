package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobModel;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobRenderer;
import io.redspace.ironsspellbooks.render.GlowingEyesLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class GenericSpellHumanoidRenderer extends AbstractSpellCastingMobRenderer {
    private boolean safeEyesLayerInstalled;

    public GenericSpellHumanoidRenderer(EntityRendererProvider.Context context, AbstractSpellCastingMobModel model) {
        super(context, model);
    }

    @Override
    public void render(AbstractSpellCastingMob entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        installSafeEyesLayer();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void installSafeEyesLayer() {
        if (safeEyesLayerInstalled) {
            return;
        }

        safeEyesLayerInstalled = true;
        var layers = getRenderLayers();
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i) instanceof GlowingEyesLayer.Geo) {
                layers.set(i, new SafeGlowingEyesLayer(this));
                return;
            }
        }
        layers.add(new SafeGlowingEyesLayer(this));
    }

    private static class SafeGlowingEyesLayer extends GeoRenderLayer<AbstractSpellCastingMob> {
        private SafeGlowingEyesLayer(GeoEntityRenderer<AbstractSpellCastingMob> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, AbstractSpellCastingMob animatable, BakedGeoModel bakedModel,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            var eye = GlowingEyesLayer.getEyeType(animatable);
            if (eye == GlowingEyesLayer.EyeType.None) {
                return;
            }

            bakedModel.getBone("head").ifPresent(headBone -> {
                float scaleX = headBone.getScaleX();
                float scaleY = headBone.getScaleY();
                float scaleZ = headBone.getScaleZ();
                float eyeScale = GlowingEyesLayer.getEyeScale(animatable);
                headBone.updateScale(eyeScale, eyeScale, eyeScale);
                try {
                    VertexConsumer eyeBuffer = bufferSource.getBuffer(GlowingEyesLayer.EYES);
                    int color = Utils.packRGB(new Vector3f(eye.r, eye.g, eye.b)) | 0xFF000000;
                    this.getRenderer().renderChildBones(poseStack, animatable, headBone, GlowingEyesLayer.EYES,
                            bufferSource, eyeBuffer, true, partialTick, packedLight, packedOverlay, color);
                } finally {
                    headBone.updateScale(scaleX, scaleY, scaleZ);
                }
            });
        }
    }
}
