package com.github.yimeng261.maidspell.player;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
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

    /** 全服的锚定记录：在线玩家以内存为准，离线玩家读存档。 */
    public static Map<UUID, LevelAndChunkPos> collectAll(MinecraftServer server) {
        Map<UUID, LevelAndChunkPos> records = new HashMap<>();
        Set<String> onlineFiles = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            records.putAll(player.getData(ATTACHMENT_TYPE).maidChunks());
            onlineFiles.add(player.getStringUUID() + ".dat");
        }
        File[] files = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile()
                .listFiles((dir, name) -> name.endsWith(".dat") && !onlineFiles.contains(name));
        if (files != null) {
            for (File file : files) {
                try {
                    records.putAll(readFromPlayerTag(NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap())));
                } catch (IOException e) {
                    MaidSpellMod.LOGGER.warn("读取玩家存档中的锚定记录失败: {}", file.getName(), e);
                }
            }
        }
        return records;
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
