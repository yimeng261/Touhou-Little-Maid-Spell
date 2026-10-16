package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity.RendererTestAccess;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SevenStarGuidanceEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SwordArrayLayout;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Mismatches;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.magicterra.stagewright.scene.Scene;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.math.MolangQueries;
import software.bernie.geckolib.loading.math.value.Variable;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * 法术渲染器的客户端几何：七星指引切换渲染类型后的线框批次、剑阵缓存网格与剔除包围盒、超新星黑洞模型与动画资源。
 */
public final class SpellRendererClientScenes {
    private static final ResourceLocation CRYSTAL_FACE = ClientAssets.id("textures/entity/seven_star_guidance/crystal_face.png");
    private static final ResourceLocation SWORD_MODEL = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "geo/summoned_sword.geo.json");
    private static final ResourceLocation SUPERNOVA_MODEL = ClientAssets.id("geo/supernova_black_hole.geo.json");
    private static final ResourceLocation SUPERNOVA_ANIMATION = ClientAssets.id("animations/supernova_black_hole.animation.json");
    private static final ResourceLocation SUPERNOVA_TEXTURE = ClientAssets.id("textures/entity/companion_black_hole/supernova.png");
    private static final String SUPERNOVA_IDLE = "animation.supernova_black_hole.idle";

    private SpellRendererClientScenes() {
    }

    public static List<Scene> integratedServer() {
        return List.of(
                scene("client.sevenStar.lockBracketsBackToLinesAfterCrystals", SpellRendererClientScenes::lockBrackets),
                scene("client.sevenStar.ringsAndReticlesBackToLinesAfterBeams", SpellRendererClientScenes::ringsAndReticles),
                scene("client.sevenStar.finaleCrystalsOwnBatch", SpellRendererClientScenes::finaleCrystals),
                scene("client.swordArray.meshKeepsFacesUvsAndNormals", SpellRendererClientScenes::swordMesh),
                scene("client.swordArray.meshUsesRestPose", SpellRendererClientScenes::swordRestPose),
                scene("client.swordArray.swordsStayUpright", SpellRendererClientScenes::swordsUpright),
                scene("client.swordArray.rotationFollowsTrajectory", SpellRendererClientScenes::swordsFaceForward),
                scene("client.swordArray.cullingBoundsContainMesh", SpellRendererClientScenes::cullingBounds),
                scene("client.swordArray.frustumCullsEachSword", SpellRendererClientScenes::frustumCulling),
                scene("client.swordArray.stationaryRotationFinite", SpellRendererClientScenes::stationaryRotation),
                scene("client.supernova.modelBakesWithValidUvs", SpellRendererClientScenes::supernovaModel),
                scene("client.supernova.idleLoopBonesExist", SpellRendererClientScenes::supernovaIdle),
                scene("client.supernova.diskUndersideMatchesTop", SpellRendererClientScenes::diskUnderside),
                scene("client.supernova.clippedDiskTilesDoNotOverlap", SpellRendererClientScenes::clippedDisk),
                scene("client.supernova.diskTrembleKeepsChanging", SpellRendererClientScenes::diskTremble),
                scene("client.supernova.textureTransparentBlackAndPurple", SpellRendererClientScenes::supernovaTexture),
                scene("client.supernova.culledCoreShowsBlack", SpellRendererClientScenes::culledCore));
    }

    private static Scene scene(String name, Consumer<Mismatches> body) {
        return Checks.scene(name, 20, ctx -> ClientSide.verify(ctx, name, body));
    }

    // ---- 七星指引 ----

    private record Batch(RenderType type, MeshData.DrawState state) {
    }

    /** 用真实的共享缓冲和渲染类型切换逻辑，但结束批次时只记下批次，不提交给 OpenGL。 */
    private static final class HeadlessBuffers extends MultiBufferSource.BufferSource implements AutoCloseable {
        private final List<Batch> batches = new ArrayList<>();
        private final ByteBufferBuilder shared;

        private HeadlessBuffers() {
            this(new ByteBufferBuilder(4096));
        }

        private HeadlessBuffers(ByteBufferBuilder shared) {
            super(shared, new LinkedHashMap<>());
            this.shared = shared;
        }

        @Override
        public void endBatch(RenderType type) {
            BufferBuilder builder = startedBuilders.remove(type);
            if (builder != null) {
                try (MeshData mesh = builder.build()) {
                    if (mesh != null) {
                        batches.add(new Batch(type, mesh.drawState()));
                    }
                }
            }
            if (type.equals(lastSharedType)) {
                lastSharedType = null;
            }
        }

        private Batch lastBatch() {
            if (batches.isEmpty()) {
                throw new IllegalStateException("渲染器没有提交任何几何");
            }
            return batches.get(batches.size() - 1);
        }

        @Override
        public void close() {
            shared.close();
        }
    }

    private static void lineBatch(Mismatches m, String label, Batch batch, int vertexCount) {
        m.check(label + " 最后一批是线框，实际 " + batch.type(), batch.type() == RenderType.lines());
        m.equal(label + " 顶点格式", DefaultVertexFormat.POSITION_COLOR_NORMAL, batch.state().format());
        m.equal(label + " 顶点数", vertexCount, batch.state().vertexCount());
    }

    /** 先画过晶体（实体格式）后，锁定括号重新请求线框缓冲，不会写进已结束的晶体批次。 */
    private static void lockBrackets(Mismatches m) {
        try (HeadlessBuffers buffers = new HeadlessBuffers()) {
            buffers.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL_FACE));
            RendererTestAccess.drawLockBrackets(buffers, new Matrix4f(), Vec3.ZERO, 0.72F, 0.5F, 0.75F);
            buffers.endBatch();
            lineBatch(m, "锁定括号", buffers.lastBatch(), 16);
        }
    }

    /** 光束层之后的打击环和最终准星也切回线框。 */
    private static void ringsAndReticles(Mismatches m) {
        try (HeadlessBuffers buffers = new HeadlessBuffers()) {
            buffers.getBuffer(RenderType.eyes(CRYSTAL_FACE));
            RendererTestAccess.drawRing(buffers, new Matrix4f(), Vec3.ZERO, 1.8F, 0.5F, 0.88F, 0.20F, 1.0F, 0.80F, 20);
            buffers.endBatch();
            lineBatch(m, "打击环", buffers.lastBatch(), 80);

            buffers.getBuffer(RenderType.eyes(CRYSTAL_FACE));
            RendererTestAccess.drawReticle(buffers, new Matrix4f(), Vec3.ZERO, 0.65F, 0.8F);
            buffers.endBatch();
            lineBatch(m, "准星", buffers.lastBatch(), 16);
        }
    }

    /** 终幕晶体在连线光束和碎边之后用自己的贴图单独成批。 */
    private static void finaleCrystals(Mismatches m) {
        for (int stage : new int[]{1, 2}) {
            for (int mask : new int[]{0, 1, 0b0101010, 0b1111111}) {
                String label = "阶段 " + stage + " 掩码 " + Integer.toBinaryString(mask);
                try (HeadlessBuffers buffers = new HeadlessBuffers()) {
                    float age = stage == 1 ? SevenStarGuidanceEntity.FINAL_TICK + 8.0F : SevenStarGuidanceEntity.FINALE_SHATTER_TICK + 4.0F;
                    RendererTestAccess.drawFinalConstellation(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                            1.0D, mask, stage, age, new Matrix4f(), buffers);
                    buffers.endBatch();
                    Batch crystals = buffers.lastBatch();
                    m.check(label + " 最后一批是晶体贴图，实际 " + crystals.type(),
                            crystals.type() == RenderType.entityTranslucentEmissive(CRYSTAL_FACE));
                    m.equal(label + " 晶体顶点格式", DefaultVertexFormat.NEW_ENTITY, crystals.state().format());
                    m.equal(label + " 晶体顶点数", 7 * 8 * 4, crystals.state().vertexCount());
                    if (mask == 0b1111111) {
                        m.check(label + " 七星全连时还要画连线效果", buffers.batches.size() > 1);
                        m.check(label + " 晶体顶点不能并进前一批光束或碎片",
                                buffers.batches.size() > 1 && buffers.batches.get(buffers.batches.size() - 2).type() != crystals.type());
                    }
                }
            }
        }
    }

    // ---- 剑阵 ----

    private static BakedGeoModel swordModel() {
        return ClientAssets.geoModel(SWORD_MODEL);
    }

    /** 缓存网格保留原模型的每个面、原样的贴图坐标（可以超出 0~1）和单位法线；重载时重新烘焙结果一致。 */
    private static void swordMesh(Mismatches m) {
        BakedGeoModel model = swordModel();
        List<RendererTestAccess.Face> mesh = new RendererTestAccess.SwordMesh(model).faces();
        List<GeoQuad> sourceFaces = model.topLevelBones().stream().flatMap(bone -> bone.getCubes().stream())
                .flatMap(cube -> Arrays.stream(cube.quads())).filter(Objects::nonNull).toList();
        m.check("原模型有面", !sourceFaces.isEmpty());
        m.equal("缓存网格的面数", sourceFaces.size(), mesh.size());
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int faceIndex = 0; faceIndex < Math.min(mesh.size(), sourceFaces.size()); faceIndex++) {
            var face = mesh.get(faceIndex);
            float[] data = face.vertices();
            m.equal("第 " + faceIndex + " 面的数据长度", 20, data.length);
            Vector3f normal = new Vector3f(face.nx(), face.ny(), face.nz());
            m.check("第 " + faceIndex + " 面法线有限 " + normal, normal.isFinite());
            m.close("第 " + faceIndex + " 面法线长度", 1.0, normal.length(), 1.0E-5);
            for (int i = 0; i < data.length; i += 5) {
                String vertex = "第 " + faceIndex + " 面第 " + i / 5 + " 顶点";
                m.check(vertex + " 坐标有限", new Vector3f(data[i], data[i + 1], data[i + 2]).isFinite());
                GeoVertex original = sourceFaces.get(faceIndex).vertices()[i / 5];
                m.equal(vertex + " u", original.texU(), data[i + 3]);
                m.equal(vertex + " v", original.texV(), data[i + 4]);
                minY = Math.min(minY, data[i + 1]);
                maxY = Math.max(maxY, data[i + 1]);
            }
        }
        m.check("剑身高度 " + (maxY - minY) + " 大于 0.2", maxY - minY > 0.2F);
        m.equal("重新烘焙的面数", mesh.size(), new RendererTestAccess.SwordMesh(model).faces().size());
    }

    /** 共享模型被其他召唤武器的动画改过姿态后，缓存仍按静止姿态烘焙，而且不改回别人的姿态。 */
    private static void swordRestPose(Mismatches m) {
        BakedGeoModel model = swordModel();
        GeoBone bone = model.topLevelBones().get(0);
        // GeckoLib 在召唤武器的动画改动共享骨骼前先保存初始姿态
        bone.saveInitialSnapshot();
        var expected = new RendererTestAccess.SwordMesh(model).faces();
        float animatedRotX = bone.getRotX() + (float) Math.toRadians(90);
        bone.updateRotation(animatedRotX, 0.4F, -0.7F);
        bone.updatePosition(8, 13, -5.5F);
        bone.updateScale(0.8F, 1.2F, 0.9F);
        var actual = new RendererTestAccess.SwordMesh(model).faces();
        m.equal("面数", expected.size(), actual.size());
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            float[] want = expected.get(i).vertices();
            float[] got = actual.get(i).vertices();
            for (int j = 0; j < Math.min(want.length, got.length); j++) {
                m.close("第 " + i + " 面数据 " + j, want[j], got[j], 1.0E-6);
            }
            m.close("第 " + i + " 面 nx", expected.get(i).nx(), actual.get(i).nx(), 1.0E-6);
            m.close("第 " + i + " 面 ny", expected.get(i).ny(), actual.get(i).ny(), 1.0E-6);
            m.close("第 " + i + " 面 nz", expected.get(i).nz(), actual.get(i).nz(), 1.0E-6);
        }
        m.equal("烘焙后骨骼 rotX 保持动画值", animatedRotX, bone.getRotX());
        m.equal("烘焙后骨骼 rotY", 0.4F, bone.getRotY());
        m.equal("烘焙后骨骼 rotZ", -0.7F, bone.getRotZ());
        m.equal("烘焙后骨骼 posY", 13.0F, bone.getPosY());
        m.equal("烘焙后骨骼 scaleY", 1.2F, bone.getScaleY());
    }

    /** 不管随机滚转多少，飞行和插地时剑身都保持竖长。 */
    private static void swordsUpright(Mismatches m) {
        BakedGeoModel model = swordModel();
        GeoBone bone = model.topLevelBones().get(0);
        bone.saveInitialSnapshot();
        bone.setRotX(bone.getRotX() + (float) Math.toRadians(90));
        AABB meshBounds = new RendererTestAccess.SwordMesh(model).bounds();
        for (long seed = 0; seed < 32; seed++) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, seed);
            for (var sword : layout.swords()) {
                for (double age : new double[]{sword.launchAge(), sword.landingAge()}) {
                    Quaternionf rotation = RendererTestAccess.swordRotation(sword.velocity(age), sword.roll());
                    AABB bounds = RendererTestAccess.swordBounds(meshBounds, rotation, sword.position(age));
                    m.check("种子 " + seed + " 第 " + age + " 刻剑身竖长 " + bounds,
                            bounds.getYsize() > Math.max(bounds.getXsize(), bounds.getZsize()));
                }
            }
        }
    }

    /** 飞行和插地时模型前方都指向剑的运动方向。 */
    private static void swordsFaceForward(Mismatches m) {
        SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, 42);
        for (var sword : layout.swords()) {
            for (double age : new double[]{sword.launchAge(), sword.launchAge() + 2.5D, sword.landingAge()}) {
                Quaternionf rotation = RendererTestAccess.swordRotation(sword.velocity(age), sword.roll());
                Vector3f forward = rotation.transform(new Vector3f(0, 0, -1));
                Vec3 velocity = sword.velocity(age);
                Vector3f expected = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z).normalize();
                m.close("第 " + age + " 刻前方", new Vec3(expected), new Vec3(forward), 1.0E-5);
            }
        }
    }

    /** 剔除包围盒在飞行、落地和消失前都包住每个渲染顶点。 */
    private static void cullingBounds(Mismatches m) {
        List<RendererTestAccess.Face> mesh = new RendererTestAccess.SwordMesh(swordModel()).faces();
        AABB meshBounds = new RendererTestAccess.SwordMesh(swordModel()).bounds();
        for (long seed : new long[]{1, 42, 20261005}) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, seed);
            for (var sword : layout.swords()) {
                for (double age : new double[]{sword.launchAge() + 0.5D, sword.landingAge() - 0.25D,
                        sword.landingAge(), sword.expiryAge() - 0.5D}) {
                    Quaternionf rotation = RendererTestAccess.swordRotation(sword.velocity(age), sword.roll());
                    Vec3 position = sword.position(age);
                    AABB bounds = RendererTestAccess.swordBounds(meshBounds, rotation, position);
                    for (var face : mesh) {
                        float[] data = face.vertices();
                        for (int i = 0; i < data.length; i += 5) {
                            Vector3f vertex = rotation.transform(new Vector3f(data[i], data[i + 1], data[i + 2]));
                            m.check("种子 " + seed + " 第 " + age + " 刻顶点 " + vertex + " 在剔除盒内",
                                    bounds.contains(vertex.x + position.x,
                                            vertex.y + position.y + SwordArrayLayout.SWORD_WIDTH * 0.5D, vertex.z + position.z));
                        }
                    }
                }
            }
        }
    }

    /** 逐把剔除：视野边缘的剑保留，视野外和身后的剑跳过。 */
    private static void frustumCulling(Mismatches m) {
        AABB meshBounds = new RendererTestAccess.SwordMesh(swordModel()).bounds();
        Frustum frustum = new Frustum(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(90), 1.0F, 0.1F, 100.0F));
        frustum.prepare(0, 0, 0);
        Quaternionf rotation = RendererTestAccess.swordRotation(new Vec3(0, -1, 0), 45);
        m.equal("正前方", true, frustum.isVisible(RendererTestAccess.swordBounds(meshBounds, rotation, new Vec3(0, 0, -5))));
        m.equal("视野边缘", true, frustum.isVisible(RendererTestAccess.swordBounds(meshBounds, rotation, new Vec3(5, 0, -5))));
        m.equal("视野外", false, frustum.isVisible(RendererTestAccess.swordBounds(meshBounds, rotation, new Vec3(30, 0, -5))));
        m.equal("身后", false, frustum.isVisible(RendererTestAccess.swordBounds(meshBounds, rotation, new Vec3(0, 0, 5))));
    }

    /** 速度为零时朝正下方，旋转仍是有限值。 */
    private static void stationaryRotation(Mismatches m) {
        Vector3f forward = RendererTestAccess.swordRotation(Vec3.ZERO, 127).transform(new Vector3f(0, 0, -1));
        m.check("前方有限 " + forward, forward.isFinite());
        m.close("前方 y", -1.0, forward.y, 1.0E-5);
    }

    // ---- 超新星黑洞 ----

    private static List<GeoBone> bones(GeoBone root) {
        List<GeoBone> bones = new ArrayList<>();
        ClientAssets.collect(root, bones);
        return bones;
    }

    private static GeoBone supernovaBone(String name) {
        return ClientAssets.geoModel(SUPERNOVA_MODEL).getBone(name)
                .orElseThrow(() -> new IllegalStateException("超新星模型缺少骨骼 " + name));
    }

    private static Animation supernovaIdleAnimation() {
        Animation animation = ClientAssets.animations(SUPERNOVA_ANIMATION).getAnimation(SUPERNOVA_IDLE);
        if (animation == null) {
            throw new IllegalStateException("缺少动画 " + SUPERNOVA_IDLE);
        }
        return animation;
    }

    private static BoneAnimation diskAnimation(Animation animation) {
        return Arrays.stream(animation.boneAnimations()).filter(bone -> bone.boneName().equals("blackholeclaub"))
                .findFirst().orElseThrow(() -> new IllegalStateException("动画里没有 blackholeclaub"));
    }

    /** GeckoLib 能烘焙整个模型：19 根骨骼、604 个方块，核心枢轴在原点，每个方块都有贴图面且坐标在 0~1。 */
    private static void supernovaModel(Mismatches m) {
        BakedGeoModel model = ClientAssets.geoModel(SUPERNOVA_MODEL);
        List<GeoBone> bones = new ArrayList<>();
        model.topLevelBones().forEach(bone -> ClientAssets.collect(bone, bones));
        m.equal("骨骼数", 19, bones.size());
        m.equal("方块数", 604, bones.stream().mapToInt(bone -> bone.getCubes().size()).sum());
        GeoBone core = model.getBone("blackholecore").orElse(null);
        m.check("有 blackholecore 骨骼", core != null);
        if (core != null) {
            m.equal("核心枢轴", List.of(0.0F, 0.0F, 0.0F), List.of(core.getPivotX(), core.getPivotY(), core.getPivotZ()));
        }
        for (GeoBone bone : bones) {
            for (GeoCube cube : bone.getCubes()) {
                boolean textured = false;
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    textured = true;
                    for (GeoVertex vertex : quad.vertices()) {
                        m.check(bone.getName() + " 顶点坐标有限", vertex.position().isFinite());
                        m.check(bone.getName() + " u=" + vertex.texU() + " 在 0~1", vertex.texU() >= 0 && vertex.texU() <= 1);
                        m.check(bone.getName() + " v=" + vertex.texV() + " 在 0~1", vertex.texV() >= 0 && vertex.texV() <= 1);
                    }
                }
                m.check(bone.getName() + " 的方块有贴图面", textured);
            }
        }
    }

    /** 待机动画循环播放，8 条骨骼动画都对应模型里的骨骼，吸积盘每秒转 12 圈。 */
    private static void supernovaIdle(Mismatches m) {
        Animation animation = supernovaIdleAnimation();
        m.close("动画时长（刻）", 0.08333D * 20, animation.length(), 1.0E-8D);
        m.check("循环方式是 LOOP", animation.loopType() == Animation.LoopType.LOOP);
        m.equal("骨骼动画数", 8, animation.boneAnimations().length);
        BakedGeoModel model = ClientAssets.geoModel(SUPERNOVA_MODEL);
        for (BoneAnimation bone : animation.boneAnimations()) {
            m.check("模型里有骨骼 " + bone.boneName(), model.getBone(bone.boneName()).isPresent());
        }
        var spin = diskAnimation(animation).rotationKeyFrames().yKeyframes();
        double turns = Math.abs(spin.get(spin.size() - 1).endValue().get() - spin.get(0).startValue().get()) / (2 * Math.PI);
        m.close("吸积盘每秒圈数", 12.0D, turns / (animation.length() / 20), 0.001D);
    }

    /** 吸积盘 64 块瓦片的底面法线朝下，贴图坐标与顶面逐点一致，厚 0.01。 */
    private static void diskUnderside(Mismatches m) {
        int diskTiles = 0;
        for (GeoBone bone : bones(supernovaBone("blackholeclaub"))) {
            for (GeoCube cube : bone.getCubes()) {
                diskTiles++;
                String label = bone.getName() + " 第 " + diskTiles + " 块";
                m.close(label + " 厚度", 0.16D, cube.size().y, 1.0E-6D);
                GeoQuad up = cube.quads()[4];
                GeoQuad down = cube.quads()[5];
                if (up == null || down == null) {
                    m.check(label + " 有顶面和底面", false);
                    continue;
                }
                m.equal(label + " 顶面法线 y", 1.0F, up.normal().y);
                m.equal(label + " 底面法线 y", -1.0F, down.normal().y);
                for (GeoVertex top : up.vertices()) {
                    GeoVertex bottom = Arrays.stream(down.vertices())
                            .filter(vertex -> vertex.position().x == top.position().x && vertex.position().z == top.position().z)
                            .findFirst().orElse(null);
                    if (bottom == null) {
                        m.check(label + " 顶点 " + top.position() + " 在底面有对应点", false);
                        continue;
                    }
                    m.close(label + " u", top.texU(), bottom.texU(), 1.0E-6);
                    m.close(label + " v", top.texV(), bottom.texV(), 1.0E-6);
                    m.close(label + " 上下面高差", 0.01F, top.position().y - bottom.position().y, 1.0E-6);
                }
            }
        }
        m.equal("吸积盘瓦片数", 64, diskTiles);
    }

    /** 原模型相邻瓦片在接缝处重叠（发光叠加）；裁剪后两两不重叠，底面与顶面一致，贴图坐标按原面插值。 */
    private static void clippedDisk(Mismatches m) {
        List<Area> originalTiles = new ArrayList<>();
        List<Area> clippedTiles = new ArrayList<>();
        for (GeoBone bone : bones(supernovaBone("blackholeclaub"))) {
            for (GeoCube cube : bone.getCubes()) {
                originalTiles.add(horizontalArea(cube, Direction.UP));
                GeoCube clipped = RendererTestAccess.clipDiskTile(cube, bone.getPivotX() / 16, bone.getPivotZ() / 16);
                Area area = horizontalArea(clipped, Direction.UP);
                m.check(bone.getName() + " 裁剪后面积为正", areaSize(area) > 1.0E-5D);
                clippedTiles.add(area);
                Area undersideDifference = horizontalArea(clipped, Direction.DOWN);
                undersideDifference.exclusiveOr(area);
                m.check(bone.getName() + " 裁剪后底面与顶面一致", areaSize(undersideDifference) < 1.0E-6D);
                clippedUvs(m, bone.getName(), cube, clipped);
            }
        }
        m.equal("瓦片数", 64, clippedTiles.size());
        int originalOverlaps = 0;
        for (int i = 0; i < clippedTiles.size(); i++) {
            for (int j = i + 1; j < clippedTiles.size(); j++) {
                Area originalOverlap = new Area(originalTiles.get(i));
                originalOverlap.intersect(originalTiles.get(j));
                if (areaSize(originalOverlap) > 1.0E-5D) {
                    originalOverlaps++;
                }
                Area clippedOverlap = new Area(clippedTiles.get(i));
                clippedOverlap.intersect(clippedTiles.get(j));
                m.check("裁剪后瓦片 " + i + " 与 " + j + " 不重叠", areaSize(clippedOverlap) < 1.0E-6D);
            }
        }
        m.check("原模型能复现接缝重叠（" + originalOverlaps + " 处，至少 16）", originalOverlaps >= 16);
    }

    private static void clippedUvs(Mismatches m, String bone, GeoCube original, GeoCube clipped) {
        for (GeoQuad face : clipped.quads()) {
            GeoVertex[] source = original.quads()[face.direction() == Direction.UP ? 4 : 5].vertices();
            Vector3f edgeU = new Vector3f(source[1].position()).sub(source[0].position());
            Vector3f edgeV = new Vector3f(source[3].position()).sub(source[0].position());
            for (GeoVertex vertex : face.vertices()) {
                Vector3f offset = new Vector3f(vertex.position()).sub(source[0].position());
                float u = offset.dot(edgeU) / edgeU.lengthSquared();
                float v = offset.dot(edgeV) / edgeV.lengthSquared();
                m.check(bone + " 裁剪点在原面内 u=" + u + " v=" + v, u >= -1.0E-5F && u <= 1.00001F && v >= -1.0E-5F && v <= 1.00001F);
                m.close(bone + " 裁剪点 u", source[0].texU() + u * (source[1].texU() - source[0].texU())
                        + v * (source[3].texU() - source[0].texU()), vertex.texU(), 1.0E-6);
                m.close(bone + " 裁剪点 v", source[0].texV() + u * (source[1].texV() - source[0].texV())
                        + v * (source[3].texV() - source[0].texV()), vertex.texV(), 1.0E-6);
            }
        }
    }

    private static Matrix4f cubeTransform(GeoCube cube) {
        var pivot = cube.pivot();
        var rotation = cube.rotation();
        return new Matrix4f()
                .translate((float) pivot.x / 16, (float) pivot.y / 16, (float) pivot.z / 16)
                .rotateZ((float) rotation.z).rotateY((float) rotation.y).rotateX((float) rotation.x)
                .translate((float) -pivot.x / 16, (float) -pivot.y / 16, (float) -pivot.z / 16);
    }

    private static Area horizontalArea(GeoCube cube, Direction side) {
        Matrix4f transform = cubeTransform(cube);
        Area area = new Area();
        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.direction() != side) {
                continue;
            }
            Path2D.Float polygon = new Path2D.Float();
            for (int i = 0; i < quad.vertices().length; i++) {
                Vector3f point = transform.transformPosition(new Vector3f(quad.vertices()[i].position()));
                if (i == 0) {
                    polygon.moveTo(point.x, point.z);
                } else {
                    polygon.lineTo(point.x, point.z);
                }
            }
            polygon.closePath();
            area.add(new Area(polygon));
        }
        return area;
    }

    private static double areaSize(Area area) {
        double total = 0, firstX = 0, firstY = 0, previousX = 0, previousY = 0;
        double[] point = new double[6];
        for (PathIterator iterator = area.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            int type = iterator.currentSegment(point);
            if (type == PathIterator.SEG_MOVETO) {
                firstX = previousX = point[0];
                firstY = previousY = point[1];
            } else if (type == PathIterator.SEG_LINETO) {
                total += previousX * point[1] - point[0] * previousY;
                previousX = point[0];
                previousY = point[1];
            } else if (type == PathIterator.SEG_CLOSE) {
                total += previousX * firstY - firstX * previousY;
            }
        }
        return Math.abs(total) / 2;
    }

    /** 吸积盘的倾斜抖动和缩放抖动在旋转循环重置时仍持续变化，幅度保持在原设计范围内。 */
    private static void diskTremble(Mismatches m) {
        BoneAnimation disk = diskAnimation(supernovaIdleAnimation());
        List<MathValue> values = List.of(disk.rotationKeyFrames().xKeyframes().get(0).endValue(),
                disk.rotationKeyFrames().zKeyframes().get(0).endValue(),
                disk.scaleKeyFrames().xKeyframes().get(0).endValue());
        Variable lifeTime = MathParser.getVariableFor(MolangQueries.LIFE_TIME);
        DoubleSupplier previous = lifeTime.value().get();
        double[] time = {0};
        lifeTime.set(() -> time[0]);
        try {
            List<String> axes = List.of("倾斜 x", "倾斜 z", "缩放 x");
            for (int axis = 0; axis < values.size(); axis++) {
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                int crossings = 0;
                double last = 0;
                for (int frame = 0; frame <= 240; frame++) {
                    time[0] = frame / 120.0D;
                    double value = values.get(axis).get();
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    double offset = value - (axis == 2 ? 1 : 0);
                    if (last * offset < 0) {
                        crossings++;
                    }
                    if (Math.abs(offset) > 1.0E-7D) {
                        last = offset;
                    }
                }
                String label = axes.get(axis);
                m.check(label + " 2 秒内过零 " + crossings + " 次，至少 16", crossings >= 16);
                if (axis == 2) {
                    m.check(label + " 范围 [" + min + ", " + max + "] 在 0.92~1.08 且幅度超过 0.08",
                            min >= 0.92 && max <= 1.08 && max - min > 0.08);
                } else {
                    m.check(label + " 范围 [" + min + ", " + max + "] 在 ±2 且幅度超过 2",
                            min >= -2.000001 && max <= 2.000001 && max - min > 2.0);
                }
            }
        } finally {
            lifeTime.set(previous);
        }
    }

    /** 贴图 1024×1024，保留透明区域、不透明纯黑核心与紫色边。 */
    private static void supernovaTexture(Mismatches m) {
        BufferedImage texture = ClientAssets.image(SUPERNOVA_TEXTURE);
        m.equal("贴图宽", 1024, texture.getWidth());
        m.equal("贴图高", 1024, texture.getHeight());
        boolean transparent = false, black = false, purple = false;
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int pixel = texture.getRGB(x, y);
                transparent |= (pixel >>> 24) == 0;
                black |= pixel == 0xFF000000;
                purple |= pixel == 0xFF8244FF;
            }
        }
        m.check("有全透明像素", transparent);
        m.check("有不透明纯黑像素", black);
        m.check("有 #8244FF 紫色像素", purple);
    }

    private record TexturedTriangle(Vector3f[] positions, GeoVertex[] textureCoordinates) {
    }

    /** 开背面剔除时从各个角度看核心都是纯黑；关掉剔除则能看到朝内的紫色面（证明这个场景能复现问题）。 */
    private static void culledCore(Mismatches m) {
        List<TexturedTriangle> triangles = new ArrayList<>();
        for (GeoBone bone : bones(supernovaBone("blackholecore"))) {
            for (GeoCube cube : bone.getCubes()) {
                Matrix4f transform = cubeTransform(cube);
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    GeoVertex[] vertices = quad.vertices();
                    for (int[] indices : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                        Vector3f[] positions = new Vector3f[3];
                        GeoVertex[] textureCoordinates = new GeoVertex[3];
                        for (int i = 0; i < 3; i++) {
                            textureCoordinates[i] = vertices[indices[i]];
                            positions[i] = transform.transformPosition(new Vector3f(textureCoordinates[i].position()));
                        }
                        triangles.add(new TexturedTriangle(positions, textureCoordinates));
                    }
                }
            }
        }
        BufferedImage texture = ClientAssets.image(SUPERNOVA_TEXTURE);
        boolean exposedPurpleBackface = false;
        for (int elevation : new int[]{-45, 0, 45, 80}) {
            for (int azimuth = 0; azimuth < 360; azimuth += 45) {
                double pitch = Math.toRadians(elevation);
                double yaw = Math.toRadians(azimuth);
                Vector3f camera = new Vector3f((float) (Math.cos(pitch) * Math.sin(yaw)),
                        (float) Math.sin(pitch), (float) (Math.cos(pitch) * Math.cos(yaw))).mul(4);
                int culled = sampleCore(triangles, texture, camera, true);
                m.equal("仰角 " + elevation + " 方位 " + azimuth + " 剔除后的颜色", Integer.toHexString(0xFF000000), Integer.toHexString(culled));
                exposedPurpleBackface |= sampleCore(triangles, texture, camera, false) != 0xFF000000;
            }
        }
        m.check("不剔除时能看到朝内的面", exposedPurpleBackface);
    }

    private static int sampleCore(List<TexturedTriangle> triangles, BufferedImage texture, Vector3f camera, boolean cull) {
        Vector3f direction = new Vector3f(camera).negate().normalize();
        float nearest = Float.POSITIVE_INFINITY;
        int color = 0;
        for (TexturedTriangle triangle : triangles) {
            Vector3f[] p = triangle.positions();
            float distance = cull
                    ? Intersectionf.intersectRayTriangleFront(camera, direction, p[0], p[1], p[2], 1.0E-6F)
                    : Intersectionf.intersectRayTriangle(camera, direction, p[0], p[1], p[2], 1.0E-6F);
            if (distance <= 0 || distance >= nearest) {
                continue;
            }
            Vector3f edge1 = new Vector3f(p[1]).sub(p[0]);
            Vector3f edge2 = new Vector3f(p[2]).sub(p[0]);
            Vector3f hit = new Vector3f(direction).mul(distance).add(camera).sub(p[0]);
            float aa = edge1.dot(edge1), ab = edge1.dot(edge2), bb = edge2.dot(edge2);
            float denominator = aa * bb - ab * ab;
            float v = (bb * hit.dot(edge1) - ab * hit.dot(edge2)) / denominator;
            float w = (aa * hit.dot(edge2) - ab * hit.dot(edge1)) / denominator;
            GeoVertex[] uv = triangle.textureCoordinates();
            float uTex = (1 - v - w) * uv[0].texU() + v * uv[1].texU() + w * uv[2].texU();
            float vTex = (1 - v - w) * uv[0].texV() + v * uv[1].texV() + w * uv[2].texV();
            int pixel = texture.getRGB(Math.max(0, Math.min(texture.getWidth() - 1, (int) (uTex * texture.getWidth()))),
                    Math.max(0, Math.min(texture.getHeight() - 1, (int) (vTex * texture.getHeight()))));
            if ((pixel >>> 24) < 26) {
                continue;
            }
            nearest = distance;
            color = pixel;
        }
        return color;
    }
}
