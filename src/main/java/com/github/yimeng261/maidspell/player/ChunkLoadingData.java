package com.github.yimeng261.maidspell.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.HashMap;
import java.util.Map;
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

    public static final AttachmentType<ChunkLoadingData> ATTACHMENT_TYPE = AttachmentType.builder(ChunkLoadingData.DEFAULT_FACTORY)
            .serialize(ChunkLoadingData.CODEC)
            .copyOnDeath() // 切换维度、重新创建实体时复制
            // 没有 sync 处理器，不给客户端同步
            .build();

    public record LevelAndChunkPos(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        public static final Codec<LevelAndChunkPos> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        Level.RESOURCE_KEY_CODEC.fieldOf("levelKey").forGetter(LevelAndChunkPos::levelKey),
                        Codec.INT.fieldOf("chunkX").forGetter(LevelAndChunkPos::chunkX),
                        Codec.INT.fieldOf("chunkZ").forGetter(LevelAndChunkPos::chunkZ)
                ).apply(instance, instance.stable(LevelAndChunkPos::new)));
    }
}
