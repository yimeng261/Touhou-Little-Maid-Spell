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
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.util.Color;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;

/** 提供的超新星模型以核心为中心，而不是原来 Y=41.1 的枢轴。 */
public abstract class SupernovaBlackHoleRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/supernova_black_hole.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/companion_black_hole/supernova.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "animations/supernova_black_hole.animation.json");
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
        // 自发光的底色与独立的发光层各自拥有独立的强度。
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
                                  int colour) {
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
                    isReRender, partialTick, light, overlay, colour);
        } finally {
            this.renderingGlow = previousGlow;
            this.diskTileBone = previousDiskTile;
        }
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int light,
                           int overlay, int colour) {
        if (this.diskTileBone != null) {
            GeoBone tileBone = this.diskTileBone;
            cube = this.diskMeshes.computeIfAbsent(cube, original -> SupernovaBlackHoleDiskMesh.clip(
                    original, tileBone.getPivotX() / 16, tileBone.getPivotZ() / 16));
        }
        super.renderCube(poseStack, cube, buffer, light, overlay, colour);
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal, VertexConsumer buffer,
                                     int light, int overlay, int colour) {
        // 补偿底材质朝下面的着色，同时不提亮顶面。
        if (this.renderingGlow && quad.direction() == Direction.DOWN) {
            float intensity = SupernovaBlackHoleGlowLayer.UNDERSIDE_GLOW_INTENSITY;
            colour = Color.ofRGBA(intensity, intensity, intensity, (colour >>> 24) / 255.0F).argbInt();
        }
        super.createVerticesOfQuad(quad, pose, normal, buffer, light, overlay, colour);
    }

    @Override
    protected void applyRotations(T entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        // 圆盘在世界坐标中保持水平，只有带动画的骨骼在旋转。
    }

    protected abstract float modelScale(T entity, float partialTick);
    protected abstract void translateToCenter(T entity, float partialTick, PoseStack poses);
}
