package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.github.yimeng261.maidspell.client.model.UnholyHaloModel;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.network.message.MaidBaubleSyncClientHandler;
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
 * 参考 Goety 的 UnholyHatModel 和 CuriosRenderer
 * 为装备不洁圣冠的女仆在头顶渲染使徒同款光环
 *
 * 特点：
 * - 放置在头顶上方
 * - 45度倾斜（与 AscensionHalo 一致）
 * - 持续旋转动画
 * - 使用发光渲染
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

        // 尝试应用待同步的饰品数据
        MaidBaubleSyncClientHandler.tryApplyPendingSync(maid);

        // 检查是否装备不洁圣冠
        if (MaidSpellItems.getUnholyHat() == null ||
            !BaubleStateManager.hasBauble(maid, MaidSpellItems.getUnholyHat())) {
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
        poseStack.translate(0, 0.4, 0.6);

        // 计算旋转动画（与 Goety 原版一致：ageInTicks * 0.01F）
        float rotation = ageInTicks * 0.01F;

        // 渲染光环
        renderHalo(poseStack, buffer, rotation, maid);

        poseStack.popPose();
    }

    /**
     * 渲染光环
     * 使用 entityTranslucent 渲染类型，与原版一致
     */
    private void renderHalo(PoseStack poseStack, MultiBufferSource buffer, float rotation, EntityMaid maid) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(HALO_TEXTURE));

        poseStack.pushPose();

        // 应用初始45度倾斜（绕z轴）- 与 Goety 原版一致
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(45.0F * Mth.DEG_TO_RAD));

        // 应用旋转动画（绕z轴旋转）
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(rotation));

        // 渲染模型
        model.renderToBuffer(poseStack, vertexConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        poseStack.popPose();

        // 结束批次以确保正确渲染
        bufferSource.endBatch(RenderType.entityTranslucent(HALO_TEXTURE));
    }
}
