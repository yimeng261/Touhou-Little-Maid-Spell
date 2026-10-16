package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
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
    private static final String PRIVATE_RETREAT_PREFIX = "the_retreat_";
    private static final ResourceLocation SHARED_RETREAT_LOCATION =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "the_retreat");

    // 估算点比估算地表高 5 格，再留出噪声误差的余量
    private static final int MAX_GROUND_SEARCH_DEPTH = 16;

    /**
     * 获取玩家专属的归隐之地维度ResourceKey
     * 格式：touhou_little_maid_spell:the_retreat_<player_uuid>
     */
    public static ResourceKey<Level> getPlayerRetreatDimension(UUID playerUUID) {
        return ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, PRIVATE_RETREAT_PREFIX + playerUUID.toString().replace("-", "_"))
        );
    }

    /** 共享归隐之地的维度 key，同时也是 FTB Quests 里「任意归隐之地」条件使用的占位维度。 */
    public static ResourceKey<Level> getSharedRetreatDimension() {
        return ResourceKey.create(Registries.DIMENSION, SHARED_RETREAT_LOCATION);
    }

    public static boolean isRetreatDimension(ResourceLocation dimensionLocation) {
        return isSharedRetreatDimension(dimensionLocation)
                || isPrivateRetreatDimension(dimensionLocation);
    }

    /** 是否为共享归隐之地。 */
    public static boolean isSharedRetreatDimension(ResourceLocation dimensionLocation) {
        return SHARED_RETREAT_LOCATION.equals(dimensionLocation);
    }

    /** 是否为以玩家 UUID 命名的独立归隐之地，路径形如 {@code the_retreat_<下划线分隔的 uuid>}。 */
    public static boolean isPrivateRetreatDimension(ResourceLocation dimensionLocation) {
        if (!MaidSpellMod.MOD_ID.equals(dimensionLocation.getNamespace())
                || !dimensionLocation.getPath().startsWith(PRIVATE_RETREAT_PREFIX)) {
            return false;
        }

        String encodedUuid = dimensionLocation.getPath().substring(PRIVATE_RETREAT_PREFIX.length());
        if (encodedUuid.isEmpty()) {
            return false;
        }

        try {
            UUID uuid = UUID.fromString(encodedUuid.replace('_', '-'));
            return uuid.toString().replace('-', '_').equals(encodedUuid);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** 任务里写死的是共享归隐之地时，匹配所有归隐之地；其余维度仍要求完全一致。 */
    public static boolean matchesTaskDimension(ResourceLocation required, ResourceLocation actual) {
        return required.equals(actual)
                || isSharedRetreatDimension(required) && isPrivateRetreatDimension(actual);
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
        teleportToRetreat(player, retreatLevel, player.blockPosition());
    }

    public static void teleportToRetreat(ServerPlayer player, ServerLevel retreatLevel, BlockPos targetPos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

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

                    MaidSpellMod.LOGGER.info("Teleported player {} to retreat dimension at {}",
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
        return isRetreatDimension(entity.level().dimension().location());
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

        try {
            // 原坐标能站人就原地落下（例如从洞穴里传送），否则按地形噪声估算地表
            if (isSafeStandingPosition(level, targetPos)) {
                MaidSpellMod.LOGGER.debug("Found direct safe position at {}", targetPos);
                return targetPos.immutable();
            }

            DensityFunction depthFn = level.getChunkSource().randomState().router().depth();
            int estimatedY = estimateSurfaceHeight(depthFn, targetPos.getX(), targetPos.getZ(), level);
            BlockPos estimated = new BlockPos(targetPos.getX(), estimatedY, targetPos.getZ());
            BlockPos ground = findGroundBelow(level, estimated);
            if (ground != null) {
                return ground;
            }
            // 估算点落在方块里（地形被改过、有建筑）或下方没有可站的地面时按高度图取地表
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetPos.getX(), targetPos.getZ());
            BlockPos surface = new BlockPos(targetPos.getX(), surfaceY, targetPos.getZ());
            if (surfaceY > level.getMinBuildHeight() && isSafeStandingPosition(level, surface)) {
                return surface;
            }
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Error while finding safe position at {}: {}", targetPos, e.getMessage(), e);
        }

        int platformY = Math.max(level.getSeaLevel() + 5, 100);
        BlockPos platformPos = new BlockPos(targetPos.getX(), platformY, targetPos.getZ());
        MaidSpellMod.LOGGER.info("No safe ground found, creating platform at {}", platformPos);
        return createSafePlatform(level, platformPos);
    }

    /**
     * 在单列上二分 depth 噪声估算地表高度，开销 O(logH)，不扫描方块也不查高度图。
     */
    private static int estimateSurfaceHeight(DensityFunction depthFn, int x, int z, ServerLevel level) {
        int lo = level.getMinBuildHeight();
        int hi = level.getMaxBuildHeight();

        while (hi - lo > 2) {
            int mid = (lo + hi) >>> 1;
            double depthAtMid = depthFn.compute(new DensityFunction.SinglePointContext(x, mid, z));
            if (depthAtMid > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        return hi + 5;
    }

    /**
     * 从估算点往下逐格找第一个可站立的位置。估算不含峡谷、侵蚀与熔岩湖，
     * 碰到方块或流体、或超出搜索深度都视为没找到。
     */
    private static BlockPos findGroundBelow(ServerLevel level, BlockPos start) {
        for (int i = 0; i <= MAX_GROUND_SEARCH_DEPTH; i++) {
            BlockPos pos = start.below(i);
            if (!isOpenForPlayer(level, pos)) {
                return null;
            }
            if (isSafeStandingPosition(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isSafeStandingPosition(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }

        BlockPos floorPos = pos.below();
        BlockState floorState = level.getBlockState(floorPos);
        if (!floorState.isFaceSturdy(level, floorPos, Direction.UP) || isHazardousFloor(floorState)) {
            return false;
        }

        return isOpenForPlayer(level, pos) && isOpenForPlayer(level, pos.above());
    }

    private static boolean isOpenForPlayer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluidState = level.getFluidState(pos);
        return fluidState.isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isHazardousFloor(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.MAGMA_BLOCK
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.CACTUS;
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
            if (isRetreatDimension(dimensionLocation)) {
                RetreatManager.unregisterDimension(serverLevel.dimension());
                MaidSpellMod.LOGGER.info("Unloading retreat dimension: {}", dimensionLocation);
            }
        }
    }
}
