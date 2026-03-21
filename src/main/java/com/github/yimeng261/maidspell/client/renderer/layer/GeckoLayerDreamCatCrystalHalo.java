package com.github.yimeng261.maidspell.client.renderer.layer;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.model.AscensionHaloModel;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * 梦云水晶任务光环渲染层
 */
public class GeckoLayerDreamCatCrystalHalo<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {
    private static final int LIGHT_LEVEL = 0xFF00F0;
    private static final float HALO_SCALE = 2.32F;
    private static final float BASE_ROTATION_DEGREES = 180.0F;

    private static final ResourceLocation[] HALO_BASE = {
        texture("dream_halo_1.png"),
        texture("dream_halo_2.png"),
        texture("dream_halo_3.png")
    };

    private static final ResourceLocation[] HALO_PART_1 = {
        texture("dream_halo_1_part_1.png"),
        texture("dream_halo_2_part_1.png"),
        texture("dream_halo_3_part_1.png")
    };

    private static final ResourceLocation[] HALO_PART_2 = {
        texture("dream_halo_1_part_2.png"),
        texture("dream_halo_2_part_2.png"),
        texture("dream_halo_3_part_2.png")
    };

    private AscensionHaloModel model;

    public GeckoLayerDreamCatCrystalHalo(R entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRendererIn) {
        return new GeckoLayerDreamCatCrystalHalo<>(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        EntityMaid maid = IMaid.convertToMaid(entity);
        if (maid == null || maid.isInvisible() || maid.isSleeping()) {
            return;
        }

        if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) {
            return;
        }

        if (model == null) {
            model = new AscensionHaloModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(AscensionHaloModel.LAYER_LOCATION)
            );
        }

        ILocationModel locationModel = getLocationModel(entity);
        if (locationModel == null || locationModel.headBones().isEmpty()) {
            return;
        }

        int haloIndex = getHaloIndex(maid);
        float clockwiseRotation = ageInTicks * 2.4F;
        float counterClockwiseRotation = -ageInTicks * 1.9F;

        poseStack.pushPose();
        RenderUtils.prepMatrixForLocator(poseStack, locationModel.headBones());
        poseStack.translate(0, 0.44, 0.6);
        renderLayer(poseStack, buffer, HALO_BASE[haloIndex], HALO_SCALE, 0.0F, BASE_ROTATION_DEGREES);
        renderLayer(poseStack, buffer, HALO_PART_1[haloIndex], HALO_SCALE, 0.0006F, BASE_ROTATION_DEGREES + clockwiseRotation);
        renderLayer(poseStack, buffer, HALO_PART_2[haloIndex], HALO_SCALE, -0.0006F, BASE_ROTATION_DEGREES + counterClockwiseRotation);
        poseStack.popPose();
    }

    private void renderLayer(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture,
                             float scale, float zOffset, float rotationDegrees) {
        VertexConsumer glowConsumer = buffer.getBuffer(RenderType.eyes(texture));
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0F, 0.0F, zOffset);
        if (rotationDegrees != 0.0F) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationDegrees));
        }
        model.renderToBuffer(poseStack, glowConsumer, LIGHT_LEVEL, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        poseStack.popPose();
    }

    private static int getHaloIndex(EntityMaid maid) {
        ResourceLocation taskUid = maid.getTask().getUid();
        if (SpellCombatFarTask.UID.equals(taskUid)) {
            return 2;
        }
        if (SpellCombatMeleeTask.UID.equals(taskUid)) {
            return 1;
        }
        return 0;
    }

    private static ResourceLocation texture(String path) {
        return new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/maid/halo/" + path);
    }
}
