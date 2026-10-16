package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SwordArrayEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SwordArrayLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.redspace.ironsspellbooks.render.RenderHelper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Bakes the existing unanimated sword once per resource reload, then batches all instances. */
public class SwordArrayRenderer extends EntityRenderer<SwordArrayEntity> {
    /** The array covers several blocks; keep the tracked entity visible while any sword is in flight. */
    private static final double MAX_RENDER_DISTANCE = 256.0D;
    private static final ResourceLocation MODEL = new ResourceLocation("irons_spellbooks", "geo/summoned_sword.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("irons_spellbooks",
            "textures/entity/summoned_weapons/summoned_sword.png");
    private static final Vector3f FORWARD = new Vector3f(0, 0, -1);
    private final RenderType renderType;
    private final Map<SwordArrayLayout, PlantedSword[]> plantedSwords = new WeakHashMap<>();
    private BakedGeoModel bakedModel;
    private List<Face> mesh = List.of();
    private AABB meshBounds;
    private Frustum renderFrustum;

    public SwordArrayRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
        renderType = magicRenderType();
    }

    private static RenderType magicRenderType() {
        try {
            return RenderHelper.CustomerRenderType.magic(TEXTURE);
        } catch (NoSuchMethodError ignored) {
            return RenderType.eyes(TEXTURE);
        }
    }

    @Override
    public void render(SwordArrayEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        SwordArrayLayout layout = entity.getLayout();
        if (layout == null) return;
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(MODEL);
        if (model == null) return;
        if (model != bakedModel) {
            bakedModel = model;
            mesh = bakeMesh(model);
            meshBounds = meshBounds(mesh);
            // Resource packs can change the geometry as well as its texture.
            plantedSwords.clear();
        }
        PlantedSword[] planted = plantedSwords.computeIfAbsent(layout,
                key -> plantedSwords(key, meshBounds));
        double age = entity.getArrayAge() + partialTick;
        VertexConsumer consumer = buffers.getBuffer(renderType);
        Quaternionf flightRotation = new Quaternionf();
        Vector3f direction = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < layout.swords().size(); i++) {
            SwordArrayLayout.Sword sword = layout.swords().get(i);
            if (entity.isSwordRemoved(i) || !sword.visible(age)) continue;
            Vec3 point = sword.position(age);
            boolean landed = age >= sword.landingAge();
            Quaternionf rotation = landed ? planted[i].rotation
                    : rotation(sword.velocity(age), sword.roll(), flightRotation, direction);
            AABB bounds = landed ? planted[i].bounds
                    : transformedBounds(meshBounds, rotation, point);
            // A single entity represents the whole array. Once its broad bounds
            // enter the view, skip individual swords that are still off screen.
            if (renderFrustum != null && !renderFrustum.isVisible(bounds.move(entity.position()))) continue;
            poses.pushPose();
            poses.translate(point.x, point.y + SwordArrayLayout.SWORD_WIDTH * 0.5D, point.z);
            poses.mulPose(rotation);
            Matrix4f matrix = poses.last().pose();
            for (Face face : mesh) {
                normal.set(face.nx, face.ny, face.nz);
                poses.last().normal().transform(normal);
                for (int vertex = 0; vertex < face.vertices.length; vertex += 5) {
                    float[] data = face.vertices;
                    consumer.vertex(matrix, data[vertex], data[vertex + 1], data[vertex + 2])
                            .color(0.82745F, 0.82745F, 0.82745F, 1.0F)
                            .uv(data[vertex + 3], data[vertex + 4]).overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(LightTexture.FULL_BRIGHT).normal(normal.x, normal.y, normal.z).endVertex();
                }
            }
            poses.popPose();
        }
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    private static PlantedSword[] plantedSwords(SwordArrayLayout layout, AABB meshBounds) {
        PlantedSword[] result = new PlantedSword[layout.swords().size()];
        Vector3f direction = new Vector3f();
        for (int i = 0; i < result.length; i++) {
            SwordArrayLayout.Sword sword = layout.swords().get(i);
            Quaternionf rotation = rotation(sword.velocity(sword.landingAge()), sword.roll(), new Quaternionf(), direction);
            result[i] = new PlantedSword(rotation,
                    transformedBounds(meshBounds, rotation, sword.position(sword.landingAge())));
        }
        return result;
    }

    static Quaternionf rotation(Vec3 velocity, float roll, Quaternionf result, Vector3f direction) {
        direction.set((float) velocity.x, (float) velocity.y, (float) velocity.z);
        if (direction.lengthSquared() < 1.0E-6F) direction.set(0, -1, 0);
        else direction.normalize();
        return result.rotationTo(FORWARD, direction).rotateZ(roll * ((float) Math.PI / 180.0F));
    }

    static AABB meshBounds(List<Face> mesh) {
        double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (Face face : mesh) {
            float[] data = face.vertices;
            for (int i = 0; i < data.length; i += 5) {
                minX = Math.min(minX, data[i]);
                minY = Math.min(minY, data[i + 1]);
                minZ = Math.min(minZ, data[i + 2]);
                maxX = Math.max(maxX, data[i]);
                maxY = Math.max(maxY, data[i + 1]);
                maxZ = Math.max(maxZ, data[i + 2]);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static AABB transformedBounds(AABB meshBounds, Quaternionf rotation, Vec3 position) {
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner.set((float) ((i & 1) == 0 ? meshBounds.minX : meshBounds.maxX),
                    (float) ((i & 2) == 0 ? meshBounds.minY : meshBounds.maxY),
                    (float) ((i & 4) == 0 ? meshBounds.minZ : meshBounds.maxZ));
            rotation.transform(corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.05D)
                .move(position.x, position.y + SwordArrayLayout.SWORD_WIDTH * 0.5D, position.z);
    }

    static List<Face> bakeMesh(BakedGeoModel model) {
        List<Face> faces = new ArrayList<>();
        PoseStack poses = new PoseStack();
        poses.translate(0, 0.01D, 0);
        for (GeoBone bone : model.topLevelBones()) bakeBone(poses, bone, faces);
        return List.copyOf(faces);
    }

    private static void bakeBone(PoseStack poses, GeoBone bone, List<Face> faces) {
        poses.pushPose();
        // The shared cached bones may still carry a summoned weapon's swing animation.
        BoneSnapshot initial = bone.getInitialSnapshot();
        if (initial == null) initial = bone.saveSnapshot();
        poses.translate(-initial.getOffsetX() / 16.0F, initial.getOffsetY() / 16.0F,
                initial.getOffsetZ() / 16.0F);
        RenderUtils.translateToPivotPoint(poses, bone);
        poses.mulPose(new Quaternionf().rotationZYX(initial.getRotZ(), initial.getRotY(), initial.getRotX()));
        poses.scale(initial.getScaleX(), initial.getScaleY(), initial.getScaleZ());
        RenderUtils.translateAwayFromPivotPoint(poses, bone);
        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                poses.pushPose();
                RenderUtils.translateToPivotPoint(poses, cube);
                RenderUtils.rotateMatrixAroundCube(poses, cube);
                RenderUtils.translateAwayFromPivotPoint(poses, cube);
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    Vector3f normal = poses.last().normal().transform(new Vector3f(quad.normal()));
                    RenderUtils.fixInvertedFlatCube(cube, normal);
                    float[] vertices = new float[quad.vertices().length * 5];
                    int offset = 0;
                    for (GeoVertex vertex : quad.vertices()) {
                        Vector3f point = poses.last().pose().transformPosition(new Vector3f(vertex.position()));
                        vertices[offset++] = point.x;
                        vertices[offset++] = point.y;
                        vertices[offset++] = point.z;
                        vertices[offset++] = vertex.texU();
                        vertices[offset++] = vertex.texV();
                    }
                    faces.add(new Face(vertices, normal.x, normal.y, normal.z));
                }
                poses.popPose();
            }
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) bakeBone(poses, child, faces);
        }
        poses.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SwordArrayEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(SwordArrayEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        renderFrustum = frustum;
        // Entity#shouldRenderAtSqrDistance is intentionally conservative for projectile-sized entities.
        // SwordArrayEntity has one tracked origin but renders a whole ring around it, so test the expanded
        // world-space bounds directly and apply a generous distance fallback for late-arriving clients.
        if (entity.getBoundingBoxForCulling().inflate(2.0D).intersects(
                cameraX - MAX_RENDER_DISTANCE, cameraY - MAX_RENDER_DISTANCE, cameraZ - MAX_RENDER_DISTANCE,
                cameraX + MAX_RENDER_DISTANCE, cameraY + MAX_RENDER_DISTANCE, cameraZ + MAX_RENDER_DISTANCE)) {
            return frustum.isVisible(entity.getBoundingBoxForCulling().inflate(2.0D));
        }
        return false;
    }

    record Face(float[] vertices, float nx, float ny, float nz) {}
    private record PlantedSword(Quaternionf rotation, AABB bounds) {}
}
