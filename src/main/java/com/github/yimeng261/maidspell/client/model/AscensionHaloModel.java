package com.github.yimeng261.maidspell.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 晋升之环模型（简化版）
 * 完全基于 goety_revelation 原版 PlayerHaloModel 的设计
 *
 * 贴图大小：16x16（与原版一致）
 * 简单的单层平面光环，带有旋转动画
 */
public class AscensionHaloModel extends Model {

    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(new ResourceLocation("touhou_little_maid_spell", "ascension_halo"), "main");

    public final ModelPart halo;

    public AscensionHaloModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.halo = root.getChild("halo");
    }

    /**
     * 创建简单的光环模型
     * 与原版 PlayerHaloModel 完全一致：单层 16x16 平面
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 主光环 - 单层 16x16 平面（与原版完全一致）
        PartDefinition halo = partdefinition.addOrReplaceChild("halo",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0F, -8.0F, 0.0F, 16.0F, 16.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        halo.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
