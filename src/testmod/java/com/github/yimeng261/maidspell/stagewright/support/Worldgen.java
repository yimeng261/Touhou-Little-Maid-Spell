package com.github.yimeng261.maidspell.stagewright.support;

import com.mojang.datafixers.util.Either;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * 自然生成相关的查询：按真实世界的结构放置网格列出候选区块、构造与原版相同的生成上下文、
 * 按真实流程把区块推进到 STRUCTURE_STARTS 取起点，以及只依赖噪声的高度采样（不生成区块）。
 */
public final class Worldgen {
    /** 测试加载区块到 STRUCTURE_STARTS 期间挂的票据。 */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("touhou_little_maid_spell_test", Comparator.comparingLong(ChunkPos::toLong));

    private Worldgen() {
    }

    public static ServerLevel level(SceneContext ctx, String dimension) {
        ServerLevel level = ctx.server().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)));
        if (level == null) {
            throw new IllegalStateException("维度未加载：" + dimension);
        }
        return level;
    }

    public static Holder<Structure> structure(ServerLevel level, String id) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
    }

    public static Holder<StructureSet> structureSet(ServerLevel level, String id) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.parse(id)));
    }

    /**
     * 按放置网格由近及远（以原点为中心的方环）列出候选区块，已计入频率和排斥区。
     * @param radius 网格半径（单位：放置网格格数）
     */
    public static List<ChunkPos> candidates(ServerLevel level, StructureSet set, int radius) {
        return candidates(level, set, ChunkPos.ZERO, radius);
    }

    /** 同上，以 center 所在的放置网格为中心。 */
    public static List<ChunkPos> candidates(ServerLevel level, StructureSet set, ChunkPos center, int radius) {
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        List<ChunkPos> out = new ArrayList<>();
        for (int ring = 0; ring <= radius; ring++) {
            for (int rx = -ring; rx <= ring; rx++) {
                for (int rz = -ring; rz <= ring; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != ring) {
                        continue;
                    }
                    // 参数是区块坐标，内部按 spacing 换算成网格
                    ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(),
                            center.x + rx * placement.spacing(), center.z + rz * placement.spacing());
                    if (placement.isStructureChunk(state, chunk.x, chunk.z)) {
                        out.add(chunk);
                    }
                }
            }
        }
        return out;
    }

    /** 与 ChunkGenerator.tryGenerateStructure 相同参数的生成上下文。 */
    public static Structure.GenerationContext context(ServerLevel level, ChunkPos chunk, Predicate<Holder<Biome>> biomes) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(),
                level.getChunkSource().getGeneratorState().getLevelSeed(), chunk, level, biomes);
    }

    /** 结构在该区块能否找到生成点（含群系判断，不经过 tryGenerateStructure，因此不受维度白名单影响）。 */
    public static Optional<Structure.GenerationStub> validPoint(ServerLevel level, Structure structure, ChunkPos chunk) {
        return structure.findValidGenerationPoint(context(level, chunk, structure.biomes()::contains));
    }

    /** 区块中心按噪声估算的群系（y 取该列地表），用于在昂贵的生成点计算前筛选候选。 */
    public static Holder<Biome> biomeAt(ServerLevel level, int x, int y, int z) {
        RandomState random = level.getChunkSource().randomState();
        return level.getChunkSource().getGenerator().getBiomeSource()
                .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler());
    }

    /** 只依赖噪声的高度图高度（不生成区块）。 */
    public static int height(ServerLevel level, int x, int z, Heightmap.Types type) {
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, type, level, level.getChunkSource().randomState());
    }

    /**
     * 按真实生成流程异步把区块推进到 STRUCTURE_STARTS（经过维度白名单等 mixin），取该结构的起点；没有则为 null。
     * 不在主线程阻塞等待区块，避免与区块线程互等时触发看门狗。
     */
    public static CompletableFuture<StructureStart> startAt(ServerLevel level, Structure structure, ChunkPos chunk) {
        return startsAt(level, chunk).thenApply(starts -> starts.get(structure));
    }

    /** 同 {@link #startAt}，返回该区块所有有效起点。 */
    public static CompletableFuture<Map<Structure, StructureStart>> startsAt(ServerLevel level, ChunkPos chunk) {
        // 原版 getChunkFuture 只加 1 tick 的临时票据，主线程不阻塞时区块会在到达目标状态前被降级；
        // 这里自己在 STRUCTURE_STARTS 级别挂票据，完成后移除
        DistanceManager tickets = Reflect.field(level.getChunkSource(), ServerChunkCache.class, "distanceManager");
        int ticketLevel = ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);
        tickets.addTicket(TICKET, chunk, ticketLevel, chunk);
        // 主线程上调用 getChunkFuture 会就地阻塞等待；从后台线程调用时原版把请求投递给主线程执行器并立即返回
        return CompletableFuture.supplyAsync(() -> level.getChunkSource()
                        .getChunkFuture(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS, true), Util.backgroundExecutor())
                .thenCompose(future -> future)
                .whenComplete((result, error) -> level.getServer().execute(
                        () -> tickets.removeTicket(TICKET, chunk, ticketLevel, chunk)))
                .thenApply(result -> {
                    ChunkAccess access = result.orElseThrow(() -> new IllegalStateException("区块 " + chunk + " 生成失败"));
                    Map<Structure, StructureStart> valid = new HashMap<>();
                    access.getAllStarts().forEach((structure, start) -> {
                        if (start.isValid()) {
                            valid.put(structure, start);
                        }
                    });
                    return valid;
                });
    }

    public static List<String> templates(StructureStart start) {
        List<String> out = new ArrayList<>();
        start.getPieces().forEach(piece -> {
            if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single) {
                out.add(templateId(single));
            }
        });
        return out;
    }

    /** 拼图元素引用的模板 ID；内联模板为 {@code <inline>}。 */
    public static String templateId(SinglePoolElement element) {
        Either<ResourceLocation, StructureTemplate> template = Reflect.field(element, SinglePoolElement.class, "template");
        return template.left().map(ResourceLocation::toString).orElse("<inline>");
    }

    /** 已注册模板池里引用各模板的拼图元素（模板 ID → 第一个引用它的元素，不含内联模板）。 */
    public static Map<String, SinglePoolElement> poolElements(ServerLevel level) {
        Map<String, SinglePoolElement> out = new LinkedHashMap<>();
        for (StructureTemplatePool pool : level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)) {
            for (StructurePoolElement element : pool.templates) {
                if (element instanceof SinglePoolElement single) {
                    String id = templateId(single);
                    if (!id.equals("<inline>")) {
                        out.putIfAbsent(id, single);
                    }
                }
            }
        }
        return out;
    }

    public static SinglePoolElement poolElement(ServerLevel level, String templateId) {
        SinglePoolElement element = poolElements(level).get(templateId);
        if (element == null) {
            throw new IllegalStateException("没有模板池引用 " + templateId);
        }
        return element;
    }

    /** 包围盒水平范围内按步长采样，水面高于水底（WORLD_SURFACE_WG &gt; OCEAN_FLOOR_WG）的列所占比例。 */
    public static double waterFraction(ServerLevel level, BoundingBox box, int step) {
        int water = 0;
        int total = 0;
        for (int x = box.minX(); x <= box.maxX(); x += step) {
            for (int z = box.minZ(); z <= box.maxZ(); z += step) {
                total++;
                if (height(level, x, z, Heightmap.Types.WORLD_SURFACE_WG) > height(level, x, z, Heightmap.Types.OCEAN_FLOOR_WG)) {
                    water++;
                }
            }
        }
        return total == 0 ? 0 : (double) water / total;
    }
}
