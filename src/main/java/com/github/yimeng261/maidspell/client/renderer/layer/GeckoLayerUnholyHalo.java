package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.github.yimeng261.maidspell.client.model.UnholyHaloModel;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * 不洁光环渲染层
 *
 * 参考 Goety 原版 UnholyHatModel：
 * - 初始倾斜 45° 绕 X 轴（原版 halo 的 PartPose xRot = 0.7854F）
 * - 旋转动画绕 Z 轴（原版 halo1.zRot = ageInTicks * 0.01F）
 */
public class GeckoLayerUnholyHalo<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {

    private static final ResourceLocation HALO_TEXTURE =
        new ResourceLocation("touhou_little_maid_spell", "textures/entity/maid/unholy_halo.png");

    private static final int LIGHT_LEVEL = 0xFF00F0; // 最大亮度

    private UnholyHaloModel model;

    public GeckoLayerUnholyHalo(R entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRendererIn) {
        return new GeckoLayerUnholyHalo<>(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                      float limbSwing, float limbSwingAmount, float partialTicks,
                      float ageInTicks, float netHeadYaw, float headPitch) {

        // 转换为女仆实体
        EntityMaid maid = IMaid.convertToMaid(entity);
        if (maid == null || maid.isInvisible() || maid.isSleeping()) {
            return;
        }

        // 检查是否装备不洁圣冠（任一版本）
        var unholyHat = MaidSpellItems.getUnholyHat();
        var unholyHatHalo = MaidSpellItems.getUnholyHatHalo();

        boolean hasHat = unholyHat != null && BaubleStateManager.hasBauble(maid, unholyHat);
        boolean hasHalo = unholyHatHalo != null && BaubleStateManager.hasBauble(maid, unholyHatHalo);

        if (!hasHat && !hasHalo) {
            return;
        }

        // 延迟初始化模型
        if (model == null) {
            model = new UnholyHaloModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(UnholyHaloModel.LAYER_LOCATION)
            );
        }

        // 获取模型定位点
        ILocationModel locationModel = getLocationModel(entity);
        if (locationModel == null || locationModel.headBones().isEmpty()) {
            return;
        }

        poseStack.pushPose();

        // 定位到头部
        RenderUtils.prepMatrixForLocator(poseStack, locationModel.headBones());

        // 调整位置（放在头顶上方）
        poseStack.translate(0, 0.7, 0.6);

        // 计算旋转动画（与 Goety 原版一致：ageInTicks * 0.01F）
        float rotation = ageInTicks * 0.01F;

        // 渲染光环
        renderHalo(poseStack, buffer, rotation, maid);

        poseStack.popPose();
    }

    /**
     * 渲染光环
     * 与 Goety 原版 UnholyHatModel 一致：X轴初始倾斜 + Z轴旋转动画
     */
    private void renderHalo(PoseStack poseStack, MultiBufferSource buffer, float rotation, EntityMaid maid) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(HALO_TEXTURE));

        poseStack.pushPose();

        // 初始 45° 倾斜绕 X 轴（对应原版 halo PartPose xRot = 0.7854F）
        poseStack.mulPose(com.mojang.math.Axis.XP.rotation(-45.0F * Mth.DEG_TO_RAD));

        // 旋转动画绕 Z 轴（对应原版 halo1.zRot = ageInTicks * 0.01F）
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(rotation));

        // 渲染模型
        model.renderToBuffer(poseStack, vertexConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        poseStack.popPose();

        // 结束批次以确保正确渲染
        bufferSource.endBatch(RenderType.entityTranslucent(HALO_TEXTURE));
    }
}
