package com.github.yimeng261.maidspell.item.bauble.anchorCore;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import com.github.yimeng261.maidspell.player.ChunkLoadingData;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 锚定核心饰品逻辑
 * 为女仆提供全面保护，防止被其他模组影响
 */
public class AnchorCoreBauble implements IMaidBauble {
    private final Map<UUID, Pair<ServerLevel, ChunkPos>> maidLastKnownChunkPos = new HashMap<>();

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) {
            return;
        }
        clearInvalidMaidChunk();

        AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
        anchoredEntityMaid.maidSpell$setAnchored(true);

        ServerLevel serverLevel = (ServerLevel) maid.level();
        ChunkLoadingData chunkLoadingData = Optional.ofNullable(maid.getOwner())
                .map(o -> o.getData(ChunkLoadingData.ATTACHMENT_TYPE))
                .orElse(null);
        var currentLevelAndChunkPos = Pair.of(serverLevel, maid.chunkPosition());
        var levelAndChunkPos = maidLastKnownChunkPos.get(maid.getUUID());
        if (!currentLevelAndChunkPos.equals(levelAndChunkPos)) {
            if (levelAndChunkPos != null) {
                Global.LOGGER.debug("取消加载女仆 {} 上次停留区块 {}", maid.getUUID(), levelAndChunkPos.second());
                levelAndChunkPos.first().getChunkSource().updateChunkForced(levelAndChunkPos.second(), false);
//                ChunkLoadingData.unloadChunk(serverLevel, maid.getUUID(), maid.chunkPosition());
            }
            if (chunkLoadingData != null) {
                Global.LOGGER.debug("强制加载女仆 {} 位置更新区块 {}", maid.getUUID(), maid.chunkPosition());
                serverLevel.getChunkSource().updateChunkForced(maid.chunkPosition(), true);
//                ChunkLoadingData.loadChunk(serverLevel, maid.getUUID(), maid.chunkPosition());
                maidLastKnownChunkPos.put(maid.getUUID(), currentLevelAndChunkPos);
                var chunkPosMap = chunkLoadingData.maidChunks();
                chunkPosMap.put(maid.getUUID(), new ChunkLoadingData.LevelAndChunkPos(serverLevel.dimension(), maid.chunkPosition().x, maid.chunkPosition().z));
            }
        }

    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) {
            return;
        }
        AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
        anchoredEntityMaid.maidSpell$setAnchored(false);
        Optional.ofNullable(maid.getOwner())
                .map(o -> o.getData(ChunkLoadingData.ATTACHMENT_TYPE))
                .ifPresent(data -> {
                    data.maidChunks().remove(maid.getUUID());
                });
        var levelAndChunkPos = maidLastKnownChunkPos.get(maid.getUUID());
        if (levelAndChunkPos != null) {
            Global.LOGGER.debug("取消加载女仆 {} 强加载区块 {}", maid.getUUID(), levelAndChunkPos.second());
            levelAndChunkPos.first().getChunkSource().updateChunkForced(levelAndChunkPos.second(), false);
        }
    }

    private void clearInvalidMaidChunk() {
        for (var iterator = maidLastKnownChunkPos.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var maidId = entry.getKey();
            var levelAndChunkPos = entry.getValue();
            var level = levelAndChunkPos.first();
            Entity entity = level.getEntity(maidId);
            if (entity == null) {
                iterator.remove();
            }
        }
    }

}
