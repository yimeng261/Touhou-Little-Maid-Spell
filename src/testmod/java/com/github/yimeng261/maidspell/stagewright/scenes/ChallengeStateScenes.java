package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import net.minecraft.server.level.ServerPlayer;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeProgress;
import com.mojang.authlib.GameProfile;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 星之魔女挑战进度记在主世界存档：旧版记在玩家身上的已击败/进行中标记的迁入与清除、挑战者死亡清掉进行中、
 * 已击败的玩家潜行右键星芒短剑能打开挑战配置。
 */
public final class ChallengeStateScenes {
    private static final String LEGACY_DEFEATED = "MaidSpellWinefoxDefeated";
    private static final String LEGACY_ACTIVE = "MaidSpellWinefoxChallengeActive";

    private ChallengeStateScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("challenge.legacyDefeatedMigrates", 5, ChallengeStateScenes::legacyDefeated));
        scenes.add(Checks.scene("challenge.legacyActiveCleared", 5, ChallengeStateScenes::legacyActive));
        scenes.add(Checks.scene("challenge.deathClearsActive", 5, ChallengeStateScenes::deathClears));
        scenes.add(Checks.scene("challenge.daggerConfigNeedsDefeat", 5, ChallengeStateScenes::daggerConfig));
        return scenes;
    }

    private static FakePlayer freshPlayer(SceneContext ctx) {
        String name = "TlmsChallenger" + Integer.toHexString(ctx.ticks() ^ (int) System.nanoTime());
        return FakePlayerFactory.get(ctx.level(), new GameProfile(UUID.randomUUID(), name));
    }

    private static void legacyDefeated(SceneContext ctx) {
        FakePlayer player = freshPlayer(ctx);
        CompoundTag persisted = new CompoundTag();
        persisted.putBoolean(LEGACY_DEFEATED, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ctx.check(WinefoxChallengeProgress.hasDefeated(player)).as("旧版已击败标记迁入").isTrue();
        ctx.check(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(LEGACY_DEFEATED)).as("玩家身上仍留着旧标记").isFalse();
        ctx.check(WinefoxChallengeProgress.hasDefeated(player)).as("迁入后再查仍是已击败").isTrue();
    }

    private static void legacyActive(SceneContext ctx) {
        FakePlayer player = freshPlayer(ctx);
        player.getPersistentData().putBoolean(LEGACY_ACTIVE, true);
        ctx.check(WinefoxChallengeProgress.hasActiveChallenge(player)).as("旧版卡住的挑战进行中").isFalse();
        ctx.check(player.getPersistentData().contains(LEGACY_ACTIVE)).as("玩家身上仍留着旧的进行中标记").isFalse();
    }

    private static void deathClears(SceneContext ctx) {
        FakePlayer player = freshPlayer(ctx);
        WinefoxChallengeProgress.markChallengeActive(ctx.server(), player.getUUID());
        ctx.check(WinefoxChallengeProgress.hasActiveChallenge(player)).as("标记后挑战进行中").isTrue();
        CommonHooks.onLivingDeath(player, player.damageSources().generic());
        ctx.check(WinefoxChallengeProgress.hasActiveChallenge(player)).as("死亡后挑战进行中").isFalse();
    }

    /** 没击败过时打不开；击败后能打开；挑战进行中打不开。 */
    private static void daggerConfig(SceneContext ctx) {
        ServerPlayer player = Owners.visitor(ctx, "Dagger" + UUID.randomUUID().toString().substring(0, 8), 0, 0, 0);
        player.setShiftKeyDown(true);
        List<String> results = new ArrayList<>();
        results.add("未击败 " + tryOpen(player));
        WinefoxChallengeProgress.markDefeated(ctx.server(), player.getUUID());
        results.add("已击败 " + tryOpen(player));
        WinefoxChallengeProgress.markChallengeActive(ctx.server(), player.getUUID());
        results.add("进行中 " + tryOpen(player));
        WinefoxChallengeProgress.clearChallengeActive(ctx.server(), player.getUUID());
        player.setShiftKeyDown(false);
        ctx.check(results).as("潜行右键星芒短剑是否打开配置").isEqualTo(List.of("未击败 false", "已击败 true", "进行中 false"));
    }

    private static boolean tryOpen(ServerPlayer player) {
        player.closeContainer();
        ItemStack dagger = Actors.stack(NS + "starglint_dagger");
        player.setItemInHand(InteractionHand.MAIN_HAND, dagger);
        dagger.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        boolean opened = player.containerMenu != player.inventoryMenu
                && player.containerMenu.getClass().getSimpleName().contains("WinefoxChallengeConfig");
        player.closeContainer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return opened;
    }
}
