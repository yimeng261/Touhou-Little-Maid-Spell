package com.github.yimeng261.maidspell.stagewright.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation.PalmBlackHoleReleasePose;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.animation.PalmBlackHoleRippleAlignment;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Mismatches;
import com.google.gson.JsonObject;
import net.magicterra.stagewright.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 掌心黑洞的客户端表现：脱手后的位置与姿态、涟漪跟手对齐，以及模型、动画、音效资源。
 */
public final class PalmBlackHoleClientScenes {
    private static final List<String> RIPPLES = List.of("heobo-z", "heobo-z2");

    private PalmBlackHoleClientScenes() {
    }

    public static List<Scene> integratedServer() {
        return List.of(
                scene("client.palmBlackHole.release.holdKeepsLastVisibleCenter", PalmBlackHoleClientScenes::holdKeepsCenter),
                scene("client.palmBlackHole.release.fallReachesLanding", PalmBlackHoleClientScenes::fallReachesLanding),
                scene("client.palmBlackHole.release.detachedPoseMatchesLocator", PalmBlackHoleClientScenes::detachedPose),
                scene("client.palmBlackHole.release.staleBuildupPoseRejected", PalmBlackHoleClientScenes::staleBuildup),
                scene("client.palmBlackHole.ripple.openingKeepsAuthoredHeight", PalmBlackHoleClientScenes::rippleOpening),
                scene("client.palmBlackHole.ripple.midCastFollowsHand", PalmBlackHoleClientScenes::rippleFollowsHand),
                scene("client.palmBlackHole.ripple.transitionGradualAndSkipsDetached", PalmBlackHoleClientScenes::rippleTransition),
                scene("client.palmBlackHole.resources.witchModelPalmMarker", PalmBlackHoleClientScenes::witchModel),
                scene("client.palmBlackHole.resources.locatorAnimationKeyframes", PalmBlackHoleClientScenes::locatorAnimation),
                scene("client.palmBlackHole.resources.soundsMonoVorbis", PalmBlackHoleClientScenes::sounds));
    }

    private static Scene scene(String name, Consumer<Mismatches> body) {
        return Checks.scene(name, 10, ctx -> ClientSide.verify(ctx, name, body));
    }

    // ---- 脱手姿态 ----

    /** 切换渲染器到落下前的 3 秒里，黑洞停在最后可见的世界位置；位置同步包只改地面落点，不挪动黑洞。 */
    private static void holdKeepsCenter(Mismatches m) {
        Vector3d center = new Vector3d(104.25, 85.75, -208.5);
        var pose = new PalmBlackHoleReleasePose(259.9F, center, new Matrix4f().rotateXYZ(0.1F, 1, 0.2F).scale(2));
        for (Vector3d landing : new Vector3d[]{new Vector3d(104, 64.04, -209), new Vector3d(100, 70.04, -200)}) {
            for (float age : new float[]{260, 260.5F, 280, 319.9F, 320}) {
                m.equal("落点 " + landing + " 第 " + age + " 刻", center, pose.centerAt(age, landing));
            }
        }
    }

    /** 落下时单调下降，第 340 刻正好落到服务端给的落点，地面比起点高或低都一样。 */
    private static void fallReachesLanding(Mismatches m) {
        Vector3d center = new Vector3d(4.1, 90, -6.2);
        var pose = new PalmBlackHoleReleasePose(259.99F, center, new Matrix4f().scale(2));
        for (double ground : new double[]{64.04, 80.04, -40.96}) {
            Vector3d landing = new Vector3d(4, ground, -6);
            double previousY = center.y;
            for (int tick = 320; tick <= 340; tick++) {
                Vector3d next = pose.centerAt(tick, landing);
                m.check("地面 " + ground + " 第 " + tick + " 刻高度 " + next.y + " 在上一刻 " + previousY + " 与地面之间",
                        next.y <= previousY && next.y >= ground);
                previousY = next.y;
            }
            m.equal("地面 " + ground + " 第 340 刻", landing, pose.centerAt(340, landing));
            m.equal("地面 " + ground + " 第 341 刻", landing, pose.centerAt(341, landing));
            m.close("地面 " + ground + " 第 330 刻高度", center.y * 0.75 + ground * 0.25, pose.centerAt(330, landing).y, 1.0E-8);
        }
    }

    /** 换成独立渲染器后朝向和大小不变；姿态保存的是副本，外部改动影响不到它。 */
    private static void detachedPose(Mismatches m) {
        Matrix4f locator = new Matrix4f().rotateY(1.2F).rotateZ(-0.3F).scale(2);
        var pose = new PalmBlackHoleReleasePose(259.9F, new Vector3d(), locator);
        Matrix4f detached = new Matrix4f().rotate(pose.rotation()).scale(pose.scale());
        for (Vector3f vertex : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
            float distance = locator.transformPosition(new Vector3f(vertex)).distance(detached.transformPosition(new Vector3f(vertex)));
            m.check("顶点 " + vertex + " 偏差 " + distance, distance < 1.0E-5F);
        }
        locator.zero();
        pose.scale().zero();
        pose.rotation().identity();
        m.equal("改动外部矩阵和返回值后的缩放", 2.0F, pose.scale().x);
    }

    /** 镜头在脱手后才回来时，蓄力早期的旧姿态不算数。 */
    private static void staleBuildup(Mismatches m) {
        m.equal("第 120 刻的姿态接近脱手", false,
                new PalmBlackHoleReleasePose(120, new Vector3d(), new Matrix4f().scale(0.1F)).isNearRelease());
        m.equal("第 259.5 刻的姿态接近脱手", true,
                new PalmBlackHoleReleasePose(259.5F, new Vector3d(), new Matrix4f().scale(2)).isNearRelease());
    }

    // ---- 涟漪对齐 ----

    private static AnimatedGeoModel rippleModel() {
        GeoBone locator = bone("Blackhole", List.of());
        GeoBone hand = bone("LeftHand", List.of(locator));
        locator.setParent(hand);
        return new AnimatedGeoModel(new GeoModel(List.of(hand, bone("heobo-z", List.of()), bone("heobo-z2", List.of())), null));
    }

    private static GeoBone bone(String name, List<GeoBone> children) {
        return new GeoBone(children, name, new Vector3f(), new Vector3f(), null, false, null, false, false);
    }

    /** 开场阶段涟漪留在设计高度。 */
    private static void rippleOpening(Mismatches m) {
        AnimatedGeoModel model = rippleModel();
        model.bones().get("heobo-z").setPositionY(21.39F);
        model.bones().get("heobo-z2").setPositionY(23.39F);
        PalmBlackHoleRippleAlignment.align(model, 100);
        m.equal("heobo-z 高度", 21.39F, model.bones().get("heobo-z").getPositionY());
        m.equal("heobo-z2 高度", 23.39F, model.bones().get("heobo-z2").getPositionY());
    }

    /** 施法中段按手部实际变换对齐高度，只改高度，涟漪自己的缩放、旋转和水平位置不动。 */
    private static void rippleFollowsHand(Mismatches m) {
        for (float height : new float[]{3, 9, 15}) {
            AnimatedGeoModel model = rippleModel();
            var hand = model.bones().get("LeftHand");
            hand.setRotationZ((float) Math.PI / 2);
            hand.setPositionY(height * 16);
            // 父骨骼手转过 90° 后，定位点的本地 X 位移变成竖直方向
            model.bones().get("Blackhole").setPositionX(-32);
            for (String name : RIPPLES) {
                var ripple = model.bones().get(name);
                ripple.setScaleX(7);
                ripple.setScaleY(6);
                ripple.setScaleZ(0.5F);
                ripple.setRotationY(1.2F);
                ripple.setPositionX(4);
                ripple.setPositionZ(5);
            }
            PalmBlackHoleRippleAlignment.align(model, 160);
            for (String name : RIPPLES) {
                var ripple = model.bones().get(name);
                String label = "手高 " + height + " " + name;
                m.close(label + " 对齐高度", height + 2, (ripple.getPositionY() + ripple.getPivotY()) / 16, 1.0E-5F);
                m.equal(label + " scaleX", 7.0F, ripple.getScaleX());
                m.equal(label + " scaleY", 6.0F, ripple.getScaleY());
                m.equal(label + " scaleZ", 0.5F, ripple.getScaleZ());
                m.equal(label + " rotationY", 1.2F, ripple.getRotationY());
                m.equal(label + " positionX", 4.0F, ripple.getPositionX());
                m.equal(label + " positionZ", 5.0F, ripple.getPositionZ());
            }
        }
    }

    /** 过渡期逐渐靠拢；黑洞脱手后不再覆盖涟漪位置。 */
    private static void rippleTransition(Mismatches m) {
        AnimatedGeoModel model = rippleModel();
        model.bones().get("LeftHand").setPositionY(160);
        model.bones().get("heobo-z").setPositionY(0);
        PalmBlackHoleRippleAlignment.align(model, 110);
        m.close("第 110 刻的高度（格）", 5, model.bones().get("heobo-z").getPositionY() / 16, 1.0E-5F);
        model.bones().get("heobo-z").setPositionY(12);
        PalmBlackHoleRippleAlignment.align(model, 260);
        m.equal("第 260 刻的高度", 12.0F, model.bones().get("heobo-z").getPositionY());
    }

    // ---- 资源 ----

    /** 魔女模型的掌心定位点挂在左手下且没有方块，贴图是模型包自带的 1024×1024。 */
    private static void witchModel(Mismatches m) {
        var model = ClientAssets.witchGeoModel("models/entity/stellar_witch.json");
        var attachment = model.getBone("Blackhole").orElse(null);
        m.check("模型里有 Blackhole 骨骼", attachment != null);
        if (attachment != null) {
            m.equal("Blackhole 的父骨骼", "LeftHand", attachment.getParent() == null ? null : attachment.getParent().getName());
            List<software.bernie.geckolib.cache.object.GeoBone> bones = new ArrayList<>();
            ClientAssets.collect(attachment, bones);
            m.equal("Blackhole 子树里的方块数", 0, bones.stream().mapToInt(bone -> bone.getCubes().size()).sum());
        }
        BufferedImage texture = ClientAssets.witchImage("textures/entity/stellar_witch.png");
        m.equal("贴图宽", 1024, texture.getWidth());
        m.equal("贴图高", 1024, texture.getHeight());
    }

    /** 施法动画一直放到落地爆炸，定位点在 13 秒移位并放大到黑洞最大尺寸，涟漪从设计高度升到 160。 */
    private static void locatorAnimation(Mismatches m) {
        JsonObject root = ClientAssets.witchJson("animation/touhou_little_maid_spell.stellar_witch.iss.animation.json")
                .getAsJsonObject("animations");
        JsonObject palm = root.getAsJsonObject("iss:palm_black_hole");
        m.check("有 iss:palm_black_hole 动画", palm != null);
        m.check("有 iss:charge_black_hole 动画", root.has("iss:charge_black_hole"));
        if (palm == null) {
            return;
        }
        m.close("动画时长（刻）", PalmBlackHoleTimeline.IMPACT_TICK, palm.get("animation_length").getAsDouble() * 20, 1.0E-9);
        m.equal("循环方式", "hold_on_last_frame", palm.get("loop").getAsString());
        JsonObject locator = palm.getAsJsonObject("bones").getAsJsonObject("Blackhole");
        JsonObject scale = locator.getAsJsonObject("scale");
        m.equal("1.0 秒缩放", 0.0F, scale.getAsJsonArray("1.0").get(0).getAsFloat());
        m.equal("1.5 秒缩放", 0.05F, scale.getAsJsonArray("1.5").get(0).getAsFloat());
        m.equal("5.0 秒缩放", 0.1F, scale.getAsJsonArray("5.0").get(0).getAsFloat());
        m.equal("13.0 秒缩放 × 定位点倍率", PalmBlackHoleTimeline.MAX_MODEL_SCALE,
                scale.getAsJsonArray("13.0").get(0).getAsFloat() * PalmBlackHoleTimeline.LOCATOR_SCALE_MULTIPLIER);
        m.equal("13.0 秒定位点 X", -150, locator.getAsJsonObject("position").getAsJsonArray("13.0").get(0).getAsInt());
        for (String name : RIPPLES) {
            JsonObject position = palm.getAsJsonObject("bones").getAsJsonObject(name).getAsJsonObject("position");
            m.close(name + " 5.0 秒高度", 21.39, position.getAsJsonArray("5.0").get(1).getAsDouble(), 1.0E-6);
            m.equal(name + " 13.8333 秒高度", 160, position.getAsJsonArray("13.8333").get(1).getAsInt());
        }
    }

    /** 两个黑洞音效都指向单声道 48kHz 的 Vorbis 文件（定位音效必须是单声道）。 */
    private static void sounds(Mismatches m) {
        JsonObject sounds = ClientAssets.json(ClientAssets.id("sounds.json"));
        for (String name : List.of("palm_black_hole_loop", "palm_black_hole_explosion")) {
            m.equal(name + " 的音效文件", "touhou_little_maid_spell:spell/" + name,
                    sounds.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsString());
            byte[] audio = ClientAssets.bytes(ClientAssets.id("sounds/spell/" + name + ".ogg"));
            m.equal(name + " 容器", "OggS", new String(audio, 0, 4, StandardCharsets.US_ASCII));
            // 第一个 Ogg 包是 Vorbis 标识头
            int header = 27 + Byte.toUnsignedInt(audio[26]);
            m.equal(name + " 包类型", 1, (int) audio[header]);
            m.equal(name + " 编码", "vorbis", new String(audio, header + 1, 6, StandardCharsets.US_ASCII));
            m.equal(name + " 声道数", 1, (int) audio[header + 11]);
            m.equal(name + " 采样率", 48000, ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN).getInt(header + 12));
        }
    }
}
