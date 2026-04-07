package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.github.yimeng261.maidspell.client.model.AscensionHaloModel;
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogHaloStyle;
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogNecklace;
import com.github.yimeng261.maidspell.item.bauble.transmogNecklace.TransmogNecklaceBauble;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

public class GeckoLayerTransmogHalo<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {
    private static final int LIGHT_LEVEL = 0xFF00F0;

    private AscensionHaloModel model;

    public GeckoLayerTransmogHalo(R renderer) {
        super(renderer);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R renderer) {
        return new GeckoLayerTransmogHalo<>(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        EntityMaid maid = IMaid.convertToMaid(entity);
        if (maid == null || maid.isInvisible() || maid.isSleeping() || !MaidHaloRenderPriority.shouldRenderTransmogHalo(maid)) {
            return;
        }

        ItemStack necklace = TransmogNecklaceBauble.findTransmogNecklace(maid);
        if (necklace.isEmpty()) {
            return;
        }

        if (model == null) {
            model = new AscensionHaloModel(Minecraft.getInstance().getEntityModels().bakeLayer(AscensionHaloModel.LAYER_LOCATION));
        }

        ILocationModel locationModel = getLocationModel(entity);
        if (locationModel == null || locationModel.headBones().isEmpty()) {
            return;
        }

        TransmogHaloStyle style = TransmogNecklace.getSelectedStyle(necklace);
        poseStack.pushPose();
        RenderUtils.prepMatrixForLocator(poseStack, locationModel.headBones());
        poseStack.translate(0, 0.4, 0.6);
        renderHalo(poseStack, buffer, style);
        poseStack.popPose();
    }

    private void renderHalo(PoseStack poseStack, MultiBufferSource buffer, TransmogHaloStyle style) {
        VertexConsumer glowConsumer = buffer.getBuffer(RenderType.eyes(style.texture()));
        poseStack.pushPose();
        poseStack.scale(2.24F, 2.24F, 2.24F);
        model.renderToBuffer(poseStack, glowConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();
    }
}
