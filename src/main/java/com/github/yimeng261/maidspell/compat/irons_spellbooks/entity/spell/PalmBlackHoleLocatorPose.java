package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.util.MathUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** 在第 13 秒采样的释放变换，此时设计好的手部与标记尚未复位。 */
public final class PalmBlackHoleLocatorPose {
    private static final String ROOT = "/assets/touhou_little_maid_spell/tlm_custom_pack/"
            + "star_witch_winefox-1.0.0/assets/touhou_little_maid_spell/";
    private static final Matrix4f TOP_POSE = loadTopPose();

    private PalmBlackHoleLocatorPose() {
    }

    public static Vector3f topOffset(float bodyYaw) {
        return topPose(bodyYaw).getTranslation(new Vector3f());
    }

    public static Matrix4f topPose(float bodyYaw) {
        return new Matrix4f().rotateY((float) Math.toRadians(180 - bodyYaw))
                .mul(TOP_POSE);
    }

    private static Matrix4f loadTopPose() {
        JsonObject model = load("models/entity/stellar_witch.json");
        JsonObject animation = load("animation/touhou_little_maid_spell.stellar_witch.iss.animation.json")
                .getAsJsonObject("animations").getAsJsonObject("iss:palm_black_hole").getAsJsonObject("bones");
        Map<String, JsonObject> bones = new HashMap<>();
        for (JsonElement value : model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones")) {
            JsonObject bone = value.getAsJsonObject();
            bones.put(bone.get("name").getAsString(), bone);
        }
        var hierarchy = new ArrayList<JsonObject>();
        for (JsonObject bone = bones.get("Blackhole"); bone != null; ) {
            hierarchy.add(bone);
            bone = bone.has("parent") ? bones.get(bone.get("parent").getAsString()) : null;
        }
        Collections.reverse(hierarchy);
        Matrix4f pose = new Matrix4f().translate(0, 0.01F, 0).scale(loadWitchScale());
        double seconds = PalmBlackHoleTimeline.TOP_TICK / 20.0D;
        for (int index = 0; index < hierarchy.size(); index++) {
            JsonObject bone = hierarchy.get(index);
            String name = bone.get("name").getAsString();
            JsonObject animated = animation.has(name) ? animation.getAsJsonObject(name) : new JsonObject();
            Vector3f pivot = sample(bone.get("pivot"), new Vector3f(), seconds).mul(-1, 1, 1).div(16);
            Vector3f position = sample(animated.get("position"), new Vector3f(), seconds).mul(-1, 1, 1).div(16);
            Vector3f rotation = sample(bone.get("rotation"), new Vector3f(), seconds)
                    .add(sample(animated.get("rotation"), new Vector3f(), seconds)).mul(-1, -1, 1)
                    .mul((float) Math.PI / 180);
            Vector3f scale = sample(animated.get("scale"), new Vector3f(1), seconds);
            pose.translate(position).translate(pivot)
                    .rotateZ(rotation.z).rotateY(rotation.y).rotateX(rotation.x).scale(scale);
            if (index + 1 < hierarchy.size()) pose.translate(new Vector3f(pivot).negate());
        }
        return pose;
    }

    private static float loadWitchScale() {
        for (JsonElement value : load("maid_model.json").getAsJsonArray("model_list")) {
            JsonObject info = value.getAsJsonObject();
            if ("touhou_little_maid_spell:stellar_witch".equals(info.get("model_id").getAsString())) {
                return info.has("render_entity_scale") ? info.get("render_entity_scale").getAsFloat() : 1.0F;
            }
        }
        throw new IllegalStateException("Missing stellar witch model info for palm locator");
    }

    static Vector3f sample(JsonElement element, Vector3f fallback, double seconds) {
        if (element == null) return new Vector3f(fallback);
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("post")) element = object.get("post");
            else if (object.has("pre")) element = object.get("pre");
            else {
                var frames = new TreeMap<Double, JsonElement>();
                for (var entry : object.entrySet()) {
                    frames.put(Double.parseDouble(entry.getKey()), entry.getValue());
                }
                var begin = frames.floorEntry(seconds);
                if (begin == null) return keyframeValue(frames.firstEntry().getValue(), "pre", fallback, seconds);
                var end = frames.higherEntry(begin.getKey());
                Vector3f from = keyframeValue(begin.getValue(), "post", fallback, seconds);
                if (end == null || seconds == begin.getKey()) return from;
                Vector3f to = keyframeValue(end.getValue(), "pre", fallback, seconds);
                float progress = (float) ((seconds - begin.getKey()) / (end.getKey() - begin.getKey()));
                if (!catmullRom(begin.getValue()) && !catmullRom(end.getValue())) return from.lerp(to, progress);
                var left = frames.lowerEntry(begin.getKey());
                var right = frames.higherEntry(end.getKey());
                Vector3f before = left == null ? new Vector3f(from)
                        : keyframeValue(left.getValue(), "post", fallback, seconds);
                Vector3f after = right == null ? new Vector3f(to)
                        : keyframeValue(right.getValue(), "pre", fallback, seconds);
                // 与车万女仆和 Blockbench 的 Catmull-Rom 插值保持一致，包括相邻关键帧。
                return MathUtil.catmullRom(progress, before, from, to, after);
            }
        }
        if (element.isJsonPrimitive()) return new Vector3f(element.getAsFloat());
        var values = element.getAsJsonArray();
        float[] xyz = new float[3];
        for (int i = 0; i < 3; i++) {
            String value = values.get(i).getAsString();
            // 根骨骼的起伏只有半个模型像素，释放时冻结其中性偏移。
            if (value.equals("5+0.5*math.sin(360*query.life_time)")) xyz[i] = 5;
            else xyz[i] = Float.parseFloat(value);
        }
        return new Vector3f(xyz[0], xyz[1], xyz[2]);
    }

    private static Vector3f keyframeValue(JsonElement frame, String side, Vector3f fallback, double seconds) {
        if (frame.isJsonObject() && frame.getAsJsonObject().has(side)) frame = frame.getAsJsonObject().get(side);
        return sample(frame, fallback, seconds);
    }

    private static boolean catmullRom(JsonElement frame) {
        return frame.isJsonObject() && frame.getAsJsonObject().has("lerp_mode")
                && "catmullrom".equals(frame.getAsJsonObject().get("lerp_mode").getAsString());
    }

    private static JsonObject load(String path) {
        try (var input = PalmBlackHoleLocatorPose.class.getResourceAsStream(ROOT + path)) {
            if (input == null) throw new IllegalStateException("Missing palm locator resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load palm locator resource", error);
        }
    }
}
