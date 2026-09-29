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
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 末影腰包服务类 - 统一管理所有enderPocket相关逻辑
 */
public class EnderPocketService {
    public static final int MAX_MAID_INFOS = 64;

    /**
     * 末影腰包女仆信息
     */
    public static class EnderPocketMaidInfo {
        public final UUID maidUUID;
        public final String maidName;
        public final ResourceKey<Level> levelKey;
        public final int maidEntityId;

        public static final int MAX_MAID_NAME_LENGTH = 64;

        public static final StreamCodec<ByteBuf, EnderPocketMaidInfo> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                EnderPocketMaidInfo::getMaidUUID,
                ByteBufCodecs.stringUtf8(MAX_MAID_NAME_LENGTH),
                EnderPocketMaidInfo::getMaidName,
                ResourceKey.streamCodec(Registries.DIMENSION),
                EnderPocketMaidInfo::getLevelKey,
                ByteBufCodecs.INT,
                EnderPocketMaidInfo::getMaidEntityId,
                EnderPocketMaidInfo::new
        );

        public EnderPocketMaidInfo(UUID maidUUID, String maidName, ResourceKey<Level> levelKey, int maidEntityId) {
            this.maidUUID = maidUUID;
            this.maidName = truncate(maidName, MAX_MAID_NAME_LENGTH);
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

        private static String truncate(String value, int maxLength) {
            if (value == null) {
                return "";
            }
            return value.length() <= maxLength ? value : value.substring(0, maxLength);
        }
    }


    /**
     * 获取玩家所有装备末影腰包的女仆信息
     */
    public static List<EnderPocketMaidInfo> getPlayerEnderPocketMaids(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(playerUUID);
        if (maids == null || maids.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnderPocketMaidInfo> enderPocketMaids = new ArrayList<>();

        for (EntityMaid maid : maids.values()) {
            if (isEnderPocketAccessible(maid, playerUUID)) {
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
    public static boolean openMaidInventory(ServerPlayer player, UUID maidUuid) {
        if (maidUuid == null) {
            return false;
        }

        Map<UUID, EntityMaid> maids = Global.ownerMaidRegistry.get(player.getUUID());
        EntityMaid maid = maids == null ? null : maids.get(maidUuid);
        if (maid == null
                || !isEnderPocketAccessible(maid, player.getUUID())
                || maid.isSleeping()
                || !maid.isOwnedBy(player)
                || !(maid.level() instanceof ServerLevel maidLevel)) {
            return false;
        }

        if (!maidLevel.getChunkSource().chunkMap.getPlayersWatching(maid).contains(player)) {
            ChunkMap.TrackedEntity trackedEntity = maidLevel.getChunkSource().chunkMap.entityMap.get(maid.getId());
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

    /**
     * 女仆存活、归属该玩家且装备了末影腰包
     */
    private static boolean isEnderPocketAccessible(EntityMaid maid, UUID playerUUID) {
        return maid.isAlive()
                && playerUUID.equals(maid.getOwnerUUID())
                && BaubleStateManager.hasBauble(maid, MaidSpellItems.ENDER_POCKET);
    }
}
