package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 隐世樱花树结构 - 单块结构
 * 在任意维度的樱花林生成，间隔至少200格
 */
public class HiddenCherryTreeStructure extends Structure {
    public static final Codec<HiddenCherryTreeStructure> CODEC = simpleCodec(HiddenCherryTreeStructure::new);
    @SuppressWarnings("removal")
    private static final ResourceLocation TEMPLATE_LOCATION = new ResourceLocation("touhou_little_maid_spell", "hidden_cherry_tree");

    public HiddenCherryTreeStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // 获取结构生成位置
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, (builder) -> {
            generatePieces(builder, context);
        });
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        // 获取区块中心位置
        int x = context.chunkPos().getMinBlockX() + 8;
        int z = context.chunkPos().getMinBlockZ() + 8;
        
        // 获取地表高度
        int y = context.chunkGenerator().getFirstOccupiedHeight(
            x, z, 
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            context.heightAccessor(), 
            context.randomState()
        );

        // 随机旋转
        Rotation rotation = Rotation.getRandom(context.random());
        
        // 加载模板
        Optional<StructureTemplate> template = context.structureTemplateManager().get(TEMPLATE_LOCATION);
        if (template.isEmpty()) {
            MaidSpellMod.LOGGER.error("HiddenCherryTree template not found: {}", TEMPLATE_LOCATION);
            return;
        }

        BlockPos pos = new BlockPos(x, y, z);
        
        MaidSpellMod.LOGGER.info("Generating HiddenCherryTree at position: {}, rotation: {}", pos, rotation);
        
        // 添加结构片段
        builder.addPiece(new SingleTemplatePiece(
            context.structureTemplateManager(),
            TEMPLATE_LOCATION,
            pos,
            rotation
        ));
    }

    @Override
    public StructureType<?> type() {
        return MaidSpellStructures.HIDDEN_CHERRY_TREE.get();
    }
}

