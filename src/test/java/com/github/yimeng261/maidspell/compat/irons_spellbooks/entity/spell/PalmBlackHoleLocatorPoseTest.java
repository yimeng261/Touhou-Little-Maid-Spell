package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.keyframe.bone.BoneKeyFrameProcessor;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.keyframe.bone.RawBoneKeyFrame;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonKeyFrameUtils;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PalmBlackHoleLocatorPoseTest {
    @Test void samplesTheReleaseKeyInsteadOfTheLastKeyThatHidesTheMarker() {
        var track = JsonParser.parseString("""
                {"0.0":[0,0,0],"13.0":[2,2,2],"15.0":[0,0,0]}
                """);
        assertEquals(new Vector3f(2), PalmBlackHoleLocatorPose.sample(track, new Vector3f(1), 13));
        assertEquals(new Vector3f(1), PalmBlackHoleLocatorPose.sample(track, new Vector3f(1), 14));
    }

    @Test void interpolatesBetweenPostAndPreValuesAndUsesPostAtTheKey() {
        var track = JsonParser.parseString("""
                {"10.0":{"pre":[0,0,0],"post":[2,4,6]},
                 "14.0":{"pre":[6,8,10],"post":[20,30,40]}}
                """);
        assertEquals(new Vector3f(5, 7, 9), PalmBlackHoleLocatorPose.sample(track, new Vector3f(), 13));
        assertEquals(new Vector3f(20, 30, 40), PalmBlackHoleLocatorPose.sample(track, new Vector3f(), 14));
    }

    @Test void catmullRomAccountsForTheLaterHandResetEvenDuringAHold() {
        var track = JsonParser.parseString("""
                {"0.0":[0,0,0],"5.0":{"post":[-15,0,0],"lerp_mode":"catmullrom"},
                 "15.8333":{"post":[-15,0,0],"lerp_mode":"catmullrom"},
                 "16.0":{"post":[-100,0,0],"lerp_mode":"catmullrom"}}
                """);
        // A held pair of keys is not constant when the neighbours change.
        var release = PalmBlackHoleLocatorPose.sample(track, new Vector3f(), 13);
        assertTrue(release.x > -15);
        assertEquals(0, release.y);
        assertEquals(0, release.z);
    }

    @Test void releaseTransformRemainsFiniteAndRigidAtEveryFacing() {
        var neutral = PalmBlackHoleLocatorPose.topOffset(0);
        assertTrue(neutral.y > 1);
        for (float yaw : new float[]{0, 45, 90, 180, 270}) {
            var pose = PalmBlackHoleLocatorPose.topPose(yaw);
            var offset = pose.getTranslation(new Vector3f());
            assertTrue(offset.isFinite());
            assertEquals(neutral.y, offset.y, 1.0E-5);
            assertEquals(neutral.length(), offset.length(), 1.0E-5);
            assertEquals(1.4F, pose.getScale(new Vector3f()).x, 1.0E-5);
        }
        // Callers cannot mutate the shared fallback used for late client tracking.
        PalmBlackHoleLocatorPose.topPose(0).zero();
        assertEquals(neutral, PalmBlackHoleLocatorPose.topOffset(0));
    }

    @Test void authoredReleaseTracksMatchTlmsOwnKeyframeProcessor() throws Exception {
        var path = Path.of(System.getProperty("maidspell.projectDir"), "src/main/resources/assets",
                "touhou_little_maid_spell/tlm_custom_pack/star_witch_winefox-1.0.0/assets",
                "touhou_little_maid_spell/animation/touhou_little_maid_spell.stellar_witch.iss.animation.json");
        var bones = JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonObject("animations").getAsJsonObject("iss:palm_black_hole").getAsJsonObject("bones");
        for (String name : new String[]{"LeftArm", "LeftForeArm", "LeftHand", "Blackhole"}) {
            for (var channel : bones.getAsJsonObject(name).entrySet()) {
                var raw = new ArrayList<RawBoneKeyFrame>();
                // These release tracks are numeric, so they need no Molang evaluator.
                JsonKeyFrameUtils.getKeyFrames(raw, channel.getValue(), null);
                var frames = BoneKeyFrameProcessor.process(raw, false);
                Vector3f nativeValue = null;
                for (var frame : frames) {
                    if (frame.getStartTick() > PalmBlackHoleTimeline.TOP_TICK) break;
                    double progress = frame.getTotalTick() == 0 ? 1 : Math.min(1,
                            (PalmBlackHoleTimeline.TOP_TICK - frame.getStartTick()) / frame.getTotalTick());
                    nativeValue = frame.getLerpPoint(null, progress);
                }
                Vector3f sampled = PalmBlackHoleLocatorPose.sample(channel.getValue(), new Vector3f(), 13);
                assertNotNull(nativeValue);
                assertTrue(nativeValue.distance(sampled) < 1.0E-4F, name + "/" + channel.getKey());
            }
        }
    }
}
