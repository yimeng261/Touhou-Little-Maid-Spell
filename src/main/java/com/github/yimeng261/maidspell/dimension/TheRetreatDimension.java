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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class TheRetreatDimension {
    /**
     * 获取玩家专属的归隐之地维度ResourceKey
     * 格式：touhou_little_maid_spell:the_retreat_<player_uuid>
     */
    public static ResourceKey<Level> getPlayerRetreatDimension(UUID playerUUID) {
        return ResourceKey.create(
            Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat_" + playerUUID.toString().replace("-", "_"))
        );
    }


    /**
     * 将玩家传送到归隐之地（异步版本，避免区块加载导致主线程卡死）
     * @param player 要传送的玩家
     */
    public static void teleportToRetreat(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel retreatLevel = PlayerRetreatManager.getLoadedPlayerRetreat(server, player.getUUID());
        if (retreatLevel == null) {
            MaidSpellMod.LOGGER.error("Retreat dimension is not ready for player: {}", player.getName().getString());
            return;
        }
        teleportToRetreat(player, retreatLevel);
    }

    public static void teleportToRetreat(ServerPlayer player, ServerLevel retreatLevel) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        BlockPos targetPos = player.blockPosition();

        // 异步加载目标区块并查找安全位置
        loadChunkAsync(retreatLevel, targetPos).thenAccept(chunkLoaded -> {
            // 在主线程中执行传送
            server.execute(() -> {
                try {
                    // 查找安全位置
                    BlockPos safePos = findSafePositionLoaded(retreatLevel, targetPos);

                    // 使用 DimensionTransition 传送玩家（NeoForge 1.21 API）
                    DimensionTransition transition = new DimensionTransition(
                            retreatLevel, safePos.getCenter(), Vec3.ZERO,
                            player.getYRot(), player.getXRot(),
                            DimensionTransition.PLACE_PORTAL_TICKET
                    );
                    player.changeDimension(transition);

                    // 设置玩家在隐世之境的重生点
                    player.setRespawnPosition(retreatLevel.dimension(), safePos, 0.0f, true, false);

                    MaidSpellMod.LOGGER.info("Teleported player {} to retreat dimension at {} and set respawn point",
                            player.getName().getString(), safePos);
                } catch (Exception e) {
                    MaidSpellMod.LOGGER.error("Error during teleportation for player {}: {}",
                            player.getName().getString(), e.getMessage(), e);
                }
            });
        }).exceptionally(throwable -> {
            MaidSpellMod.LOGGER.error("Failed to load chunk for teleportation: {}", throwable.getMessage(), throwable);
            return null;
        });
    }

    /**
     * 将玩家从归隐之地传送回主世界（异步版本，避免区块加载导致主线程卡死）
     * @param player 要传送的玩家
     */
    public static void teleportFromRetreat(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        BlockPos targetPos = player.blockPosition();

        // 异步加载目标区块并查找安全位置
        loadChunkAsync(overworld, targetPos).thenAccept(chunkLoaded -> {
            // 在主线程中执行传送
            server.execute(() -> {
                try {
                    // 查找安全位置
                    BlockPos safePos = findSafePositionLoaded(overworld, targetPos);

                    // 使用 DimensionTransition 传送玩家（NeoForge 1.21 API）
                    DimensionTransition transition = new DimensionTransition(
                            overworld, safePos.getCenter(), Vec3.ZERO,
                            player.getYRot(), player.getXRot(),
                            DimensionTransition.PLACE_PORTAL_TICKET
                    );
                    player.changeDimension(transition);

                    MaidSpellMod.LOGGER.info("Teleported player {} from retreat dimension to overworld at {}",
                            player.getName().getString(), safePos);
                } catch (Exception e) {
                    MaidSpellMod.LOGGER.error("Error during teleportation from retreat for player {}: {}",
                            player.getName().getString(), e.getMessage(), e);
                }
            });
        }).exceptionally(throwable -> {
            MaidSpellMod.LOGGER.error("Failed to load chunk for return teleportation: {}", throwable.getMessage(), throwable);
            return null;
        });
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
     * 异步加载区块（避免主线程阻塞）
     * @param level 服务器世界
     * @param pos 目标位置
     * @return CompletableFuture，完成时返回区块是否成功加载
     */
    private static CompletableFuture<Boolean> loadChunkAsync(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);

        // 使用Minecraft的异步区块加载API
        return level.getChunkSource().getChunkFuture(
                chunkPos.x,
                chunkPos.z,
                ChunkStatus.FULL,
                true
        ).thenApply(result -> {
            // result.isSuccess()表示区块是否成功加载
            boolean success = result.isSuccess();
            if (success) {
                MaidSpellMod.LOGGER.debug("Successfully loaded chunk at {} for teleportation", chunkPos);
            } else {
                MaidSpellMod.LOGGER.warn("Failed to load chunk at {}: {}", chunkPos, result.getError());
            }
            return success;
        });
    }

    /**
     * 查找安全的传送位置（假设区块已加载）
     * 确保玩家不会卡在方块里或掉入虚空
     */
    private static BlockPos findSafePositionLoaded(ServerLevel level, BlockPos targetPos) {
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
            for (int y = maxY; y >= minY; y--) {
                BlockPos pos = new BlockPos(targetPos.getX(), y, targetPos.getZ());
                if (level.getBlockState(pos).isSolid()) {
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
     * 监听维度卸载事件，清理资源
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ResourceLocation dimensionLocation = serverLevel.dimension().location();
            if (dimensionLocation.getNamespace().equals(MaidSpellMod.MOD_ID)
                    && dimensionLocation.getPath().startsWith("the_retreat")) {
                RetreatManager.unregisterDimension(serverLevel.dimension());
                MaidSpellMod.LOGGER.info("Unloading retreat dimension: {}", dimensionLocation);
            }
        }
    }
}

