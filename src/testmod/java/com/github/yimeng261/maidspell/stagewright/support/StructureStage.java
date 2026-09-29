package com.github.yimeng261.maidspell.stagewright.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 在场景场地里按自然生成的方式放置结构。
 * <p>用 {@link JigsawPlacement} 组装拼图，先把 StructureStart 和引用写进区块再逐区块放置，
 * 所以处理器、实体 finalize、依赖"是否在结构内"的 mixin 都和自然生成一致；只绕过各结构类型
 * 自己的选址/地形判定（那部分由自然生成场景覆盖）。放置前算出每个拼图片预期的方块和实体，供保真度比对。
 */
public final class StructureStage {
    private StructureStage() {
    }

    /**
     * 一个拼图片：模板 ID、拼图片包围盒、处理后内容的实际范围（重力等处理器会把方块移出包围盒），
     * 以及放置前按处理器算好的方块与实体（世界坐标）。
     * @param terrainMatching 贴地投影的拼图片：方块按放置时的实时高度图逐列落地
     */
    public record Piece(String template, BoundingBox box, BoundingBox extent, boolean terrainMatching,
                        List<StructureTemplate.StructureBlockInfo> blocks,
                        List<StructureTemplate.StructureEntityInfo> entities) {
    }

    /** @param allBoxes 所有拼图片（含原版补充片）的包围盒，用于判断位置是否被别的片覆盖 */
    public record Placed(List<Piece> pieces, List<BoundingBox> allBoxes) {
    }

    /** 拼图组装参数（从结构对象的 codec 读出，按结构类型补上起点规则）。 */
    private record Jigsaw(Holder<StructureTemplatePool> pool, Optional<ResourceLocation> startJigsaw, int size,
                          BlockPos start, boolean expansionHack, Optional<Heightmap.Types> projection,
                          int maxDistance, LiquidSettings liquid) {
    }

    public static Placed place(SceneContext ctx, String structureId) {
        ServerLevel level = ctx.level();
        RegistryAccess access = level.registryAccess();
        Structure structure = access.registryOrThrow(Registries.STRUCTURE)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(structureId)));
        Jigsaw jigsaw = jigsawOf(ctx, structure, structureId);
        return assemble(ctx, structure, jigsaw);
    }

    /** 从指定模板池组装（村庄补充街道这种没有独立结构的内容），起点注册在 registerAs 结构名下。 */
    public static Placed placePool(SceneContext ctx, String registerAs, String poolId, int size) {
        ServerLevel level = ctx.level();
        RegistryAccess access = level.registryAccess();
        Structure structure = access.registryOrThrow(Registries.STRUCTURE)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(registerAs)));
        Holder<StructureTemplatePool> pool = access.registryOrThrow(Registries.TEMPLATE_POOL)
                .getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.parse(poolId)));
        BlockPos start = new BlockPos(ctx.originX(), ctx.surfaceY(0, 0), ctx.originZ());
        return assemble(ctx, structure, new Jigsaw(pool, Optional.empty(), size, start, false, Optional.empty(),
                80, LiquidSettings.APPLY_WATERLOGGING));
    }

    private static Jigsaw jigsawOf(SceneContext ctx, Structure structure, String structureId) {
        RegistryAccess access = ctx.level().registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        JsonObject json = Structure.DIRECT_CODEC.encodeStart(ops, structure)
                .getOrThrow(message -> new IllegalStateException("无法编码结构 " + structureId + ": " + message))
                .getAsJsonObject();
        String type = json.get("type").getAsString();
        Holder<StructureTemplatePool> pool = access.registryOrThrow(Registries.TEMPLATE_POOL).getHolderOrThrow(
                ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.parse(json.get("start_pool").getAsString())));
        int size = json.get("size").getAsInt();
        int firstAir = ctx.surfaceY(0, 0);
        return switch (type) {
            // HiddenRetreatStructure：起点在估算地表，最远距离写死 150，不应用含水
            case "touhou_little_maid_spell:hidden_retreat" -> new Jigsaw(pool, Optional.empty(), size,
                    at(ctx, firstAir - 1), false, Optional.empty(), 150, LiquidSettings.IGNORE_WATERLOGGING);
            // RelicSanctum（精灵秘境同类型）/FallenSanctum：起点在地表上一格
            case "touhou_little_maid_spell:relic_sanctum", "touhou_little_maid_spell:fallen_sanctum" -> new Jigsaw(
                    pool, Optional.empty(), size, at(ctx, firstAir), false, Optional.empty(),
                    json.has("max_distance_from_center") ? json.get("max_distance_from_center").getAsInt() : 256,
                    LiquidSettings.APPLY_WATERLOGGING);
            // 原版拼图与 LandJigsaw：固定起始高度，可投影到高度图
            case "minecraft:jigsaw", "touhou_little_maid_spell:land_jigsaw" -> new Jigsaw(pool,
                    json.has("start_jigsaw_name")
                            ? Optional.of(ResourceLocation.parse(json.get("start_jigsaw_name").getAsString()))
                            : Optional.empty(),
                    size,
                    at(ctx, json.getAsJsonObject("start_height").get("absolute").getAsInt()),
                    json.has("use_expansion_hack") && json.get("use_expansion_hack").getAsBoolean(),
                    json.has("project_start_to_heightmap")
                            ? Optional.of(Heightmap.Types.CODEC.parse(ops, json.get("project_start_to_heightmap")).getOrThrow())
                            : Optional.empty(),
                    maxDistance(json.get("max_distance_from_center")),
                    json.has("liquid_settings")
                            ? LiquidSettings.CODEC.parse(ops, json.get("liquid_settings")).getOrThrow()
                            : LiquidSettings.APPLY_WATERLOGGING);
            default -> throw new IllegalStateException("不支持的结构类型 " + type + "（" + structureId + "）");
        };
    }

    private static int maxDistance(JsonElement element) {
        return element.isJsonObject() ? element.getAsJsonObject().get("horizontal").getAsInt() : element.getAsInt();
    }

    private static BlockPos at(SceneContext ctx, int y) {
        return new BlockPos(ctx.originX(), y, ctx.originZ());
    }

    private static Placed assemble(SceneContext ctx, Structure structure, Jigsaw jigsaw) {
        ServerLevel level = ctx.level();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkPos chunkPos = new ChunkPos(jigsaw.start());
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunkPos, level, biome -> true);
        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(context, jigsaw.pool(), jigsaw.startJigsaw(),
                jigsaw.size(), jigsaw.start(), jigsaw.expansionHack(), jigsaw.projection(), jigsaw.maxDistance(),
                PoolAliasLookup.EMPTY, DimensionPadding.ZERO, jigsaw.liquid());
        if (stub.isEmpty()) {
            throw new IllegalStateException("拼图组装失败：起始池 " + jigsaw.pool().getRegisteredName());
        }
        PiecesContainer container = stub.get().getPiecesBuilder().build();
        // 拼图可能向任意方向延伸，水平居中到场地原点，让强制加载范围对称覆盖
        BoundingBox raw = container.calculateBoundingBox();
        int dx = ctx.originX() - raw.getCenter().getX();
        int dz = ctx.originZ() - raw.getCenter().getZ();
        container.pieces().forEach(piece -> piece.move(dx, 0, dz));
        StructureStart start = new StructureStart(structure, new ChunkPos(container.pieces().getFirst().getBoundingBox().getCenter()), 0, container);
        return placeStart(ctx, structure, start);
    }

    private static Placed placeStart(SceneContext ctx, Structure structure, StructureStart start) {
        ServerLevel level = ctx.level();
        List<Piece> pieces = expectedPieces(level, start);
        BoundingBox box = start.getBoundingBox();
        for (Piece piece : pieces) {
            box = BoundingBox.encapsulatingBoxes(List.of(box, piece.extent())).orElse(box);
        }
        ctx.record("structureBox", box.toString());
        for (int[] corner : new int[][]{{box.minX(), box.minZ()}, {box.maxX(), box.maxZ()},
                {box.minX(), box.maxZ()}, {box.maxX(), box.minZ()}}) {
            if (ctx.outsideForcedChunks(corner[0] - ctx.originX(), corner[1] - ctx.originZ())) {
                throw new IllegalStateException("结构包围盒 " + box + " 超出强制加载范围（chunkRadius="
                        + ctx.chunkRadius() + "），需要调大场景的区块半径");
            }
        }

        // 注册起点和引用，让 getStructureWithPieceAt 在放置期间就能认出这个结构
        ChunkPos startChunk = start.getChunkPos();
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4))
                .forEach(chunks::add);
        level.getChunk(startChunk.x, startChunk.z).setStartForStructure(structure, start);
        for (ChunkPos chunk : chunks) {
            level.getChunk(chunk.x, chunk.z).addReferenceForStructure(structure, startChunk.toLong());
        }
        ctx.cleanup(() -> unregister(level, structure, startChunk, chunks));

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (ChunkPos chunk : chunks) {
            start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                    new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                            chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk);
        }
        ctx.record("templates", pieces.stream().map(Piece::template).toList());
        return new Placed(pieces, start.getPieces().stream().map(StructurePiece::getBoundingBox).toList());
    }

    /** 与 StructureStart.placeInChunk → SinglePoolElement.place 相同的参数，放置前算出预期内容。 */
    private static List<Piece> expectedPieces(ServerLevel level, StructureStart start) {
        StructureTemplateManager templates = level.getStructureManager();
        BoundingBox startBox = start.getBoundingBox();
        BlockPos pivot = new BlockPos(startBox.getCenter().getX(), startBox.minY(), startBox.getCenter().getZ());
        List<Piece> pieces = new ArrayList<>();
        for (StructurePiece structurePiece : start.getPieces()) {
            if (!(structurePiece instanceof PoolElementStructurePiece piece)
                    || !(piece.getElement() instanceof SinglePoolElement single)) {
                continue;
            }
            String id = Worldgen.templateId(single);
            StructureTemplate template = Reflect.call(single, SinglePoolElement.class, "getTemplate",
                    new Class<?>[]{StructureTemplateManager.class}, templates);
            LiquidSettings liquid = Reflect.field(piece, PoolElementStructurePiece.class, "liquidSettings");
            StructurePlaceSettings settings = Reflect.call(single, SinglePoolElement.class, "getSettings",
                    new Class<?>[]{Rotation.class, BoundingBox.class, LiquidSettings.class, boolean.class},
                    piece.getRotation(), piece.getBoundingBox(), liquid, false);
            List<StructureTemplate.Palette> palettes = Reflect.field(template, StructureTemplate.class, "palettes");
            List<StructureTemplate.StructureBlockInfo> blocks = palettes.isEmpty() ? List.of()
                    : StructureTemplate.processBlockInfos(level, piece.getPosition(), pivot, settings,
                    settings.getRandomPalette(palettes, piece.getPosition()).blocks(), template);
            List<StructureTemplate.StructureEntityInfo> rawEntities =
                    Reflect.field(template, StructureTemplate.class, "entityInfoList");
            List<StructureTemplate.StructureEntityInfo> entities =
                    StructureTemplate.processEntityInfos(template, level, piece.getPosition(), settings, rawEntities);
            List<BlockPos> positions = new ArrayList<>();
            blocks.forEach(info -> positions.add(info.pos()));
            entities.forEach(info -> positions.add(BlockPos.containing(info.pos)));
            BoundingBox box = piece.getBoundingBox();
            positions.add(new BlockPos(box.minX(), box.minY(), box.minZ()));
            positions.add(new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
            BoundingBox extent = BoundingBox.encapsulatingPositions(positions).orElse(box);
            boolean terrainMatching = single.getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
            pieces.add(new Piece(id, box, extent, terrainMatching, blocks, entities));
        }
        return pieces;
    }

    private static void unregister(ServerLevel level, Structure structure, ChunkPos startChunk, List<ChunkPos> chunks) {
        ChunkAccess startAccess = level.getChunk(startChunk.x, startChunk.z);
        Map<Structure, StructureStart> starts = new HashMap<>(startAccess.getAllStarts());
        starts.remove(structure);
        startAccess.setAllStarts(starts);
        for (ChunkPos chunk : chunks) {
            ChunkAccess access = level.getChunk(chunk.x, chunk.z);
            Map<Structure, LongSet> references = new HashMap<>(access.getAllReferences());
            references.remove(structure);
            access.setAllReferences(references);
        }
    }
}
