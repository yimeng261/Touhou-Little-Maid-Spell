package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.render.RenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 剑牢沿用铁魔法召唤利剑的模型与贴图，落地后保持入射朝向。 */
public class WinefoxSwordProjectileRenderer extends GeoEntityRenderer<WinefoxSwordProjectileEntity> {

    private static final Vector3f MODEL_FORWARD = new Vector3f(0.0F, 0.0F, -1.0F);

    public WinefoxSwordProjectileRenderer(EntityRendererProvider.Context context) {
        super(context, new StarEquipmentGeoModel<>(
            new ResourceLocation("irons_spellbooks", "geo/summoned_sword.geo.json"),
            new ResourceLocation("irons_spellbooks", "textures/entity/summoned_weapons/summoned_sword.png")));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, WinefoxSwordProjectileEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, float red, float green,
                          float blue, float alpha) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
    }

    @Override
    public RenderType getRenderType(WinefoxSwordProjectileEntity entity, ResourceLocation texture,
                                    MultiBufferSource buffer, float partialTick) {
        return RenderHelper.CustomerRenderType.magic(texture);
    }

    @Override
    public Color getRenderColor(WinefoxSwordProjectileEntity entity, float partialTick, int packedLight) {
        return Color.LIGHT_GRAY;
    }

    /**
     * 让剑尖朝着实际运动方向。
     *
     * <p>这里**不能**照抄箭矢那套 {@code yRot/xRot}：{@code AbstractArrow} 会维护这两个角，
     * 而本实体继承的是 {@code AbstractMagicProjectile}，它们一直是 0 ——
     * 原来"方向乱七八糟"就是这么来的。直接拿 {@code getDeltaMovement()} 算。
     *
     * <p>钉在地上之后 {@code deltaMovement} 被清零，改用入射时存下来的
     * {@code getPlantedDirection()}，否则一落地枪就弹回默认朝向。
     */
    @Override
    protected void applyRotations(WinefoxSwordProjectileEntity entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        Vec3 direction = entity.isPlanted()
                ? entity.getPlantedDirection()
                : entity.getDeltaMovement();
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, -1.0D, 0.0D);
        }

        Vector3f target = new Vector3f(
                (float) direction.x, (float) direction.y, (float) direction.z).normalize();
        poseStack.mulPose(new Quaternionf().rotationTo(MODEL_FORWARD, target));
        // 绕自身长轴的滚转，让一圈剑不是齐刷刷同一个面朝外。
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRoll()));
    }
}
