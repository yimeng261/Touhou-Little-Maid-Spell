package com.github.yimeng261.maidspell.player;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 玩家的女仆强加载区块
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-11-14 22:11
 */
public record ChunkLoadingData(Map<UUID, LevelAndChunkPos> maidChunks) {
    private static final Supplier<ChunkLoadingData> DEFAULT_FACTORY = () -> new ChunkLoadingData(new HashMap<>());

    private static final Codec<ChunkLoadingData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, LevelAndChunkPos.CODEC).fieldOf("maidChunks").forGetter(ChunkLoadingData::maidChunks)
            ).apply(instance, instance.stable(map -> new ChunkLoadingData(new HashMap<>(map)))));

    public static final ResourceLocation ATTACHMENT_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid-chunks");

    public static final AttachmentType<ChunkLoadingData> ATTACHMENT_TYPE = AttachmentType.builder(ChunkLoadingData.DEFAULT_FACTORY)
            .serialize(ChunkLoadingData.CODEC)
            .copyOnDeath() // 切换维度、重新创建实体时复制
            // 没有 sync 处理器，不给客户端同步
            .build();

    /** 从玩家存档 NBT 读出锚定记录；没有或解析失败时为空。 */
    public static Map<UUID, LevelAndChunkPos> readFromPlayerTag(CompoundTag playerTag) {
        CompoundTag attachment = playerTag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                .getCompound(ATTACHMENT_ID.toString());
        return CODEC.parse(NbtOps.INSTANCE, attachment).result()
                .map(ChunkLoadingData::maidChunks)
                .orElseGet(Map::of);
    }

    /** 离线玩家存档里的锚定记录，每次开服只读一遍，之后随登录、下线更新 */
    private static final Map<UUID, Map<UUID, LevelAndChunkPos>> OFFLINE_RECORDS = new HashMap<>();
    private static boolean offlineRecordsLoaded;

    /** 全服的锚定记录（女仆 → 位置）：在线玩家以内存为准，离线玩家读存档。 */
    public static Map<UUID, LevelAndChunkPos> collectAll(MinecraftServer server) {
        Map<UUID, LevelAndChunkPos> records = new HashMap<>();
        collectAllByOwner(server).values().forEach(records::putAll);
        return records;
    }

    /** 全服的锚定记录，按主人分组。 */
    public static Map<UUID, Map<UUID, LevelAndChunkPos>> collectAllByOwner(MinecraftServer server) {
        loadOfflineRecords(server);
        Map<UUID, Map<UUID, LevelAndChunkPos>> records = new HashMap<>();
        OFFLINE_RECORDS.forEach((owner, maids) -> records.put(owner, new HashMap<>(maids)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            records.put(player.getUUID(), new HashMap<>(player.getData(ATTACHMENT_TYPE).maidChunks()));
        }
        records.values().removeIf(Map::isEmpty);
        return records;
    }

    private static void loadOfflineRecords(MinecraftServer server) {
        if (!offlineRecordsLoaded) {
            OfflinePlayerDataScan.scan(server);
        }
    }

    /**
     * 收下离线玩家存档里读出的锚定记录，并在这时扣掉待删的记录；之后缓存里不会再有待删项，
     * 离线期间的删除由 {@link #removeRecord} 同时改缓存和待删表。
     */
    static void acceptOfflineRecords(MinecraftServer server, Map<UUID, Map<UUID, LevelAndChunkPos>> fromPlayerData) {
        offlineRecordsLoaded = true;
        fromPlayerData.forEach((owner, maids) -> OFFLINE_RECORDS.put(owner, new HashMap<>(maids)));
        AnchorPendingRemovalData.get(server).forEach((owner, maids) -> {
            Map<UUID, LevelAndChunkPos> cached = OFFLINE_RECORDS.get(owner);
            if (cached != null) {
                cached.keySet().removeAll(maids);
            }
        });
        OFFLINE_RECORDS.values().removeIf(Map::isEmpty);
    }

    /** 主人登录：删掉离线期间记下的待删记录，之后以他身上的记录为准。 */
    public static void onOwnerLogin(ServerPlayer player) {
        OFFLINE_RECORDS.remove(player.getUUID());
        Set<UUID> removed = AnchorPendingRemovalData.get(player.server).take(player.getUUID());
        if (!removed.isEmpty()) {
            player.getData(ATTACHMENT_TYPE).maidChunks().keySet().removeAll(removed);
        }
    }

    /** 主人下线：他身上的记录随存档保存，缓存里记一份。 */
    public static void onOwnerLogout(ServerPlayer player) {
        if (!offlineRecordsLoaded) {
            return;
        }
        Map<UUID, LevelAndChunkPos> maids = player.getData(ATTACHMENT_TYPE).maidChunks();
        if (maids.isEmpty()) {
            OFFLINE_RECORDS.remove(player.getUUID());
        } else {
            OFFLINE_RECORDS.put(player.getUUID(), new HashMap<>(maids));
        }
    }

    /** 删掉一条锚定记录；主人不在线时先记为待删，等他登录再删。 */
    public static void removeRecord(MinecraftServer server, UUID owner, UUID maid) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            player.getData(ATTACHMENT_TYPE).maidChunks().remove(maid);
            return;
        }
        loadOfflineRecords(server);
        Map<UUID, LevelAndChunkPos> cached = OFFLINE_RECORDS.get(owner);
        if (cached == null || cached.remove(maid) == null) {
            return;
        }
        AnchorPendingRemovalData.get(server).add(owner, maid);
    }

    public static void clearSessionCache() {
        OFFLINE_RECORDS.clear();
        offlineRecordsLoaded = false;
        OfflinePlayerDataScan.clear();
    }

    public record LevelAndChunkPos(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        public static final Codec<LevelAndChunkPos> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        Level.RESOURCE_KEY_CODEC.fieldOf("levelKey").forGetter(LevelAndChunkPos::levelKey),
                        Codec.INT.fieldOf("chunkX").forGetter(LevelAndChunkPos::chunkX),
                        Codec.INT.fieldOf("chunkZ").forGetter(LevelAndChunkPos::chunkZ)
                ).apply(instance, instance.stable(LevelAndChunkPos::new)));
    }
}
