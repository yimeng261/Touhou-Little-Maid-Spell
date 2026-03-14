package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructurePieceTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * 不参与地形填充的拼图块。
 * 实现 {@link PieceBeardifierModifier}，使 Beardifier 跳过该块的地形适配。
 */
public class NoTerrainFillPiece extends PoolElementStructurePiece implements PieceBeardifierModifier {

    public NoTerrainFillPiece(
            StructureTemplateManager structureTemplateManager,
            StructurePoolElement element,
            BlockPos position,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox boundingBox,
            LiquidSettings liquidSettings
    ) {
        super(structureTemplateManager, element, position, groundLevelDelta, rotation, boundingBox, liquidSettings);
    }

    public NoTerrainFillPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(context, tag);
    }

    @Override
    public StructurePieceType getType() {
        return MaidSpellStructurePieceTypes.NO_TERRAIN_FILL.get();
    }

    @Override
    public BoundingBox getBeardifierBox() {
        return this.getBoundingBox();
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return super.getGroundLevelDelta();
    }
}
