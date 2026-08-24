package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model.MagicalWinefoxBossModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.layer.WinefoxBossEquipmentLayer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.layer.WinefoxBossHeldItemLayer;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.MagicalWinefoxBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

public class MagicalWinefoxBossRenderer extends GeoEntityRenderer<MagicalWinefoxBossEntity> {
    public MagicalWinefoxBossRenderer(EntityRendererProvider.Context context) {
        super(context, new MagicalWinefoxBossModel());
        this.withScale(1.0F);
        this.addRenderLayer(new WinefoxBossEquipmentLayer(this));
        // 顺序无所谓：两层各管各的骨骼，且拿自制装备时持物层会自己让开。
        this.addRenderLayer(new WinefoxBossHeldItemLayer(this, context.getItemInHandRenderer()));
        this.shadowRadius = 0.8F;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, MagicalWinefoxBossEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        poseStack.pushPose();
        RenderUtils.prepMatrixForBone(poseStack, bone);

        int boneLight = bone.getName().startsWith("ysmGlow")
                ? LightTexture.pack(15, 15)
                : packedLight;
        this.renderCubesOfBone(poseStack, bone, buffer, boneLight, packedOverlay, red, green, blue, alpha);

        if (!isReRender) {
            this.applyRenderLayersForBone(poseStack, animatable, bone, renderType, bufferSource, buffer,
                    partialTick, boneLight, packedOverlay);
        }

        this.renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.popPose();
    }
}
