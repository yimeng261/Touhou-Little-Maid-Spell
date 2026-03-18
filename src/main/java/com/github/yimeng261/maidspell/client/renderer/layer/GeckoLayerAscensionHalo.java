package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.github.yimeng261.maidspell.client.model.AscensionHaloModel;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
 * 晋升之环渲染层
 * 参考：goety_revelation/PlayerHaloLayer
 *
 * 使用简化的光环模型，带有简单的旋转动画
 */
public class GeckoLayerAscensionHalo<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {

    private static final ResourceLocation HALO_TEXTURE = new ResourceLocation("touhou_little_maid_spell", "textures/entity/maid/ascension_halo.png");
    private static final int LIGHT_LEVEL = 0xFF00F0; // 最大亮度

    private AscensionHaloModel model;

    public GeckoLayerAscensionHalo(R entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRendererIn) {
        return new GeckoLayerAscensionHalo<>(entityRendererIn);
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

        // 检查是否装备晋升之环
        if (MaidSpellItems.getAscensionHalo() == null || !BaubleStateManager.hasBauble(maid, MaidSpellItems.getAscensionHalo())) {
            return;
        }

        // 延迟初始化模型
        if (model == null) {
            model = new AscensionHaloModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(AscensionHaloModel.LAYER_LOCATION)
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
        poseStack.translate(0, 0.4, 0.6);

        // 计算旋转动画（简单的时间基础旋转）
        float time = (float) (System.currentTimeMillis() % 100000L) / 1000.0F;
        float rotation = time * 18.0F * Mth.DEG_TO_RAD; // 每秒旋转18度

        // 渲染光环
        renderHalo(poseStack, buffer, rotation, maid);

        poseStack.popPose();
    }

    /**
     * 渲染光环（简化版）
     */
    private void renderHalo(PoseStack poseStack, MultiBufferSource buffer, float rotation, EntityMaid maid) {
        // 检查是否为半血状态（用于特效切换）
        boolean isSecondPhase = maid.getHealth() / maid.getMaxHealth() < 0.5F;

        // 选择渲染类型
        RenderType renderType = isSecondPhase ?
            RenderType.energySwirl(HALO_TEXTURE, 1f, 1f) :
            RenderType.entityTranslucent(HALO_TEXTURE);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        poseStack.pushPose();

        // 应用初始45度倾斜（绕z轴）
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(45.0F * Mth.DEG_TO_RAD));

        // 应用旋转动画
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(rotation));

        // 渲染模型
        model.renderToBuffer(poseStack, vertexConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        poseStack.popPose();

        // 结束批次以确保正确渲染
        bufferSource.endBatch(renderType);

        // 如果是半血状态，添加额外的发光效果
        if (isSecondPhase) {
            VertexConsumer glowConsumer = bufferSource.getBuffer(RenderType.eyes(HALO_TEXTURE));

            poseStack.pushPose();

            // 应用初始45度倾斜（绕z轴）
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(45.0F * Mth.DEG_TO_RAD));

            // 应用旋转动画
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(rotation));

            // 设置发光颜色（更亮）
            RenderSystem.setShaderColor(1f, 1f, 1f, 1.5F);
            model.renderToBuffer(poseStack, glowConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);  // 恢复默认颜色

            poseStack.popPose();
            bufferSource.endBatch(RenderType.eyes(HALO_TEXTURE));
        }
    }
}
