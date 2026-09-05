package com.github.yimeng261.maidspell.client.renderer.item;

import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import javax.annotation.Nullable;

/**
 * 物品栏里画平面图标，其余场合（手持、头顶、展示框、掉落物）还是 GeckoLib 的 3D 模型。
 *
 * <p>平面那份是一个普通的 {@code item/generated} 模型，得在 {@code ModelEvent.RegisterAdditional}
 * 里登记才会被烘焙 —— 没有任何物品直接引用它。
 */
public class StarEquipmentItemRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {

    @Nullable
    private final ResourceLocation guiModel;

    public StarEquipmentItemRenderer(StarEquipmentGeoModel<T> model, @Nullable ResourceLocation guiModel) {
        super(model);
        this.guiModel = guiModel;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (context == ItemDisplayContext.GUI && this.guiModel != null) {
            renderFlatIcon(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        super.renderByItem(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
    }

    /**
     * {@code ItemRenderer.render} 在调进来之前已经做过 translate(-0.5, -0.5, -0.5)，
     * 而下面这次 render 会自己再做一遍，所以先抵掉。物品栏槽位的 display 变换必须是单位变换，
     * 否则平面图标会被 3D 模型那套角度转歪。
     */
    private void renderFlatIcon(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel flat = minecraft.getModelManager().getModel(this.guiModel);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        minecraft.getItemRenderer().render(stack, context, false, poseStack, bufferSource,
                packedLight, packedOverlay, flat);
        poseStack.popPose();
    }
}
