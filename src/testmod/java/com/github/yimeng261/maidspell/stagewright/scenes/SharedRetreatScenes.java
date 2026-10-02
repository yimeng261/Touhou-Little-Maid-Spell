package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatDimensionData;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.support.Batch;
import com.github.yimeng261.maidspell.stagewright.support.ChatTap;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.BELLS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.BELL_KEY;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.ENTERED;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.ENTER_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.HIDDEN_RETREAT;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.NOTICE_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.SEARCH_TICKS;
import static com.github.yimeng261.maidspell.stagewright.scenes.RetreatScenes.WAITING;

/**
 * 共享归隐之地模式（关闭私人维度、开启配额）：首次进入的提示与配额、搜索期间换手不吞铃、
 * 已找到后重复查看、没有配额时的提示、不同玩家各自找到自己的隐世之境。
 * <p>场景按注册顺序运行并共享宿主玩家在 RetreatDimensionData 里的记录。
 */
public final class SharedRetreatScenes {
    private static final String FIRST_ENTRY = BELL_KEY + "first_entry_shared";
    private static final String NO_QUOTA = BELL_KEY + "no_quota";
    private static final ResourceLocation SHARED = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat");
    /** 动作栏提示在客户端停留约 60 tick；首次进入提示至少要这么久不被别的动作栏消息顶掉才看得到。 */
    private static final int OVERLAY_VISIBLE_TICKS = 40;

    private SharedRetreatScenes() {
    }

    public static List<Scene> integratedServerShared() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("shared.windBell.firstEntryNoticeVisible", ENTER_TICKS + 200,
                SharedRetreatScenes::firstEntryNoticeVisible));
        scenes.add(Players.hostScene("shared.windBell.firstEntry", 2 * ENTER_TICKS + 100, SharedRetreatScenes::firstEntry));
        scenes.add(Players.hostScene("shared.windBell.switchItemDuringSearch", ENTER_TICKS + SEARCH_TICKS + 100,
                SharedRetreatScenes::switchItemDuringSearch));
        scenes.add(Players.hostScene("shared.windBell.reshowSameCoordinates", ENTER_TICKS + SEARCH_TICKS + 100,
                (ctx, player) -> reshow(ctx, player, false)));
        // 重复查看复用坐标，仍消耗一只铃
        scenes.add(Players.hostScene("shared.windBell.reshowConsumesBell", ENTER_TICKS + SEARCH_TICKS + 100,
                (ctx, player) -> reshow(ctx, player, true)));
        scenes.add(Players.hostScene("shared.windBell.noQuota", ENTER_TICKS + 200, SharedRetreatScenes::noQuota));
        scenes.add(Checks.superflat("shared.windBell.playersFindOwnRetreats", ENTER_TICKS + 2 * SEARCH_TICKS + 800,
                SharedRetreatScenes::playersFindOwnRetreats));
        return scenes;
    }

    // ---- 场景 ----

    /** 首次进入共享归隐之地的"已分配专属隐世之境"动作栏提示要能被看到：之后一段时间内不被别的动作栏消息顶掉。 */
    private static void firstEntryNoticeVisible(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, RetreatScenes.bell(BELLS));
        resetFirstEntry(ctx, player);
        RetreatScenes.enterFromOverworld(ctx, player, mark -> ctx.await(() -> ChatTap.find(mark, FIRST_ENTRY, true).isPresent())
                .within(NOTICE_TICKS).then(() -> Checks.after(ctx, OVERLAY_VISIBLE_TICKS, () -> {
                    List<String> overlays = ChatTap.keys(mark, true);
                    ctx.record("overlays", overlays);
                    ctx.check(overlays.getLast()).as("首次进入后 " + OVERLAY_VISIBLE_TICKS
                            + " tick 内最后显示的动作栏提示（依次收到：" + overlays + "）").isEqualTo(FIRST_ENTRY);
                })));
    }

    /** 主世界右键进入共享归隐之地：首次进入提示、登记 1 个配额、不消耗铃。 */
    private static void firstEntry(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, RetreatScenes.bell(BELLS));
        resetFirstEntry(ctx, player);
        RetreatScenes.enterFromOverworld(ctx, player, mark -> ctx.await(() -> ChatTap.find(mark, ENTERED, true).isPresent())
                .within(NOTICE_TICKS).then(() -> {
                    ctx.check(player.level().dimension().location()).as("进入的维度").isEqualTo(SHARED);
                    ctx.check(ChatTap.find(mark, FIRST_ENTRY, true).isPresent())
                            .as("收到首次进入提示（动作栏：" + ChatTap.keys(mark, true) + "）").isTrue();
                    ctx.check(quota(player)).as("首次进入后的搜索配额").isEqualTo(1);
                    ctx.check(player.getMainHandItem().getCount()).as("进入后手上铃的数量").isEqualTo(BELLS);
                }));
    }

    /** 搜索期间把铃从手上换走：仍给出坐标，不吞铃、不飞铃，配额照常消耗并记住找到的位置。 */
    private static void switchItemDuringSearch(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, RetreatScenes.bell(BELLS));
        RetreatScenes.enterRetreat(ctx, player, () -> {
            RetreatManager.clearPlayerCache(player.getUUID());
            // 搜索分帧进行，结果最早下一 tick 才回来；右键后立刻把铃挪到背包别的格子
            RetreatScenes.search(ctx, player, () -> {
                ItemStack bells = player.getMainHandItem().copy();
                Players.hold(player, new ItemStack(Items.STICK));
                player.getInventory().setItem(player.getInventory().selected == 8 ? 7 : 8, bells);
            }, result -> {
                RetreatScenes.Found found = RetreatScenes.found(ctx, result.mark());
                ctx.check(player.getInventory().countItem(MaidSpellItems.WIND_SEEKING_BELL.get())).as("背包里铃的总数").isEqualTo(BELLS);
                ctx.check(result.flyingBells().size()).as("飞出的寻风之铃实体数").isEqualTo(0);
                ctx.check(quota(player)).as("找到后剩余配额").isEqualTo(0);
                RetreatDimensionData.DimensionInfo info = info(player);
                BlockPos saved = info != null ? info.foundStructurePos : null;
                if (found != null) {
                    ctx.check(saved == null ? "<未记录>" : saved.getX() + "," + saved.getZ()).as("记住的结构位置")
                            .isEqualTo(found.x() + "," + found.z());
                }
            });
        });
    }

    /** 已找到后再次右键：直接给出记住的坐标，不再进入等待搜索；checkConsumption 时还检查消耗一只铃。 */
    private static void reshow(SceneContext ctx, ServerPlayer player, boolean checkConsumption) {
        Players.hold(player, RetreatScenes.bell(BELLS));
        RetreatScenes.enterRetreat(ctx, player, () -> {
            BlockPos saved = RetreatDimensionData.get(ctx.server()).getFoundStructurePos(player.getUUID());
            ctx.check(saved != null).as("已记住找到的结构位置（前面的场景已找到）").isTrue();
            if (saved == null) {
                return;
            }
            RetreatScenes.search(ctx, player, result -> {
                RetreatScenes.Found found = RetreatScenes.found(ctx, result.mark());
                ctx.check(ChatTap.keys(result.mark(), true).contains(WAITING)).as("重复查看时进入等待搜索").isFalse();
                if (found != null) {
                    ctx.check(found.x() + "," + found.z()).as("重复查看给出的坐标").isEqualTo(saved.getX() + "," + saved.getZ());
                }
                if (checkConsumption) {
                    ctx.check(player.getMainHandItem().getCount()).as("重复查看后手上铃的数量").isEqualTo(BELLS - 1);
                }
            });
        });
    }

    /** 没有配额、也没有找到过的结构时右键：提示"你只能搜索一次！"，不搜索、不消耗铃。 */
    private static void noQuota(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, RetreatScenes.bell(BELLS));
        RetreatScenes.enterRetreat(ctx, player, () -> {
            RetreatDimensionData.DimensionInfo info = info(player);
            ctx.check(info != null).as("玩家已登记").isTrue();
            if (info == null) {
                return;
            }
            BlockPos saved = info.foundStructurePos;
            int quota = info.structureQuota;
            ctx.cleanup(() -> {
                info.foundStructurePos = saved;
                info.structureQuota = quota;
                RetreatDimensionData.get(ctx.server()).setDirty();
            });
            info.foundStructurePos = null;
            info.structureQuota = 0;
            RetreatManager.clearPlayerCache(player.getUUID());
            RetreatScenes.whenReady(ctx, player, () -> {
                long mark = ChatTap.mark();
                Players.use(player);
                ctx.await(() -> ChatTap.find(mark, NO_QUOTA, true).isPresent()).within(40).then(() -> Checks.after(ctx, 10, () -> {
                    ctx.check(ChatTap.keys(mark, true).contains(WAITING)).as("没有配额时进入等待搜索").isFalse();
                    ctx.check(player.getMainHandItem().getCount()).as("没有配额时手上铃的数量").isEqualTo(BELLS);
                }));
            });
        });
    }

    /**
     * 两个模拟玩家（NeoForge FakePlayer，直接放在共享归隐之地同一位置）先后用铃搜索：各自找到一个隐世之境，
     * 位置互不相同、也不同于宿主玩家的；再次搜索不会得到新的。模拟玩家收不到消息，从服务器记录读结果。
     */
    private static void playersFindOwnRetreats(SceneContext ctx) {
        BlockPos hostFound = RetreatDimensionData.get(ctx.server()).getFoundStructurePos(ctx.player().getUUID());
        CompletableFuture<ServerLevel> shared = PlayerRetreatManager.getOrCreateSharedRetreatAsync(ctx.server());
        ctx.await(shared::isDone).within(ENTER_TICKS).then(() -> {
            ServerLevel level = shared.join();
            ctx.cleanup(() -> RetreatScenes.cancelSearches(level));
            List<ServerPlayer> walkers = new ArrayList<>();
            for (String name : List.of("TlmsWalkerA", "TlmsWalkerB")) {
                walkers.add(RetreatScenes.walker(level, RetreatScenes.profile(name), new BlockPos(ctx.originX(), 100, ctx.originZ())));
            }
            Map<String, BlockPos> found = new LinkedHashMap<>();
            searchInTurn(ctx, walkers, 0, found, () -> {
                ctx.record("found", found.toString());
                ctx.record("hostFound", String.valueOf(hostFound));
                List<BlockPos> positions = new ArrayList<>(found.values());
                ctx.check(positions.size()).as("找到隐世之境的模拟玩家数").isEqualTo(walkers.size());
                if (positions.size() != walkers.size()) {
                    return;
                }
                ctx.check(new HashSet<>(positions).size()).as("模拟玩家找到的不同位置数").isEqualTo(walkers.size());
                if (hostFound != null) {
                    ctx.check(positions.contains(hostFound)).as("模拟玩家找到了宿主玩家的隐世之境").isFalse();
                }
                for (ServerPlayer walker : walkers) {
                    ctx.check(walker.getMainHandItem().getCount()).as(walker.getGameProfile().getName() + " 搜索后手上铃的数量")
                            .isEqualTo(BELLS - 1);
                }
                Structure structure = Worldgen.structure(level, HIDDEN_RETREAT).value();
                Map<String, Boolean> hasStart = new LinkedHashMap<>();
                Batch.runAsync(ctx, List.copyOf(found.entrySet()), entry -> Worldgen.startAt(level, structure, new ChunkPos(entry.getValue()))
                        .thenApply(start -> {
                            hasStart.put(entry.getKey(), start != null);
                            return false;
                        }), 600, () -> {
                    ctx.check(hasStart).as("模拟玩家找到的位置上有隐世之境起点")
                            .isEqualTo(Map.of("TlmsWalkerA", true, "TlmsWalkerB", true));
                    // 同一玩家再次搜索：不会得到新的隐世之境
                    ServerPlayer again = walkers.getFirst();
                    BlockPos first = found.get(again.getGameProfile().getName());
                    again.getCooldowns().removeCooldown(MaidSpellItems.WIND_SEEKING_BELL.get());
                    RetreatManager.clearPlayerCache(again.getUUID());
                    Players.use(again);
                    Checks.after(ctx, 40, () -> {
                        ctx.check(RetreatDimensionData.get(ctx.server()).getFoundStructurePos(again.getUUID())).as("再次搜索后记住的位置")
                                .isEqualTo(first);
                        ctx.check(quota(again)).as("再次搜索后的配额").isEqualTo(0);
                    });
                });
            });
        });
    }

    /** 依次让模拟玩家右键搜索，把找到的位置（服务器记录）放进 found。 */
    private static void searchInTurn(SceneContext ctx, List<ServerPlayer> walkers, int index, Map<String, BlockPos> found, Runnable done) {
        if (index >= walkers.size()) {
            done.run();
            return;
        }
        ServerPlayer walker = walkers.get(index);
        RetreatDimensionData data = RetreatDimensionData.get(ctx.server());
        Players.use(walker);
        ctx.record(walker.getGameProfile().getName() + ".quotaAfterFirstUse", quota(walker));
        ctx.await(() -> data.getFoundStructurePos(walker.getUUID()) != null).within(SEARCH_TICKS).then(() -> {
            found.put(walker.getGameProfile().getName(), data.getFoundStructurePos(walker.getUUID()));
            // 扣铃和发铃在找到后的下一 tick，等它完成再比较铃的数量
            Checks.after(ctx, 2, () -> searchInTurn(ctx, walkers, index + 1, found, done));
        });
    }

    // ---- 工具 ----

    private static RetreatDimensionData.DimensionInfo info(ServerPlayer player) {
        return RetreatDimensionData.get(player.server).getDimensionInfo(player.getUUID());
    }

    /** 剩余搜索配额；没有登记时为 -1。 */
    private static int quota(ServerPlayer player) {
        RetreatDimensionData.DimensionInfo info = info(player);
        return info != null ? info.structureQuota : -1;
    }

    /** 回到主世界并删掉玩家在共享归隐之地的登记，让下一次进入重新算作首次进入。 */
    private static void resetFirstEntry(SceneContext ctx, ServerPlayer player) {
        RetreatScenes.toOverworldNearOrigin(ctx, player);
        RetreatDimensionData.get(ctx.server()).removeDimension(player.getUUID());
        RetreatManager.clearPlayerCache(player.getUUID());
    }
}
