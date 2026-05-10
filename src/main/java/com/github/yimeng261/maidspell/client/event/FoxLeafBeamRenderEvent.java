package com.github.yimeng261.maidspell.client.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 当女仆佩戴狐叶饰品且主人在附近流体表面行走时，
 * 在女仆和主人之间绘制一道信标光柱，颜色随距离渐变。
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, value = Dist.CLIENT)
public class FoxLeafBeamRenderEvent {

    private static final float BEAM_WIDTH = 0.22F;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class,
                mc.player.getBoundingBox().inflate(48.0D))) {
            if (!maid.isAlive()) {
                continue;
            }
            boolean hasFloatingLeaf = ItemsUtil.hasBaubleItemInMaid(maid, MaidSpellItems.FLOATING_FOX_LEAF.get());
            boolean hasMoltenLeaf = ItemsUtil.hasBaubleItemInMaid(maid, MaidSpellItems.MOLTEN_FOX_LEAF.get());

            if (!hasFloatingLeaf && !hasMoltenLeaf) {
                continue;
            }

            LivingEntity owner = maid.getOwner();
            if (!(owner instanceof Player) && maid.getOwnerUUID() != null
                    && maid.getOwnerUUID().equals(mc.player.getUUID())) {
                owner = mc.player;
            }
            if (!(owner instanceof Player)) {
                continue;
            }

            BlockPos ownerPos = owner.blockPosition();
            FluidState feetFluid = level.getFluidState(ownerPos);
            FluidState belowFluid = level.getFluidState(ownerPos.below());

            boolean onWater = feetFluid.is(FluidTags.WATER) || belowFluid.is(FluidTags.WATER);
            boolean onLava = feetFluid.is(FluidTags.LAVA) || belowFluid.is(FluidTags.LAVA);
            boolean renderBeam = (hasFloatingLeaf && onWater) || (hasMoltenLeaf && onLava);

            if (!renderBeam) {
                continue;
            }

            double range = hasFloatingLeaf ? Config.floatingFoxLeafOwnerRange : Config.moltenFoxLeafOwnerRange;
            double distSqr = owner.distanceToSqr(maid);
            if (distSqr > range * range) {
                continue;
            }

            renderBeamBetween(poseStack, cameraPos, maid, owner, distSqr, range, gameTime, partialTick);
        }
    }

    private static void renderBeamBetween(PoseStack poseStack, Vec3 cameraPos,
                                          EntityMaid maid, LivingEntity owner,
                                          double distSqr, double range,
                                          long gameTime, float partialTick) {
        float maidHeight = maid.getBbHeight();
        float ownerHeight = owner.getBbHeight();

        Vec3 start = getInterpolatedPos(maid, partialTick).add(0.0D, maidHeight * 0.55D, 0.0D);
        Vec3 end = getInterpolatedPos(owner, partialTick).add(0.0D, ownerHeight * 0.55D, 0.0D);

        Vec3 mid = start.add(end).scale(0.5);
        Vec3 beamDir = end.subtract(start);
        double beamLength = beamDir.length();
        if (beamLength < 0.01) {
            return;
        }
        beamDir = beamDir.normalize();

        Vec3 toCamera = cameraPos.subtract(mid).normalize();
        Vec3 right = beamDir.cross(toCamera);
        double rightLen = right.length();
        if (rightLen < 0.001) {
            return;
        }
        right = right.scale(BEAM_WIDTH * 0.5 / rightLen);

        // 距离颜色：绿 → 黄绿 → 橙 → 红
        float ratio = (float) (Math.sqrt(distSqr) / range);
        float r, g, b;
        if (ratio < 0.33f) {
            float t = ratio / 0.33f;
            r = t;
            g = 1.0f;
            b = 0.2f * (1.0f - t);
        } else if (ratio < 0.66f) {
            float t = (ratio - 0.33f) / 0.33f;
            r = 1.0f;
            g = 1.0f - t * 0.4f;
            b = 0.0f;
        } else {
            float t = Math.min((ratio - 0.66f) / 0.34f, 1.0f);
            r = 1.0f;
            g = 0.6f * (1.0f - t);
            b = 0.1f * t;
        }
        float pulse = 0.85f + 0.15f * (float) Math.sin((gameTime + partialTick) * 0.25f);
        float alpha = (0.55f - ratio * 0.28f) * pulse;
        alpha = Math.max(0.18f, Math.min(alpha, 0.62f));

        double halfLen = beamLength * 0.5;
        Vec3 beamOffset = beamDir.scale(halfLen);

        Vec3 p0 = mid.subtract(right).subtract(beamOffset).subtract(cameraPos);
        Vec3 p1 = mid.add(right).subtract(beamOffset).subtract(cameraPos);
        Vec3 p2 = mid.add(right).add(beamOffset).subtract(cameraPos);
        Vec3 p3 = mid.subtract(right).add(beamOffset).subtract(cameraPos);

        float x0 = (float) p0.x;
        float y0 = (float) p0.y;
        float z0 = (float) p0.z;

        float x1 = (float) p1.x;
        float y1 = (float) p1.y;
        float z1 = (float) p1.z;

        float x2 = (float) p2.x;
        float y2 = (float) p2.y;
        float z2 = (float) p2.z;

        float x3 = (float) p3.x;
        float y3 = (float) p3.y;
        float z3 = (float) p3.z;

        poseStack.pushPose();
        RenderType renderType = RenderType.lightning();
        renderType.setupRenderState();

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        PoseStack.Pose pose = poseStack.last();
        addVertex(builder, pose, x0, y0, z0, r, g, b, alpha);
        addVertex(builder, pose, x1, y1, z1, r, g, b, alpha);
        addVertex(builder, pose, x2, y2, z2, r, g, b, alpha);
        addVertex(builder, pose, x3, y3, z3, r, g, b, alpha);

        MeshData meshData = builder.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }
        renderType.clearRenderState();
        poseStack.popPose();
    }

    private static Vec3 getInterpolatedPos(LivingEntity entity, float partialTick) {
        double x = entity.xo + (entity.getX() - entity.xo) * partialTick;
        double y = entity.yo + (entity.getY() - entity.yo) * partialTick;
        double z = entity.zo + (entity.getZ() - entity.zo) * partialTick;
        return new Vec3(x, y, z);
    }

    private static void addVertex(BufferBuilder builder, PoseStack.Pose pose, float x, float y, float z,
                                  float r, float g, float b, float alpha) {
        builder.addVertex(pose, x, y, z).setColor(r, g, b, alpha);
    }
}
