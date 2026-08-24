package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import com.github.yimeng261.maidspell.item.common.StarShadowSpearItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 剑牢法术召出来的星影投枪，飞行中与钉在地上都用它。
 *
 * <p>几何体与贴图就是手持那一份 {@link StarShadowSpearItem#MODEL} ——
 * 原先另有一份 {@code winefox_spear_projectile.geo.json}，是同一把枪的另一次导出，
 * 只是根骨骼旋转不同（长轴在 X 而不是 Z）。同一件东西留两份模型，
 * 在 Blockbench 里改了一份另一份就悄悄对不上了，所以合并成一份。
 *
 * <p>渲染也回到常规实体渲染：原先套着铁魔法的 {@code RenderHelper.magic()} 加
 * {@code Color.LIGHT_GRAY}，把枪画成半透明的灰色发光体，贴图上的配色全丢了。
 */
public class WinefoxSwordProjectileRenderer extends GeoEntityRenderer<WinefoxSwordProjectileEntity> {

    /**
     * 模型里枪柄朝 +Z、枪头朝 −Z，所以"枪尖朝前"在模型局部就是 −Z。
     *
     * <p>实测：整份模型在实体渲染空间里 z ∈ [−2.0245, +2.4233]，
     * 最长的一根轴就是 Z，而 −Z 那一端正是枪尖（{@link #TIP_TO_ORIGIN} 与它对得上）。
     */
    private static final Vector3f MODEL_FORWARD = new Vector3f(0.0F, 0.0F, -1.0F);

    /**
     * 枪尖距模型原点 32.37 像素。不往回挪的话，命中瞬间枪尖会捅到目标后面两格去 ——
     * 判定点在实体坐标上，而枪尖在它前方两格。往 +Z（枪柄方向）挪回来对齐。
     */
    private static final float TIP_TO_ORIGIN = 32.37F / 16.0F;

    public WinefoxSwordProjectileRenderer(EntityRendererProvider.Context context) {
        super(context, new StarEquipmentGeoModel<>(StarShadowSpearItem.MODEL, StarShadowSpearItem.TEXTURE));
        this.shadowRadius = 0.0F;
    }

    /**
     * 让枪尖朝着实际运动方向。
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
        poseStack.translate(0.0F, 0.0F, TIP_TO_ORIGIN);
    }
}
