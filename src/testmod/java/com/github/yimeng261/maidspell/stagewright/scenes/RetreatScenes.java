package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.dimension.PlayerRetreatManager;
import com.github.yimeng261.maidspell.dimension.RetreatManager;
import com.github.yimeng261.maidspell.dimension.StructureSearchWorker;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.entity.WindSeekingBellEntity;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Batch;
import com.github.yimeng261.maidspell.stagewright.support.ChatTap;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Worldgen;
import com.mojang.authlib.GameProfile;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 寻风之铃与归隐之地（集成服，私人维度模式，宿主玩家实际收到的提示经 {@link ChatTap} 核对）：
 * 进入/返回、搜索隐世之境（等待提示、坐标消息与点击填入的传送命令、飞铃、只消耗一个）、
 * 每个私人维度最多一个隐世之境、重复查看、缓存丢失后重新搜索、驯服结构女仆的提示。
 * <p>场景按注册顺序运行并共享宿主玩家的私人维度：第一次进入时生成的隐世之境会被后续场景复用。
 */
public final class RetreatScenes {
    static final String BELL_KEY = "item.touhou_little_maid_spell.wind_seeking_bell.";
    static final String ENTERED = BELL_KEY + "entered_retreat";
    private static final String RETURNED = BELL_KEY + "return_to_overworld";
    static final String WAITING = BELL_KEY + "waiting_for_structure";
    private static final String FOUND = BELL_KEY + "found_structure";
    static final String SEARCH_TIME = BELL_KEY + "search_time";
    private static final String FIRST_USE = BELL_KEY + "first_use";
    private static final String NO_STRUCTURE = BELL_KEY + "no_structure";
    private static final String TAMED_IN_RETREAT = "item.touhou_little_maid_spell.maid_tamed_event.maid_in_hidden_retreat";
    static final String HIDDEN_RETREAT = NS + "hidden_retreat";
    private static final String HIDDEN_RETREAT_SET = NS + "hidden_retreat_set";
    /** 首次进入要创建维度并生成落点附近的区块。 */
    static final int ENTER_TICKS = 600;
    /** 分帧搜索的等待上限。 */
    static final int SEARCH_TICKS = 2400;
    /** 进入后等提示送达客户端的上限。 */
    static final int NOTICE_TICKS = 100;
    static final int BELLS = 3;
    /** 铃每次使用后有 20 tick 冷却（见 WindSeekingBell），等它结束的上限留一倍余量。 */
    private static final int BELL_COOLDOWN_WAIT = 40;
    /** 检查"每个维度最多一个"时，找到的结构周围取几圈放置网格。 */
    private static final int OTHER_CANDIDATE_RINGS = 2;
    /** 宿主玩家用铃前站的主世界坐标（x = z）。 */
    private static final int NEAR_ORIGIN = 8;
    /** 远距离进入的缺陷场景里，等搜索出结果的上限。 */
    private static final int FAR_SEARCH_TICKS = 1500;

    private RetreatScenes() {
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("retreat.windBell.enterAndReturn", ENTER_TICKS + 300, RetreatScenes::enterAndReturn));
        scenes.add(Players.hostScene("retreat.windBell.searchFindsHiddenRetreat", ENTER_TICKS + SEARCH_TICKS + 1300,
                RetreatScenes::searchFindsHiddenRetreat));
        scenes.add(Players.hostScene("retreat.windBell.reshowSameCoordinates", ENTER_TICKS + 2 * SEARCH_TICKS + 100,
                RetreatScenes::reshowSameCoordinates));
        scenes.add(Players.hostScene("retreat.windBell.researchAfterCacheLoss", ENTER_TICKS + 2 * SEARCH_TICKS + 100,
                RetreatScenes::researchAfterCacheLoss));
        scenes.add(Players.hostScene("retreat.hiddenRetreat.tameMessage", ENTER_TICKS + SEARCH_TICKS + 1400,
                RetreatScenes::tameMessage));
        scenes.add(Checks.superflat("knownDefect.privateSearchFindsStructureAtFarEntry", ENTER_TICKS + FAR_SEARCH_TICKS + 1400,
                RetreatScenes::farEntrySearch).withRequired(false));
        return scenes;
    }

    // ---- 场景 ----

    /** 主世界右键进入自己的私人归隐之地并提示；Shift+右键返回主世界并提示；两次都不消耗铃。 */
    private static void enterAndReturn(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, bell(BELLS));
        enterFromOverworld(ctx, player, mark -> ctx.await(() -> ChatTap.find(mark, ENTERED, true).isPresent())
                .within(NOTICE_TICKS).then(() -> {
                    ctx.check(player.level().dimension()).as("进入的维度")
                            .isEqualTo(TheRetreatDimension.getPlayerRetreatDimension(player.getUUID()));
                    ctx.check(player.getMainHandItem().getCount()).as("进入后手上铃的数量").isEqualTo(BELLS);
                    BlockPos inRetreat = player.blockPosition();
                    long back = ChatTap.mark();
                    player.setShiftKeyDown(true);
                    Players.use(player);
                    player.setShiftKeyDown(false);
                    ctx.await(() -> player.level().dimension() == Level.OVERWORLD && ChatTap.find(back, RETURNED, true).isPresent())
                            .within(200).then(() -> {
                                ctx.check(player.getMainHandItem().getCount()).as("返回后手上铃的数量").isEqualTo(BELLS);
                                double horizontal = Math.hypot(player.getX() - inRetreat.getX() - 0.5, player.getZ() - inRetreat.getZ() - 0.5);
                                ctx.record("returnHorizontalOffset", horizontal);
                                ctx.check(horizontal).as("返回主世界的落点与归隐之地里所站位置的水平距离").isAtMost(2.0);
                            });
                }));
    }

    /**
     * 归隐之地右键：先在动作栏提示等待，找到后聊天栏给出坐标、距离（点击填入 /tp 命令）、搜索耗时与首次使用说明；
     * 铃飞出、只消耗一个；坐标所在区块确有隐世之境起点，周围其它候选区块没有第二个。
     */
    private static void searchFindsHiddenRetreat(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, bell(BELLS));
        enterRetreat(ctx, player, () -> {
            structureCache().remove(player.getUUID());
            BlockPos from = player.blockPosition();
            search(ctx, player, result -> {
                long mark = result.mark();
                Found found = found(ctx, mark);
                ctx.check(ChatTap.keys(mark, true).contains(WAITING))
                        .as("动作栏提示过等待搜索（实际：" + ChatTap.keys(mark, true) + "）").isTrue();
                ctx.check(ChatTap.find(mark, FIRST_USE, false).isPresent()).as("收到首次使用说明").isTrue();
                ctx.check(result.flyingBells().size()).as("飞出的寻风之铃实体数").isEqualTo(1);
                ctx.check(player.getMainHandItem().getCount()).as("搜索后手上铃的数量").isEqualTo(BELLS - 1);
                if (found == null) {
                    return;
                }
                int distance = (int) Math.sqrt(Math.pow(found.x - from.getX(), 2) + Math.pow(found.z - from.getZ(), 2));
                ctx.check(found.distance).as("消息里的距离").isEqualTo(distance);
                checkTpCommand(ctx, found);
                checkOnlyOne(ctx, player.serverLevel(), found);
            });
        });
    }

    /** 已找到后再次右键：直接给出同一坐标。 */
    private static void reshowSameCoordinates(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, bell(BELLS));
        enterRetreat(ctx, player, () -> locate(ctx, player, first -> {
            int afterFirst = player.getMainHandItem().getCount();
            int started = ctx.ticks();
            search(ctx, player, result -> {
                Found again = found(ctx, result.mark());
                ctx.record("reshowTicks", ctx.ticks() - started);
                ctx.record("bellsConsumedByReshow", afterFirst - player.getMainHandItem().getCount());
                ctx.check(ChatTap.keys(result.mark(), true).contains(WAITING)).as("重复查看时又进入等待搜索").isFalse();
                if (again != null) {
                    ctx.check(again.x + "," + again.z).as("重复查看给出的坐标").isEqualTo(first.x + "," + first.z);
                }
            });
        }));
    }

    /**
     * 内存里的查找缓存丢失（服务器重启即如此）后重新搜索，仍应找到本维度里已生成的那一个隐世之境。
     * 宿主玩家从主世界原点附近进入，结构生成在进入点附近。
     */
    private static void researchAfterCacheLoss(SceneContext ctx, ServerPlayer player) {
        Players.hold(player, bell(BELLS));
        enterRetreat(ctx, player, () -> locate(ctx, player, first -> {
            structureCache().remove(player.getUUID());
            search(ctx, player, result -> {
                ctx.check(ChatTap.find(result.mark(), NO_STRUCTURE, true).isPresent()).as("提示附近没有隐世之境").isFalse();
                Found again = found(ctx, result.mark());
                if (again != null) {
                    ctx.check(again.x + "," + again.z).as("缓存丢失后重新搜索给出的坐标").isEqualTo(first.x + "," + first.z);
                }
            });
        }));
    }

    /**
     * 驯服隐世之境里坐着的结构女仆时收到"您的手艺，一如既往呢"；在结构外驯服坐着的女仆不会收到。
     */
    private static void tameMessage(SceneContext ctx, ServerPlayer player) {
        EntityMaid outside = Actors.sittingMaid(ctx, 2, 0, 0);
        long controlMark = ChatTap.mark();
        ctx.check(Players.tame(player, outside)).as("驯服结构外的女仆").isTrue();
        Checks.after(ctx, 10, () -> {
            ctx.check(ChatTap.find(controlMark, TAMED_IN_RETREAT, false).isPresent()).as("在结构外驯服坐着的女仆收到隐世之境提示").isFalse();
            Players.hold(player, bell(BELLS));
            enterRetreat(ctx, player, () -> locate(ctx, player, found -> {
                ServerLevel level = player.serverLevel();
                Structure structure = Worldgen.structure(level, HIDDEN_RETREAT).value();
                ChunkPos chunk = new ChunkPos(new BlockPos(found.x, 0, found.z));
                CompletableFuture<StructureStart> pending = Worldgen.startAt(level, structure, chunk);
                ctx.await(pending::isDone).within(600).then(() -> {
                    StructureStart start = pending.exceptionally(error -> null).join();
                    ctx.check(start != null).as("找到的坐标所在区块 " + chunk + " 有隐世之境起点").isTrue();
                    if (start == null) {
                        return;
                    }
                    BoundingBox box = start.getBoundingBox();
                    AABB area = AABB.of(box);
                    // 飞到结构上空等区块生成出结构女仆，避免落到屋顶上摔伤
                    boolean flying = player.getAbilities().flying;
                    boolean mayfly = player.getAbilities().mayfly;
                    ctx.cleanup(() -> {
                        player.getAbilities().flying = flying;
                        player.getAbilities().mayfly = mayfly;
                        player.onUpdateAbilities();
                    });
                    player.getAbilities().mayfly = true;
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, box.getCenter().getX() + 0.5, box.maxY() + 2, box.getCenter().getZ() + 0.5, Set.of(), 0, 0);
                    ctx.await(() -> !structureMaids(level, area).isEmpty()).within(1200).then(() -> {
                        EntityMaid maid = structureMaids(level, area).getFirst();
                        ctx.record("structureMaid", maid.blockPosition().toShortString());
                        ctx.check(maid.isOrderedToSit()).as("结构女仆坐着").isTrue();
                        long mark = ChatTap.mark();
                        ctx.check(Players.tame(player, maid)).as("驯服结构女仆").isTrue();
                        int at = ctx.ticks();
                        ctx.await(() -> ChatTap.find(mark, TAMED_IN_RETREAT, false).isPresent() || ctx.ticks() >= at + 40)
                                .within(60).then(() -> ctx.check(ChatTap.find(mark, TAMED_IN_RETREAT, false).isPresent())
                                        .as("驯服结构女仆后收到\"您的手艺，一如既往呢\"（实际聊天：" + ChatTap.keys(mark, false) + "）")
                                        .isTrue());
                    });
                });
            }));
        });
    }

    /**
     * 远离原点进入私人归隐之地：隐世之境随落点附近的区块生成而生成，之后第一次用铃搜索应能找到它。
     * 用一个全新 UUID 的私人维度和 NeoForge 模拟玩家（直接放在该维度的远处落点）走真实的右键流程；
     * 模拟玩家收不到消息，结果从服务器的查找缓存读取。
     */
    private static void farEntrySearch(SceneContext ctx) {
        GameProfile profile = profile("TlmsFarEntry");
        CompletableFuture<ServerLevel> created = PlayerRetreatManager.getOrCreatePlayerRetreatAsync(ctx.server(), profile.getId());
        ctx.await(created::isDone).within(ENTER_TICKS).then(() -> {
            ServerLevel level = created.join();
            ctx.cleanup(() -> cancelSearches(level));
            // 落点与主世界场地同坐标（x≈100000）
            BlockPos entryPos = new BlockPos(ctx.originX(), 100, ctx.originZ());
            ChunkPos entry = new ChunkPos(entryPos);
            ctx.record("entry", entryPos.toShortString());
            Structure structure = Worldgen.structure(level, HIDDEN_RETREAT).value();
            // 与真实玩家到达时一样：落点周围的候选区块推进到 STRUCTURE_STARTS，第一个成功的就是本维度唯一的隐世之境
            List<ChunkPos> around = Worldgen.candidates(level, Worldgen.structureSet(level, HIDDEN_RETREAT_SET).value(), entry, 1);
            List<ChunkPos> generated = new ArrayList<>();
            Batch.runAsync(ctx, around, chunk -> Worldgen.startAt(level, structure, chunk).thenApply(start -> {
                if (start != null) {
                    generated.add(start.getChunkPos());
                }
                return false;
            }), 600, () -> {
                ctx.record("generatedNearEntry", generated.toString());
                ctx.check(generated.size()).as("落点附近生成的隐世之境数").isEqualTo(1);
                if (generated.size() != 1) {
                    return;
                }
                FakePlayer walker = walker(level, profile, entryPos);
                structureCache().remove(profile.getId());
                Players.use(walker);
                int started = ctx.ticks();
                ctx.await(() -> RetreatManager.checkCache(profile.getId()).hasCache || ctx.ticks() >= started + FAR_SEARCH_TICKS)
                        .within(FAR_SEARCH_TICKS + 10).then(() -> {
                            RetreatManager.CacheResult result = RetreatManager.checkCache(profile.getId());
                            ctx.record("searchTicks", ctx.ticks() - started);
                            String got = !result.hasCache ? "<" + FAR_SEARCH_TICKS + " tick 内没有结果>"
                                    : result.position == null ? "<没有找到>" : new ChunkPos(result.position).toString();
                            ctx.check(got).as("搜索结果所在区块").isEqualTo(generated.getFirst().toString());
                        });
            });
        });
    }

    // ---- 工具 ----

    static ItemStack bell(int count) {
        return new ItemStack(MaidSpellItems.WIND_SEEKING_BELL.get(), count);
    }

    /** 不在归隐之地时用手上的铃进入（不消耗），然后运行 next。 */
    static void enterRetreat(SceneContext ctx, ServerPlayer player, Runnable next) {
        if (TheRetreatDimension.isInRetreat(player)) {
            next.run();
            return;
        }
        enterFromOverworld(ctx, player, mark -> next.run());
    }

    /**
     * 移到主世界原点附近，用手上的铃进入归隐之地（不消耗），进入后把右键前取的消息标记交给 next。
     * 归隐之地的落点与主世界坐标相同，隐世之境会生成在落点附近；而私人维度一旦生成过结构，
     * 之后的搜索都以维度出生点为中心（见 knownDefect.privateSearchFindsStructureAtFarEntry），所以要在原点附近进入。
     */
    static void enterFromOverworld(SceneContext ctx, ServerPlayer player, LongConsumer next) {
        player.setShiftKeyDown(false);
        toOverworldNearOrigin(ctx, player);
        whenReady(ctx, player, () -> {
            long mark = ChatTap.mark();
            Players.use(player);
            ctx.await(() -> TheRetreatDimension.isInRetreat(player)).within(ENTER_TICKS).then(() -> next.accept(mark));
        });
    }

    /** 传送到主世界原点附近的地面上（场景场地在 StageWright 自己的维度里）。 */
    static void toOverworldNearOrigin(SceneContext ctx, ServerPlayer player) {
        ServerLevel overworld = ctx.server().overworld();
        overworld.getChunk(NEAR_ORIGIN >> 4, NEAR_ORIGIN >> 4);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, NEAR_ORIGIN, NEAR_ORIGIN);
        player.teleportTo(overworld, NEAR_ORIGIN + 0.5, y, NEAR_ORIGIN + 0.5, Set.of(), 0, 0);
    }

    /** 原版在物品冷却中直接忽略右键：等铃的冷却结束再运行 next。 */
    static void whenReady(SceneContext ctx, ServerPlayer player, Runnable next) {
        ctx.await(() -> !player.getCooldowns().isOnCooldown(MaidSpellItems.WIND_SEEKING_BELL.get())).within(BELL_COOLDOWN_WAIT).then(next);
    }

    static void search(SceneContext ctx, ServerPlayer player, Consumer<Search> next) {
        search(ctx, player, () -> {
        }, next);
    }

    /**
     * 在归隐之地右键一次铃（先等过冷却），afterUse 在右键后立即运行；等到搜索结束（收到搜索耗时消息），
     * 期间记下玩家附近飞出的铃。场景结束时取消本维度仍在进行的搜索。
     */
    static void search(SceneContext ctx, ServerPlayer player, Runnable afterUse, Consumer<Search> next) {
        ServerLevel level = player.serverLevel();
        ctx.cleanup(() -> cancelSearches(level));
        Set<Integer> flyingBells = new HashSet<>();
        whenReady(ctx, player, () -> {
            long mark = ChatTap.mark();
            Players.use(player);
            afterUse.run();
            ctx.await(() -> {
                level.getEntitiesOfClass(WindSeekingBellEntity.class, player.getBoundingBox().inflate(32))
                        .forEach(bell -> flyingBells.add(bell.getId()));
                return ChatTap.find(mark, SEARCH_TIME, false).isPresent();
            }).within(SEARCH_TICKS).then(() -> next.accept(new Search(mark, flyingBells)));
        });
    }

    /** 右键一次铃，等到坐标消息后把坐标交给 next（找不到时记失败，不运行 next）。 */
    static void locate(SceneContext ctx, ServerPlayer player, Consumer<Found> next) {
        search(ctx, player, result -> {
            Found found = found(ctx, result.mark());
            if (found != null) {
                next.accept(found);
            }
        });
    }

    /** mark 之后收到的坐标消息；没有时记一条失败并返回 null。 */
    static Found found(SceneContext ctx, long mark) {
        ChatTap.Received received = ChatTap.find(mark, FOUND, false).orElse(null);
        ctx.check(received != null).as("收到隐世之境坐标消息（实际：" + ChatTap.since(mark) + "）").isTrue();
        if (received == null) {
            return null;
        }
        return new Found(received.intArg(0), received.intArg(1), received.intArg(2), received.intArg(3), received);
    }

    /** 按名字固定 UUID 的玩家档案。 */
    static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    /** 放在 level 的 pos 处、手上拿着铃的 NeoForge 模拟玩家。 */
    static FakePlayer walker(ServerLevel level, GameProfile profile, BlockPos pos) {
        FakePlayer walker = FakePlayerFactory.get(level, profile);
        walker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        Players.hold(walker, bell(BELLS));
        return walker;
    }

    /** 坐标消息可点击，填入的 /tp 命令指向结构坐标上方 3 格，并有悬停提示。 */
    private static void checkTpCommand(SceneContext ctx, Found found) {
        ClickEvent click = found.message.message().getStyle().getClickEvent();
        HoverEvent hover = found.message.message().getStyle().getHoverEvent();
        ctx.check(hover != null).as("坐标消息有悬停提示").isTrue();
        ctx.check(click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND).as("坐标消息点击后填入命令").isTrue();
        if (click == null) {
            return;
        }
        ctx.record("tpCommand", click.getValue());
        String[] parts = click.getValue().split(" ");
        ctx.check(parts.length).as("填入命令的段数（" + click.getValue() + "）").isEqualTo(4);
        if (parts.length != 4) {
            return;
        }
        ctx.check(parts[0]).as("填入的命令").isEqualTo("/tp");
        try {
            ctx.check(Double.parseDouble(parts[1])).as("/tp 的 x").isCloseTo(found.x + 0.5, 1e-6);
            ctx.check(Double.parseDouble(parts[2])).as("/tp 的 y").isCloseTo(found.height + 3, 1e-6);
            ctx.check(Double.parseDouble(parts[3])).as("/tp 的 z").isCloseTo(found.z + 0.5, 1e-6);
        } catch (NumberFormatException e) {
            ctx.check(click.getValue()).as("/tp 命令里的坐标不是合法数字").isEqualTo("<合法坐标>");
        }
    }

    /** 坐标所在区块有隐世之境起点；周围几圈放置网格的其它候选区块没有第二个。 */
    private static void checkOnlyOne(SceneContext ctx, ServerLevel level, Found found) {
        Structure structure = Worldgen.structure(level, HIDDEN_RETREAT).value();
        ChunkPos foundChunk = new ChunkPos(new BlockPos(found.x, 0, found.z));
        List<ChunkPos> chunks = new ArrayList<>();
        chunks.add(foundChunk);
        Worldgen.candidates(level, Worldgen.structureSet(level, HIDDEN_RETREAT_SET).value(), foundChunk, OTHER_CANDIDATE_RINGS)
                .stream().filter(chunk -> !chunk.equals(foundChunk)).forEach(chunks::add);
        Map<ChunkPos, Boolean> hasStart = new LinkedHashMap<>();
        Batch.runAsync(ctx, chunks, chunk -> Worldgen.startAt(level, structure, chunk).thenApply(start -> {
            hasStart.put(chunk, start != null);
            return false;
        }), 1200, () -> {
            ctx.record("otherCandidates", chunks.size() - 1);
            ctx.check(hasStart.get(foundChunk)).as("坐标所在区块 " + foundChunk + " 有隐世之境起点").isEqualTo(Boolean.TRUE);
            List<String> others = hasStart.entrySet().stream()
                    .filter(e -> !e.getKey().equals(foundChunk) && e.getValue()).map(e -> e.getKey().toString()).toList();
            ctx.check(others).as("同一私人维度里的其它隐世之境").isEmpty();
        });
    }

    private static List<EntityMaid> structureMaids(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(EntityMaid.class, area, maid -> maid.getOwnerUUID() == null);
    }

    static Map<UUID, ?> structureCache() {
        return Reflect.field(null, RetreatManager.class, "structureCache");
    }

    /** 取消本维度仍在进行的分帧搜索，免得超时的场景拖慢后面的场景。 */
    static void cancelSearches(ServerLevel level) {
        Map<String, StructureSearchWorker> searches = Reflect.field(null, RetreatManager.class, "ongoingSearches");
        String dimension = level.dimension().location().toString();
        searches.forEach((key, search) -> {
            if (key.startsWith(dimension)) {
                search.cancel();
            }
        });
    }

    /** 一次搜索：右键前取的消息标记、等待期间飞出的铃。 */
    record Search(long mark, Set<Integer> flyingBells) {
    }

    /** 坐标消息的参数：结构 x、地表高度、结构 z、距离。 */
    record Found(int x, int height, int z, int distance, ChatTap.Received message) {
    }
}
