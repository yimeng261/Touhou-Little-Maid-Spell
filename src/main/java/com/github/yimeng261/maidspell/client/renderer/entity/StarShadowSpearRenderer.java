package com.github.yimeng261.maidspell.client.renderer.entity;

import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.github.yimeng261.maidspell.entity.StarShadowSpearEntity;
import com.github.yimeng261.maidspell.item.common.StarShadowSpearItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 飞行中的星影投枪，用的就是手持时那份几何体与贴图。
 *
 * <p>朝向照抄 {@code ThrownTridentRenderer}：先把枪身摆成原版三叉戟模型的姿态，
 * 再套用原版那两步旋转，这样飞行姿态与三叉戟完全一致。
 */
public class StarShadowSpearRenderer extends GeoEntityRenderer<StarShadowSpearEntity> {

    /**
     * 把枪身从自己的坐标系摆到原版 {@code TridentModel} 的坐标系。
     *
     * <p>模型里枪柄朝 +Z、枪头朝 -Z，根骨骼那个绕 Z 的旋转不改变 Z 轴，
     * 所以在 GeckoLib 的实体渲染空间里枪尖仍是 -Z；原版三叉戟模型的枪尖是 -Y。
     * 绕 X 转 -90° 正好把 -Z 对到 -Y，同时枪头那几片翼保持在 X 轴上 ——
     * 和三叉戟的三个尖齿一个方向。
     *
     * <p>与 {@code WinefoxSwordProjectileRenderer} 的差别只在这一步：
     * 那边是纯法术弹体，没有 {@code yRot/xRot} 可用，直接按速度向量转；
     * 这边是真的 {@code ThrownTrident}，两个角由原版维护，照抄原版的用法最稳。
     */
    private static final float TRIDENT_FRAME_ROTATION = -90.0F;

    /**
     * 枪尖在模型里距原点 32.37 像素。原版三叉戟只有 4 像素，差别看不出来；
     * 这把枪有两格多，不往回挪的话命中瞬间枪尖会捅到目标后面两格去。
     * 往 +Z（枪柄方向）挪回来，让枪尖正好落在实体坐标上，也就是判定点上。
     */
    private static final float TIP_TO_ORIGIN = 32.37F / 16.0F;

    public StarShadowSpearRenderer(EntityRendererProvider.Context context) {
        super(context, new StarEquipmentGeoModel<>(StarShadowSpearItem.MODEL, StarShadowSpearItem.TEXTURE));
        this.shadowRadius = 0.0F;
    }

    /**
     * {@code AbstractArrow} 存的 yRot/xRot 不是实体朝向那套约定 ——
     * 它是 {@code atan2(x, z)} / {@code atan2(y, 水平距离)}，两个符号都跟朝向相反。
     * 所以这里必须照抄原版箭矢/三叉戟的 {@code yaw - 90} 与 {@code pitch + 90}，
     * 不能拿 {@code Vec3.directionFromRotation} 去算方向。
     */
    @Override
    protected void applyRotations(StarShadowSpearEntity entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) + 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(TRIDENT_FRAME_ROTATION));
        poseStack.translate(0.0F, 0.0F, TIP_TO_ORIGIN);
    }
}
