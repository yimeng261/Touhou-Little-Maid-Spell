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
 * 不洁光环模型（使徒同款光环）
 *
 * 参考 Goety 的 UnholyHatModel，简化为仅渲染光环部分
 * 贴图大小：32x32（从原版 64x64 的光环部分裁剪）
 *
 * 特点：
 * - 单层光环，带有旋转动画
 * - 45度倾斜放置
 * - 使用 entityTranslucent 渲染类型
 */
public class UnholyHaloModel extends Model {

    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(new ResourceLocation("touhou_little_maid_spell", "unholy_halo"), "main");

    public final ModelPart halo;

    public UnholyHaloModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.halo = root.getChild("halo");
    }

    /**
     * 创建光环模型
     * 与 Goety 原版一致：32x32 贴图的平面光环
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 主光环 - 16x16 平面（使用 32x32 贴图的纹理坐标）
        PartDefinition halo = partdefinition.addOrReplaceChild("halo",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0F, -8.0F, 0.0F, 16.0F, 16.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        halo.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
