package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.layer;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.MagicalWinefoxBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 把万法酒狐双手拿着的原版物品画出来。
 *
 * <p>模型里的 {@code RightHandLocator} / {@code LeftHandLocator} 是 TLM 女仆骨架的约定挂点，
 * 专门用来放原版 ItemStack —— 位置、旋转都由动画驱动，本层只负责在那个位置上调
 * {@link ItemInHandRenderer#renderItem}。
 *
 * <p>与 {@link WinefoxBossEquipmentLayer} 的分工：那一层画的是自制 GeckoLib 装备
 * （法帽、长剑、法杖），几何体来自各自的 geo 文件，挂在 {@code witchcap} /
 * {@code MStarShadowSword} 这些骨骼上；本层画的是**其余任何物品**，走原版物品渲染。
 * 主手拿着自制武器时本层跳过，免得同一把武器画两遍。
 *
 * <p>姿势那两行（{@code translate(0, -0.0625, -0.1)} 与 {@code XP.rotationDegrees(-90)}）
 * 抄自 TLM 的 {@code GeckoLayerMaidHeld}：物品模型的 {@code thirdperson_*} 变换是按
 * 原版手臂骨骼的朝向定的，挂点坐标系与它差着这一个旋转，不补上物品就是躺平的。
 */
public class WinefoxBossHeldItemLayer extends GeoRenderLayer<MagicalWinefoxBossEntity> {

    private static final String RIGHT_HAND_BONE = "RightHandLocator";
    private static final String LEFT_HAND_BONE = "LeftHandLocator";

    private final ItemInHandRenderer itemInHandRenderer;

    public WinefoxBossHeldItemLayer(GeoRenderer<MagicalWinefoxBossEntity> renderer,
                                    ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void renderForBone(PoseStack poseStack, MagicalWinefoxBossEntity animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        String boneName = bone.getName();
        boolean rightHand = RIGHT_HAND_BONE.equals(boneName);
        if (!rightHand && !LEFT_HAND_BONE.equals(boneName)) {
            return;
        }

        ItemStack stack = rightHand ? animatable.getMainHandItem() : animatable.getOffhandItem();
        if (stack.isEmpty()) {
            return;
        }
        // 自制装备由 WinefoxBossEquipmentLayer 按自己的骨骼画，这里让开。
        if (WinefoxBossEquipmentLayer.isCustomEquipment(stack)) {
            return;
        }
        // 动画把挂点缩成 0 就是"这一段别显示"，原版物品也得认这个约定。
        if (bone.getScaleX() == 0.0F && bone.getScaleY() == 0.0F && bone.getScaleZ() == 0.0F) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, -0.0625D, -0.1D);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        this.itemInHandRenderer.renderItem(animatable, stack,
                rightHand ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                !rightHand, poseStack, bufferSource, packedLight);
        poseStack.popPose();

        // 物品渲染会把批次切到它自己的 RenderType 上，不切回来的话 boss 剩下的骨骼
        // 会被画进物品那一批里（与 WinefoxBossEquipmentLayer 末尾同一个坑）。
        bufferSource.getBuffer(renderType);
    }
}
