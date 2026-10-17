package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.keyframe.bone.BoneKeyFrameProcessor;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.keyframe.bone.RawBoneKeyFrame;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonKeyFrameUtils;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleLocatorPose;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.PalmBlackHoleTimeline;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SpellTestAccess;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SwordArrayLayout;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Mismatches;
import com.github.yimeng261.maidspell.stagewright.support.WitchPack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 星之魔女专属法术的纯计算部分：剑阵的剑位、弹道、寿命与碰撞索引，掌心黑洞的时间线、伤害、冲击波，
 * 以及黑洞定位点从动画关键帧算出的释放姿态。
 */
public final class SpellLogicScenes {
    private static final double EPSILON = 1.0E-12D;

    private SpellLogicScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("swordArray.layout.swordCountsAndFiveTickRings", 5, SpellLogicScenes::swordCounts));
        scenes.add(Checks.scene("swordArray.layout.trajectoryMatchesPerTickGravity", 5, SpellLogicScenes::trajectories));
        scenes.add(Checks.scene("swordArray.layout.lifetimeAndContactHits", 5, SpellLogicScenes::lifetime));
        scenes.add(Checks.scene("swordArray.layout.oneCentreSwordFallsVertically", 5, SpellLogicScenes::centreSword));
        scenes.add(Checks.scene("swordArray.layout.descriptorRebuildsSameState", 5, SpellLogicScenes::descriptorRebuild));
        scenes.add(Checks.scene("swordArray.index.largeAndEdgeTargetsDeduplicated", 5, SpellLogicScenes::indexEdges));
        scenes.add(Checks.scene("swordArray.index.neverMissesComparedWithBruteForce", 5, SpellLogicScenes::indexBruteForce));
        scenes.add(Checks.scene("swordArray.index.onlyCentreSwordHitsCentre", 5, SpellLogicScenes::indexCentre));
        scenes.add(Checks.scene("swordArray.index.worksFarFromOrigin", 5, SpellLogicScenes::indexFarAway));
        scenes.add(Checks.scene("palmBlackHole.timeline.warningCoversLastFiveSeconds", 5, SpellLogicScenes::warning));
        scenes.add(Checks.scene("palmBlackHole.timeline.pulsesEverySecondUntilImpact", 5, SpellLogicScenes::pulses));
        scenes.add(Checks.scene("palmBlackHole.timeline.damageScalesWithMaxHealth", 5, SpellLogicScenes::damage));
        scenes.add(Checks.scene("palmBlackHole.timeline.pulseRadiusIsRound", 5, SpellLogicScenes::pulseRadius));
        scenes.add(Checks.scene("palmBlackHole.timeline.locatorHoldsThenFalls", 5, SpellLogicScenes::locatorDrop));
        scenes.add(Checks.scene("palmBlackHole.timeline.topOffsetRotatesWithWitch", 5, SpellLogicScenes::topOffset));
        scenes.add(Checks.scene("palmBlackHole.timeline.detachedKeepsReleaseScale", 5, SpellLogicScenes::detachedScale));
        scenes.add(Checks.scene("palmBlackHole.timeline.waveSweepsEveryDistanceOnce", 5, SpellLogicScenes::wave));
        scenes.add(Checks.scene("palmBlackHole.locator.samplesReleaseKey", 5, SpellLogicScenes::sampleReleaseKey));
        scenes.add(Checks.scene("palmBlackHole.locator.interpolatesPostToPre", 5, SpellLogicScenes::samplePrePost));
        scenes.add(Checks.scene("palmBlackHole.locator.catmullRomSeesLaterReset", 5, SpellLogicScenes::sampleCatmullRom));
        scenes.add(Checks.scene("palmBlackHole.locator.releasePoseFiniteAndRigid", 5, SpellLogicScenes::releaseRigid));
        scenes.add(Checks.scene("palmBlackHole.locator.matchesTlmKeyframeProcessor", 5, SpellLogicScenes::matchesTlm));
        return scenes;
    }

    // ---- 剑阵布局 ----

    /** 各等级剑数不变，每圈相隔 5 tick 依次出现。 */
    private static void swordCounts(SceneContext ctx) {
        int[] totals = {18, 34, 55, 81, 112, 148};
        Mismatches m = new Mismatches();
        for (int level = 1; level <= 6; level++) {
            SwordArrayLayout layout = new SwordArrayLayout(level, SwordArrayLayout.innerRadius(0.3D), 123L);
            m.equal("等级 " + level + " 剑数", totals[level - 1], layout.swords().size());
            for (SwordArrayLayout.Sword sword : layout.swords()) {
                m.equal("出现时刻 = 圈数 × 5", sword.ring() * 5, sword.launchAge());
                m.check("出现前一刻不可见", !sword.visible(sword.launchAge() - 0.01D));
                m.check("出现时可见", sword.visible(sword.launchAge()));
            }
        }
        m.verify(ctx, "剑数与出现时刻");
        ctx.check(SwordArrayLayout.swordsForRadius(100)).as("半径 100 的单圈剑数").isEqualTo(48);
    }

    /** 弹道与逐 tick 加重力的旧算法一致，落到目标平面时正好停住，全程在包围盒内。 */
    private static void trajectories(SceneContext ctx) {
        Mismatches m = new Mismatches();
        for (long seed = 0; seed < 32; seed++) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, seed);
            for (SwordArrayLayout.Sword sword : layout.swords()) {
                Vec3 point = sword.position(sword.launchAge());
                Vec3 velocity = sword.velocity(sword.launchAge());
                m.close("初速", 1.45F, velocity.length(), EPSILON);
                for (int age = sword.launchAge() + 1; age <= sword.landingAge(); age++) {
                    Vec3 next = point.add(velocity);
                    if (next.y <= 0.0D) {
                        double fraction = point.y / (point.y - next.y);
                        Vec3 hit = point.add(velocity.scale(fraction));
                        next = new Vec3(hit.x, 0, hit.z);
                        m.equal("落地时刻", sword.landingAge(), age);
                    }
                    m.close("位置 age=" + age, next, sword.position(age), EPSILON);
                    m.close("半 tick 位置 age=" + age, point.lerp(next, 0.5D), sword.position(age - 0.5D), EPSILON);
                    m.check("包围盒包含 " + next, layout.bounds().contains(next));
                    point = next;
                    velocity = velocity.add(0, -0.05D, 0);
                }
                m.equal("落地高度", 0.0D, point.y);
                m.close("到期时位置", point, sword.position(sword.expiryAge()), EPSILON);
            }
        }
        m.verify(ctx, "剑阵弹道");
    }

    /** 插地后的寿命随等级增加；接触伤害每 8 tick 一次，一直到最后一刻。 */
    private static void lifetime(SceneContext ctx) {
        ctx.check(SwordArrayLayout.DAMAGE_INTERVAL).as("接触伤害间隔").isEqualTo(8);
        Mismatches m = new Mismatches();
        for (int level = 1; level <= 6; level++) {
            SwordArrayLayout layout = new SwordArrayLayout(level, 1.25D, 42L);
            int planted = 80 + level * 4;
            int latestExpiry = 0;
            for (SwordArrayLayout.Sword sword : layout.swords()) {
                m.equal("等级 " + level + " 插地寿命", planted, sword.expiryAge() - sword.landingAge());
                latestExpiry = Math.max(latestExpiry, sword.expiryAge());
                int hits = 0;
                for (int age = 0; age <= layout.lifetime() + 1; age++) {
                    if (sword.dealsContactDamage(age)) {
                        hits++;
                        m.equal("第 " + hits + " 次接触伤害时刻", sword.landingAge() + hits * SwordArrayLayout.DAMAGE_INTERVAL, age);
                    }
                }
                m.equal("等级 " + level + " 接触伤害次数", planted / SwordArrayLayout.DAMAGE_INTERVAL, hits);
                m.check("落地那一刻不造成接触伤害", !sword.dealsContactDamage(sword.landingAge()));
                int lastDamageAge = sword.landingAge() + (planted / SwordArrayLayout.DAMAGE_INTERVAL) * SwordArrayLayout.DAMAGE_INTERVAL;
                m.check("最后一次接触伤害", sword.dealsContactDamage(lastDamageAge));
                m.equal("到期那一刻是否造成伤害", planted % 8 == 0, sword.dealsContactDamage(sword.expiryAge()));
                m.check("两次之间不造成伤害", !sword.dealsContactDamage(sword.landingAge() + 4));
                m.check("到期后不可见", !sword.visible(sword.expiryAge()));
                m.check("到期后不再造成伤害", !sword.dealsContactDamage(sword.expiryAge() + SwordArrayLayout.DAMAGE_INTERVAL));
            }
            m.equal("等级 " + level + " 法阵寿命", latestExpiry, layout.lifetime());
        }
        m.verify(ctx, "寿命与接触伤害");
    }

    /** 每个法阵恰好一把中心剑，随最内圈出现并竖直落下。 */
    private static void centreSword(SceneContext ctx) {
        Mismatches m = new Mismatches();
        for (long seed = 0; seed < 32; seed++) {
            SwordArrayLayout layout = new SwordArrayLayout(6, 3.5D, seed);
            int centreCount = 0;
            for (SwordArrayLayout.Sword sword : layout.swords()) {
                Vec3 landing = sword.position(sword.landingAge());
                if (landing.x == 0 && landing.z == 0) {
                    centreCount++;
                    m.equal("中心剑出现时刻", 0, sword.launchAge());
                    m.equal("中心剑插地寿命", 104, sword.expiryAge() - sword.landingAge());
                    for (int age = 0; age <= sword.landingAge(); age++) {
                        m.equal("中心剑 x age=" + age, 0.0D, sword.position(age).x);
                        m.equal("中心剑 z age=" + age, 0.0D, sword.position(age).z);
                    }
                    m.check("中心剑造成接触伤害", sword.dealsContactDamage(sword.landingAge() + SwordArrayLayout.DAMAGE_INTERVAL));
                }
            }
            m.equal("seed " + seed + " 中心剑数", 1, centreCount);
        }
        m.verify(ctx, "中心剑");
    }

    /** 同一描述（等级、半径、种子）在服务端与晚加入的客户端算出完全相同的状态。 */
    private static void descriptorRebuild(SceneContext ctx) {
        SwordArrayLayout server = new SwordArrayLayout(6, 2.5D, 8675309L);
        SwordArrayLayout client = new SwordArrayLayout(6, 2.5D, 8675309L);
        ctx.check(client.lifetime()).as("寿命").isEqualTo(server.lifetime());
        Mismatches m = new Mismatches();
        for (int i = 0; i < server.swords().size(); i++) {
            SwordArrayLayout.Sword a = server.swords().get(i);
            SwordArrayLayout.Sword b = client.swords().get(i);
            m.equal("剑 " + i + " 翻滚角", a.roll(), b.roll());
            m.equal("剑 " + i + " 落地时刻", a.landingAge(), b.landingAge());
            for (double age : new double[]{0, 5.5, 28, 54.25, 90, 200}) {
                m.close("剑 " + i + " 位置 age=" + age, a.position(age), b.position(age), EPSILON);
                m.equal("剑 " + i + " 可见 age=" + age, a.visible(age), b.visible(age));
            }
        }
        m.verify(ctx, "重建后的剑阵状态");
    }

    // ---- 碰撞索引 ----

    /** 跨越多个格子的大目标和边缘目标都能查到，同一目标只出现一次。 */
    private static void indexEdges(SceneContext ctx) {
        SpellTestAccess.CollisionIndex<String> index = new SpellTestAccess.CollisionIndex<>(new AABB(-10, -1, -10, 10, 12, 10));
        index.add("large", new AABB(-100, 0, -100, 100, 10, 100));
        index.add("negative", new AABB(-2.2, 0, -2.2, -1.8, 2, -1.8));
        ctx.check(index.query(new AABB(-2.1, 0, -2.1, -1.9, 2, -1.9))).as("负坐标处").isEqualTo(Set.of("large", "negative"));
        ctx.check(index.query(new AABB(9, 0, 9, 11, 2, 11))).as("边缘处").isEqualTo(Set.of("large"));
        ctx.check(index.query(new AABB(20, 0, 20, 21, 2, 21))).as("范围外").isEmpty();
    }

    /** 粗筛结果覆盖暴力遍历找到的所有相交目标。 */
    private static void indexBruteForce(SceneContext ctx) {
        SpellTestAccess.CollisionIndex<Integer> index = new SpellTestAccess.CollisionIndex<>(new AABB(-12, -1, -12, 12, 12, 12));
        Random random = new Random(42);
        List<AABB> targets = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            double x = random.nextDouble() * 30 - 15;
            double z = random.nextDouble() * 30 - 15;
            AABB box = new AABB(x, 0, z, x + random.nextDouble() * 6, 2 + random.nextDouble() * 4, z + random.nextDouble() * 6);
            targets.add(box);
            index.add(i, box);
        }
        Mismatches m = new Mismatches();
        for (int trial = 0; trial < 500; trial++) {
            double x = random.nextDouble() * 22 - 11;
            double z = random.nextDouble() * 22 - 11;
            AABB query = new AABB(x, 0, z, x + 0.8D, 1.25D, z + 0.8D);
            Set<Integer> candidates = index.query(query);
            for (int i = 0; i < targets.size(); i++) {
                if (targets.get(i).intersects(query)) {
                    m.check("第 " + trial + " 次查询漏掉目标 " + i, candidates.contains(i));
                }
            }
        }
        m.verify(ctx, "粗筛漏掉的目标");
    }

    /** 中心目标只被中心剑的接触盒碰到，各圈之间留有空隙。 */
    private static void indexCentre(SceneContext ctx) {
        SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, 42);
        SpellTestAccess.CollisionIndex<Integer> index = new SpellTestAccess.CollisionIndex<>(layout.bounds());
        AABB center = new AABB(-0.3D, 0, -0.3D, 0.3D, 1.8D, 0.3D);
        index.add(1, center);
        int centreContacts = 0;
        Mismatches m = new Mismatches();
        for (SwordArrayLayout.Sword sword : layout.swords()) {
            AABB contact = SwordArrayLayout.swordBox(sword.position(sword.landingAge())).inflate(SwordArrayLayout.CONTACT_INFLATION);
            if (contact.intersects(center)) {
                centreContacts++;
                m.equal("碰到中心目标的剑落点", Vec3.ZERO, sword.position(sword.landingAge()));
                m.check("索引查到中心目标", index.query(contact).contains(1));
            }
        }
        m.verify(ctx, "中心剑接触");
        ctx.check(centreContacts).as("碰到中心目标的剑数").isEqualTo(1);
        SwordArrayLayout.Sword sword = layout.swords().get(0);
        AABB contact = SwordArrayLayout.swordBox(sword.position(sword.landingAge())).inflate(SwordArrayLayout.CONTACT_INFLATION);
        index.add(2, contact);
        ctx.check(index.query(contact).contains(2)).as("接触盒查到自身").isTrue();
    }

    /** 远离原点（含世界边界附近）时查询依然准确，平移 1000 格后查不到。 */
    private static void indexFarAway(SceneContext ctx) {
        SwordArrayLayout layout = new SwordArrayLayout(6, 1.25D, 42);
        Mismatches m = new Mismatches();
        for (Vec3 center : new Vec3[]{new Vec3(246004.87, 151, -8176.63), new Vec3(-29000000, -30, 29000000)}) {
            SpellTestAccess.CollisionIndex<Integer> index = new SpellTestAccess.CollisionIndex<>(layout.bounds().move(center));
            for (int i = 0; i < layout.swords().size(); i++) {
                SwordArrayLayout.Sword sword = layout.swords().get(i);
                index.add(i, SwordArrayLayout.swordBox(sword.position(sword.landingAge()).add(center)));
            }
            for (int i = 0; i < layout.swords().size(); i++) {
                SwordArrayLayout.Sword sword = layout.swords().get(i);
                AABB query = SwordArrayLayout.swordBox(sword.position(sword.landingAge()).add(center))
                        .inflate(SwordArrayLayout.CONTACT_INFLATION);
                m.check(center + " 处查到剑 " + i, index.query(query).contains(i));
                m.check(center + " 处平移后查不到", index.query(query.move(1000, 0, 1000)).isEmpty());
            }
        }
        m.verify(ctx, "远离原点的查询");
    }

    // ---- 掌心黑洞时间线 ----

    /** 爆炸预警覆盖冲击前 5 秒，冲击时结束。 */
    private static void warning(SceneContext ctx) {
        ctx.check(PalmBlackHoleTimeline.BLAST_RADIUS).as("爆炸半径").isEqualTo(16.0F);
        ctx.check(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.WARNING_START_TICK - 0.01)).as("预警开始前").isFalse();
        ctx.check(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.WARNING_START_TICK)).as("预警开始").isTrue();
        ctx.check(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.IMPACT_TICK - 0.01)).as("冲击前一刻").isTrue();
        ctx.check(PalmBlackHoleTimeline.isWarningActive(PalmBlackHoleTimeline.IMPACT_TICK)).as("冲击时").isFalse();
        int warningTicks = 0;
        for (int age = 0; age <= PalmBlackHoleTimeline.IMPACT_TICK + PalmBlackHoleTimeline.WAVE_TICKS; age++) {
            if (PalmBlackHoleTimeline.isWarningActive(age)) {
                warningTicks++;
            }
        }
        ctx.check(warningTicks).as("预警 tick 数").isEqualTo(5 * 20);
    }

    /** 脉冲从 2.5 秒开始每秒一次，直到冲击前。 */
    private static void pulses(SceneContext ctx) {
        List<Integer> pulses = new ArrayList<>();
        for (int age = 0; age <= PalmBlackHoleTimeline.IMPACT_TICK + 20; age++) {
            if (PalmBlackHoleTimeline.isPulseTick(age)) {
                pulses.add(age);
            }
        }
        ctx.check(pulses).as("脉冲时刻").hasSize(15);
        ctx.check(pulses.get(0)).as("第一次脉冲").isEqualTo(50);
        ctx.check(pulses.get(pulses.size() - 1)).as("最后一次脉冲").isEqualTo(330);
        Mismatches m = new Mismatches();
        for (int i = 1; i < pulses.size(); i++) {
            m.equal("第 " + i + " 次间隔", 20, pulses.get(i) - pulses.get(i - 1));
        }
        m.verify(ctx, "脉冲间隔");
        ctx.check(PalmBlackHoleTimeline.isPulseTick(PalmBlackHoleTimeline.IMPACT_TICK)).as("冲击时不脉冲").isFalse();
    }

    /** 脉冲与爆炸伤害按受害者自己的最大生命计算。 */
    private static void damage(SceneContext ctx) {
        Map<Float, float[]> expected = Map.of(20F, new float[]{4, 14}, 100F, new float[]{12, 30}, 1000F, new float[]{102, 210});
        expected.forEach((health, values) -> {
            ctx.check(PalmBlackHoleTimeline.pulseDamage(health)).as("最大生命 " + health + " 的脉冲伤害").isCloseTo(values[0], 1.0E-5);
            ctx.check(PalmBlackHoleTimeline.explosionDamage(health)).as("最大生命 " + health + " 的爆炸伤害").isCloseTo(values[1], 1.0E-5);
        });
    }

    /** 脉冲范围是半径 10 格的圆，不是查询盒的四角。 */
    private static void pulseRadius(SceneContext ctx) {
        ctx.check(PalmBlackHoleTimeline.insidePulse(0, 0)).as("圆心").isTrue();
        ctx.check(PalmBlackHoleTimeline.insidePulse(10, 0)).as("正好 10 格").isTrue();
        ctx.check(PalmBlackHoleTimeline.insidePulse(6, 8)).as("斜向 10 格").isTrue();
        ctx.check(PalmBlackHoleTimeline.insidePulse(10.01, 0)).as("略超 10 格").isFalse();
        ctx.check(PalmBlackHoleTimeline.insidePulse(8, 8)).as("盒角").isFalse();
    }

    /** 定位点升到顶后停 3 秒，再用 1 秒落到地面。 */
    private static void locatorDrop(SceneContext ctx) {
        ctx.check(PalmBlackHoleTimeline.TOP_TICK).as("到顶时刻").isEqualTo(260);
        ctx.check(PalmBlackHoleTimeline.DROP_TICK - PalmBlackHoleTimeline.TOP_TICK).as("停留 tick").isEqualTo(60);
        ctx.check(PalmBlackHoleTimeline.IMPACT_TICK - PalmBlackHoleTimeline.DROP_TICK).as("下落 tick").isEqualTo(20);
        Mismatches m = new Mismatches();
        for (double topHeight : new double[]{1, 4, 20}) {
            for (int tick = 260; tick <= 320; tick++) {
                m.equal("顶高 " + topHeight + " 停留中 tick=" + tick, topHeight, PalmBlackHoleTimeline.dropHeight(tick, topHeight));
            }
            double previous = topHeight;
            for (int tick = 320; tick <= 340; tick++) {
                double height = PalmBlackHoleTimeline.dropHeight(tick, topHeight);
                m.check("顶高 " + topHeight + " 下落单调且不低于地面 tick=" + tick, height <= previous && height >= 0);
                previous = height;
            }
            m.equal("顶高 " + topHeight + " 最终高度", 0.0D, previous);
        }
        m.verify(ctx, "定位点下落");
    }

    /** 顶点偏移稳定，并随魔女朝向旋转。 */
    private static void topOffset(SceneContext ctx) {
        Vector3f front = PalmBlackHoleLocatorPose.topOffset(0);
        Vector3f reverse = PalmBlackHoleLocatorPose.topOffset(180);
        ctx.check(front.isFinite()).as("偏移有限").isTrue();
        ctx.check(front.y).as("偏移高度").isGreaterThan(1);
        ctx.check(reverse.y).as("转身后高度").isCloseTo(front.y, 1.0E-5);
        ctx.check(reverse.x).as("转身后 x").isCloseTo(-front.x, 1.0E-5);
        ctx.check(reverse.z).as("转身后 z").isCloseTo(-front.z, 1.0E-5);
        ctx.check(PalmBlackHoleLocatorPose.topOffset(0)).as("重复取值").isEqualTo(front);
    }

    /** 脱手阶段保持释放时的缩放，直到标记消失。 */
    private static void detachedScale(SceneContext ctx) {
        Vector3f scale = PalmBlackHoleLocatorPose.topPose(0).getScale(new Vector3f());
        ctx.check(scale.x * PalmBlackHoleTimeline.LOCATOR_SCALE_MULTIPLIER).as("释放缩放")
                .isCloseTo(PalmBlackHoleTimeline.MAX_MODEL_SCALE * 0.7F, 1.0E-5);
        ctx.check(scale.y).as("y 缩放").isCloseTo(scale.x, 1.0E-5);
        ctx.check(scale.z).as("z 缩放").isCloseTo(scale.x, 1.0E-5);
    }

    /** 记住已命中的目标时，扩散的冲击波让每个距离都恰好被扫过一次。 */
    private static void wave(SceneContext ctx) {
        ctx.check(PalmBlackHoleTimeline.waveRadius(339)).as("冲击前半径").isEqualTo(0.0F);
        ctx.check(PalmBlackHoleTimeline.waveRadius(350)).as("半程半径").isEqualTo(8.0F);
        ctx.check(PalmBlackHoleTimeline.waveRadius(360)).as("终点半径").isEqualTo(16.0F);
        Mismatches m = new Mismatches();
        for (double distance = 0; distance <= 17; distance += 0.25) {
            int firstHit = -1;
            for (int tick = 340; tick <= 360; tick++) {
                if (firstHit < 0 && PalmBlackHoleTimeline.crossedWave(distance,
                        PalmBlackHoleTimeline.waveRadius(tick - 1), PalmBlackHoleTimeline.waveRadius(tick))) {
                    firstHit = tick;
                }
            }
            m.equal("距离 " + distance + " 是否被扫到", distance <= 16, firstHit >= 0);
            if (distance > 9 && distance <= 16) {
                m.check("距离 " + distance + " 在半程之后才被扫到", firstHit > 350);
            }
        }
        m.verify(ctx, "冲击波扫过的距离");
    }

    // ---- 定位点关键帧采样 ----

    /** 取释放那一帧，而不是最后一帧（那一帧把标记缩没了）。 */
    private static void sampleReleaseKey(SceneContext ctx) {
        JsonElement track = JsonParser.parseString("""
                {"0.0":[0,0,0],"13.0":[2,2,2],"15.0":[0,0,0]}
                """);
        ctx.check(SpellTestAccess.sampleLocator(track, new Vector3f(1), 13)).as("13 秒").isEqualTo(new Vector3f(2));
        ctx.check(SpellTestAccess.sampleLocator(track, new Vector3f(1), 14)).as("14 秒").isEqualTo(new Vector3f(1));
    }

    /** 两帧之间从前一帧的 post 插值到后一帧的 pre，正好在关键帧上取 post。 */
    private static void samplePrePost(SceneContext ctx) {
        JsonElement track = JsonParser.parseString("""
                {"10.0":{"pre":[0,0,0],"post":[2,4,6]},
                 "14.0":{"pre":[6,8,10],"post":[20,30,40]}}
                """);
        ctx.check(SpellTestAccess.sampleLocator(track, new Vector3f(), 13)).as("13 秒").isEqualTo(new Vector3f(5, 7, 9));
        ctx.check(SpellTestAccess.sampleLocator(track, new Vector3f(), 14)).as("14 秒").isEqualTo(new Vector3f(20, 30, 40));
    }

    /** catmull-rom 插值会受后面手部复位的影响，即使前后两帧值相同也不是常量。 */
    private static void sampleCatmullRom(SceneContext ctx) {
        JsonElement track = JsonParser.parseString("""
                {"0.0":[0,0,0],"5.0":{"post":[-15,0,0],"lerp_mode":"catmullrom"},
                 "15.8333":{"post":[-15,0,0],"lerp_mode":"catmullrom"},
                 "16.0":{"post":[-100,0,0],"lerp_mode":"catmullrom"}}
                """);
        Vector3f release = SpellTestAccess.sampleLocator(track, new Vector3f(), 13);
        ctx.check(release.x).as("x").isGreaterThan(-15);
        ctx.check(release.y).as("y").isEqualTo(0.0F);
        ctx.check(release.z).as("z").isEqualTo(0.0F);
    }

    /** 各朝向的释放变换有限且是刚体变换，共享的回退姿态不会被调用方改掉。 */
    private static void releaseRigid(SceneContext ctx) {
        Vector3f neutral = PalmBlackHoleLocatorPose.topOffset(0);
        ctx.check(neutral.y).as("偏移高度").isGreaterThan(1);
        Mismatches m = new Mismatches();
        for (float yaw : new float[]{0, 45, 90, 180, 270}) {
            var pose = PalmBlackHoleLocatorPose.topPose(yaw);
            Vector3f offset = pose.getTranslation(new Vector3f());
            m.check("朝向 " + yaw + " 偏移有限", offset.isFinite());
            m.close("朝向 " + yaw + " 高度", neutral.y, offset.y, 1.0E-5);
            m.close("朝向 " + yaw + " 距离", neutral.length(), offset.length(), 1.0E-5);
            m.close("朝向 " + yaw + " 缩放", 1.4F, pose.getScale(new Vector3f()).x, 1.0E-5);
        }
        m.verify(ctx, "各朝向的释放变换");
        PalmBlackHoleLocatorPose.topPose(0).zero();
        ctx.check(PalmBlackHoleLocatorPose.topOffset(0)).as("改动返回值后再取").isEqualTo(neutral);
    }

    /** 动画里释放时刻的骨骼轨道，采样结果与 TLM 自己的关键帧处理器一致。 */
    private static void matchesTlm(SceneContext ctx) {
        JsonObject bones = WitchPack.animations("touhou_little_maid_spell.stellar_witch.iss.animation.json")
                .getAsJsonObject("iss:palm_black_hole").getAsJsonObject("bones");
        Mismatches m = new Mismatches();
        for (String name : new String[]{"LeftArm", "LeftForeArm", "LeftHand", "Blackhole"}) {
            for (var channel : bones.getAsJsonObject(name).entrySet()) {
                List<RawBoneKeyFrame> raw = new ArrayList<>();
                // 释放段的轨道都是数值，不需要 Molang 求值器
                JsonKeyFrameUtils.getKeyFrames(raw, channel.getValue(), null);
                Vector3f nativeValue = null;
                for (BoneKeyFrame frame : BoneKeyFrameProcessor.process(raw, false)) {
                    if (frame.getStartTick() > PalmBlackHoleTimeline.TOP_TICK) {
                        break;
                    }
                    double progress = frame.getTotalTick() == 0 ? 1 : Math.min(1,
                            (PalmBlackHoleTimeline.TOP_TICK - frame.getStartTick()) / frame.getTotalTick());
                    nativeValue = frame.getLerpPoint(null, progress);
                }
                Vector3f sampled = SpellTestAccess.sampleLocator(channel.getValue(), new Vector3f(), 13);
                String label = name + "/" + channel.getKey();
                m.check(label + " 在释放前有关键帧", nativeValue != null);
                if (nativeValue != null) {
                    m.check(label + "：TLM " + nativeValue + "，本模组 " + sampled, nativeValue.distance(sampled) < 1.0E-4F);
                }
            }
        }
        m.verify(ctx, "与 TLM 关键帧处理器不一致的轨道");
    }
}
