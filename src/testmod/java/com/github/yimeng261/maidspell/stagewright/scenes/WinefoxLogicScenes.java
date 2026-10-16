package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxBossSpellAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTestAccess;
import com.github.yimeng261.maidspell.effect.StaranchorStunEffect;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Mismatches;
import com.github.yimeng261.maidspell.stagewright.support.WitchPack;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import com.github.yimeng261.maidspell.winefox.WinefoxSpellChoice;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 星之魔女战斗里的纯计算部分：承伤先减伤后封顶、高伤保护只护下一击、剑系连段的步骤顺序、
 * 眩晕动画与星锚眩晕时长、法术默认权重、近战连段的出招节奏，以及挑战配置（含护甲）的存取与网络往返。
 */
public final class WinefoxLogicScenes {
    private static final List<WinefoxAction> SWORD_ACTIONS = List.of(
            WinefoxAction.SWORD_ATTACK_1, WinefoxAction.SWORD_ATTACK_2,
            WinefoxAction.SWORD_ATTACK_3, WinefoxAction.SWORD_ATTACK_4);

    private WinefoxLogicScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("winefox.damage.reductionBeforeSingleHitCap", 5, WinefoxLogicScenes::reductionBeforeCap));
        scenes.add(Checks.scene("winefox.damage.extraHealthWriteCounted", 5, WinefoxLogicScenes::extraHealthWrite));
        scenes.add(Checks.scene("winefox.damage.overkillNotClippedToRemainingHealth", 5, WinefoxLogicScenes::overkill));
        scenes.add(Checks.scene("winefox.damage.extremeWritesStayFinite", 5, WinefoxLogicScenes::extremeWrites));
        scenes.add(Checks.scene("winefox.damage.healingAndInvalidWritesAreNotDamage", 5, WinefoxLogicScenes::healingWrites));
        scenes.add(Checks.scene("winefox.highDamageGuard.protectsOnlyNextHit", 5, WinefoxLogicScenes::guardNextHit));
        scenes.add(Checks.scene("winefox.highDamageGuard.refreshesWithoutStacking", 5, WinefoxLogicScenes::guardNoStacking));
        scenes.add(Checks.scene("winefox.highDamageGuard.thresholdFivePercentInclusive", 5, WinefoxLogicScenes::guardThreshold));
        scenes.add(Checks.scene("winefox.highDamageGuard.pendingRestoredAndReset", 5, WinefoxLogicScenes::guardPending));
        scenes.add(Checks.scene("winefox.swordCombo.stepsInOrder", 5, WinefoxLogicScenes::comboOrder));
        scenes.add(Checks.scene("winefox.swordCombo.unrelatedReleasesCannotSkip", 5, WinefoxLogicScenes::comboNoSkip));
        scenes.add(Checks.scene("winefox.swordCombo.interruptionClearsEveryStep", 5, WinefoxLogicScenes::comboInterrupt));
        scenes.add(Checks.scene("winefox.stun.dizzyAnimationCoversPearlStun", 5, WinefoxLogicScenes::dizzyCoversStun));
        scenes.add(Checks.scene("winefox.spellWeight.swordPrisonHalvedOnlyInPhaseTwo", 5, WinefoxLogicScenes::spellWeights));
        scenes.add(Checks.scene("winefox.melee.threeHitComboTimeline", 5, ctx -> comboTimeline(ctx, 3, 0, 12, 24, 42)));
        scenes.add(Checks.scene("winefox.melee.fourHitComboTimeline", 5, ctx -> comboTimeline(ctx, 4, 0, 12, 24, 36, 55)));
        scenes.add(Checks.scene("winefox.melee.onlySwordActionsFollowUpEarly", 5, WinefoxLogicScenes::onlySwordsFollowUp));
        scenes.add(Checks.scene("winefox.melee.followUpsKeepEventsAndFullRecovery", 5, WinefoxLogicScenes::followUpsKeepEvents));
        scenes.add(Checks.scene("winefox.challengeConfig.armorRoundTrips", 5, WinefoxLogicScenes::armorRoundTrips));
        scenes.add(Checks.scene("winefox.challengeConfig.legacyTagsUseDefaultArmor", 5, WinefoxLogicScenes::legacyArmor));
        scenes.add(Checks.scene("winefox.challengeConfig.armorBounds", 5, WinefoxLogicScenes::armorBounds));
        scenes.add(Checks.scene("winefox.challengeConfig.basicLimitsKeepTheirSlots", 5, WinefoxLogicScenes::basicLimits));
        scenes.add(Checks.scene("winefox.challengeConfig.presetsRoundTripWithEmptySlots", 5, WinefoxLogicScenes::presetsRoundTrip));
        return scenes;
    }

    // ---- 承伤换算 ----

    /** 先减伤再封顶：100 减到 50 再封顶 48，而不是先封顶到 48 再减成 24。 */
    private static void reductionBeforeCap(SceneContext ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(300.0F, 200.0F);
        ctx.check(WinefoxTestAccess.reduceAndCapDamage(damage, 0.5D, 48.0F)).as("减伤后封顶").isEqualTo(48.0F);
    }

    /** 额外直接写入的生命变化也算进本次伤害。 */
    private static void extraHealthWrite(SceneContext ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(250.0F, 190.0F);
        ctx.check(damage).as("本次伤害").isEqualTo(60.0F);
        ctx.check(WinefoxTestAccess.reduceAndCapDamage(damage, 0.5D, 48.0F)).as("减伤后").isEqualTo(30.0F);
    }

    /** 溢出伤害按原始压力计算，不截到剩余生命。 */
    private static void overkill(SceneContext ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(300.0F, 300.0F - 1_000_000_000.0F);
        ctx.check(damage).as("本次伤害").isCloseTo(1_000_000_000.0F, 128.0F);
        ctx.check(WinefoxTestAccess.reduceAndCapDamage(damage, 0.05D, 48.0F)).as("减伤后封顶").isEqualTo(48.0F);
    }

    /** 极端但有限的写入仍得到有限伤害。 */
    private static void extremeWrites(SceneContext ctx) {
        float damage = WinefoxTestAccess.damageBeforeCap(Float.MAX_VALUE, -Float.MAX_VALUE);
        ctx.check(Float.isFinite(damage)).as("有限").isTrue();
        ctx.check(damage).as("本次伤害").isEqualTo(Float.MAX_VALUE);
    }

    /** 治疗和 NaN、负无穷这类无效写入不算伤害。 */
    private static void healingWrites(SceneContext ctx) {
        ctx.check(WinefoxTestAccess.damageBeforeCap(300.0F, 320.0F)).as("治疗").isEqualTo(0.0F);
        ctx.check(WinefoxTestAccess.damageBeforeCap(300.0F, Float.NaN)).as("NaN").isEqualTo(0.0F);
        ctx.check(WinefoxTestAccess.damageBeforeCap(300.0F, Float.NEGATIVE_INFINITY)).as("负无穷").isEqualTo(0.0F);
    }

    // ---- 高伤保护 ----

    /** 高伤一击之后只保护下一次有效命中。 */
    private static void guardNextHit(SceneContext ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        float threshold = WinefoxTestAccess.highDamageThreshold(600.0D);
        ctx.check(guard.multiplier()).as("初始倍率").isEqualTo(1.0D);
        guard.recordEffectiveHit(100.0F, threshold);
        ctx.check(guard.multiplier()).as("高伤后倍率").isEqualTo(0.7D);
        ctx.check(48.0D * guard.multiplier()).as("48 封顶伤害打折后").isCloseTo(33.6D, 0.0001D);
        guard.recordEffectiveHit(20.0F, threshold);
        ctx.check(guard.pending()).as("下一击后仍在保护").isFalse();
        ctx.check(guard.multiplier()).as("下一击后倍率").isEqualTo(1.0D);
    }

    /** 连续高伤只刷新保护，不叠加。 */
    private static void guardNoStacking(SceneContext ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        Mismatches m = new Mismatches();
        for (int i = 0; i < 5; i++) {
            guard.recordEffectiveHit(1_000_000_000.0F, WinefoxTestAccess.highDamageThreshold(600.0D));
            m.equal("第 " + (i + 1) + " 次高伤后倍率", 0.7D, guard.multiplier());
        }
        m.verify(ctx, "连续高伤");
    }

    /** 阈值是默认最大生命的 5%，正好等于阈值也算高伤。 */
    private static void guardThreshold(SceneContext ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        float threshold = WinefoxTestAccess.highDamageThreshold(600.0D);
        ctx.check(threshold).as("600 生命的阈值").isEqualTo(30.0F);
        ctx.check(WinefoxTestAccess.highDamageThreshold(1_000.0D)).as("1000 生命的阈值").isEqualTo(50.0F);
        guard.recordEffectiveHit(29.0F, threshold);
        ctx.check(guard.pending()).as("低于阈值").isFalse();
        guard.recordEffectiveHit(30.0F, threshold);
        ctx.check(guard.pending()).as("等于阈值").isTrue();
        guard.recordEffectiveHit(20.0F, threshold);
        ctx.check(guard.pending()).as("下一击后").isFalse();
    }

    /** 保护状态可以从存档恢复，也可以清掉。 */
    private static void guardPending(SceneContext ctx) {
        WinefoxTestAccess.HighDamageGuard guard = new WinefoxTestAccess.HighDamageGuard();
        guard.setPending(true);
        ctx.check(guard.multiplier()).as("恢复保护后倍率").isEqualTo(0.7D);
        guard.setPending(false);
        ctx.check(guard.multiplier()).as("清掉后倍率").isEqualTo(1.0D);
    }

    // ---- 剑系连段 ----

    /** 剑阵之后依次是停顿、闪身、瞬移、斩击，各步等对应法术真正开始或放出。 */
    private static void comboOrder(SceneContext ctx) {
        WinefoxTestAccess.SwordCombo combo = new WinefoxTestAccess.SwordCombo();
        combo.start(100);
        ctx.check(combo.step(119)).as("119").isEqualTo("PAUSE");
        ctx.check(combo.step(120)).as("120").isEqualTo("FLASH");
        combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        ctx.check(combo.step(121)).as("开始施放瞬移").isEqualTo("FLASH_CASTING");
        combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        ctx.check(combo.step(122)).as("瞬移放出").isEqualTo("TELEPORT");
        combo.teleported();
        ctx.check(combo.step(123)).as("瞬移完成").isEqualTo("SLASH");
        combo.castStarted(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        ctx.check(combo.step(124)).as("开始施放斩击").isEqualTo("SLASH_CASTING");
        combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        ctx.check(combo.isActive()).as("斩击放出后仍在连段").isFalse();
    }

    /** 无关法术的放出不能跳过停顿或闪身。 */
    private static void comboNoSkip(SceneContext ctx) {
        WinefoxTestAccess.SwordCombo combo = new WinefoxTestAccess.SwordCombo();
        combo.start(0);
        combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        combo.teleported();
        ctx.check(combo.step(19)).as("19").isEqualTo("PAUSE");
        ctx.check(combo.step(20)).as("20").isEqualTo("FLASH");
        combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
        combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
        ctx.check(combo.step(100)).as("放出无关法术后").isEqualTo("FLASH_CASTING");
    }

    /** 在任意一步打断都会清空连段，之后能重新开始。 */
    private static void comboInterrupt(SceneContext ctx) {
        List<String> steps = Arrays.asList(WinefoxTestAccess.SwordCombo.stepNames());
        Mismatches m = new Mismatches();
        for (String interrupted : steps) {
            int at = steps.indexOf(interrupted);
            WinefoxTestAccess.SwordCombo combo = new WinefoxTestAccess.SwordCombo();
            combo.start(0);
            if (at >= steps.indexOf("FLASH")) {
                combo.step(20);
            }
            if (at >= steps.indexOf("FLASH_CASTING")) {
                combo.castStarted(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            }
            if (at >= steps.indexOf("TELEPORT")) {
                combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            }
            if (at >= steps.indexOf("SLASH")) {
                combo.teleported();
            }
            if (interrupted.equals("SLASH_CASTING")) {
                combo.castStarted(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
            }
            combo.cancel();
            combo.spellReleased(WinefoxBossSpellAction.MODIFIED_TELEPORT);
            combo.spellReleased(WinefoxBossSpellAction.STAR_SHADOW_STRIKE);
            m.check(interrupted + " 处打断后仍在连段", !combo.isActive());
            combo.start(200);
            m.check(interrupted + " 处打断后无法重新开始", combo.isActive());
            m.equal(interrupted + " 处打断后 219", "PAUSE", combo.step(219));
            m.equal(interrupted + " 处打断后 220", "FLASH", combo.step(220));
        }
        m.verify(ctx, "打断连段");
    }

    // ---- 眩晕与法术权重 ----

    /** 眩晕动画长 6 秒，覆盖星锚珍珠的整段眩晕。 */
    private static void dizzyCoversStun(SceneContext ctx) {
        JsonObject animations = WitchPack.animations("touhou_little_maid_spell.stellar_witch.main.animation.json");
        double seconds = animations.getAsJsonObject(WinefoxAction.DIZZY.animationName()).get("animation_length").getAsDouble();
        ctx.check(seconds).as("眩晕动画秒数").isEqualTo(6.0D);
        ctx.check((int) Math.ceil(seconds * 20)).as("动画 tick").isEqualTo(StaranchorStunEffect.STUN_TICKS);
        ctx.check(WinefoxAction.DIZZY.durationTicks()).as("眩晕动作时长").isEqualTo(StaranchorStunEffect.STUN_TICKS);
    }

    /** 默认权重：剑牢二阶段减半，黑洞二阶段加倍，其余法术两阶段都是 2。 */
    private static void spellWeights(SceneContext ctx) {
        ctx.check(WinefoxBossSpellAction.SWORD_PRISON.defaultSelectionWeight(false)).as("剑牢一阶段").isEqualTo(2);
        ctx.check(WinefoxBossSpellAction.SWORD_PRISON.defaultSelectionWeight(true)).as("剑牢二阶段").isEqualTo(1);
        ctx.check(WinefoxBossSpellAction.BLACK_HOLE.defaultSelectionWeight(false)).as("黑洞一阶段").isEqualTo(1);
        ctx.check(WinefoxBossSpellAction.BLACK_HOLE.defaultSelectionWeight(true)).as("黑洞二阶段").isEqualTo(2);
        Mismatches m = new Mismatches();
        for (WinefoxBossSpellAction action : WinefoxBossSpellAction.values()) {
            if (action != WinefoxBossSpellAction.SWORD_PRISON && action != WinefoxBossSpellAction.BLACK_HOLE) {
                m.equal(action.name() + " 一阶段", 2, action.defaultSelectionWeight(false));
                m.equal(action.name() + " 二阶段", 2, action.defaultSelectionWeight(true));
            }
        }
        m.verify(ctx, "其余法术的默认权重");
    }

    // ---- 近战节奏 ----

    /** 连段每一击的起手时刻，最后一项是整套打完、AI 被放开的时刻。 */
    private static void comboTimeline(SceneContext ctx, int hits, int... expected) {
        int[] ticks = new int[hits + 1];
        Mismatches m = new Mismatches();
        for (int i = 0; i < hits; i++) {
            WinefoxAction action = SWORD_ACTIONS.get(i);
            int cooldown = action.meleeCooldownTicks(i + 1 < hits);
            m.check(action.name() + " 冷却不短于接续窗口", cooldown >= action.followUpTicks());
            ticks[i + 1] = ticks[i] + cooldown;
        }
        m.equal("起手时刻互不相同", (long) hits, Arrays.stream(ticks).limit(hits).distinct().count());
        m.verify(ctx, hits + " 连击的冷却");
        ctx.check(Arrays.toString(ticks)).as(hits + " 连击时间线").isEqualTo(Arrays.toString(expected));
    }

    /** 只有剑系动作允许提前接续，其余动作的接续时刻等于动作时长。 */
    private static void onlySwordsFollowUp(SceneContext ctx) {
        Mismatches m = new Mismatches();
        for (WinefoxAction action : WinefoxAction.values()) {
            if (!SWORD_ACTIONS.contains(action)) {
                m.equal(action.name(), action.durationTicks(), action.followUpTicks());
            }
        }
        m.verify(ctx, "非剑系动作的接续时刻");
    }

    /** 剑系接续不丢动作事件，单独出招时冷却覆盖完整动画。 */
    private static void followUpsKeepEvents(SceneContext ctx) {
        JsonObject animations = WitchPack.animations("touhou_little_maid_spell.stellar_witch.arm.animation.json");
        Mismatches m = new Mismatches();
        for (WinefoxAction action : SWORD_ACTIONS) {
            double length = animations.getAsJsonObject(action.animationName()).get("animation_length").getAsDouble();
            m.equal(action.name() + " 收招冷却", (int) Math.ceil(length * 20), action.meleeCooldownTicks(false));
            m.check(action.name() + " 接续窗口在动作中途", action.followUpTicks() > 0 && action.followUpTicks() < action.durationTicks());
            for (WinefoxAction.Event event : action.events()) {
                m.check(action.name() + " 的事件 " + event + " 在接续前触发", event.tick() < action.followUpTicks());
            }
        }
        m.verify(ctx, "剑系动作的接续与事件");
    }

    // ---- 挑战配置 ----

    private static WinefoxChallengeConfig config(double armor) {
        return new WinefoxChallengeConfig(900.0D, armor, 1.4D, 2.3D, 0.12D, 9,
                0.6D, 7.0D, 0.9D, true, false, 40,
                List.of(new WinefoxSpellChoice("irons_spellbooks:magic_missile", 7)),
                List.of(new WinefoxSpellChoice("irons_spellbooks:heal", 4)), true);
    }

    private static <T> T roundTrip(Consumer<FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read, SceneContext ctx) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.accept(buffer);
            T result = read.apply(buffer);
            ctx.check(buffer.readableBytes()).as("读完后剩余字节").isEqualTo(0);
            return result;
        } finally {
            buffer.release();
        }
    }

    /** 护甲与其他设置一起经 NBT 和网络往返后不变。 */
    private static void armorRoundTrips(SceneContext ctx) {
        WinefoxChallengeConfig config = config(18.0D);
        ctx.check(WinefoxChallengeConfig.fromTag(config.toTag())).as("NBT 往返").isEqualTo(config);
        ctx.check(roundTrip(config::writeToBuffer, WinefoxChallengeConfig::fromBuffer, ctx)).as("网络往返").isEqualTo(config);
    }

    /** 没有护甲字段的旧挑战与预设按默认护甲读入。 */
    private static void legacyArmor(SceneContext ctx) {
        CompoundTag legacy = config(18.0D).toTag();
        legacy.remove("Armor");
        ctx.check(WinefoxChallengeConfig.defaults().armor()).as("默认护甲").isEqualTo(12.0D);
        ctx.check(WinefoxChallengeConfig.fromTag(legacy)).as("旧数据").isEqualTo(config(12.0D));
    }

    /** 护甲下限是服务器默认值的一半，上限是原版属性上限 30。 */
    private static void armorBounds(SceneContext ctx) {
        WinefoxChallengeConfig defaults = config(12.0D);
        ctx.check(WinefoxChallengeConfig.minimumBasicValue(1, defaults)).as("护甲下限").isEqualTo(6.0D);
        ctx.check(config(-1.0D).sanitized(defaults).armor()).as("负数").isEqualTo(6.0D);
        ctx.check(config(Double.NaN).sanitized(defaults).armor()).as("NaN").isEqualTo(6.0D);
        ctx.check(config(100.0D).sanitized(defaults).armor()).as("超上限").isEqualTo(30.0D);
        ctx.check(config(0.0D).sanitized(config(18.0D)).armor()).as("默认 18 时的下限").isEqualTo(9.0D);
    }

    /** 插入护甲一项后，其他基础设置的下限仍对应各自的设置项。 */
    private static void basicLimits(SceneContext ctx) {
        WinefoxChallengeConfig defaults = config(12.0D);
        double[] expected = {450.0D, 6.0D, 0.7D, 1.15D, 0.06D, 5.0D, 0.3D, 3.5D, 0.45D};
        Mismatches m = new Mismatches();
        for (int i = 0; i < expected.length; i++) {
            m.equal("第 " + i + " 项下限", expected[i], WinefoxChallengeConfig.minimumBasicValue(i, defaults));
        }
        m.verify(ctx, "基础设置的下限");
    }

    /** 预设经网络往返后保留护甲和空槽位。 */
    private static void presetsRoundTrip(SceneContext ctx) {
        List<WinefoxChallengeConfig> presets = WinefoxChallengePresets.empty();
        presets.set(0, config(18.0D));
        presets.set(2, config(24.0D));
        List<WinefoxChallengeConfig> restored = roundTrip(buffer -> WinefoxChallengePresets.writeBuffer(buffer, presets),
                WinefoxChallengePresets::readBuffer, ctx);
        ctx.check(restored).as("往返后的预设").isEqualTo(presets);
        ctx.check(restored.get(1)).as("空槽位").isNull();
    }
}
