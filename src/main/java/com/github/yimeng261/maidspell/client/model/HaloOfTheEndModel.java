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
 * 终末之环模型
 * 参考：revelationfix/OdamaneHaloModel
 *
 * 贴图大小：64x64
 * 包含多个部分：主光环、左右装饰、上下装饰
 */
public class HaloOfTheEndModel extends Model {

    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(new ResourceLocation("touhou_little_maid_spell", "halo_of_the_end"), "main");

    public final ModelPart Halo;
    public final ModelPart bone;
    public final ModelPart bone2;
    public final ModelPart bone3;
    public final ModelPart bone4;
    public final ModelPart bone5;

    public HaloOfTheEndModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.Halo = root.getChild("Halo");
        this.bone = this.Halo.getChild("bone");
        this.bone2 = this.Halo.getChild("bone2");
        this.bone3 = this.Halo.getChild("bone3");
        this.bone4 = this.Halo.getChild("bone4");
        this.bone5 = this.Halo.getChild("bone5");
    }

    /**
     * 创建模型定义（与原版完全一致）
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Halo = partdefinition.addOrReplaceChild("Halo",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 主光环 (bone)
        PartDefinition bone = Halo.addOrReplaceChild("bone",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-9.0F, -9.0F, 0.0F, 18.0F, 18.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 18).addBox(9.0F, -3.0F, 0.0F, 8.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(16, 18).addBox(-17.0F, -3.0F, 0.0F, 8.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 24).addBox(-1.5F, 9.0F, 0.0F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(8, 24).addBox(-1.5F, -15.0F, 0.0F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(8, 24).mirror().addBox(-2.5F, -15.0F, 0.0F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(0, 24).mirror().addBox(-2.5F, 9.0F, 0.0F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 右侧装饰 (bone2)
        PartDefinition bone2 = Halo.addOrReplaceChild("bone2",
            CubeListBuilder.create()
                .texOffs(16, 31).addBox(20.5F, -1.5F, 0.0F, 7.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r1 = bone2.addOrReplaceChild("cube_r1",
            CubeListBuilder.create()
                .texOffs(3, 35).mirror().addBox(0.2164F, -0.9763F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(21.0237F, -0.7836F, 0.0F, 0.0F, 0.0F, -1.789F));

        PartDefinition cube_r2 = bone2.addOrReplaceChild("cube_r2",
            CubeListBuilder.create()
                .texOffs(5, 34).mirror().addBox(-1.0F, 0.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(19.7456F, 1.653F, 0.0F, 0.0F, 0.0F, -1.2654F));

        PartDefinition cube_r3 = bone2.addOrReplaceChild("cube_r3",
            CubeListBuilder.create()
                .texOffs(1, 34).mirror().addBox(1.0F, 19.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0005F)).mirror(false),
            PartPose.offsetAndRotation(1.0F, 2.0F, 0.0F, 0.0F, 0.0F, -1.5708F));

        // 左侧装饰 (bone3)
        PartDefinition bone3 = Halo.addOrReplaceChild("bone3",
            CubeListBuilder.create()
                .texOffs(16, 31).mirror().addBox(-27.5F, -1.5F, 0.0F, 7.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r4 = bone3.addOrReplaceChild("cube_r4",
            CubeListBuilder.create()
                .texOffs(7, 35).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-19.7456F, 1.653F, 0.0F, 0.0F, 0.0F, 1.2654F));

        PartDefinition cube_r5 = bone3.addOrReplaceChild("cube_r5",
            CubeListBuilder.create()
                .texOffs(1, 36).addBox(-3.0F, 19.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0005F)),
            PartPose.offsetAndRotation(-1.0F, 2.0F, 0.0F, 0.0F, 0.0F, 1.5708F));

        PartDefinition cube_r6 = bone3.addOrReplaceChild("cube_r6",
            CubeListBuilder.create()
                .texOffs(1, 35).addBox(-2.2164F, -0.9763F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-21.0237F, -0.7836F, 0.0F, 0.0F, 0.0F, 1.789F));

        // 下方装饰 (bone4)
        PartDefinition bone4 = Halo.addOrReplaceChild("bone4",
            CubeListBuilder.create()
                .texOffs(16, 36).addBox(-0.5F, 17.5F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(-0.005F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r7 = bone4.addOrReplaceChild("cube_r7",
            CubeListBuilder.create()
                .texOffs(16, 29).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(-0.0005F)).mirror(false),
            PartPose.offsetAndRotation(-0.7218F, 17.0F, 1.0F, 0.0F, 0.0F, 0.2182F));

        PartDefinition cube_r8 = bone4.addOrReplaceChild("cube_r8",
            CubeListBuilder.create()
                .texOffs(17, 29).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(-0.0005F)).mirror(false),
            PartPose.offsetAndRotation(0.7218F, 17.0F, 1.0F, 0.0F, 0.0F, -0.2182F));

        // 上方装饰 (bone5)
        PartDefinition bone5 = Halo.addOrReplaceChild("bone5",
            CubeListBuilder.create()
                .texOffs(16, 36).addBox(-0.5F, -18.5F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(-0.0009F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r9 = bone5.addOrReplaceChild("cube_r9",
            CubeListBuilder.create()
                .texOffs(16, 29).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.7218F, -17.0F, 1.0F, 0.0F, 0.0F, 0.2182F));

        PartDefinition cube_r10 = bone5.addOrReplaceChild("cube_r10",
            CubeListBuilder.create()
                .texOffs(18, 29).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-0.7218F, -17.0F, 1.0F, 0.0F, 0.0F, -0.2182F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Halo.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
