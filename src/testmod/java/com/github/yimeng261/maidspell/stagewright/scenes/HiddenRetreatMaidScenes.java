package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginData;
import com.github.yimeng261.maidspell.dimension.HiddenRetreatMaidData;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 隐世女仆的归属记录与共享维度兼容：来源标记在转运中的保留与永久效果协议、认领与结构归属的落盘读回、
 * 独立与共享归隐之地的维度判定和任务匹配、共享模式下结构搜索的串行排队。
 */
public final class HiddenRetreatMaidScenes {
    private static final UUID FIRST = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final UUID SECOND = UUID.fromString("87654321-4321-8765-cba9-cba987654321");
    private static final ResourceLocation DIMENSION = id("the_retreat");
    private static final ResourceLocation SHARED = id("the_retreat");
    private static final ResourceLocation FIRST_PRIVATE = id("the_retreat_12345678_1234_5678_9abc_123456789abc");
    private static final ResourceLocation SECOND_PRIVATE = id("the_retreat_87654321_4321_8765_cba9_cba987654321");
    private static final String QUEUE = TheRetreatDimension.class.getPackageName() + ".SharedRetreatSearchQueue";

    private HiddenRetreatMaidScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.scene("retreatMaid.origin.transportKeepsOtherDataAndFutureFields", 5,
                HiddenRetreatMaidScenes::transportKeepsOtherData));
        scenes.add(Checks.scene("retreatMaid.origin.rootOnlyMarkersSurviveEntityLoad", 5,
                HiddenRetreatMaidScenes::rootOnlyMarkers));
        scenes.add(Checks.scene("retreatMaid.origin.filmRestoresOnlyInfiniteEffects", 5,
                HiddenRetreatMaidScenes::filmRestoresInfiniteEffects));
        scenes.add(Checks.scene("retreatMaid.origin.originAloneGrantsNoEffectPermission", 5,
                HiddenRetreatMaidScenes::originAloneGrantsNothing));
        scenes.add(Checks.scene("retreatMaid.claims.surviveRestartAndStayPerPlayer", 5,
                HiddenRetreatMaidScenes::claimsSurviveRestart));
        scenes.add(Checks.scene("retreatMaid.claims.constructionFailureReleasesPermit", 5,
                HiddenRetreatMaidScenes::constructionFailureReleasesPermit));
        scenes.add(Checks.scene("retreatMaid.claims.sharedStructureOwnerCannotBeReassigned", 5,
                HiddenRetreatMaidScenes::structureOwnership));
        scenes.add(Checks.scene("retreatMaid.dimension.sharedConditionAcceptsAnyPrivateRetreat", 5,
                HiddenRetreatMaidScenes::sharedConditionAcceptsPrivate));
        scenes.add(Checks.scene("retreatMaid.dimension.explicitConditionStaysExact", 5,
                HiddenRetreatMaidScenes::explicitConditionExact));
        scenes.add(Checks.scene("retreatMaid.dimension.similarNamesAreNotRetreats", 5,
                HiddenRetreatMaidScenes::similarNames));
        scenes.add(Checks.scene("retreatMaid.dimension.ordinaryConditionsStayExact", 5,
                HiddenRetreatMaidScenes::ordinaryConditions));
        scenes.add(Checks.scene("retreatMaid.searchQueue.cancelledSearchDoesNotReleaseSuccessor", 5,
                HiddenRetreatMaidScenes::cancelledSearchHoldsQueue));
        scenes.add(Checks.scene("retreatMaid.searchQueue.failureDoesNotBlockAndDimensionsIndependent", 5,
                HiddenRetreatMaidScenes::failureDoesNotBlock));
        return scenes;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, path);
    }

    // ---- 来源标记 ----

    private static CompoundTag persistent(boolean preserve) {
        CompoundTag tag = new CompoundTag();
        tag.put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin("touhou_little_maid_spell:fallen_sanctum", preserve));
        return tag;
    }

    private static void copyToTransport(CompoundTag persistent, CompoundTag data) {
        Reflect.call(null, MaidOriginData.class, "copyToTransport",
                new Class<?>[]{CompoundTag.class, CompoundTag.class}, persistent, data);
    }

    private static void restoreTransport(CompoundTag data, CompoundTag persistent) {
        Reflect.call(null, MaidOriginData.class, "restoreTransportData",
                new Class<?>[]{CompoundTag.class, CompoundTag.class}, data, persistent);
    }

    private static void restorePermanentEffects(CompoundTag persistent, ListTag effects, CompoundTag data) {
        Reflect.call(null, MaidOriginData.class, "restorePermanentEffects",
                new Class<?>[]{CompoundTag.class, ListTag.class, CompoundTag.class}, persistent, effects, data);
    }

    /** 转运只覆盖自己的字段，不动持久数据里其他模组的内容，来源标记里将来新增的字段也原样带过去。 */
    private static void transportKeepsOtherData(SceneContext ctx) {
        CompoundTag persistent = persistent(true);
        persistent.getCompound(MaidOriginData.ORIGIN).putString("FutureField", "retained");
        CompoundTag data = new CompoundTag();
        CompoundTag persistentData = new CompoundTag();
        persistentData.putString("OtherMod", "keep");
        data.put("NeoForgeData", persistentData);
        copyToTransport(persistent, data);
        ctx.check(data.getCompound("NeoForgeData").getString("OtherMod")).as("其他模组的数据").isEqualTo("keep");
        ctx.check(MaidOriginData.getOrigin(data).getString("FutureField")).as("新增字段随转运带过去").isEqualTo("retained");
        persistent.getCompound(MaidOriginData.ORIGIN).putString("FutureField", "changed");
        ctx.check(MaidOriginData.getOrigin(data).getString("FutureField")).as("转运数据是副本").isEqualTo("retained");
        CompoundTag restored = new CompoundTag();
        restoreTransport(data, restored);
        ctx.check(MaidOriginData.preservesPermanentEffects(restored)).as("恢复后仍声明保留永久效果").isTrue();
        ctx.check(restored.getCompound(MaidOriginData.ORIGIN).equals(data.getCompound(MaidOriginData.ORIGIN)))
                .as("恢复出的来源标记与转运数据一致").isTrue();
    }

    /** 只有根节点上标记的旧数据，经恢复后同样出现在实体加载会读取的持久数据里，并补上来源标记。 */
    private static void rootOnlyMarkers(SceneContext ctx) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(MaidOriginData.HIDDEN_RETREAT_MAID, true);
        data.putBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED, true);
        restoreTransport(data, new CompoundTag());
        CompoundTag loadedPersistent = data.getCompound("NeoForgeData");
        ctx.check(loadedPersistent.getBoolean(MaidOriginData.HIDDEN_RETREAT_MAID)).as("隐世女仆标记").isTrue();
        ctx.check(loadedPersistent.getBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED)).as("星之魔女标记").isTrue();
        ctx.check(MaidOriginData.isHiddenRetreatMaid(loadedPersistent)).as("按持久数据判定为隐世女仆").isTrue();
        ctx.check(MaidOriginData.getOrigin(data).getString("Source")).as("补上的来源")
                .isEqualTo(MaidOriginData.HIDDEN_RETREAT_SOURCE);
        ctx.check(MaidOriginData.preservesPermanentEffects(data)).as("旧数据不声明保留永久效果").isFalse();
    }

    /** 声明保留永久效果的女仆，胶片里只补回无限时长的效果；没声明的、魂符里已有效果的都不动。 */
    private static void filmRestoresInfiniteEffects(SceneContext ctx) {
        CompoundTag permanent = new CompoundTag();
        permanent.putInt("duration", -1);
        permanent.putString("id", "future_mod:effect");
        permanent.putByte("amplifier", (byte) 4);
        CompoundTag finite = new CompoundTag();
        finite.putInt("duration", 1200);
        ListTag effects = new ListTag();
        effects.add(permanent);
        effects.add(finite);

        CompoundTag film = new CompoundTag();
        restorePermanentEffects(persistent(true), effects, film);
        ListTag restored = film.getList("active_effects", Tag.TAG_COMPOUND);
        ctx.check(restored.size()).as("胶片补回的效果数").isEqualTo(1);
        ctx.check(restored.getCompound(0).equals(permanent)).as("补回的是无限时长那条").isTrue();

        CompoundTag ordinaryFilm = new CompoundTag();
        restorePermanentEffects(new CompoundTag(), effects, ordinaryFilm);
        ctx.check(ordinaryFilm.contains("active_effects")).as("未声明的女仆不补效果").isFalse();

        CompoundTag charm = new CompoundTag();
        charm.put("active_effects", effects.copy());
        restorePermanentEffects(persistent(true), effects, charm);
        ctx.check(effects.equals(charm.get("active_effects"))).as("魂符里已有的效果原样保留").isTrue();
    }

    /** 光有来源标记不构成保留永久效果的许可，来源不是合法注册名也不行。 */
    private static void originAloneGrantsNothing(SceneContext ctx) {
        ctx.check(MaidOriginData.preservesPermanentEffects(persistent(false))).as("未声明保留").isFalse();
        ctx.check(MaidOriginData.isHiddenRetreatMaid(persistent(true))).as("别的来源不是隐世女仆").isFalse();
        CompoundTag invalid = persistent(true);
        invalid.getCompound(MaidOriginData.ORIGIN).putString("Source", "invalid source");
        ctx.check(MaidOriginData.preservesPermanentEffects(invalid)).as("来源不是合法注册名").isFalse();
    }

    // ---- 认领与结构归属 ----

    private static HiddenRetreatMaidData reload(SceneContext ctx, HiddenRetreatMaidData data) {
        HolderLookup.Provider registries = ctx.server().registryAccess();
        return HiddenRetreatMaidData.load(data.save(new CompoundTag(), registries), registries);
    }

    /** 认领落盘后重启仍在、互不影响别的玩家，管理员重置后又能重新领取。 */
    private static void claimsSurviveRestart(SceneContext ctx) {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        ctx.check(data.claim(FIRST)).as("第一次认领").isTrue();
        ctx.check(data.claim(FIRST)).as("重复认领").isFalse();
        HiddenRetreatMaidData loaded = reload(ctx, data);
        ctx.check(loaded.hasClaim(FIRST)).as("重启后保留认领").isTrue();
        ctx.check(loaded.hasClaim(SECOND)).as("别的玩家没有认领").isFalse();
        ctx.check(loaded.tryBeginSpawn(FIRST)).as("已认领的玩家不再发许可").isFalse();
        ctx.check(loaded.tryBeginSpawn(SECOND)).as("未认领的玩家可以发许可").isTrue();
        loaded.finishSpawn(SECOND);
        ctx.check(loaded.reset(FIRST)).as("重置认领").isTrue();
        ctx.check(loaded.tryBeginSpawn(FIRST)).as("重置后可以再发许可").isTrue();
    }

    /** 创建许可是短时的：不会存成认领，构造失败释放后可重试，认领之后不再发许可。 */
    private static void constructionFailureReleasesPermit(SceneContext ctx) {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        ctx.check(data.tryBeginSpawn(FIRST)).as("第一次发许可").isTrue();
        ctx.check(data.tryBeginSpawn(FIRST)).as("许可未释放时不重复发").isFalse();
        HiddenRetreatMaidData restarted = reload(ctx, data);
        ctx.check(restarted.tryBeginSpawn(FIRST)).as("未释放的许可不会落盘").isTrue();
        data.finishSpawn(FIRST);
        ctx.check(data.tryBeginSpawn(FIRST)).as("释放后可以重试").isTrue();
        data.claim(FIRST);
        data.finishSpawn(FIRST);
        ctx.check(data.tryBeginSpawn(FIRST)).as("认领之后不再发许可").isFalse();
    }

    private static HiddenRetreatMaidData.StructureOwner owner(UUID player) {
        return new HiddenRetreatMaidData.StructureOwner(DIMENSION, 0, 60, 0, 10, 80, 10, player);
    }

    /** 共享维度里结构的归属落盘后不变，后来的搜索改派不了，范围之外和别的维度都没有归属。 */
    private static void structureOwnership(SceneContext ctx) {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        data.bindStructure(owner(FIRST));
        data.bindStructure(owner(SECOND));
        HiddenRetreatMaidData loaded = reload(ctx, data);
        ctx.check(FIRST.equals(loaded.ownerAt(DIMENSION, new BlockPos(5, 70, 5)))).as("范围内属于最先绑定的玩家").isTrue();
        ctx.check(loaded.ownerAt(DIMENSION, new BlockPos(11, 70, 5))).as("范围外没有归属").isNull();
        ctx.check(loaded.ownerAt(ResourceLocation.fromNamespaceAndPath("other", "the_retreat"), new BlockPos(5, 70, 5)))
                .as("别的维度没有归属").isNull();
    }

    // ---- 维度判定 ----

    private static void sharedConditionAcceptsPrivate(SceneContext ctx) {
        ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, SHARED)).as("共享匹配共享").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, FIRST_PRIVATE)).as("共享匹配甲的独立维度").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, SECOND_PRIVATE)).as("共享匹配乙的独立维度").isTrue();
        ctx.check(TheRetreatDimension.isPrivateRetreatDimension(SHARED)).as("共享不是独立维度").isFalse();
        ctx.check(TheRetreatDimension.isPrivateRetreatDimension(FIRST_PRIVATE)).as("甲的维度是独立维度").isTrue();
    }

    private static void explicitConditionExact(SceneContext ctx) {
        ctx.check(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, FIRST_PRIVATE)).as("指定玩家匹配自己").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, SECOND_PRIVATE)).as("指定玩家不匹配别人").isFalse();
        ctx.check(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, SHARED)).as("指定玩家不匹配共享").isFalse();
    }

    private static void similarNames(SceneContext ctx) {
        ResourceLocation[] locations = {
                id("the_retreat_"), id("the_retreat_backup"), id("the_retreat_old"),
                id("the_retreat_1_2_3_4_5"), id("the_retreat_12345678-1234-5678-9abc-123456789abc"),
                ResourceLocation.fromNamespaceAndPath("other_mod", "the_retreat"),
                ResourceLocation.fromNamespaceAndPath("other_mod", FIRST_PRIVATE.getPath()),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")};
        for (ResourceLocation location : locations) {
            ctx.check(TheRetreatDimension.isRetreatDimension(location)).as(location + " 是归隐之地").isFalse();
            ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, location)).as(location + " 匹配共享条件").isFalse();
        }
    }

    private static void ordinaryConditions(SceneContext ctx) {
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        ctx.check(TheRetreatDimension.matchesTaskDimension(overworld, overworld)).as("主世界匹配自己").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(overworld, nether)).as("主世界不匹配下界").isFalse();
        ctx.check(TheRetreatDimension.matchesTaskDimension(overworld, FIRST_PRIVATE)).as("主世界不匹配独立维度").isFalse();
    }

    // ---- 搜索队列 ----

    /** 同一线程上手动推进的执行器。 */
    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void drain() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }

    private static Object newQueue() {
        try {
            Constructor<?> constructor = Class.forName(QUEUE).getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法创建共享归隐之地搜索队列", e);
        }
    }

    private static void enqueue(Object queue, ResourceKey<Level> dimension, Executor server,
                                CompletableFuture<?> result, Runnable start) {
        try {
            Reflect.call(queue, Class.forName(QUEUE), "enqueue",
                    new Class<?>[]{ResourceKey.class, Executor.class, CompletableFuture.class, Runnable.class},
                    dimension, server, result, start);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到共享归隐之地搜索队列", e);
        }
    }

    /** 排队中的搜索被取消后，不能让它后面的搜索抢在正在进行的搜索完成之前开始。 */
    private static void cancelledSearchHoldsQueue(SceneContext ctx) {
        Object queue = newQueue();
        QueuedExecutor server = new QueuedExecutor();
        List<Integer> started = new ArrayList<>();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        CompletableFuture<Void> third = new CompletableFuture<>();
        enqueue(queue, Level.OVERWORLD, server, first, () -> started.add(1));
        enqueue(queue, Level.OVERWORLD, server, second, () -> started.add(2));
        enqueue(queue, Level.OVERWORLD, server, third, () -> started.add(3));
        server.drain();
        ctx.check(started).as("只有第一个开始").isEqualTo(List.of(1));
        second.cancel(false);
        server.drain();
        ctx.check(started).as("取消第二个后第三个仍在等第一个").isEqualTo(List.of(1));
        first.complete(null);
        server.drain();
        ctx.check(started).as("第一个完成后跳过已取消的，第三个开始").isEqualTo(List.of(1, 3));
        third.complete(null);
        server.drain();
    }

    /** 启动失败的搜索不堵后面的搜索，不同维度各自排队。 */
    private static void failureDoesNotBlock(SceneContext ctx) {
        Object queue = newQueue();
        QueuedExecutor server = new QueuedExecutor();
        CompletableFuture<Void> failed = new CompletableFuture<>();
        CompletableFuture<Void> next = new CompletableFuture<>();
        CompletableFuture<Void> otherDimension = new CompletableFuture<>();
        List<String> started = new ArrayList<>();
        enqueue(queue, Level.OVERWORLD, server, failed, () -> {
            throw new IllegalStateException("failed start");
        });
        enqueue(queue, Level.OVERWORLD, server, next, () -> started.add("next"));
        enqueue(queue, Level.NETHER, server, otherDimension, () -> started.add("other"));
        server.drain();
        ctx.check(failed.isCompletedExceptionally()).as("启动失败的搜索以异常结束").isTrue();
        ctx.check(started.containsAll(List.of("next", "other"))).as("后面的搜索和别的维度的搜索都开始了").isTrue();
    }
}
