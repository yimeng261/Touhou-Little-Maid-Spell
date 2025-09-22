package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HidenRetreatStructure extends Structure {
    public static final Codec<HidenRetreatStructure> CODEC = RecordCodecBuilder.<HidenRetreatStructure>mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size)
            ).apply(instance, HidenRetreatStructure::new)
    ).codec();

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private int height;
    
    // 地形平坦度检测的最大方差阈值（单位：方块高度的平方）
    private static final double MAX_TERRAIN_VARIANCE = 16.0; // 相当于标准差约4个方块
    // 每个区块的采样点数量
    private static final int SAMPLES_PER_CHUNK = 5;

    public HidenRetreatStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        BlockPos centerPos = new BlockPos(chunkPos.getMinBlockX() + 8, 0, chunkPos.getMinBlockZ() + 8);
        
        // 检查以当前区块为中心的3*3区块是否都是樱花林并且地形足够平坦
        if (!isValidGenerationLocation(context, chunkPos)) {
            return Optional.empty();
        }
        
        BlockPos structurePos = new BlockPos(centerPos.getX(), this.height, centerPos.getZ()); // 向上偏移1格
        
        return JigsawPlacement.addPieces(
                context,
                this.startPool,
                Optional.empty(), // 无可选目标池
                this.size,
                structurePos,
                false, // 不使用扩展高度
                Optional.empty(), // 无投影
                150 // 最大距离 - 增加以支持大型结构和垂直连接
        );
    }


    @Override
    public StructureType<?> type() {
        return MaidSpellStructures.HIDEN_RETREAT.get();
    }
    
    /**
     * 检查以给定区块为中心的3*3区块是否都是樱花林生物群系并且地形足够平坦
     * @param context 生成上下文
     * @param centerChunk 中心区块位置
     * @return 如果所有区块都是樱花林且地形平坦则返回true，否则返回false
     */
    private boolean isValidGenerationLocation(GenerationContext context, ChunkPos centerChunk) {
        List<Integer> heightSamples = new ArrayList<>();
        int range = 2;
        
        // 检查以centerChunk为中心的3*3区块范围
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                
                // 1. 检查生物群系
                int x = checkChunk.getMinBlockX() + 8;
                int z = checkChunk.getMinBlockZ() + 8;
                int y = context.chunkGenerator().getSeaLevel();
                
                Holder<Biome> biome = context.biomeSource().getNoiseBiome(
                        x >> 2, y >> 2, z >> 2, context.randomState().sampler()
                );
                
                // 如果不是樱花林生物群系，返回false
                if (!biome.is(Biomes.CHERRY_GROVE)) {
                    return false;
                }
                
                // 2. 收集高度样本用于地形平坦度检测
                List<Integer> chunkHeightSamples = sampleChunkHeights(context, checkChunk);
                heightSamples.addAll(chunkHeightSamples);
            }
        }
        
        // 3. 检查地形平坦度
        return isTerrainFlat(heightSamples);
    }
    
    /**
     * 在指定区块内随机采样高度
     * @param context 生成上下文
     * @param chunk 目标区块
     * @return 采样点的高度列表
     */
    private List<Integer> sampleChunkHeights(GenerationContext context, ChunkPos chunk) {
        List<Integer> heights = new ArrayList<>();
        
        // 创建基于区块坐标的确定性随机源，确保结果一致
        RandomSource random = RandomSource.create(chunk.toLong());
        
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        
        for (int i = 0; i < SAMPLES_PER_CHUNK; i++) {
            // 在区块内随机选择一个位置
            int x = minX + random.nextInt(16);
            int z = minZ + random.nextInt(16);
            
            // 获取该位置的地面高度
            int height = context.chunkGenerator().getFirstOccupiedHeight(
                    x, z, 
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 
                    context.heightAccessor(), 
                    context.randomState()
            );
            
            heights.add(height);
        }
        
        return heights;
    }
    
    /**
     * 检查地形是否足够平坦
     * @param heights 所有采样点的高度
     * @return 如果地形平坦度符合要求则返回true
     */
    private boolean isTerrainFlat(List<Integer> heights) {
        if (heights.size() < 2) {
            return true; // 样本过少，认为是平坦的
        }
        
        // 计算平均高度
        double sum = 0;
        for (int height : heights) {
            sum += height;
        }
        double mean = sum / heights.size();
        this.height = (int) mean;
        
        // 计算方差
        double varianceSum = 0;
        for (int height : heights) {
            double diff = height - mean;
            varianceSum += diff * diff;
        }
        double variance = varianceSum / heights.size();
        
        // 检查方差是否在可接受范围内
        return variance <= MAX_TERRAIN_VARIANCE;
    }
}
