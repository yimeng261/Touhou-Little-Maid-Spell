package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.common.WorldWorkerManager;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 WorldWorkerManager 的分帧结构搜索器。
 * <p>
 * 复现 ChunkGenerator.findNearestMapStructure 的环形搜索逻辑，
 * 每次 {@link #doWork()} 只检查一个边界候选区块，在主线程分帧执行。
 * <p>
 * 关键设计：每次 doWork 最多执行一次昂贵的区块生成操作（{@code getChunk(STRUCTURE_STARTS)}），
 * 执行后主动让出控制权（返回 false），确保 WorldWorkerManager 有机会检查时间预算，
 * 避免单次 tick 被阻塞过久。
 */
public class StructureSearchWorker implements WorldWorkerManager.IWorker {

    private final ServerLevel level;
    private final Set<Holder<Structure>> structureHolders;
    private final RandomSpreadStructurePlacement placement;
    private final int searchCenterChunkX;
    private final int searchCenterChunkZ;
    private final BlockPos searchCenterBlock;
    private final int maxRadius;
    private final boolean skipKnownStructures;
    private final long seed;
    private final CompletableFuture<BlockPos> resultFuture;

    // ========== 环形搜索迭代状态 ==========

    /**
     * 当前搜索的环半径（0 = 中心点，1 = 第一环，...）
     */
    private int currentRing = 0;

    /**
     * 当前环的边位置索引。
     * <p>
     * 环形边界按四段遍历：上边、右边、下边、左边。
     * edgeIndex 是当前环所有边界位置的线性索引。
     * ring=0 时只有 1 个位置 (0,0)，ring=N 时有 8*N 个位置。
     */
    private int edgeIndex = 0;

    private boolean finished = false;
    private int samples = 0;
    private final long startTimeMs;

    // ========== 当前环的最优结果 ==========

    @Nullable
    private Pair<BlockPos, Holder<Structure>> bestResult = null;
    private double bestDistanceSq = Double.MAX_VALUE;
    private int lastLoggedRing = -1;

    public StructureSearchWorker(
            ServerLevel level,
            Set<Holder<Structure>> structureHolders,
            RandomSpreadStructurePlacement placement,
            BlockPos searchCenter,
            int maxRadius,
            boolean skipKnownStructures
    ) {
        this.level = level;
        this.structureHolders = structureHolders;
        this.placement = placement;
        this.searchCenterChunkX = SectionPos.blockToSectionCoord(searchCenter.getX());
        this.searchCenterChunkZ = SectionPos.blockToSectionCoord(searchCenter.getZ());
        this.searchCenterBlock = searchCenter;
        this.maxRadius = maxRadius;
        this.skipKnownStructures = skipKnownStructures;
        this.seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        this.resultFuture = new CompletableFuture<>();
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * 获取搜索结果的 Future
     */
    public CompletableFuture<BlockPos> getResultFuture() {
        return resultFuture;
    }

    /**
     * 注册到 WorldWorkerManager 开始搜索
     */
    public void start() {
        MaidSpellMod.LOGGER.info("分帧搜索开始 - 中心: ({}, {}), 半径: {}, spacing: {}",
                searchCenterChunkX, searchCenterChunkZ, maxRadius, placement.spacing());
        WorldWorkerManager.addWorker(this);
    }

    @Override
    public boolean hasWork() {
        return !finished;
    }

    /**
     * 每次调用检查一个边界候选区块。
     * <p>
     * 返回值语义：
     * <ul>
     *   <li>{@code true} — 本次操作是廉价的（缓存命中），可以继续执行下一个候选</li>
     *   <li>{@code false} — 本次操作是昂贵的（触发了区块生成）或搜索已结束，
     *       应让出控制权让 WorldWorkerManager 重新检查时间预算</li>
     * </ul>
     */
    @Override
    public boolean doWork() {
        if (finished) {
            return false;
        }

        // 进度日志：每 10 环输出一次
        if (currentRing > 0 && currentRing % 10 == 0 && currentRing != lastLoggedRing) {
            lastLoggedRing = currentRing;
            long elapsed = System.currentTimeMillis() - startTimeMs;
            MaidSpellMod.LOGGER.info("搜索进度: {}/{} 环, 已检查 {} 个候选, 耗时 {}ms",
                    currentRing, maxRadius, samples, elapsed);
        }

        // 搜索结束判断
        if (currentRing > maxRadius) {
            finishSearch();
            return false;
        }

        // 获取当前环的边界位置总数
        int edgeCount = currentRing == 0 ? 1 : 8 * currentRing;

        if (edgeIndex >= edgeCount) {
            // 当前环遍历完毕
            if (bestResult != null) {
                // 原版逻辑：在当前环找到结果就立即返回
                completeSearch(bestResult.getFirst());
                return false;
            }

            // 进入下一环
            currentRing++;
            edgeIndex = 0;

            if (currentRing > maxRadius) {
                finishSearch();
                return false;
            }
            return true; // 廉价操作，可继续
        }

        // 计算当前 edgeIndex 对应的 (j, k) 偏移
        int j, k;
        if (currentRing == 0) {
            j = 0;
            k = 0;
        } else {
            int[] jk = edgeIndexToOffset(currentRing, edgeIndex);
            j = jk[0];
            k = jk[1];
        }
        edgeIndex++;

        // 检查候选
        CheckCost cost = checkCandidateAt(j, k);

        // 昂贵操作后返回 false，让 WorldWorkerManager 重新检查时间预算
        return cost != CheckCost.EXPENSIVE;
    }

    /**
     * 将环的线性边界索引转换为 (j, k) 偏移。
     * <p>
     * 环 ring=N 的边界有 8*N 个位置，按四段排列：
     * <ol>
     *   <li>上边：j 从 -N 到 N-1，k = -N（共 2N 个）</li>
     *   <li>右边：j = N，k 从 -N 到 N-1（共 2N 个）</li>
     *   <li>下边：j 从 N 到 -N+1，k = N（共 2N 个）</li>
     *   <li>左边：j = -N，k 从 N 到 -N+1（共 2N 个）</li>
     * </ol>
     */
    private static int[] edgeIndexToOffset(int ring, int idx) {
        int sideLen = 2 * ring; // 每段的长度

        if (idx < sideLen) {
            // 上边：j = -ring + idx, k = -ring
            return new int[]{-ring + idx, -ring};
        }
        idx -= sideLen;

        if (idx < sideLen) {
            // 右边：j = ring, k = -ring + idx
            return new int[]{ring, -ring + idx};
        }
        idx -= sideLen;

        if (idx < sideLen) {
            // 下边：j = ring - idx, k = ring
            return new int[]{ring - idx, ring};
        }
        idx -= sideLen;

        // 左边：j = -ring, k = ring - idx
        return new int[]{-ring, ring - idx};
    }

    /**
     * 检查操作的成本分类
     */
    private enum CheckCost {
        /**
         * 廉价：只查了缓存或无操作
         */
        CHEAP,
        /**
         * 昂贵：触发了区块生成（getChunk）
         */
        EXPENSIVE
    }

    /**
     * 检查指定偏移处的候选区块，返回操作成本。
     */
    private CheckCost checkCandidateAt(int offsetJ, int offsetK) {
        int spacing = placement.spacing();
        int regionX = searchCenterChunkX + spacing * offsetJ;
        int regionZ = searchCenterChunkZ + spacing * offsetK;

        ChunkPos candidateChunk = placement.getPotentialStructureChunk(seed, regionX, regionZ);
        samples++;

        CandidateResult result = checkCandidate(candidateChunk);
        if (result.found != null) {
            double distSq = searchCenterBlock.distSqr(result.found.getFirst());
            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                bestResult = result.found;
            }
        }
        return result.cost;
    }

    private record CandidateResult(@Nullable Pair<BlockPos, Holder<Structure>> found, CheckCost cost) {
        static CandidateResult cheap(@Nullable Pair<BlockPos, Holder<Structure>> found) {
            return new CandidateResult(found, CheckCost.CHEAP);
        }

        static CandidateResult expensive(@Nullable Pair<BlockPos, Holder<Structure>> found) {
            return new CandidateResult(found, CheckCost.EXPENSIVE);
        }
    }

    /**
     * 检查候选区块是否包含目标结构（复现 ChunkGenerator.getStructureGeneratingAt 逻辑）。
     * 同时返回操作成本，用于决定是否让出控制权。
     */
    private CandidateResult checkCandidate(ChunkPos chunkPos) {
        var structureManager = level.structureManager();

        for (Holder<Structure> holder : structureHolders) {
            StructureCheckResult checkResult = structureManager.checkStructurePresence(
                    chunkPos, holder.value(), placement, skipKnownStructures);

            if (checkResult == StructureCheckResult.START_NOT_PRESENT) {
                continue;
            }

            if (!skipKnownStructures && checkResult == StructureCheckResult.START_PRESENT) {
                return CandidateResult.cheap(Pair.of(placement.getLocatePos(chunkPos), holder));
            }

            // CHUNK_LOAD_NEEDED — getChunk 可能触发 generate()，这是昂贵操作
            RetreatManager.setSearchingStructure(true);
            try {
                ChunkAccess chunkAccess = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
                StructureStart start = structureManager.getStartForStructure(
                        SectionPos.bottomOf(chunkAccess), holder.value(), chunkAccess);

                if (start != null && start.isValid()) {
                    if (skipKnownStructures) {
                        if (start.canBeReferenced()) {
                            structureManager.addReference(start);
                            return CandidateResult.expensive(
                                    Pair.of(placement.getLocatePos(start.getChunkPos()), holder));
                        }
                    } else {
                        return CandidateResult.expensive(Pair.of(placement.getLocatePos(chunkPos), holder));
                    }
                }
            } finally {
                RetreatManager.setSearchingStructure(false);
            }

            // getChunk 已执行，即使未找到结构也标记为昂贵
            return CandidateResult.expensive(null);
        }

        return CandidateResult.cheap(null);
    }

    /**
     * 搜索完成（找到结构），强制加载区块并返回结果
     */
    private void completeSearch(BlockPos structurePos) {
        finished = true;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        MaidSpellMod.LOGGER.info("分帧搜索完成 - 位置: {}, 搜索了 {} 环, {} 个候选, 耗时 {}ms",
                structurePos, currentRing, samples, elapsed);

        // 强制加载结构附近区块以触发完整生成（从而触发 afterPlace）
        RetreatManager.forceLoadStructureChunks(level, structurePos);

        resultFuture.complete(structurePos);
    }

    /**
     * 搜索结束（未找到结构）
     */
    private void finishSearch() {
        finished = true;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        MaidSpellMod.LOGGER.warn("分帧搜索完成，未找到结构 - 已搜索 {} 环, {} 个候选, 耗时 {}ms",
                maxRadius, samples, elapsed);
        resultFuture.complete(null);
    }

    /**
     * 取消搜索
     */
    public void cancel() {
        finished = true;
        if (!resultFuture.isDone()) {
            resultFuture.cancel(true);
        }
    }
}
