package com.github.yimeng261.maidspell.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 镇石方块实体，用于追踪全球范围内的镇石位置以阻止敌对生物生成
 */
public class SuppressionStoneBlockEntity extends BlockEntity {

    /** 按维度存储镇石位置，支持快速查找 */
    private static final Map<ResourceLocation, Set<BlockPos>> ACTIVE_STONES = new ConcurrentHashMap<>();

    /** 检测范围：以镇石所在区块为中心，向四周各延伸 CHUNK_RANGE 个区块 */
    private static final int CHUNK_RANGE = 3;

    public SuppressionStoneBlockEntity(BlockPos pos, BlockState state) {
        super(MaidSpellBlockEntities.SUPPRESSION_STONE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            ResourceLocation dimKey = level.dimension().location();
            ACTIVE_STONES.computeIfAbsent(dimKey, k -> ConcurrentHashMap.newKeySet()).add(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        // 必须在 super.setRemoved() 之前保存引用，super 会将 level 置为 null
        Level currentLevel = level;
        BlockPos currentPos = worldPosition;
        super.setRemoved();
        if (currentLevel != null && !currentLevel.isClientSide()) {
            ResourceLocation dimKey = currentLevel.dimension().location();
            Set<BlockPos> positions = ACTIVE_STONES.get(dimKey);
            if (positions != null) {
                positions.remove(currentPos);
                if (positions.isEmpty()) {
                    ACTIVE_STONES.remove(dimKey);
                }
            }
        }
    }

    /**
     * 检查给定位置是否在任何镇石的区块压制范围内
     */
    public static boolean isWithinSuppressionRange(LevelAccessor levelAccessor, BlockPos pos) {
        Level level;
        if (levelAccessor instanceof Level l) {
            level = l;
        } else if (levelAccessor instanceof ServerLevelAccessor sla) {
            level = sla.getLevel();
        } else {
            return false;
        }
        ResourceLocation dimKey = level.dimension().location();
        Set<BlockPos> positions = ACTIVE_STONES.get(dimKey);
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        int targetChunkX = pos.getX() >> 4;
        int targetChunkZ = pos.getZ() >> 4;
        for (BlockPos stonePos : positions) {
            int stoneChunkX = stonePos.getX() >> 4;
            int stoneChunkZ = stonePos.getZ() >> 4;
            if (Math.abs(targetChunkX - stoneChunkX) <= CHUNK_RANGE
                && Math.abs(targetChunkZ - stoneChunkZ) <= CHUNK_RANGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前已加载的镇石数量（调试用）
     */
    public static int getActiveStoneCount(ResourceLocation dimKey) {
        Set<BlockPos> positions = ACTIVE_STONES.get(dimKey);
        return positions == null ? 0 : positions.size();
    }
}
