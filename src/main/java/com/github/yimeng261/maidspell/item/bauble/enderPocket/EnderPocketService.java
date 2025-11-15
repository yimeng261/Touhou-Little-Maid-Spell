package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 末影腰包服务类 - 统一管理所有enderPocket相关逻辑
 */
public class EnderPocketService {

    /**
     * 末影腰包女仆信息
     */
    public static class EnderPocketMaidInfo {
        public final UUID maidUUID;
        public final String maidName;
        public final ResourceKey<Level> levelKey;
        public final int maidEntityId;

        public static final StreamCodec<ByteBuf, EnderPocketMaidInfo> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                EnderPocketMaidInfo::getMaidUUID,
                ByteBufCodecs.STRING_UTF8,
                EnderPocketMaidInfo::getMaidName,
                ResourceKey.streamCodec(Registries.DIMENSION),
                EnderPocketMaidInfo::getLevelKey,
                ByteBufCodecs.INT,
                EnderPocketMaidInfo::getMaidEntityId,
                EnderPocketMaidInfo::new
        );

        public EnderPocketMaidInfo(UUID maidUUID, String maidName, ResourceKey<Level> levelKey, int maidEntityId) {
            this.maidUUID = maidUUID;
            this.maidName = maidName;
            this.levelKey = levelKey;
            this.maidEntityId = maidEntityId;
        }

        public UUID getMaidUUID() {
            return maidUUID;
        }

        public String getMaidName() {
            return maidName;
        }

        public int getMaidEntityId() {
            return maidEntityId;
        }

        public ResourceKey<Level> getLevelKey() {
            return levelKey;
        }
    }


    /**
     * 获取玩家所有装备末影腰包的女仆信息
     */
    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(ServerPlayer player) {
        HashMap<UUID, EntityMaid> maids = Global.maidInfos.get(player.getUUID());
        if (maids == null || maids.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnderPocketMaidInfo> enderPocketMaids = new ArrayList<>();

        for (EntityMaid maid : maids.values()) {
            if (BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET)) {
                enderPocketMaids.add(new EnderPocketMaidInfo(
                        maid.getUUID(),
                        maid.getName().getString(),
                        maid.level().dimension(),
                        maid.getId()
                ));
            }
        }

        return enderPocketMaids;
    }

    /**
     * 打开女仆背包
     */
    public static boolean openMaidInventory(ServerPlayer player, ResourceKey<Level> maidLevelKey, int maidEntityId) {
        ServerLevel maidLevel = player.server.getLevel(maidLevelKey);
        Entity entity = maidLevel.getEntity(maidEntityId);
        if (!(entity instanceof EntityMaid maid)) {
            return false;
        }

        // 检查权限
        if (!maid.isOwnedBy(player) || maid.isSleeping() || !maid.isAlive()) {
            return false;
        }

        if (!maidLevel.getChunkSource().chunkMap.getPlayersWatching(maid).contains(player)) {
            ChunkMap.TrackedEntity trackedEntity = maidLevel.getChunkSource().chunkMap.entityMap.get(maidEntityId);
            if (trackedEntity == null) {
                return false;
            }
            // 强行配对
            ServerEntity serverEntity = trackedEntity.serverEntity;
            serverEntity.addPairing(player);
        }

        // 使用车万女仆本体的GUI打开方法
        maid.openMaidGui(player, com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex.MAIN);
        return true;
    }
}
