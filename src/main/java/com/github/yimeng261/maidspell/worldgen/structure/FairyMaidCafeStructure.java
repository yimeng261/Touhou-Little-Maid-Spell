package com.github.yimeng261.maidspell.worldgen.structure;

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
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 妖精女仆咖啡厅结构 - 单块结构
 * 仅在安装妖怪归家模组时生成，群系：樱花森林、樱花树林、竹林、繁花森林、稀疏丛林
 */
public class FairyMaidCafeStructure extends Structure {
    public static final Codec<FairyMaidCafeStructure> CODEC = simpleCodec(FairyMaidCafeStructure::new);
    @SuppressWarnings("removal")
    private static final ResourceLocation TEMPLATE_LOCATION = new ResourceLocation("touhou_little_maid_spell", "fairy_maid_cafe");

    public FairyMaidCafeStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // 检查妖怪归家模组是否已安装
        if (!ModList.get().isLoaded("youkaishomecoming")) {
            return Optional.empty();
        }

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
            return;
        }

        BlockPos pos = new BlockPos(x, y-1, z);
        
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
        return MaidSpellStructures.FAIRY_MAID_CAFE.get();
    }
}
