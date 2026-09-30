package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.utils.PortableTimerMath;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 梦云水晶（女仆佩戴）：时停 1 秒（含卸载、换维度、重进世界）、20 格范围强化、概率复活
 * （120 秒窗口、每次 -10%、最多 10 条记录、15 秒无敌、旧记录迁移）、Boss 中立、受击间隔内被挡下的一击不追加真伤。
 * <p>计时都以主世界时间为准，在其他维度也一样。
 */
public final class DreamCrystalScenes {
    private static final String CRYSTAL = NS + "dream_cat_crystal";
    private static final String FROZEN_TAG = NS + "dream_crystal_frozen_until";
    private static final ResourceLocation NEARBY_BOOST = ResourceLocation.parse(NS + "dream_crystal_nearby_boost");
    private static final int FREEZE_TICKS = 20;
    private static final int REVIVE_WINDOW = 2400;
    private static final int REVIVE_INVULNERABLE = 300;
    /** 概率统计的试验次数。 */
    private static final int TRIALS = 200;

    private DreamCrystalScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.superflat("dream_crystal.timeStop", FREEZE_TICKS + 40, DreamCrystalScenes::timeStop));
        scenes.add(Checks.superflat("dream_crystal.timeStopKeepsNoAiMobs", FREEZE_TICKS + 40, DreamCrystalScenes::timeStopKeepsNoAi));
        scenes.add(Checks.superflat("dream_crystal.timeStopEndsAfterReload", FREEZE_TICKS + 60, DreamCrystalScenes::timeStopReload));
        scenes.add(Checks.superflat("dream_crystal.timeStopEndsAfterDimensionChange", FREEZE_TICKS + 60, DreamCrystalScenes::timeStopDimension));
        scenes.add(Checks.scene("dream_crystal.timeStopInOtherDimension", FREEZE_TICKS + 40, DreamCrystalScenes::timeStop)
                .withDimension("minecraft:the_nether"));
        scenes.add(Checks.superflat("dream_crystal.rangeBoost", 200, DreamCrystalScenes::rangeBoost));
        scenes.add(Checks.superflat("dream_crystal.revive.firstDeath", 40, DreamCrystalScenes::reviveFirstDeath));
        scenes.add(Checks.superflat("dream_crystal.revive.invulnerabilityExpires", 60, DreamCrystalScenes::invulnerabilityExpires));
        scenes.add(Checks.superflat("dream_crystal.revive.chanceByRecentCount", 20, DreamCrystalScenes::reviveChance));
        scenes.add(Checks.superflat("dream_crystal.revive.tenRecentNeverRevives", 40, DreamCrystalScenes::tenRecentDies));
        scenes.add(Checks.superflat("dream_crystal.revive.legacyHistoryCountsAsNone", 20, DreamCrystalScenes::legacyHistory));
        scenes.add(Checks.scene("dream_crystal.revive.legacyHistoryInOtherDimension", 20, DreamCrystalScenes::legacyHistory)
                .withDimension("minecraft:the_nether"));
        scenes.add(Checks.superflat("dream_crystal.revive.historyCarriesAcrossDimensions", 40, DreamCrystalScenes::historyAcrossDimensions));
        scenes.add(Checks.superflat("dream_crystal.revive.legacyInvulnerableTicksMigrate", 20, DreamCrystalScenes::legacyInvulnerable));
        scenes.add(Checks.superflat("dream_crystal.bossNeutral", 20, DreamCrystalScenes::bossNeutral));
        scenes.add(Checks.superflat("dream_crystal.blockedHitAddsNoTrueDamage", 20, DreamCrystalScenes::blockedHit));
        return scenes;
    }

    private static long now(SceneContext ctx) {
        return PortableTimerMath.overworldGameTime(ctx.server());
    }

    // ---- 时停 ----

    /** 女仆打到敌人后，敌人被定住（NoAI + 到期标记）约 1 秒后恢复。 */
    private static void timeStop(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, CRYSTAL);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, false);
        zombie.setPersistenceRequired();
        Checks.after(ctx, 2, () -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 2);
            ctx.check(zombie.isNoAi()).as("命中后敌人被定住").isTrue();
            ctx.check(zombie.getPersistentData().contains(FROZEN_TAG)).as("命中后有时停到期标记").isTrue();
            ctx.await(() -> !zombie.isNoAi()).within(FREEZE_TICKS + 20).then(() ->
                    ctx.check(zombie.getPersistentData().contains(FROZEN_TAG)).as("恢复后仍有时停标记").isFalse());
        });
    }

    /** 本来就是 NoAI 的生物被打后仍保持 NoAI，也不打标记。 */
    private static void timeStopKeepsNoAi(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, CRYSTAL);
        Mob statue = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true);
        Checks.after(ctx, 2, () -> {
            statue.hurt(maid.damageSources().mobAttack(maid), 2);
            ctx.check(statue.getPersistentData().contains(FROZEN_TAG)).as("原本 NoAI 的生物被打上时停标记").isFalse();
            Checks.after(ctx, FREEZE_TICKS + 10, () -> ctx.check(statue.isNoAi()).as("到期后原本 NoAI 的生物仍 NoAI").isTrue());
        });
    }

    /** 定身期间随区块卸载，到期后重新加载：已恢复行动，标记已清除。 */
    private static void timeStopReload(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, CRYSTAL);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, false);
        zombie.setPersistenceRequired();
        Checks.after(ctx, 2, () -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 2);
            ctx.check(zombie.isNoAi()).as("命中后敌人被定住").isTrue();
            Maids.reload(ctx, zombie, FREEZE_TICKS + 10, loaded -> Checks.after(ctx, 2, () -> {
                Mob mob = (Mob) loaded;
                ctx.check(mob.isNoAi()).as("重新加载后仍被定住").isFalse();
                ctx.check(mob.getPersistentData().contains(FROZEN_TAG)).as("重新加载后仍有时停标记").isFalse();
            }));
        });
    }

    /** 定身期间被传送到下界：到期后在下界恢复行动。 */
    private static void timeStopDimension(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, CRYSTAL);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, false);
        zombie.setPersistenceRequired();
        Checks.after(ctx, 2, () -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 2);
            ServerLevel nether = ctx.server().getLevel(Level.NETHER);
            Entity moved = zombie.changeDimension(new DimensionTransition(nether, new Vec3(0.5, 64, 0.5), Vec3.ZERO,
                    0, 0, DimensionTransition.DO_NOTHING));
            ctx.check(moved).as("传送到下界后的实体").isNotNull();
            if (!(moved instanceof Mob mob)) {
                return;
            }
            ctx.cleanup(mob::discard);
            ctx.await(() -> !mob.isNoAi()).within(FREEZE_TICKS + 30).then(() ->
                    ctx.check(mob.getPersistentData().contains(FROZEN_TAG)).as("下界里恢复后仍有时停标记").isFalse());
        });
    }

    // ---- 范围强化 ----

    /** 20 格内的其他女仆获得攻击加成，20 格外的没有；佩戴者走远后约 2 秒内加成消失。 */
    private static void rangeBoost(SceneContext ctx) {
        EntityMaid wearer = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(wearer, CRYSTAL);
        EntityMaid near = Actors.sittingMaid(ctx, 10, 0, 0);
        EntityMaid far = Actors.sittingMaid(ctx, 0, 0, 24);
        ctx.await(() -> boosted(near)).within(40).then(() -> {
            ctx.check(boosted(far)).as("24 格外的女仆有攻击加成").isFalse();
            ctx.check(boosted(wearer)).as("佩戴者自己有范围攻击加成").isFalse();
            wearer.teleportTo(wearer.getX() - 60, wearer.getY(), wearer.getZ());
            ctx.await(() -> !boosted(near)).within(80).then(() -> ctx.passNote("佩戴者走远后加成已消失"));
        });
    }

    private static boolean boosted(EntityMaid maid) {
        var attribute = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        return attribute != null && attribute.getModifier(NEARBY_BOOST) != null;
    }

    // ---- 复活 ----

    /** 第一次致死：复活、回满、获得 15 秒无敌（期间不受伤），记下一条复活时间。 */
    static void reviveFirstDeath(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ItemStack crystal = Actors.equipBauble(maid, CRYSTAL);
        Checks.after(ctx, 2, () -> {
            long at = now(ctx);
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("第一次致死后女仆存活").isTrue();
            ctx.check(maid.getHealth()).as("复活后生命").isCloseTo(maid.getMaxHealth(), 1e-3);
            ctx.check(crystal.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get()))
                    .as("无敌截止时间").isEqualTo(at + REVIVE_INVULNERABLE);
            ctx.check(crystal.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), List.of()))
                    .as("复活记录").isEqualTo(List.of(at));
            maid.invulnerableTime = 0;
            float before = maid.getHealth();
            maid.hurt(maid.damageSources().generic(), 4);
            ctx.check(maid.getHealth()).as("无敌期间受 4 点伤害后的生命").isCloseTo(before, 1e-3);
        });
    }

    /** 无敌到期后解除（按主世界时间），之后照常受伤。 */
    private static void invulnerabilityExpires(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ItemStack crystal = Actors.equipBauble(maid, CRYSTAL);
        crystal.set(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get(), now(ctx) + 10);
        ctx.check(DreamCatCrystalBauble.isInvulnerable(maid)).as("截止时间前无敌").isTrue();
        Checks.after(ctx, 20, () -> {
            ctx.check(DreamCatCrystalBauble.isInvulnerable(maid)).as("截止时间后无敌").isFalse();
            ctx.check(crystal.has(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get())).as("到期后仍留着截止时间").isFalse();
            Actors.setMaidHealth(maid, maid.getMaxHealth());
            maid.invulnerableTime = 0;
            maid.hurt(maid.damageSources().mobAttack(Actors.spawn(ctx, "minecraft:zombie", 0, 0, 3, true)), 4);
            ctx.check(maid.getHealth()).as("无敌到期后受伤").isLessThan(maid.getMaxHealth());
        });
    }

    /** 120 秒内每复活一次，概率降 10%：0 条记录必定复活，5 条约一半，窗口外的旧记录不计。 */
    private static void reviveChance(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        long now = now(ctx);
        List<Long> five = Collections.nCopies(5, now - 10);
        List<Long> stale = Collections.nCopies(10, now - REVIVE_WINDOW - 1);
        int none = trials(maid, List.of(), 1);
        int half = trials(maid, five, TRIALS);
        int old = trials(maid, stale, 1);
        ctx.record("revivedWithFive", half);
        ctx.check(none).as("没有记录时复活").isEqualTo(1);
        ctx.check(old).as("记录都在 120 秒外时复活").isEqualTo(1);
        ctx.check(half).as("5 条近期记录时 " + TRIALS + " 次里复活的次数（期望约一半）").isBetween(TRIALS * 35 / 100, TRIALS * 65 / 100);
    }

    /** 用给定的复活记录试 times 次，每次用新的水晶，返回复活次数。 */
    private static int trials(EntityMaid maid, List<Long> history, int times) {
        int revived = 0;
        for (int i = 0; i < times; i++) {
            ItemStack stack = Actors.stack(CRYSTAL);
            stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get(), 1);
            if (!history.isEmpty()) {
                stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), history);
            }
            if (DreamCatCrystalBauble.tryRevive(maid, stack)) {
                revived++;
            }
        }
        return revived;
    }

    /** 120 秒内已复活 10 次：不再复活，女仆真正死亡。 */
    private static void tenRecentDies(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ItemStack crystal = Actors.equipBauble(maid, CRYSTAL);
        Checks.after(ctx, 2, () -> {
            crystal.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get(), 1);
            crystal.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), Collections.nCopies(10, now(ctx) - 5));
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("近期已复活 10 次后致死，女仆存活").isFalse();
        });
    }

    /**
     * 旧版本（按各维度时钟、没有时钟版本号）留下的复活记录：升级后第一次复活按近期没有复活过（100%），
     * 之后按主世界时间记录。
     */
    private static void legacyHistory(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        long now = now(ctx);
        ItemStack stack = Actors.stack(CRYSTAL);
        stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), Collections.nCopies(10, now - 5));
        ctx.check(DreamCatCrystalBauble.tryRevive(maid, stack)).as("旧记录 10 条时复活").isTrue();
        ctx.check(stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get())).as("迁移后的时钟版本").isEqualTo(1);
        ctx.check(stack.get(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get())).as("迁移后的复活记录").isEqualTo(List.of(now));
    }

    /** 在主世界复活几次后到下界：记录按主世界时间延续，不清零也不卡满。 */
    private static void historyAcrossDimensions(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        long now = now(ctx);
        ItemStack stack = Actors.stack(CRYSTAL);
        stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_CLOCK_VERSION.get(), 1);
        stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), List.of(now - 30, now - 20));
        ServerLevel nether = ctx.server().getLevel(Level.NETHER);
        EntityMaid inNether = (EntityMaid) Maids.copyToDimension(ctx, maid, nether, new Vec3(0.5, 64, 0.5));
        ctx.check(inNether).as("传送到下界的女仆").isNotNull();
        if (inNether == null) {
            return;
        }
        ctx.cleanup(() -> Actors.cleanupEntity(inNether));
        inNether.setNoAi(true);
        // 两条记录时概率 80%，多试几次直到成功一次
        boolean revived = false;
        for (int i = 0; i < 20 && !revived; i++) {
            revived = DreamCatCrystalBauble.tryRevive(inNether, stack);
        }
        ctx.check(revived).as("下界里复活").isTrue();
        List<Long> history = stack.getOrDefault(MaidSpellDataComponents.DREAM_CRYSTAL_REVIVE_TIMESTAMPS.get(), List.of());
        ctx.check(history.size()).as("下界复活后的记录条数").isEqualTo(3);
        ctx.check(history.getLast()).as("下界复活记下的时间（主世界时间）").isEqualTo(now(ctx));
    }

    /** 旧存档里正处于复活无敌（按剩余 tick 记）的水晶：升级后换成截止时间，剩余时间后结束。 */
    private static void legacyInvulnerable(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ItemStack stack = Actors.stack(CRYSTAL);
        stack.set(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS.get(), 100);
        maid.getMaidBauble().setStackInSlot(0, stack);
        ItemStack worn = maid.getMaidBauble().getStackInSlot(0);
        long at = now(ctx);
        Checks.after(ctx, 3, () -> {
            ctx.check(worn.has(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_TICKS.get())).as("迁移后仍有旧的剩余 tick").isFalse();
            Long until = worn.get(MaidSpellDataComponents.DREAM_CRYSTAL_INVULNERABLE_UNTIL.get());
            ctx.check(until).as("迁移后的无敌截止时间").isNotNull();
            if (until != null) {
                ctx.check(until - at).as("截止时间距迁移前的时间").isBetween(95L, 105L);
            }
            ctx.check(DreamCatCrystalBauble.isInvulnerable(maid)).as("迁移后仍无敌").isTrue();
        });
    }

    // ---- Boss 中立 ----

    /** 普通怪物不以佩戴者为目标；Boss 只在女仆打过它之后才会以她为目标。 */
    private static void bossNeutral(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, CRYSTAL);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 3, 0, 0, true);
        Mob wither = Actors.spawn(ctx, "minecraft:wither", -6, 0, 0, true);
        ctx.check(Owners.aim(zombie, maid)).as("僵尸以佩戴者为目标").isNull();
        ctx.check(Owners.aim(wither, maid)).as("女仆没打过时凋灵以她为目标").isNull();
        wither.invulnerableTime = 0;
        wither.hurt(maid.damageSources().mobAttack(maid), 1);
        ctx.check(Owners.aim(wither, maid)).as("女仆打过后凋灵以她为目标").isEqualTo(maid);
        ctx.check(Owners.aim(zombie, maid)).as("女仆打过凋灵后僵尸以她为目标").isNull();
    }

    // ---- 受击间隔 ----

    /** 同样伤害连打两下、第二下在受击间隔内被挡：只结算第一下和它的真伤（100 血的猪剩 96）。 */
    private static void blockedHit(SceneContext ctx) {
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, CRYSTAL);
        Mob pig = Actors.spawn(ctx, "minecraft:pig", 0, 0, 2, true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        pig.setHealth(100);
        pig.invulnerableTime = 0;
        pig.hurt(maid.damageSources().mobAttack(maid), 2);
        pig.hurt(maid.damageSources().mobAttack(maid), 2);
        Checks.after(ctx, 2, () -> ctx.check(pig.getHealth()).as("连打两下后猪的生命").isCloseTo(96, 1e-3));
    }
}
