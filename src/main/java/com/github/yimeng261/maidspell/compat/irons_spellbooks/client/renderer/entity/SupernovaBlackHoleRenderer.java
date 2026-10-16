package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.core.object.Color;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;
import software.bernie.geckolib.core.animatable.GeoAnimatable;

/** The supplied Supernova model is centered on its core instead of its original Y=41.1 pivot. */
public abstract class SupernovaBlackHoleRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private static final ResourceLocation MODEL = new ResourceLocation(MaidSpellMod.MOD_ID,
            "geo/supernova_black_hole.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(MaidSpellMod.MOD_ID,
            "textures/entity/companion_black_hole/supernova.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(MaidSpellMod.MOD_ID,
            "animations/supernova_black_hole.animation.json");
    private static final Color MODEL_COLOR = Color.ofRGB(0.65F, 0.65F, 0.65F);
    private boolean renderingGlow;
    private GeoBone diskTileBone;
    private final Map<GeoCube, GeoCube> diskMeshes = new WeakHashMap<>();

    protected SupernovaBlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context, new StarEquipmentGeoModel<>(MODEL, TEXTURE, ANIMATION));
        this.shadowRadius = 0.0F;
        addRenderLayer(new SupernovaBlackHoleGlowLayer<>(this));
    }

    @Override
    public void render(T entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        float scale = modelScale(entity, partialTick);
        if (scale <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        translateToCenter(entity, partialTick, poseStack);
        poseStack.scale(scale, scale, scale);
        // Self-lit base color and the separate glow layer have independent intensities.
        super.render(entity, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public RenderType getRenderType(T entity, ResourceLocation texture,
                                    MultiBufferSource buffers, float partialTick) {
        return SupernovaBlackHoleRenderType.DISK;
    }

    @Override
    public Color getRenderColor(T entity, float partialTick, int light) {
        return MODEL_COLOR;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, T entity, GeoBone bone,
                                  RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        if (bone.getName().equals("blackholecore")) {
            if (renderType == SupernovaBlackHoleRenderType.GLOW) {
                return;
            }
            renderType = SupernovaBlackHoleRenderType.CORE;
            buffer = buffers.getBuffer(renderType);
        }
        boolean previousGlow = this.renderingGlow;
        GeoBone previousDiskTile = this.diskTileBone;
        this.renderingGlow = renderType == SupernovaBlackHoleRenderType.GLOW;
        this.diskTileBone = bone.getName().startsWith("ysmGlowbone") ? bone : null;
        try {
            super.renderRecursively(poseStack, entity, bone, renderType, buffers, buffer,
                    isReRender, partialTick, light, overlay, red, green, blue, alpha);
        } finally {
            this.renderingGlow = previousGlow;
            this.diskTileBone = previousDiskTile;
        }
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int light,
                           int overlay, float red, float green, float blue, float alpha) {
        if (this.diskTileBone != null) {
            GeoBone tileBone = this.diskTileBone;
            cube = this.diskMeshes.computeIfAbsent(cube, original -> SupernovaBlackHoleDiskMesh.clip(
                    original, tileBone.getPivotX() / 16, tileBone.getPivotZ() / 16));
        }
        super.renderCube(poseStack, cube, buffer, light, overlay, red, green, blue, alpha);
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buffer,
                                     int light, int overlay, float red, float green, float blue, float alpha) {
        // Compensate for the base material's downward-face shading without brightening the top.
        if (this.renderingGlow && quad.direction() == Direction.DOWN) {
            red = green = blue = SupernovaBlackHoleGlowLayer.UNDERSIDE_GLOW_INTENSITY;
        }
        super.createVerticesOfQuad(quad, pose, normal, buffer, light, overlay, red, green, blue, alpha);
    }

    @Override
    protected void applyRotations(T entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        // The disk stays horizontal in world space; only its animated bones rotate.
    }

    protected abstract float modelScale(T entity, float partialTick);
    protected abstract void translateToCenter(T entity, float partialTick, PoseStack poses);
}
