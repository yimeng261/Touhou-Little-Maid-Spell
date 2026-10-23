package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 饰品运行态按女仆隔离：魂之书主人保护（集成服，需要真实玩家）与受伤间隔、破愈咒锋禁疗记录、紫荆银冠计数；
 * 卸下、死亡、收进魂符、区块卸载时清理（戴锚定核心的女仆区块不卸载，保护保留），被复活饰品救下时保留。
 */
public final class BaubleRuntimeScenes {
    private static final String SOUL_BOOK = NS + "soul_book";
    private static final String WOUND_RIME = NS + "wound_rime_blade";
    private static final String CERCIS = NS + "sliver_cercis";
    private static final String CRYSTAL = NS + "dream_cat_crystal";
    private static final String ANCHOR = NS + "anchor_core";
    /** 远程女仆与玩家的距离：超出集成服视距，强加载解除后区块会卸载。 */
    private static final int REMOTE = 320;

    private BaubleRuntimeScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.superflat("bauble.soul_book.intervalAfterReequip", 40 + Config.soulBookDamageIntervalThreshold * 3,
                BaubleRuntimeScenes::soulBookInterval));
        scenes.add(Checks.superflat("bauble.wound_rime_blade.blocksHealingUntilUsedUp", 40, BaubleRuntimeScenes::woundRimeBlocks));
        scenes.add(Checks.superflat("bauble.wound_rime_blade.skipsPlayersAndMaids", 40, BaubleRuntimeScenes::woundRimeSkips));
        scenes.add(Checks.superflat("bauble.wound_rime_blade.noResidueAfterDeath", 60, ctx -> woundRimeResidue(ctx, "death")));
        scenes.add(Checks.superflat("bauble.wound_rime_blade.noResidueAfterUnload", 60, ctx -> woundRimeResidue(ctx, "unload")));
        scenes.add(Checks.superflat("bauble.wound_rime_blade.noResidueAfterDimensionChange", 60, ctx -> woundRimeResidue(ctx, "dimension")));
        scenes.add(Checks.superflat("bauble.sliver_cercis.resetOnTakeOff", 40, BaubleRuntimeScenes::cercisReset));
        scenes.add(Checks.superflat("bauble.runtimeKeptWhenRevived", 60, BaubleRuntimeScenes::keptWhenRevived));
        scenes.add(Checks.superflat("bauble.runtimeClearedOnDeath", 60, BaubleRuntimeScenes::clearedOnDeath));
        return scenes;
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("bauble.soul_book.ownerProtectionOnOff", 20, BaubleRuntimeScenes::protectionOnOff));
        scenes.add(Players.hostScene("bauble.soul_book.ownerProtectionAfterCycles", 20, BaubleRuntimeScenes::protectionCycles));
        scenes.add(Players.hostScene("bauble.soul_book.ownerProtectionEndsOnDeath", 20, BaubleRuntimeScenes::protectionDeath));
        scenes.add(Players.hostScene("bauble.soul_book.ownerProtectionEndsInSlab", 40, BaubleRuntimeScenes::protectionSlab));
        scenes.add(Players.hostScene("bauble.soul_book.ownerProtectionEndsOnUnload", 400, BaubleRuntimeScenes::protectionUnload));
        scenes.add(Players.hostScene("bauble.soul_book.ownerProtectionKeptWhenAnchored", 400, BaubleRuntimeScenes::protectionAnchored));
        return scenes;
    }

    // ---- 魂之书 ----

    /** 直接 setHealth 往下调：受保护时被拦下（生命不变），否则照常生效。返回是否被拦下。 */
    private static boolean directWriteBlocked(ServerPlayer player) {
        player.setHealth(20);
        player.setHealth(10);
        boolean blocked = player.getHealth() == 20;
        player.setHealth(20);
        return blocked;
    }

    /** 装备魂之书时主人受保护、正常受伤照常扣血；卸下后立即失效，再装备后恢复。 */
    private static void protectionOnOff(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
        ctx.check(directWriteBlocked(player)).as("装备魂之书时直接改写主人生命被拦下").isTrue();
        player.setHealth(20);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), 4);
        ctx.check(player.getHealth()).as("装备魂之书时主人正常受 4 点伤害后的生命").isCloseTo(16, 1e-4);
        player.setHealth(20);
        Maids.takeOffAll(maid);
        ctx.check(directWriteBlocked(player)).as("卸下魂之书后直接改写主人生命被拦下").isFalse();
        Actors.equipBauble(maid, SOUL_BOOK);
        ctx.check(directWriteBlocked(player)).as("重新装备后直接改写主人生命被拦下").isTrue();
    }

    /** 反复穿脱多次后状态仍与当前是否装备一致：不会永久开启或永久失效。 */
    private static void protectionCycles(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
        EntityMaid second = Players.ownedMaid(ctx, player, -2, 0, 0, SOUL_BOOK);
        for (int i = 0; i < 5; i++) {
            Maids.takeOffAll(maid);
            Actors.equipBauble(maid, SOUL_BOOK);
        }
        Maids.takeOffAll(second);
        ctx.check(directWriteBlocked(player)).as("穿脱 5 次后仍装备着时受保护").isTrue();
        Maids.takeOffAll(maid);
        ctx.check(directWriteBlocked(player)).as("两只女仆都卸下后受保护").isFalse();
        Actors.equipBauble(second, SOUL_BOOK);
        ctx.check(directWriteBlocked(player)).as("另一只女仆重新装备后受保护").isTrue();
    }

    /** 装备魂之书的女仆死亡后保护失效。 */
    private static void protectionDeath(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
        ctx.check(directWriteBlocked(player)).as("女仆存活时受保护").isTrue();
        hurt(maid, 2);
        Checks.after(ctx, Config.soulBookDamageIntervalThreshold + 2, () -> {
            Maids.kill(maid);
            ctx.check(maid.isAlive()).as("女仆已死亡").isFalse();
            ctx.check(directWriteBlocked(player)).as("女仆死亡后受保护").isFalse();
        });
    }

    /** 女仆被收进魂符后保护失效；放出来后恢复。 */
    private static void protectionSlab(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Players.ownedMaid(ctx, player, 2, 0, 0, SOUL_BOOK);
        UUID id = maid.getUUID();
        ItemStack slab = Players.storeInSlab(player, maid);
        ctx.check(maid.isRemoved()).as("女仆已收进魂符").isTrue();
        ctx.check(directWriteBlocked(player)).as("女仆在魂符里时受保护").isFalse();
        Players.releaseSlab(ctx, player, slab, ctx.rel(2, -1, 0), () -> Checks.after(ctx, 2, () -> {
            Entity released = Maids.find(ctx.level(), id);
            ctx.check(released instanceof EntityMaid).as("魂符放出了同一只女仆").isTrue();
            if (released != null) {
                ctx.cleanup(() -> Actors.cleanupEntity(released));
            }
            ctx.check(directWriteBlocked(player)).as("放出后受保护").isTrue();
        }));
    }

    /** 开家模式并把家设在女仆当前位置：不跟随主人，也不被传回默认的原点。 */
    private static void stayHome(EntityMaid maid) {
        maid.setHomeModeEnable(true);
        maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
    }

    /** 女仆所在区块卸载后保护失效。 */
    private static void protectionUnload(SceneContext ctx, ServerPlayer player) {
        ServerLevel level = ctx.level();
        BlockPos far = ctx.rel(0, 0, REMOTE);
        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        ctx.cleanup(() -> level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false));
        EntityMaid maid = Players.ownedMaid(ctx, player, 0, 0, REMOTE, SOUL_BOOK);
        stayHome(maid);
        ctx.check(directWriteBlocked(player)).as("远处女仆区块加载时受保护").isTrue();
        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
        ctx.await(maid::isRemoved).within(360).then(() -> {
            ctx.record("removalReason", String.valueOf(maid.getRemovalReason()));
            ctx.check(maid.getRemovalReason()).as("女仆随区块卸载").isEqualTo(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            ctx.check(directWriteBlocked(player)).as("女仆随区块卸载后受保护").isFalse();
        });
    }

    /** 同时戴锚定核心的女仆，撤掉场景的强加载后区块仍由锚定核心保持加载，保护不失效。 */
    private static void protectionAnchored(SceneContext ctx, ServerPlayer player) {
        ServerLevel level = ctx.level();
        BlockPos far = ctx.rel(0, 0, REMOTE);
        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        ctx.cleanup(() -> level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false));
        EntityMaid maid = Players.ownedMaid(ctx, player, 0, 0, REMOTE, SOUL_BOOK);
        Maids.putOn(maid, 1, Actors.stack(ANCHOR));
        stayHome(maid);
        ctx.check(directWriteBlocked(player)).as("远处女仆区块加载时受保护").isTrue();
        Checks.after(ctx, 10, () -> {
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
            Checks.after(ctx, 360, () -> {
                ctx.check(maid.isRemoved()).as("戴锚定核心的女仆随区块卸载").isFalse();
                ctx.check(directWriteBlocked(player)).as("撤掉场景强加载后受保护").isTrue();
            });
        });
    }

    /**
     * 受伤间隔：卸下再装备后，旧的受伤时间被清掉；重新装备后第一次受击按首次受击记录，
     * 间隔超过阈值的下一次受击照常扣血。
     */
    private static void soulBookInterval(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, SOUL_BOOK);
        int gap = Config.soulBookDamageIntervalThreshold + 2;
        Checks.after(ctx, 2, () -> {
            hurt(maid, 2);
            ctx.check(Maids.soulBookRecorded(maid.getUUID())).as("受伤后记录了受伤时间").isTrue();
            Maids.takeOffAll(maid);
            ctx.check(Maids.soulBookRecorded(maid.getUUID())).as("卸下后仍有受伤时间记录").isFalse();
            Actors.equipBauble(maid, SOUL_BOOK);
            float first = hurt(maid, 2);
            ctx.record("firstHitAfterReequip", first);
            Checks.after(ctx, gap, () -> ctx.check(hurt(maid, 2))
                    .as("重新装备后隔 " + gap + " tick 的受击扣血").isGreaterThan(0F));
        });
    }

    private static float hurt(LivingEntity entity, float amount) {
        if (entity instanceof EntityMaid maid) {
            Actors.setMaidHealth(maid, maid.getMaxHealth());
        } else {
            entity.setHealth(entity.getMaxHealth());
        }
        entity.invulnerableTime = 0;
        float before = entity.getHealth();
        entity.hurt(entity.damageSources().generic(), amount);
        return before - entity.getHealth();
    }

    // ---- 破愈咒锋 ----

    /** 被女仆打过的敌人回血被拦下；记录次数用完后恢复正常回血。 */
    private static void woundRimeBlocks(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, WOUND_RIME);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true);
        Checks.after(ctx, 2, () -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 4);
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as("命中后有禁疗记录").isTrue();
            int blocked = 0;
            int limit = Config.woundRimeBladeRecordTimes * 2 + 2;
            while (blocked < limit) {
                float before = zombie.getHealth();
                zombie.heal(1);
                if (zombie.getHealth() > before) {
                    break;
                }
                blocked++;
            }
            ctx.record("blockedHeals", blocked);
            ctx.check(blocked).as("被拦下的回血次数").isGreaterThan(0);
            ctx.check(blocked).as("被拦下的回血次数（次数用完应恢复）").isLessThan(limit);
            float before = zombie.getHealth();
            zombie.heal(1);
            ctx.check(zombie.getHealth()).as("记录用完后回血").isGreaterThan(before);
        });
    }

    /** 玩家和女仆不会被记录。 */
    private static void woundRimeSkips(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, WOUND_RIME);
        EntityMaid other = Actors.stillMaid(ctx, 0, 0, 2);
        Checks.after(ctx, 2, () -> {
            other.hurt(maid.damageSources().mobAttack(maid), 2);
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as("打女仆后有禁疗记录").isFalse();
            Actors.setMaidHealth(other, other.getMaxHealth() - 4);
            float before = other.getHealth();
            other.heal(2);
            ctx.check(other.getHealth()).as("被打过的女仆回血").isGreaterThan(before);
        });
    }

    /** 敌人死亡、卸载或换维度后记录被清掉：同 UUID 的实体回来后照常回血。 */
    private static void woundRimeResidue(SceneContext ctx, String how) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, WOUND_RIME);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true);
        UUID id = zombie.getUUID();
        Checks.after(ctx, 2, () -> {
            zombie.hurt(maid.damageSources().mobAttack(maid), 4);
            ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as("命中后有禁疗记录").isTrue();
            switch (how) {
                case "death" -> zombie.kill();
                case "unload" -> zombie.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                default -> {
                    ServerLevel nether = ctx.server().getLevel(Level.NETHER);
                    Entity moved = zombie.changeDimension(new DimensionTransition(nether, zombie.position(), Vec3.ZERO,
                            0, 0, DimensionTransition.DO_NOTHING));
                    if (moved != null) {
                        ctx.cleanup(() -> Actors.cleanupEntity(moved));
                    }
                }
            }
            // 女仆每 tick 清理不在场或已死亡的目标
            Checks.after(ctx, 5, () -> {
                ctx.check(Maids.woundRimeRecorded(maid.getUUID())).as(how + " 后仍有禁疗记录").isFalse();
                Mob again = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -2, true, (Mob mob) -> mob.setUUID(id));
                again.setHealth(again.getMaxHealth() - 4);
                float before = again.getHealth();
                again.heal(2);
                ctx.check(again.getHealth()).as(how + " 后同 UUID 的实体回血").isGreaterThan(before);
            });
        });
    }

    // ---- 紫荆银冠 ----

    /** 受伤计数在卸下饰品后清空，重新装备后从头计。 */
    private static void cercisReset(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, CERCIS);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true);
        Checks.after(ctx, 2, () -> {
            for (int i = 0; i < 2; i++) {
                maid.setLastHurtByMob(zombie);
                hurtBy(maid, zombie);
            }
            ctx.record("countBeforeTakeOff", Maids.silverCercisCount(maid.getUUID()));
            ctx.check(Maids.silverCercisCount(maid.getUUID())).as("受击后有计数").isNotNull();
            Maids.takeOffAll(maid);
            ctx.check(Maids.silverCercisCount(maid.getUUID())).as("卸下后的计数").isNull();
            Actors.equipBauble(maid, CERCIS);
            maid.setLastHurtByMob(zombie);
            hurtBy(maid, zombie);
            Integer count = Maids.silverCercisCount(maid.getUUID());
            ctx.check(count == null || count <= 1).as("重新装备后第一次受击后的计数（" + count + "）从头开始").isTrue();
        });
    }

    private static void hurtBy(EntityMaid maid, Mob attacker) {
        Actors.setMaidHealth(maid, maid.getMaxHealth());
        maid.invulnerableTime = 0;
        maid.hurt(maid.damageSources().mobAttack(attacker), 2);
    }

    // ---- 复活与死亡 ----

    /** 被梦云水晶救下（死亡事件取消）后，魂之书受伤时间、破愈咒锋禁疗记录、紫荆银冠计数都保留。 */
    private static void keptWhenRevived(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Maids.equip(maid, CRYSTAL, SOUL_BOOK, WOUND_RIME, CERCIS);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true);
        Checks.after(ctx, 2, () -> {
            buildRuntimeState(maid, zombie);
            UUID id = maid.getUUID();
            ctx.record("before", state(id));
            Checks.after(ctx, Config.soulBookDamageIntervalThreshold + 2, () -> {
                Maids.kill(maid);
                ctx.check(maid.isAlive()).as("梦云水晶救下女仆").isTrue();
                ctx.check(maid.getHealth()).as("复活恢复满血").isCloseTo(maid.getMaxHealth(), 1e-3f);
                ctx.check(state(id)).as("复活后的运行态（魂之书/破愈咒锋/紫荆银冠）").isEqualTo(List.of(true, true, true));
            });
        });
    }

    /** 真正死亡时三种运行态都清掉。 */
    private static void clearedOnDeath(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Maids.equip(maid, SOUL_BOOK, WOUND_RIME, CERCIS);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true);
        Checks.after(ctx, 2, () -> {
            buildRuntimeState(maid, zombie);
            UUID id = maid.getUUID();
            ctx.check(state(id)).as("死亡前的运行态").isEqualTo(List.of(true, true, true));
            Checks.after(ctx, Config.soulBookDamageIntervalThreshold + 2, () -> {
                Maids.kill(maid);
                ctx.check(maid.isAlive()).as("女仆已死亡").isFalse();
                ctx.check(state(id)).as("死亡后的运行态").isEqualTo(List.of(false, false, false));
            });
        });
    }

    private static void buildRuntimeState(EntityMaid maid, Mob zombie) {
        hurt(maid, 2);
        zombie.hurt(maid.damageSources().mobAttack(maid), 2);
        maid.setLastHurtByMob(zombie);
        hurtBy(maid, zombie);
    }

    private static List<Boolean> state(UUID id) {
        return List.of(Maids.soulBookRecorded(id), Maids.woundRimeRecorded(id), Maids.silverCercisCount(id) != null);
    }
}
