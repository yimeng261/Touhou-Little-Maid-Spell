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
 * 使用独立 16x16 贴图，整张图即为光环纹理
 * 初始倾斜 45° 绕 X 轴，旋转动画绕 Z 轴（与 Goety 原版逻辑一致）
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
     * 使用 16x16 独立贴图，整张图映射到光环面
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition halo = partdefinition.addOrReplaceChild("halo",
            CubeListBuilder.create()
                .texOffs(32, 17).addBox(-8.0F, -8.0F, 0.0F, 16.0F, 16.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        halo.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
