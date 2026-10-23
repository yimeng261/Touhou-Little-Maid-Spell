package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedMagicMissileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.IronsSpellbooks;
import io.redspace.ironsspellbooks.entity.spells.fireball.FireballRenderer;
import io.redspace.ironsspellbooks.render.RenderHelper;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 与铁魔法魔法飞弹相同的外观：能量弹体加一圈旋转的光晕。
 * <p>
 * 铁魔法的渲染器只接受它自己的飞弹类，这里按同样的画法渲染本模组的飞弹。
 */
public class ModifiedMagicMissileRenderer extends EntityRenderer<ModifiedMagicMissileEntity> {
    private static final ResourceLocation TEXTURE = IronsSpellbooks.id("textures/entity/magic_missile/magic_missile.png");
    private static final ResourceLocation FLARE = IronsSpellbooks.id("textures/entity/lens_flare.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final ModelPart body;

    public ModifiedMagicMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.body = context.bakeLayer(FireballRenderer.MODEL_LAYER_LOCATION).getChild("body");
    }

    @Override
    public void render(@NotNull ModifiedMagicMissileEntity entity, float yaw, float partialTicks,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int light) {
        poseStack.pushPose();
        Vec3 motion = entity.getDeltaMovement();
        float xRot = -((float) (Mth.atan2(motion.horizontalDistance(), motion.y) * Mth.RAD_TO_DEG) - 90.0F);
        float yRot = -((float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) + 90.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
        poseStack.scale(0.35F, 0.35F, 0.35F);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.energySwirl(TEXTURE, 0.0F, 0.0F));
        this.body.render(poseStack, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, RenderHelper.colorf(0.8F, 0.8F, 0.8F));
        poseStack.popPose();

        poseStack.pushPose();
        Matrix4f poseMatrix = poseStack.last().pose();
        float age = entity.tickCount + partialTicks;
        float scale = 0.5F + Mth.sin(age) * 0.125F;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 15.0F));
        consumer = bufferSource.getBuffer(RenderType.entityTranslucent(FLARE));
        flareVertex(consumer, poseMatrix, -1.0F, -1.0F, 0.0F, 1.0F);
        flareVertex(consumer, poseMatrix, 1.0F, -1.0F, 0.0F, 0.0F);
        flareVertex(consumer, poseMatrix, 1.0F, 1.0F, 1.0F, 0.0F);
        flareVertex(consumer, poseMatrix, -1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        super.render(entity, yaw, partialTicks, poseStack, bufferSource, light);
    }

    private static void flareVertex(VertexConsumer consumer, Matrix4f pose, float y, float z, float u, float v) {
        consumer.addVertex(pose, 0.0F, y, z)
                .setColor(255, 180, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ModifiedMagicMissileEntity entity) {
        return TEXTURE;
    }
}
