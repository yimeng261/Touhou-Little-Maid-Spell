package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin到ChunkMap，确保动态创建的维度正确初始化结构集
 * 
 * 问题：
 * 动态创建的维度在调用 chunkGenerator.createState() 时，
 * 传入的 structure set lookup 可能为空或不完整，导致 findNearestMapStructure 超时
 * 
 * 解决方案：
 * 在ChunkMap构造后，验证并确保ChunkGeneratorStructureState正确初始化
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    
    @Shadow
    @Final
    private ServerLevel level;
    
    @Shadow
    @Final
    private ChunkGeneratorStructureState chunkGeneratorState;
    
    /**
     * 在ChunkMap构造完成后，确保结构状态已生成
     * 这对于动态创建的维度尤其重要
     */
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void onChunkMapInit(CallbackInfo ci) {
        try {
            // 确保结构生成位置已经初始化
            // 这会触发 ChunkGeneratorStructureState.generatePositions()
            chunkGeneratorState.ensureStructuresGenerated();
            
            // 对于归隐之地维度，记录调试信息
            String dimensionName = level.dimension().location().toString();
            if (dimensionName.contains("the_retreat")) {
                MaidSpellMod.LOGGER.info("ChunkMap initialized for retreat dimension: {}", dimensionName);
                MaidSpellMod.LOGGER.info("  - Structure sets count: {}", 
                    chunkGeneratorState.possibleStructureSets().size());
                MaidSpellMod.LOGGER.info("  - Level seed: {}", level.getSeed());
                
                // 输出所有可能的结构集
                chunkGeneratorState.possibleStructureSets().forEach(holder -> {
                    StructureSet set = holder.value();
                    MaidSpellMod.LOGGER.debug("  - Structure Set: {} (placement: {})", 
                        holder.unwrapKey().map(key -> key.location().toString()).orElse("unknown"),
                        set.placement().getClass().getSimpleName());
                });
            }
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to ensure structures generated for dimension: {}", 
                level.dimension().location(), e);
        }
    }
}

