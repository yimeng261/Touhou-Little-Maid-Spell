package com.github.yimeng261.maidspell.player;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 离线玩家的存档每次开服只读一遍：锚定记录交给 {@link ChunkLoadingData}，所在维度留给归隐之地的开服预载。
 * <p>
 * 谁先用到谁触发读取：读档恢复持久票据时的锚定校验，或开服预载归隐之地。
 */
public final class OfflinePlayerDataScan {
    /** 单个存档读进来的字节上限：只统计取出的两项，跳过的部分既不分配也不计数；嵌套深度按原版上限 */
    private static final long MAX_COLLECTED_BYTES = 8L * 1024 * 1024;

    /** 离线玩家存档里记的所在维度；null 表示本次开服还没读完，锚定记录也还没交给 {@link ChunkLoadingData} */
    @Nullable
    private static Map<UUID, ResourceKey<Level>> playerDimensions;

    private OfflinePlayerDataScan() {
    }

    /** 离线玩家存档里记的所在维度（只读） */
    public static synchronized Map<UUID, ResourceKey<Level>> playerDimensions(MinecraftServer server) {
        scan(server);
        return Collections.unmodifiableMap(playerDimensions);
    }

    /** 本次开服是否已读完，离线锚定记录已交给 {@link ChunkLoadingData} */
    static synchronized boolean isScanned() {
        return playerDimensions != null;
    }

    /** 已读完时直接返回；中途抛异常则保持未读完，下次调用重读 */
    static synchronized void scan(MinecraftServer server) {
        if (playerDimensions != null) {
            return;
        }
        Map<UUID, ResourceKey<Level>> dimensions = new LinkedHashMap<>();
        Map<UUID, Map<UUID, ChunkLoadingData.LevelAndChunkPos>> anchors = new HashMap<>();
        File[] files = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile()
                .listFiles((dir, name) -> name.endsWith(".dat"));
        for (File file : files == null ? new File[0] : files) {
            UUID player = parsePlayerUuid(file.getName());
            if (player == null || server.getPlayerList().getPlayer(player) != null) {
                continue;
            }
            CompoundTag tag = readNeededFields(file);
            if (tag == null) {
                continue;
            }
            ResourceKey<Level> dimension = parseDimensionKey(tag);
            if (dimension != null) {
                dimensions.put(player, dimension);
            }
            Map<UUID, ChunkLoadingData.LevelAndChunkPos> maids = ChunkLoadingData.readFromPlayerTag(tag);
            if (!maids.isEmpty()) {
                anchors.put(player, maids);
            }
        }
        ChunkLoadingData.acceptOfflineRecords(server, anchors);
        playerDimensions = dimensions;
    }

    static synchronized void clear() {
        playerDimensions = null;
    }

    /** 只读出所在维度和锚定记录两项；存档损坏时跳过这个玩家，不影响开服 */
    @Nullable
    private static CompoundTag readNeededFields(File file) {
        CollectFields collector = new CollectFields(
                new FieldSelector(StringTag.TYPE, "Dimension"),
                new FieldSelector(AttachmentHolder.ATTACHMENTS_NBT_KEY, CompoundTag.TYPE, ChunkLoadingData.ATTACHMENT_ID.toString()));
        try {
            NbtIo.parseCompressed(file.toPath(), collector, NbtAccounter.create(MAX_COLLECTED_BYTES));
        } catch (Exception e) {
            // 与原版读玩家存档一致：截断、深度超限等也会以运行时异常抛出
            MaidSpellMod.LOGGER.warn("读取玩家存档失败，跳过: {}", file.getName(), e);
            return null;
        }
        Tag result = collector.getResult();
        return result instanceof CompoundTag tag ? tag : null;
    }

    @Nullable
    private static UUID parsePlayerUuid(String fileName) {
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - ".dat".length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static ResourceKey<Level> parseDimensionKey(CompoundTag tag) {
        if (!tag.contains("Dimension")) {
            return null;
        }
        return DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, tag.get("Dimension")))
                .resultOrPartial(MaidSpellMod.LOGGER::error)
                .orElse(null);
    }
}
