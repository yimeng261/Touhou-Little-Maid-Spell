package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import java.util.function.Function;

/**
 * 归隐之地维度工具类
 * 职责：
 * 1. 提供维度ResourceKey生成工具方法
 * 2. 提供玩家传送功能（传送到/从归隐之地）
 * 3. 提供维度状态检查方法
 * 4. 提供安全传送位置查找功能
 * 维度特性：
 * 1. 坐标与主世界完全相同
 * 2. 单一樱花林群系
 * 3. 每个玩家一个独立维度
 * 4. 只生成一个隐世之境结构
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class TheRetreatDimension {
    /**
     * 获取玩家专属的归隐之地维度ResourceKey
     * 格式：touhou_little_maid_spell:the_retreat_<player_uuid>
     */
    @SuppressWarnings("removal")
    public static ResourceKey<Level> getPlayerRetreatDimension(UUID playerUUID) {
        return ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(MaidSpellMod.MOD_ID, "the_retreat_" + playerUUID.toString().replace("-", "_"))
        );
    }
    
    
    /**
     * 将玩家传送到归隐之地（同步版本，修复死锁问题）
     * changeDimension 方法会自动处理区块加载，无需手动异步加载
     * @param player 要传送的玩家
     */
    public static void teleportToRetreat(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 使用PlayerRetreatManager统一获取或创建维度
        ServerLevel retreatLevel = PlayerRetreatManager.getOrCreatePlayerRetreat(server, player.getUUID());

        if (retreatLevel == null) {
            MaidSpellMod.LOGGER.error("Failed to get or create retreat dimension for player: {}", player.getName().getString());
            return;
        }

        try {
            // 使用玩家当前坐标作为传送目标（私人和共享模式都一样）
            BlockPos targetPos = player.blockPosition();

            // 查找安全位置（changeDimension会自动加载区块）
            BlockPos safePos = findSafePosition(retreatLevel, targetPos);

            // 使用自定义传送器传送玩家
            player.changeDimension(retreatLevel, new RetreatTeleporter(safePos));

            // 设置玩家在隐世之境的重生点
            player.setRespawnPosition(retreatLevel.dimension(), safePos, 0.0f, true, false);

            MaidSpellMod.LOGGER.info("Teleported player {} to retreat dimension at {} and set respawn point",
                player.getName().getString(), safePos);
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Error during teleportation for player {}: {}",
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * 将玩家从归隐之地传送回主世界（同步版本，修复死锁问题）
     * changeDimension 方法会自动处理区块加载，无需手动异步加载
     * @param player 要传送的玩家
     */
    public static void teleportFromRetreat(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        try {
            BlockPos targetPos = player.blockPosition();

            // 查找安全位置（changeDimension会自动加载区块）
            BlockPos safePos = findSafePosition(overworld, targetPos);

            // 使用自定义传送器传送玩家
            player.changeDimension(overworld, new RetreatTeleporter(safePos));

            MaidSpellMod.LOGGER.info("Teleported player {} from retreat dimension to overworld at {}",
                player.getName().getString(), safePos);
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Error during teleportation from retreat for player {}: {}",
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * 检查玩家是否在归隐之地维度
     */
    public static boolean isInRetreat(Entity entity) {
        ResourceLocation dimensionLocation = entity.level().dimension().location();
        return dimensionLocation.getNamespace().equals(MaidSpellMod.MOD_ID) 
            && dimensionLocation.getPath().startsWith("the_retreat");
    }
    
    /**
     * 检查玩家是否在自己的归隐之地
     */
    public static boolean isInOwnRetreat(ServerPlayer player) {
        ResourceKey<Level> playerRetreat = getPlayerRetreatDimension(player.getUUID());
        return player.level().dimension().equals(playerRetreat);
    }
    /**
     * 查找安全的传送位置
     * changeDimension 会自动加载区块，因此这里可以直接访问方块状态
     * 确保玩家不会卡在方块里或掉入虚空
     */
    private static BlockPos findSafePosition(ServerLevel level, BlockPos targetPos) {
        // 二次验证区块是否已加载
        if (!level.hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
            MaidSpellMod.LOGGER.warn("Chunk still not loaded at {}, using default safe height", targetPos);
            // 使用默认安全高度
            int safeY = Math.max(level.getSeaLevel() + 5, 100);
            return createSafePlatform(level, new BlockPos(targetPos.getX(), safeY, targetPos.getZ()));
        }
        
        // 限制搜索范围，避免过度迭代
        int maxY = Math.min(level.getMaxBuildHeight() - 1, 319);
        int minY = Math.max(level.getMinBuildHeight(), -64);
        
        // 从高处向下搜索第一个固体方块
        try {
            for(int y = maxY; y >= minY; y--) {
                BlockPos pos = new BlockPos(targetPos.getX(), y, targetPos.getZ());
                if(level.getBlockState(pos).isSolid()) {
                    BlockPos safePos = pos.above();
                    MaidSpellMod.LOGGER.debug("Found safe position at {}", safePos);
                    return safePos;
                }
            }
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Error while finding safe position at {}: {}", targetPos, e.getMessage(), e);
        }
        
        // 如果没有找到固体方块，在合理高度创建安全平台
        int platformY = Math.max(level.getSeaLevel() + 5, 100);
        BlockPos platformPos = new BlockPos(targetPos.getX(), platformY, targetPos.getZ());
        MaidSpellMod.LOGGER.info("No safe ground found, creating platform at {}", platformPos);
        return createSafePlatform(level, platformPos);
    }

    
    /**
     * 创建安全平台
     */
    private static BlockPos createSafePlatform(ServerLevel level, BlockPos pos) {
        // 在目标位置下方创建一个小平台
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos platformPos = pos.offset(x, -1, z);
                level.setBlock(platformPos, Blocks.STONE.defaultBlockState(), 3);
            }
        }
        
        // 清理上方的方块
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        
        return pos;
    }

    /**
     * 自定义传送器，用于精确控制传送位置
     */
    public record RetreatTeleporter(BlockPos targetPos) implements ITeleporter {

    @Override
        public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld,
                                  float yaw, Function<Boolean, Entity> repositionEntity) {
            // 重新定位实体到目标位置
            Entity repositioned = repositionEntity.apply(false);
            if (repositioned != null) {
                repositioned.moveTo(
                        targetPos.getX() + 0.5,
                        targetPos.getY(),
                        targetPos.getZ() + 0.5,
                        yaw,
                        entity.getXRot()
                );
            }
            return repositioned;
        }

    @Override
        public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld,
                                        Function<ServerLevel, PortalInfo> defaultPortalInfo) {
            return new PortalInfo(
                    new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5),
                    Vec3.ZERO,
                    entity.getYRot(),
                    entity.getXRot()
            );
        }
    }
    
    /**
     * 监听维度卸载事件，清理资源
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ResourceLocation dimensionLocation = serverLevel.dimension().location();
            if (dimensionLocation.getNamespace().equals(MaidSpellMod.MOD_ID) 
                && dimensionLocation.getPath().startsWith("the_retreat_")) {
                MaidSpellMod.LOGGER.info("Unloading retreat dimension: " + dimensionLocation);
            }
        }
    }
}

