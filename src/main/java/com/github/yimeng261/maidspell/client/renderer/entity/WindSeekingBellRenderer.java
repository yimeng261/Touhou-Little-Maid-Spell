package com.github.yimeng261.maidspell.client.renderer.entity;

import com.github.yimeng261.maidspell.entity.WindSeekingBellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 寻风之铃实体渲染器
 */
public class WindSeekingBellRenderer extends EntityRenderer<WindSeekingBellEntity> {
    private final ItemRenderer itemRenderer;

    public WindSeekingBellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(WindSeekingBellEntity entity, float entityYaw, float partialTicks,
                      PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 与 ThrownItemRenderer 一致：前 2 tick 且距离很近时跳过，避免穿模
        if (entity.tickCount < 2 && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) {
            return;
        }

        ItemStack itemStack = entity.getItem();
        if (itemStack.isEmpty()) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        poseStack.pushPose();

        // 先面向摄像机（公告板），再绕 Z 轴自旋，使铃铛始终正面朝向玩家
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float spin = (entity.tickCount + partialTicks) * 4.0F;
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));

        this.itemRenderer.renderStatic(
                itemStack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(WindSeekingBellEntity entity) {
        // 不需要单独的纹理，因为我们直接渲染物品
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }
}
