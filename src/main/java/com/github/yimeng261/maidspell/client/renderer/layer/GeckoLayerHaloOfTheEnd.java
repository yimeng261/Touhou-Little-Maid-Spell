package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.github.yimeng261.maidspell.client.model.HaloOfTheEndModel;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.network.message.MaidBaubleSyncClientHandler;
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
 * 终末之环渲染层
 * 参考：revelationfix/OdamaneHaloLayer
 *
 * 使用复杂的多层光环模型，带有多个旋转部件的动画
 */
public class GeckoLayerHaloOfTheEnd<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {

    private static final ResourceLocation HALO_TEXTURE = new ResourceLocation("touhou_little_maid_spell", "textures/entity/maid/halo_the_end.png");
    private static final int LIGHT_LEVEL = 0xFF00F0; // 最大亮度

    private HaloOfTheEndModel model;

    public GeckoLayerHaloOfTheEnd(R entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRendererIn) {
        return new GeckoLayerHaloOfTheEnd<>(entityRendererIn);
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

        // 检查是否装备终末之环
        if (MaidSpellItems.getHaloOfTheEnd() == null || !BaubleStateManager.hasBauble(maid, MaidSpellItems.getHaloOfTheEnd())) {
            return;
        }

        // 延迟初始化模型
        if (model == null) {
            model = new HaloOfTheEndModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(HaloOfTheEndModel.LAYER_LOCATION)
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

        // 调整位置（放在头顶上方，稍微靠后）
        poseStack.translate(0, 0.4, 0.6);

        // 计算旋转动画（与原版一致）
        // 使用玩家名称长度和时间来生成独特的旋转
        float time = (float) (System.currentTimeMillis() % 100000L) / 1000.0F;
        float baseRotation = (Mth.cos(maid.getName().getString().length()) * 3201123F + time * 90F) * Mth.DEG_TO_RAD;

        // 渲染光环
        renderHalo(poseStack, buffer, baseRotation, maid, partialTicks);

        poseStack.popPose();
    }

    /**
     * 渲染光环（完整版，包含多个旋转部件）
     */
    private void renderHalo(PoseStack poseStack, MultiBufferSource buffer, float baseRotation, EntityMaid maid, float partialTicks) {
        // 检查是否为半血状态（用于特效切换）
        boolean isSecondPhase = maid.getHealth() / maid.getMaxHealth() < 0.5F;

        // 选择渲染类型
        RenderType renderType = isSecondPhase ?
            RenderType.energySwirl(HALO_TEXTURE, 1f, 1f) :
            RenderType.entityTranslucent(HALO_TEXTURE);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        poseStack.pushPose();

        // 应用旋转动画到各个部件（与原版一致）
        model.bone2.zRot = baseRotation;
        model.bone3.zRot = baseRotation;
        model.bone4.zRot = -baseRotation;
        model.bone5.zRot = -baseRotation;
        model.bone.y = (Mth.cos(baseRotation) + 1);

        // 渲染模型
        model.renderToBuffer(poseStack, vertexConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        poseStack.popPose();

        // 结束批次以确保正确渲染
        bufferSource.endBatch(renderType);

        // 如果是半血状态，添加额外的发光效果
        if (isSecondPhase) {
            VertexConsumer glowConsumer = bufferSource.getBuffer(RenderType.eyes(HALO_TEXTURE));

            poseStack.pushPose();

            // 重新应用旋转
            model.bone2.zRot = baseRotation;
            model.bone3.zRot = baseRotation;
            model.bone4.zRot = -baseRotation;
            model.bone5.zRot = -baseRotation;
            model.bone.y = (Mth.cos(baseRotation) + 1);

            // 设置发光颜色（更亮，带有脉动效果）
            float alpha = 2F; // 半血时更亮
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            model.renderToBuffer(poseStack, glowConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);  // 恢复默认颜色

            poseStack.popPose();
            bufferSource.endBatch(RenderType.eyes(HALO_TEXTURE));
        }
    }
}
