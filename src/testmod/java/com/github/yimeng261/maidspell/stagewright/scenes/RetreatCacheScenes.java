package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 归隐之地的搜索缓存与维度：摇铃后不留强加载、reset 命令清缓存、找不到时的负缓存约 5 分钟后过期、
 * 不同玩家的私人归隐之地地形不同。
 */
public final class RetreatCacheScenes {
    /** 负缓存有效期（游戏刻），与寻风之铃「约 5 分钟内再用直接提示没有」对应。 */
    private static final long NEGATIVE_TTL = 6000;

    private RetreatCacheScenes() {
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("retreat.searchLeavesNoForcedChunks", 1200, RetreatCacheScenes::noForcedChunks));
        scenes.add(Players.hostScene("retreat.resetCommandClearsCache", 1200, RetreatCacheScenes::resetClears));
        scenes.add(Players.hostScene("retreat.negativeCacheExpires", 800, RetreatCacheScenes::negativeExpires));
        scenes.add(Players.hostScene("retreat.privateTerrainDiffersPerPlayer", 1200, RetreatCacheScenes::terrainDiffers));
        return scenes;
    }

    private static void noForcedChunks(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, RetreatScenes.bell(RetreatScenes.BELLS));
        RetreatScenes.enterRetreat(ctx, player, () -> RetreatScenes.locate(ctx, player, found ->
                ctx.check(player.serverLevel().getForcedChunks().size()).as("摇铃找到结构后归隐之地的强加载区块数").isEqualTo(0)));
    }

    private static void resetClears(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, RetreatScenes.bell(RetreatScenes.BELLS));
        RetreatScenes.enterRetreat(ctx, player, () -> RetreatScenes.locate(ctx, player, found -> {
            ServerLevel level = player.serverLevel();
            ctx.check(RetreatManager.checkCache(level, player.getUUID()).position != null).as("找到后有缓存坐标").isTrue();
            try {
                ctx.server().getCommands().getDispatcher().execute("maidspell retreat reset " + player.getScoreboardName(),
                        ctx.server().createCommandSourceStack().withSuppressedOutput().withPermission(4));
            } catch (CommandSyntaxException e) {
                ctx.fail("reset 命令执行失败：" + e.getMessage());
                return;
            }
            ctx.check(RetreatManager.checkCache(level, player.getUUID()).hasCache).as("reset 后仍有缓存").isFalse();
        }));
    }

    /** 负缓存在记下后 6000 刻内有效，之后当作没有缓存重新搜索（临时把主世界游戏时间往后拨再拨回）。 */
    private static void negativeExpires(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, RetreatScenes.bell(RetreatScenes.BELLS));
        RetreatScenes.enterRetreat(ctx, player, () -> {
            ServerLevel level = player.serverLevel();
            UUID probe = UUID.randomUUID();
            RetreatManager.updateCache(level, probe, null);
            ctx.check(RetreatManager.checkCache(level, probe).isNegative).as("刚记下的负缓存").isTrue();
            ServerLevelData data = (ServerLevelData) ctx.server().overworld().getLevelData();
            long now = data.getGameTime();
            data.setGameTime(now + NEGATIVE_TTL - 100);
            boolean stillNegative = RetreatManager.checkCache(level, probe).isNegative;
            data.setGameTime(now + NEGATIVE_TTL + 100);
            boolean expired = !RetreatManager.checkCache(level, probe).hasCache;
            data.setGameTime(now);
            ctx.check(stillNegative).as("快到 5 分钟时负缓存仍有效").isTrue();
            ctx.check(expired).as("过了 5 分钟负缓存失效").isTrue();
        });
    }

    /** 宿主和另一名玩家的私人归隐之地，同一批坐标上的地表高度不全相同。 */
    private static void terrainDiffers(SceneContext ctx, ServerPlayer player) {
        UUID other = UUID.nameUUIDFromBytes("TlmsRetreatNeighbor".getBytes());
        CompletableFuture<ServerLevel> mine = PlayerRetreatManager.getOrCreatePlayerRetreatAsync(ctx.server(), player.getUUID());
        CompletableFuture<ServerLevel> theirs = PlayerRetreatManager.getOrCreatePlayerRetreatAsync(ctx.server(), other);
        ctx.await(() -> mine.isDone() && theirs.isDone()).within(1100).then(() -> {
            ServerLevel a = mine.join();
            ServerLevel b = theirs.join();
            List<Integer> ha = new ArrayList<>();
            List<Integer> hb = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                BlockPos p = new BlockPos(i * 97 - 700, 0, i * 53 - 400);
                ha.add(height(a, p));
                hb.add(height(b, p));
            }
            ctx.record("mine", ha);
            ctx.record("theirs", hb);
            ctx.check(ha.equals(hb)).as("两名玩家归隐之地同批坐标的地表高度完全相同").isFalse();
        });
    }

    private static int height(ServerLevel level, BlockPos p) {
        return level.getChunkSource().getGenerator().getBaseHeight(p.getX(), p.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                level, level.getChunkSource().randomState());
    }
}
