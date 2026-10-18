package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructurePieceTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 放完模板后给最底层的实心方块垫地基：往下补到最近的实心地面为止，最多补 foundationDepth 格。
 * 下界开阔洞穴里地形适配的填充很弱，地面低一两格就会露出缝；洞穴雕刻器还会在地形生成之后掏空脚下的地面。两种都靠这一步补实。
 */
public class GroundedTemplatePiece extends PoolElementStructurePiece {
    private static final String FOUNDATION_DEPTH = "FoundationDepth";

    private final StructureTemplateManager templateManager;
    private final int foundationDepth;

    public GroundedTemplatePiece(StructureTemplateManager templateManager, StructurePoolElement element, BlockPos position,
                                 int groundLevelDelta, Rotation rotation, BoundingBox boundingBox, int foundationDepth) {
        super(templateManager, element, position, groundLevelDelta, rotation, boundingBox);
        this.templateManager = templateManager;
        this.foundationDepth = foundationDepth;
    }

    public GroundedTemplatePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(context, tag);
        this.templateManager = context.structureTemplateManager();
        this.foundationDepth = tag.getInt(FOUNDATION_DEPTH);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull StructurePieceSerializationContext context, @NotNull CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putInt(FOUNDATION_DEPTH, foundationDepth);
    }

    @Override
    public @NotNull StructurePieceType getType() {
        return MaidSpellStructurePieceTypes.GROUNDED_TEMPLATE.get();
    }

    @Override
    public void postProcess(@NotNull WorldGenLevel level, @NotNull StructureManager structureManager,
                            @NotNull ChunkGenerator generator, @NotNull RandomSource random, @NotNull BoundingBox chunkBox,
                            @NotNull ChunkPos chunkPos, @NotNull BlockPos pivot) {
        super.postProcess(level, structureManager, generator, random, chunkBox, chunkPos, pivot);
        boolean nether = level.dimensionType().ultraWarm();
        Map<BlockState, Integer> used = new HashMap<>();
        List<BlockPos> carved = new ArrayList<>();
        for (BlockPos base : solidBase(templateManager, element, rotation, position)) {
            if (!chunkBox.isInside(base)) {
                continue;
            }
            int depth = groundDepth(level, base);
            if (depth < 0) {
                carved.add(base);
                continue;
            }
            BlockState fill = foundationFor(level.getBlockState(base.below(depth)), nether);
            fill(level, base, depth - 1, fill, chunkBox);
            used.merge(fill, 1, Integer::sum);
        }
        // 范围内没有地面的列是地面被洞穴雕刻器掏空了（选址看的噪声地形不含雕刻）：补一块和选址允许的深度一样厚的地面，
        // 不往下接到洞底，免得在洞穴里立起柱子
        BlockState fallback = used.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse((nether ? Blocks.NETHERRACK : Blocks.DIRT).defaultBlockState());
        for (BlockPos base : carved) {
            fill(level, base, foundationDepth, fallback, chunkBox);
        }
    }

    /** base 下方第几格是实心地面；foundationDepth + 1 格内都没有时为 -1。 */
    private int groundDepth(WorldGenLevel level, BlockPos base) {
        for (int depth = 1; depth <= foundationDepth + 1; depth++) {
            if (level.getBlockState(base.below(depth)).blocksMotion()) {
                return depth;
            }
        }
        return -1;
    }

    /** 把 base 正下方 count 格补成 fill。 */
    private static void fill(WorldGenLevel level, BlockPos base, int count, BlockState fill, BoundingBox chunkBox) {
        for (int y = 1; y <= count; y++) {
            BlockPos target = base.below(y);
            if (chunkBox.isInside(target)) {
                level.setBlock(target, fill, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 地基用的方块：沿用下方地面，长草、菌岩一类的表层换成它底下的土和下界岩。 */
    private static BlockState foundationFor(BlockState ground, boolean nether) {
        if (ground.is(Blocks.CRIMSON_NYLIUM) || ground.is(Blocks.WARPED_NYLIUM)) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (ground.is(BlockTags.DIRT)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (ground.hasBlockEntity() || !ground.getFluidState().isEmpty()) {
            return (nether ? Blocks.NETHERRACK : Blocks.DIRT).defaultBlockState();
        }
        return ground.getBlock().defaultBlockState();
    }

    /** 模板最底层（模板内 y=0）实心方块的世界坐标；不是单模板元素时为空。 */
    static List<BlockPos> solidBase(StructureTemplateManager templateManager, StructurePoolElement element,
                                    Rotation rotation, BlockPos origin) {
        List<BlockPos> out = new ArrayList<>();
        if (!(element instanceof SinglePoolElement single)) {
            return out;
        }
        StructureTemplate template = single.getTemplate(templateManager);
        if (template.palettes.isEmpty()) {
            return out;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        for (StructureTemplate.StructureBlockInfo info : template.palettes.get(0).blocks()) {
            if (info.pos().getY() == 0 && info.state().blocksMotion()) {
                out.add(StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin));
            }
        }
        return out;
    }
}
