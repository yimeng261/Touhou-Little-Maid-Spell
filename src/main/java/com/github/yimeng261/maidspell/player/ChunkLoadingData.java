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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    /** 离线玩家存档里的锚定记录，每次开服只读一遍，之后随登录、下线和离线期间的变更更新 */
    private static final Map<UUID, Map<UUID, LevelAndChunkPos>> OFFLINE_RECORDS = new HashMap<>();

    /** 全服的锚定记录（女仆 → 位置）：在线玩家以内存为准，离线玩家读存档。 */
    public static Map<UUID, LevelAndChunkPos> collectAll(MinecraftServer server) {
        Map<UUID, LevelAndChunkPos> records = new HashMap<>();
        collectAllByOwner(server).values().forEach(records::putAll);
        return records;
    }

    /** 全服的锚定记录，按主人分组。 */
    public static Map<UUID, Map<UUID, LevelAndChunkPos>> collectAllByOwner(MinecraftServer server) {
        OfflinePlayerDataScan.scan(server);
        Map<UUID, Map<UUID, LevelAndChunkPos>> records = new HashMap<>();
        OFFLINE_RECORDS.forEach((owner, maids) -> records.put(owner, new HashMap<>(maids)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            records.put(player.getUUID(), new HashMap<>(player.getData(ATTACHMENT_TYPE).maidChunks()));
        }
        records.values().removeIf(Map::isEmpty);
        return records;
    }

    /**
     * 收下离线玩家存档里读出的锚定记录，并在这时应用待变更；之后缓存与待变更一致，
     * 离线期间的变更由 {@link #updateRecord}、{@link #removeRecord} 同时改缓存和待变更表。
     */
    static void acceptOfflineRecords(MinecraftServer server, Map<UUID, Map<UUID, LevelAndChunkPos>> fromPlayerData) {
        // 上次读到一半抛了异常时这里可能留有残缺的缓存
        OFFLINE_RECORDS.clear();
        fromPlayerData.forEach((owner, maids) -> OFFLINE_RECORDS.put(owner, new HashMap<>(maids)));
        AnchorPendingChangeData.get(server).forEach((owner, changes) -> {
            if (server.getPlayerList().getPlayer(owner) == null) {
                AnchorPendingChangeData.apply(changes, OFFLINE_RECORDS.computeIfAbsent(owner, key -> new HashMap<>()));
            }
        });
        OFFLINE_RECORDS.values().removeIf(Map::isEmpty);
    }

    /** 主人登录：把离线期间记下的待变更写进他身上的记录，之后以他身上的记录为准。 */
    public static void onOwnerLogin(ServerPlayer player) {
        OFFLINE_RECORDS.remove(player.getUUID());
        AnchorPendingChangeData.apply(AnchorPendingChangeData.get(player.server).take(player.getUUID()),
                player.getData(ATTACHMENT_TYPE).maidChunks());
    }

    /** 主人下线：他身上的记录随存档保存，缓存里记一份。 */
    public static void onOwnerLogout(ServerPlayer player) {
        if (!OfflinePlayerDataScan.isScanned()) {
            return;
        }
        Map<UUID, LevelAndChunkPos> maids = player.getData(ATTACHMENT_TYPE).maidChunks();
        if (maids.isEmpty()) {
            OFFLINE_RECORDS.remove(player.getUUID());
        } else {
            OFFLINE_RECORDS.put(player.getUUID(), new HashMap<>(maids));
        }
    }

    /** 一条锚定记录；主人不在线时读缓存 */
    @Nullable
    public static LevelAndChunkPos getRecord(MinecraftServer server, UUID owner, UUID maid) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            return player.getData(ATTACHMENT_TYPE).maidChunks().get(maid);
        }
        OfflinePlayerDataScan.scan(server);
        return OFFLINE_RECORDS.getOrDefault(owner, Map.of()).get(maid);
    }

    /** 记下女仆的新位置；主人不在线时先记为待变更，等他登录再写进他身上。 */
    public static void updateRecord(MinecraftServer server, UUID owner, UUID maid, LevelAndChunkPos pos) {
        changeRecord(server, owner, maid, Optional.of(pos));
    }

    /** 删掉一条锚定记录；主人不在线时先记为待变更，等他登录再删。 */
    public static void removeRecord(MinecraftServer server, UUID owner, UUID maid) {
        changeRecord(server, owner, maid, Optional.empty());
    }

    private static void changeRecord(MinecraftServer server, UUID owner, UUID maid, Optional<LevelAndChunkPos> pos) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            AnchorPendingChangeData.apply(Map.of(maid, pos), player.getData(ATTACHMENT_TYPE).maidChunks());
            return;
        }
        OfflinePlayerDataScan.scan(server);
        Map<UUID, LevelAndChunkPos> cached = OFFLINE_RECORDS.computeIfAbsent(owner, key -> new HashMap<>());
        LevelAndChunkPos previous = cached.get(maid);
        AnchorPendingChangeData.apply(Map.of(maid, pos), cached);
        if (cached.isEmpty()) {
            OFFLINE_RECORDS.remove(owner);
        }
        if (!Optional.ofNullable(previous).equals(pos)) {
            AnchorPendingChangeData.get(server).put(owner, maid, pos);
        }
    }

    public static void clearSessionCache() {
        OFFLINE_RECORDS.clear();
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
