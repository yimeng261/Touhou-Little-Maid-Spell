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

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Function;

/**
 * 归隐之地维度工具类
 * 
 * 职责：
 * 1. 提供维度ResourceKey生成工具方法
 * 2. 提供玩家传送功能（传送到/从归隐之地）
 * 3. 提供维度状态检查方法
 * 4. 提供安全传送位置查找功能
 * 
 * 注意：维度的创建、删除、生命周期管理由 PlayerRetreatManager 负责
 * 
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
     * 将玩家传送到归隐之地
     * @param player 要传送的玩家
     * @param targetPos 目标位置（如果为null则使用玩家当前位置）
     */
    public static void teleportToRetreat(ServerPlayer player, @Nullable BlockPos targetPos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        // 使用PlayerRetreatManager统一获取或创建维度
        ServerLevel retreatLevel = PlayerRetreatManager.getOrCreatePlayerRetreat(server, player.getUUID());
        
        if (retreatLevel == null) {
            MaidSpellMod.LOGGER.error("Failed to get or create retreat dimension for player: {}", player.getName().getString());
            return;
        }
        
        // 确定传送位置
        BlockPos finalPos = targetPos != null ? targetPos : player.blockPosition();
        
        // 确保目标位置是安全的
        BlockPos safePos = findSafePosition(retreatLevel, finalPos);
        
        // 使用自定义传送器传送玩家
        player.changeDimension(retreatLevel, new RetreatTeleporter(safePos));
        
        MaidSpellMod.LOGGER.info("Teleported player {} to retreat dimension at {}", 
            player.getName().getString(), safePos);
    }
    
    /**
     * 将玩家从归隐之地传送回主世界
     * @param player 要传送的玩家
     * @param targetPos 目标位置（如果为null则使用玩家在归隐之地的位置）
     */
    public static void teleportFromRetreat(ServerPlayer player, @Nullable BlockPos targetPos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        
        // 确定传送位置
        BlockPos finalPos = targetPos != null ? targetPos : player.blockPosition();
        
        // 确保目标位置是安全的
        BlockPos safePos = findSafePosition(overworld, finalPos);
        
        // 使用自定义传送器传送玩家
        player.changeDimension(overworld, new RetreatTeleporter(safePos));
        
        MaidSpellMod.LOGGER.info("Teleported player {} from retreat dimension to overworld at {}", 
            player.getName().getString(), safePos);
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
     * 确保玩家不会卡在方块里或掉入虚空
     */
    private static BlockPos findSafePosition(ServerLevel level, BlockPos targetPos) {
        // 如果目标位置已经安全，直接返回
        if (isSafePosition(level, targetPos)) {
            return targetPos;
        }
        
        // 向上搜索安全位置（最多20格）
        for (int y = 0; y <= 20; y++) {
            BlockPos upPos = targetPos.above(y);
            if (isSafePosition(level, upPos)) {
                return upPos;
            }
        }
        
        // 向下搜索安全位置（最多10格）
        for (int y = 1; y <= 10; y++) {
            BlockPos downPos = targetPos.below(y);
            if (isSafePosition(level, downPos)) {
                return downPos;
            }
        }
        
        // 如果都不安全，在目标位置上方创建一个安全平台
        return createSafePlatform(level, targetPos.above(2));
    }
    
    /**
     * 检查位置是否安全
     */
    private static boolean isSafePosition(ServerLevel level, BlockPos pos) {
        // 检查脚下有方块（使用isSolidRender代替已弃用的isSolid）
        if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
            return false;
        }
        
        // 检查头部和身体位置是否有空气
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
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
    private record RetreatTeleporter(BlockPos targetPos) implements ITeleporter {

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

