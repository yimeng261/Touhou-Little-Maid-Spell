package com.github.yimeng261.maidspell.item.common.WindSeekingBell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * 生物群系验证器
 * 负责检查区块是否符合樱花林生物群系要求
 */
public class BiomeValidator {
    
    /**
     * 检查潜在的结构中心点是否符合樱花林要求
     * 验证3×3区域的中心、四角和边中点是否都是樱花林生物群系
     * 
     * @param level 服务器世界
     * @param centerChunk 中心区块
     * @return 如果符合要求返回true，否则返回false
     */
    public boolean validateCherryGroveRegion(ServerLevel level, ChunkPos centerChunk) {
        // 1. 首先检查中心是否为樱花林（类似Main算法检查中心格子）
        if (!isCherryGroveChunk(level, centerChunk)) {
            return false;
        }
        
        // 2. 检查3×3区域的四个角落（类似Main算法检查四个角）
        ChunkPos[] corners = {
            new ChunkPos(centerChunk.x - SearchConfig.CHERRY_GROVE_CHECK_RADIUS, centerChunk.z - SearchConfig.CHERRY_GROVE_CHECK_RADIUS), // 左上
            new ChunkPos(centerChunk.x - SearchConfig.CHERRY_GROVE_CHECK_RADIUS, centerChunk.z + SearchConfig.CHERRY_GROVE_CHECK_RADIUS), // 左下  
            new ChunkPos(centerChunk.x + SearchConfig.CHERRY_GROVE_CHECK_RADIUS, centerChunk.z - SearchConfig.CHERRY_GROVE_CHECK_RADIUS), // 右上
            new ChunkPos(centerChunk.x + SearchConfig.CHERRY_GROVE_CHECK_RADIUS, centerChunk.z + SearchConfig.CHERRY_GROVE_CHECK_RADIUS)  // 右下
        };
        
        for (ChunkPos corner : corners) {
            if (!isCherryGroveChunk(level, corner)) {
                return false; // 角落不是樱花林，不符合条件
            }
        }
        
        // 3. 检查3×3区域的边中点（类似Main算法检查边中点）
        ChunkPos[] edges = {
            new ChunkPos(centerChunk.x - SearchConfig.CHERRY_GROVE_CHECK_RADIUS, centerChunk.z), // 左边
            new ChunkPos(centerChunk.x + SearchConfig.CHERRY_GROVE_CHECK_RADIUS, centerChunk.z), // 右边
            new ChunkPos(centerChunk.x, centerChunk.z - SearchConfig.CHERRY_GROVE_CHECK_RADIUS), // 上边
            new ChunkPos(centerChunk.x, centerChunk.z + SearchConfig.CHERRY_GROVE_CHECK_RADIUS)  // 下边
        };
        
        for (ChunkPos edge : edges) {
            if (!isCherryGroveChunk(level, edge)) {
                return false; // 边中点不是樱花林，不符合条件
            }
        }
        
        // 所有检查通过
        return true;
    }
    
    /**
     * 快速检查区块是否为樱花林生物群系
     * 
     * @param level 服务器世界
     * @param chunk 要检查的区块
     * @return 如果是樱花林返回true，否则返回false
     */
    public boolean isCherryGroveChunk(ServerLevel level, ChunkPos chunk) {
        try {
            // 只检查区块中心点的生物群系（使用预定义常量）
            int x = chunk.getMinBlockX() + SearchConfig.CHUNK_CENTER_OFFSET;
            int z = chunk.getMinBlockZ() + SearchConfig.CHUNK_CENTER_OFFSET;
            int y = level.getSeaLevel();
            
            Holder<Biome> biome = level.getBiome(new BlockPos(x, y, z));
            return biome.is(Biomes.CHERRY_GROVE);
        } catch (Exception e) {
            // 如果出现异常，保守处理返回false
            return false;
        }
    }
    
    /**
     * 批量检查多个区块是否都是樱花林
     * 
     * @param level 服务器世界
     * @param chunks 要检查的区块数组
     * @return 如果所有区块都是樱花林返回true，否则返回false
     */
    public boolean areAllCherryGrove(ServerLevel level, ChunkPos[] chunks) {
        for (ChunkPos chunk : chunks) {
            if (!isCherryGroveChunk(level, chunk)) {
                return false;
            }
        }
        return true;
    }
}
