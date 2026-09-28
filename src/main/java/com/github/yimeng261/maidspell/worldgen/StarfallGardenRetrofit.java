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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 为旧存档认领已有庭院，或在已探明区块主动放置一座。
 * 已生成区块不会重跑结构生成，因此只改结构集配置无法补出庭院。
 * 放置期间临时打开补生成闸门，复用正常落点规则；超过区块预算则放弃。
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

    /** 限制同步加载的区块数；超出预算则整座庭院不放置。 */
    private static final long MAX_PLACEMENT_CHUNKS = 400L;

    /** 扫描的格点上限（spacing 网格）。超了就放弃，宁可不补也不能把开服变成几分钟。 */
    private static final long MAX_GRID_CELLS = 2_000_000L;

    /** 每个服务器会话只排一次补生成任务。 */
    private static final AtomicBoolean PASS_SCHEDULED = new AtomicBoolean(false);

    private StarfallGardenRetrofit() {
    }

    /** 此时主世界尚未创建，只重置内存状态。 */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        StarfallGardenData.resetSession();
        PASS_SCHEDULED.set(false);
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

    /** 开服后再执行补生成，避免阻塞启动事件。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!PASS_SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        server.execute(() -> {
            try {
                runRetrofit(server);
            } catch (RuntimeException e) {
                // 补生成只是尽力而为，绝不能因为它把服务器任务队列搞成崩溃报告。
                StarfallGardenData.relockAfterRetrofit();
                MaidSpellMod.LOGGER.error("星落之庭旧存档补生成失败", e);
            }
        });
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
        if (garden.isRetrofitPlaceInExplored()) {
            placeInto(overworld, garden, candidates);
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
     * <p>逐候选尝试，第一个能生成出合法 {@code StructureStart} 的候选就落。失败不记
     * {@code markPlaced}，换下一个候选继续；全部失败也只是白扫一遍，不会留下半成品。
     */
    private static void placeInto(ServerLevel overworld, StarfallGardenStructure garden,
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

            // 每个候选都要重开一次窗口：上一个候选可能已经把闸门关回去了。
            StarfallGardenData.unlockForRetrofit();
            StructureStart start;
            try {
                start = garden.generate(overworld.registryAccess(), chunkGenerator,
                        chunkGenerator.getBiomeSource(), randomState, overworld.getStructureManager(),
                        seed, candidate, 0, overworld, holder -> true);
            } catch (RuntimeException e) {
                MaidSpellMod.LOGGER.warn("星落之庭补生成：候选 {} 生成失败，换下一个", candidate, e);
                StarfallGardenData.relockAfterRetrofit();
                continue;
            }
            if (start == null || !start.isValid()) {
                // 距离带 / 高度夹取不通过，或者这个候选本来就不该长。正常情况，换下一个。
                StarfallGardenData.relockAfterRetrofit();
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
                StarfallGardenData.relockAfterRetrofit();
                MaidSpellMod.LOGGER.error(
                        "星落之庭补生成：候选 {} 的落点需要 {} 个区块，超过上限 {}，放弃补生成。"
                                + "把数据包里的 max_distance_from_center 调小可以收窄覆盖范围",
                        candidate, chunkCount, MAX_PLACEMENT_CHUNKS);
                return;
            }

            try {
                int placedChunks = 0;
                for (ChunkPos pos : ChunkPos.rangeClosed(min, max).toList()) {
                    // getChunk(x, z) 不给状态：已经生成过的区块从盘里读回来就是完整区块，
                    // 未生成的会被生成完整地形。placeInChunk 只是往这些区块里写方块，
                    // 对区块状态没有要求 —— 这正是原版 /place structure 的做法。
                    overworld.getChunk(pos.x, pos.z);
                    start.placeInChunk(overworld, overworld.structureManager(), chunkGenerator,
                            overworld.getRandom(),
                            new BoundingBox(pos.getMinBlockX(), overworld.getMinBuildHeight(), pos.getMinBlockZ(),
                                    pos.getMaxBlockX(), overworld.getMaxBuildHeight(), pos.getMaxBlockZ()),
                            pos);
                    placedChunks++;
                }
                StarfallGardenData.markPlaced(box.getCenter());
                MaidSpellMod.LOGGER.info(
                        "星落之庭补生成：已在旧存档落下一座（锚点区块 {}，覆盖 {} 个区块，高度 {}~{}）",
                        candidate, placedChunks, box.minY(), box.maxY());
            } catch (RuntimeException e) {
                MaidSpellMod.LOGGER.error("星落之庭补生成：写入候选 {} 时失败", candidate, e);
            } finally {
                // 无论成败都恢复：markPlaced 已经把闸门关上了，这里再关一次是幂等的；
                // 失败路径上则靠它避免闸门永久敞开、整座存档到处长庭院。
                StarfallGardenData.relockAfterRetrofit();
            }
            return;
        }

        MaidSpellMod.LOGGER.info("星落之庭补生成：试了 {} 个候选都没能落下，本次放弃", tried);
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
