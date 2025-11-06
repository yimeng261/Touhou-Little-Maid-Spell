package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructurePieceTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 单块结构片段处理类
 * 用于从NBT模板文件加载并生成结构
 */
public class SingleTemplatePiece extends TemplateStructurePiece {
    
    public SingleTemplatePiece(StructureTemplateManager templateManager, ResourceLocation templateName, BlockPos pos, Rotation rotation) {
        super(MaidSpellStructurePieceTypes.SINGLE_TEMPLATE_PIECE.get(), 0, templateManager, templateName, templateName.toString(), 
            makeSettings(rotation), pos);
    }

    public SingleTemplatePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(MaidSpellStructurePieceTypes.SINGLE_TEMPLATE_PIECE.get(), tag, context.structureTemplateManager(), 
            (resourceLocation) -> makeSettings(Rotation.NONE));
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation) {
        return new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(Mirror.NONE)
            .setRotationPivot(BlockPos.ZERO)
            .setIgnoreEntities(false);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        // 处理数据标记，例如战利品箱子
        // 在这里可以根据name来处理不同类型的数据标记
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, 
                           RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        // 确保结构被正确放置
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
    }
}
