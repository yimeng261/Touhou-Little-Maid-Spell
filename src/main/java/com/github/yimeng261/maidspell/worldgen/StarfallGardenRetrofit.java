package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.StarfallGardenStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 为旧存档认领已有庭院，或在已探明区块主动放置一座。
 * 已生成区块不会重跑结构生成，因此只改结构集配置无法补出庭院。
 * 复用正常落点规则选点，超过区块预算则放弃；选定后分摊到多个服务器 tick 写入方块。
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class StarfallGardenRetrofit {
    private static final ResourceKey<Structure> STARFALL_GARDEN_KEY = ResourceKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "starfall_garden"));

    /** 认领扫描最多查多少个候选区块。只是读盘问一句「有没有」，可以放宽些。 */
    private static final int MAX_CLAIM_CANDIDATES = 64;

    /** 主动放置最多试多少个候选。每次失败都可能已经付过一次 generate 的代价，所以比认领紧。 */
    private static final int MAX_PLACE_ATTEMPTS = 16;

    /** 限制主动放置涉及的区块总数；超出预算则整座庭院不放置。 */
    private static final long MAX_PLACEMENT_CHUNKS = 400L;

    /** 把主动放置分摊到多个服务器 tick，避免开服任务一次性卡住主线程。 */
    private static final int CHUNKS_PER_PLACEMENT_TICK = 8;

    /** 扫描的格点上限（spacing 网格）。超了就放弃，宁可不补也不能把开服变成几分钟。 */
    private static final long MAX_GRID_CELLS = 2_000_000L;

    /** 分帧放置最多有几次没写完（正常停服不算）。某个区块稳定抛异常时，不让每次开服都把半座庭院重写一遍。 */
    private static final int MAX_PLACEMENT_ATTEMPTS = 3;

    /** 正在分帧写入的放置任务，只在服务器主线程读写。 */
    private static PlacementTask pendingPlacement;

    private StarfallGardenRetrofit() {
    }

    /** 此时主世界尚未创建，只重置内存状态。 */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        StarfallGardenData.resetSession();
        pendingPlacement = null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (pendingPlacement == null) {
            return;
        }
        PlacementTask task = pendingPlacement;
        pendingPlacement = null;
        continuePlacement(event.getServer(), task);
    }

    /** 放置途中正常停服不算失败，退还这次计数；同时放掉任务持有的主世界。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (pendingPlacement == null) {
            return;
        }
        pendingPlacement = null;
        StarfallGardenData.refundPlacementAttempt(event.getServer());
    }

    /** 出生点区块生成前恢复存档闸门，防止旧存档多生成一座。 */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        StarfallGardenData.restore(level.getServer());
    }

    /**
     * 开服完成后在主线程跑补生成：认领阶段同步推进最多 {@value #MAX_CLAIM_CANDIDATES} 个候选区块；
     * 放置阶段选定落点后，每 tick 写 {@value #CHUNKS_PER_PLACEMENT_TICK} 个区块，最多 {@value #MAX_PLACEMENT_CHUNKS} 个。
     * 跑完一次就记 RetrofitAttempted，之后开服直接跳过；放置写入中途失败或停服则不记，下次开服重试，
     * 写入失败或崩服累计 {@value #MAX_PLACEMENT_ATTEMPTS} 次后放弃，并关闸防止世界生成再放一座。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            runRetrofit(event.getServer());
        } catch (RuntimeException e) {
            // 补生成只是尽力而为，失败不能影响开服
            MaidSpellMod.LOGGER.error("星落之庭旧存档补生成失败", e);
        }
    }

    private static void runRetrofit(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null || !server.isRunning()) {
            return;
        }
        if (!server.getWorldData().worldGenOptions().generateStructures()) {
            // 关掉结构生成的世界不该被我们偷偷塞一座进去。
            return;
        }
        if (StarfallGardenData.isPlaced()) {
            return;
        }
        if (StarfallGardenData.isRetrofitAttempted(server)) {
            return;
        }

        var structureRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = structureRegistry.get(STARFALL_GARDEN_KEY);
        if (!(structure instanceof StarfallGardenStructure garden) || !garden.isRetrofitOnLoad()) {
            // 数据包把补生成关了（或者这个 id 已经被改成了别的结构类型）：记一笔，免得每次开服都来问。
            StarfallGardenData.markRetrofitAttempted(server);
            return;
        }

        List<ChunkPos> candidates = collectCandidates(server, overworld, garden);

        // 第一段：认领旧版本留下的庭院。
        //
        // <p>这个标记是后加的，升级上来的存档第一次跑补生成时存档里根本没有记录，但那一座可能早就
        // 长在地上了。不认领的话第二段会再放一座。所以先只读不写地扫一遍，找到就补记一笔收工。
        if (claimExisting(overworld, garden, candidates)) {
            StarfallGardenData.markRetrofitAttempted(server);
            return;
        }

        // 第二段：真放。这一段才是「旧存档找不到新结构」的解法，原理见类注释。
        if (StarfallGardenData.placementAttempts(server) >= MAX_PLACEMENT_ATTEMPTS) {
            MaidSpellMod.LOGGER.error("星落之庭补生成：放置 {} 次都没能写完，不再重试；写到一半的庭院留在原处，不再另放一座",
                    MAX_PLACEMENT_ATTEMPTS);
            StarfallGardenData.giveUpPlacement(server);
            return;
        }
        if (garden.isRetrofitPlaceInExplored()) {
            if (placeInto(overworld, garden, candidates)) {
                // 已排上分帧放置任务，写完后由 continuePlacement 记账。
                return;
            }
        } else {
            MaidSpellMod.LOGGER.info("星落之庭补生成：主动放置已在数据包里关闭（retrofit_place_in_explored=false）");
        }

        StarfallGardenData.markRetrofitAttempted(server);
    }

    /**
     * 第一段：扫描候选区块，看有没有旧版本留下的、还没被记进存档标记的庭院。
     *
     * <p>刻意只走到 {@code STRUCTURE_STARTS} 就够：这一步只是把区块读回来问一句
     * 「这里面有没有本结构的 StructureStart」，不需要加载完整地形，也不写任何方块。
     */
    private static boolean claimExisting(ServerLevel overworld, StarfallGardenStructure garden,
                                         List<ChunkPos> candidates) {
        int tried = 0;
        for (ChunkPos candidate : candidates) {
            if (tried >= MAX_CLAIM_CANDIDATES) {
                break;
            }
            tried++;
            try {
                ChunkAccess chunk = overworld.getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true);
                StructureStart existing = overworld.structureManager()
                        .getStartForStructure(SectionPos.bottomOf(chunk), garden, chunk);
                if (existing != null && existing.isValid()) {
                    StarfallGardenData.markPlaced(existing.getBoundingBox().getCenter());
                    MaidSpellMod.LOGGER.info("星落之庭补生成：区块 {} 里已有庭院，补记存档标记并停止", candidate);
                    return true;
                }
            } catch (RuntimeException e) {
                MaidSpellMod.LOGGER.warn("星落之庭补生成：查询区块 {} 失败，跳过", candidate, e);
            }
        }
        return false;
    }

    /**
     * 第二段：把庭院真正放进旧存档。
     *
     * <p>逐候选尝试，第一个能生成出合法 {@code StructureStart} 且不超区块预算的候选就落。
     * 生成失败不占名额，换下一个候选继续；选定之后先在内存里占名额挡住世界生成，
     * 再排上分帧放置任务，方块全部写完才落盘名额并登记结构起点。
     *
     * @return 是否排上了放置任务
     */
    private static boolean placeInto(ServerLevel overworld, StarfallGardenStructure garden,
                                     List<ChunkPos> candidates) {
        var chunkGenerator = overworld.getChunkSource().getGenerator();
        var randomState = overworld.getChunkSource().randomState();
        long seed = overworld.getSeed();

        int tried = 0;
        for (ChunkPos candidate : candidates) {
            if (tried >= MAX_PLACE_ATTEMPTS) {
                break;
            }
            tried++;

            if (StarfallGardenData.isPlaced()) {
                MaidSpellMod.LOGGER.info("星落之庭补生成：世界生成已放下一座，放弃补生成");
                return false;
            }
            StructureStart start;
            try {
                start = garden.computeStart(overworld.registryAccess(), chunkGenerator,
                        chunkGenerator.getBiomeSource(), randomState, overworld.getStructureManager(),
                        seed, candidate, 0, overworld, holder -> true);
            } catch (RuntimeException e) {
                MaidSpellMod.LOGGER.warn("星落之庭补生成：候选 {} 生成失败，换下一个", candidate, e);
                continue;
            }
            if (!start.isValid()) {
                // 距离带 / 高度夹取不通过，或者这个候选本来就不该长。正常情况，换下一个。
                continue;
            }
            BoundingBox box = start.getBoundingBox();
            ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                    SectionPos.blockToSectionCoord(box.minZ()));
            ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()),
                    SectionPos.blockToSectionCoord(box.maxZ()));
            long chunkCount = (long) (max.x - min.x + 1) * (max.z - min.z + 1);
            if (chunkCount > MAX_PLACEMENT_CHUNKS) {
                // 超出预算就整个放弃，不做「放一半」：半座岛比没有岛更糟。
                MaidSpellMod.LOGGER.error(
                        "星落之庭补生成：候选 {} 的落点需要 {} 个区块，超过上限 {}，放弃补生成。"
                                + "把数据包里的 max_distance_from_center 调小可以收窄覆盖范围",
                        candidate, chunkCount, MAX_PLACEMENT_CHUNKS);
                return false;
            }
            MinecraftServer server = overworld.getServer();
            if (!StarfallGardenData.recordPlacementAttempt(server, box.getCenter())) {
                return false;
            }
            if (!StarfallGardenData.tryReserve()) {
                // 选点期间世界生成先占到了名额
                StarfallGardenData.refundPlacementAttempt(server);
                MaidSpellMod.LOGGER.info("星落之庭补生成：世界生成已放下一座，放弃补生成");
                return false;
            }

            pendingPlacement = new PlacementTask(overworld, garden, start, candidate,
                    ChunkPos.rangeClosed(min, max).toList(), 0);
            return true;
        }

        MaidSpellMod.LOGGER.info("星落之庭补生成：试了 {} 个候选都没能落下，本次放弃", tried);
        return false;
    }

    /**
     * 写入一批区块；没写完就把剩下的排到下一 tick，写完再登记起点、落盘名额并记账。
     * 写入失败或停服时名额只在本次会话内保持占用，不落盘也不记账，下次开服重新补生成，次数受 {@value #MAX_PLACEMENT_ATTEMPTS} 限制。
     */
    private static void continuePlacement(MinecraftServer server, PlacementTask task) {
        if (!server.isRunning()) {
            return;
        }

        ServerLevel overworld = task.overworld();
        ChunkGenerator chunkGenerator = overworld.getChunkSource().getGenerator();
        List<ChunkPos> chunks = task.chunks();
        int end = Math.min(task.nextIndex() + CHUNKS_PER_PLACEMENT_TICK, chunks.size());
        try {
            for (int i = task.nextIndex(); i < end; i++) {
                ChunkPos pos = chunks.get(i);
                // getChunk(x, z) 不给状态：已经生成过的区块从盘里读回来就是完整区块，
                // 未生成的会被生成完整地形。placeInChunk 只是往这些区块里写方块，
                // 对区块状态没有要求 —— 这正是原版 /place structure 的做法。
                overworld.getChunk(pos.x, pos.z);
                task.start().placeInChunk(overworld, overworld.structureManager(), chunkGenerator,
                        overworld.getRandom(),
                        new BoundingBox(pos.getMinBlockX(), overworld.getMinBuildHeight(), pos.getMinBlockZ(),
                                pos.getMaxBlockX(), overworld.getMaxBuildHeight(), pos.getMaxBlockZ()),
                        pos);
            }
        } catch (RuntimeException e) {
            MaidSpellMod.LOGGER.error("星落之庭补生成：写入候选 {} 时失败，留待下次开服重试",
                    task.candidate(), e);
            return;
        }

        if (end < chunks.size()) {
            pendingPlacement = new PlacementTask(overworld, task.garden(), task.start(),
                    task.candidate(), chunks, end);
            return;
        }

        if (!registerStart(overworld, task.garden(), task.start(), task.candidate(), chunks)) {
            return;
        }
        BoundingBox box = task.start().getBoundingBox();
        StarfallGardenData.markPlaced(box.getCenter());
        StarfallGardenData.markRetrofitAttempted(server);
        MaidSpellMod.LOGGER.info(
                "星落之庭补生成：已在旧存档落下一座（锚点区块 {}，覆盖 {} 个区块，高度 {}~{}）",
                task.candidate(), chunks.size(), box.minY(), box.maxY());
    }

    private record PlacementTask(ServerLevel overworld, StarfallGardenStructure garden,
                                 StructureStart start, ChunkPos candidate, List<ChunkPos> chunks,
                                 int nextIndex) {
    }

    /**
     * 原版 /place structure 不登记起点和引用；这里照世界生成补上，/locate、location_check 和结构标题才认得出这一座。
     *
     * <p>必须在写完方块之后登记：包围盒里没生成过的区块在放置时 getChunk 走完整生成，
     * 那时起点若已登记，它们会在 STRUCTURE_REFERENCES 阶段引用起点、在 FEATURES 阶段自己放一遍，
     * 接着又被放置任务再放一遍。区块可能在分帧期间卸载过，这里按坐标重新取。
     *
     * @return 是否登记成功
     */
    private static boolean registerStart(ServerLevel overworld, StarfallGardenStructure garden, StructureStart start,
                                         ChunkPos anchor, List<ChunkPos> chunks) {
        try {
            ChunkAccess anchorChunk = overworld.getChunk(anchor.x, anchor.z);
            anchorChunk.setStartForStructure(garden, start);
            overworld.onStructureStartsAvailable(anchorChunk);
            for (ChunkPos pos : chunks) {
                overworld.getChunk(pos.x, pos.z).addReferenceForStructure(garden, anchor.toLong());
            }
            return true;
        } catch (RuntimeException e) {
            MaidSpellMod.LOGGER.error("星落之庭补生成：登记锚点区块 {} 的结构起点失败，留待下次开服重试", anchor, e);
            return false;
        }
    }

    /**
     * 收集环带内的候选区块：直接问结构集的 placement「这一格的潜在区块在哪」，而不是自己瞎撒点——
     * 这样补生成访问的区块和正常情况下世界生成会去访问的完全是同一批，不会造出「原版永远不会放结构」
     * 的区块里凭空多一座的怪事。
     */
    private static List<ChunkPos> collectCandidates(MinecraftServer server, ServerLevel overworld,
                                                    StarfallGardenStructure garden) {
        ChunkGeneratorStructureState state = overworld.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();
        BlockPos spawn = overworld.getSharedSpawnPos();

        List<ChunkPos> candidates = new ArrayList<>();
        long minSq = (long) garden.getMinSpawnDistance() * garden.getMinSpawnDistance();
        long maxSq = (long) garden.getMaxSpawnDistance() * garden.getMaxSpawnDistance();

        var setRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        for (Holder.Reference<StructureSet> holder : setRegistry.holders().toList()) {
            StructureSet set = holder.value();
            boolean containsGarden = set.structures().stream()
                    .anyMatch(entry -> entry.structure().is(STARFALL_GARDEN_KEY));
            if (!containsGarden) {
                continue;
            }
            // 只认 random_spread。环带式 placement 的候选区块算法完全不同，数据包真换了就整个跳过，
            // 而不是拿错误的格点去硬套。
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
                MaidSpellMod.LOGGER.warn("星落之庭所在的结构集 {} 不是 random_spread，跳过旧存档补生成",
                        holder.key().location());
                continue;
            }
            collectFromSpread(state, spread, seed, spawn, garden, minSq, maxSq, candidates);
        }

        candidates.sort(Comparator.comparingLong(pos -> distanceSqToSpawn(pos, spawn)));
        return candidates;
    }

    private static void collectFromSpread(ChunkGeneratorStructureState state, RandomSpreadStructurePlacement spread,
                                          long seed, BlockPos spawn, StarfallGardenStructure garden,
                                          long minSq, long maxSq, List<ChunkPos> candidates) {
        int spacing = Math.max(1, spread.spacing());

        int cellMinX = Math.floorDiv(spawn.getX() - garden.getMaxSpawnDistance(), 16 * spacing);
        int cellMaxX = Math.floorDiv(spawn.getX() + garden.getMaxSpawnDistance(), 16 * spacing);
        int cellMinZ = Math.floorDiv(spawn.getZ() - garden.getMaxSpawnDistance(), 16 * spacing);
        int cellMaxZ = Math.floorDiv(spawn.getZ() + garden.getMaxSpawnDistance(), 16 * spacing);

        long cells = (long) (cellMaxX - cellMinX + 1) * (cellMaxZ - cellMinZ + 1);
        if (cells > MAX_GRID_CELLS) {
            MaidSpellMod.LOGGER.warn("星落之庭环带过大（{} 个格点），跳过旧存档补生成；"
                    + "把 max_spawn_distance 调小才能让补生成跑得动", cells);
            return;
        }

        // 只扫环带外接的正方形格点；真正的圆环判定交给下面和结构里一模一样的那套平方距离比较。
        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                ChunkPos candidate = spread.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
                if (!spread.isStructureChunk(state, candidate.x, candidate.z)) {
                    continue;
                }
                long distSq = distanceSqToSpawn(candidate, spawn);
                if (distSq < minSq || distSq > maxSq) {
                    continue;
                }
                candidates.add(candidate);
            }
        }
    }

    /** 和 {@code StarfallGardenStructure} 里完全相同的水平平方距离，避免补生成和实际落点判定打架。 */
    private static long distanceSqToSpawn(ChunkPos pos, BlockPos spawn) {
        long dx = (long) pos.getMiddleBlockX() - spawn.getX();
        long dz = (long) pos.getMiddleBlockZ() - spawn.getZ();
        return dx * dx + dz * dz;
    }
}
