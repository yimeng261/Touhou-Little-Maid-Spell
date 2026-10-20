package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Hits;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.utils.FriendlyDamageHealthGuard;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 强力防友伤（{@code powerfulFriendlyFireProtection}）：友方强制写血在 1 tick 窗口内被恢复、窗口内的移除被拦截，
 * 窗口过期后放开，敌方伤害照常致死，开关默认关闭时什么都不做；星之魔女的 hurt 早退路径不残留伤害帧。
 */
public final class FriendlyGuardScenes {
    private FriendlyGuardScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.superflat("friendlyGuard.restoresForcedWriteAndBlocksRemoval", 10, FriendlyGuardScenes::restoresAndBlocks));
        scenes.add(Checks.superflat("friendlyGuard.windowExpiresAfterOneTick", 20, FriendlyGuardScenes::windowExpires));
        scenes.add(Checks.superflat("friendlyGuard.offByDefaultDoesNothing", 10, FriendlyGuardScenes::offByDefault));
        scenes.add(Checks.superflat("friendlyGuard.hostileDamageStillKills", 10, FriendlyGuardScenes::hostileKills));
        scenes.add(Checks.superflat("friendlyGuard.winefoxEarlyExitLeavesNoFrame", 10, FriendlyGuardScenes::winefoxEarlyExit));
        return scenes;
    }

    /** 打开开关，场景结束时还原。 */
    private static void enable(SceneContext ctx) {
        boolean previous = Config.powerfulFriendlyFireProtection;
        Config.powerfulFriendlyFireProtection = true;
        ctx.cleanup(() -> Config.powerfulFriendlyFireProtection = previous);
    }

    /** 主人的魔法伤害：打自己的宠物属于友方伤害。 */
    private static DamageSource friendly(ServerPlayer owner) {
        return Hits.magic(null, owner);
    }

    /** 友方命中后一个 tick 内：强制把血写低被恢复到命中前，/kill 式的移除被拦下。 */
    private static void restoresAndBlocks(SceneContext ctx) {
        enable(ctx);
        ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
        TamableAnimal wolf = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
        float full = wolf.getHealth();

        FriendlyDamageHealthGuard.onDamageAttempt(wolf, friendly(owner));
        wolf.setHealth(1.0F);
        ctx.check(wolf.getHealth()).as("友方命中后强制写到 1 点的生命").isEqualTo(full);

        wolf.kill();
        ctx.check(wolf.isRemoved()).as("窗口内的 KILLED 移除").isFalse();
        ctx.check(wolf.getHealth()).as("移除被拦下后的生命").isEqualTo(full);
    }

    /** 窗口只有 1 tick：之后同样的强制写血不再被恢复。 */
    private static void windowExpires(SceneContext ctx) {
        enable(ctx);
        ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
        TamableAnimal wolf = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
        FriendlyDamageHealthGuard.onDamageAttempt(wolf, friendly(owner));
        Checks.after(ctx, 4, () -> {
            wolf.setHealth(1.0F);
            ctx.check(wolf.getHealth()).as("窗口过期后强制写到 1 点的生命").isEqualTo(1.0F);
        });
    }

    /** 开关默认关闭：友方命中后强制写血照常生效。 */
    private static void offByDefault(SceneContext ctx) {
        ctx.check(Config.powerfulFriendlyFireProtection).as("默认值").isFalse();
        ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
        TamableAnimal wolf = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
        FriendlyDamageHealthGuard.onDamageAttempt(wolf, friendly(owner));
        wolf.setHealth(1.0F);
        ctx.check(wolf.getHealth()).as("关闭时强制写到 1 点的生命").isEqualTo(1.0F);
    }

    /** 开启后敌对生物的正常攻击不被误拦：致命一击照常致死。 */
    private static void hostileKills(SceneContext ctx) {
        enable(ctx);
        ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
        TamableAnimal wolf = Owners.pet(ctx, "minecraft:wolf", owner, 3, 0, 0);
        LivingEntity zombie = Actors.spawn(ctx, "minecraft:zombie", -3, 0, 0, true);
        wolf.hurt(Hits.source(ctx.level(), DamageTypes.MOB_ATTACK, zombie, zombie), 1000.0F);
        ctx.check(wolf.isDeadOrDying()).as("敌对生物致命一击后宠物").isTrue();
    }

    /**
     * 星之魔女的 hurt 在伤害量非正等早退分支里不进 super.hurt，RETURN 钩子不会清伤害帧；
     * 魔女自己用 finally 清掉，不能残留到 tick 末。
     */
    @SuppressWarnings("unchecked")
    private static void winefoxEarlyExit(SceneContext ctx) {
        enable(ctx);
        ServerPlayer owner = Owners.visitor(ctx, "TlmsGuardOwner", 0, 0, 0);
        MagicalWinefoxBossEntity boss = Actors.spawn(ctx, NS + "stellar_witch", 4, 0, 0, true);
        Owners.team(ctx, "TlmsGuardTeam", false, owner);
        ctx.server().getScoreboard().addPlayerToTeam(boss.getStringUUID(), ctx.server().getScoreboard().getPlayerTeam("TlmsGuardTeam"));
        DamageSource source = Hits.source(ctx.level(), DamageTypes.PLAYER_ATTACK, owner, owner);
        ThreadLocal<Deque<?>> frames = Reflect.field(null, FriendlyDamageHealthGuard.class, "DAMAGE_FRAMES");

        FriendlyDamageHealthGuard.onDamageAttempt(boss, source);
        ctx.check(frames.get().size()).as("同队玩家的伤害被识别为友方，记了一帧").isEqualTo(1);
        FriendlyDamageHealthGuard.leaveDamageAttempt(boss, source);
        ctx.check(frames.get().size()).as("手动离开后的伤害帧").isEqualTo(0);

        boolean landed = boss.hurt(source, 0.0F);
        ctx.check(landed).as("伤害量为 0 的命中").isFalse();
        ctx.check(frames.get().size()).as("早退后残留的伤害帧").isEqualTo(0);
    }
}
