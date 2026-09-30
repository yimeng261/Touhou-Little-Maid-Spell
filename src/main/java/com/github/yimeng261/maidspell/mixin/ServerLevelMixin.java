package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.accessor.RandomStateAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Mixin到ServerLevel，在创建时自动为RandomState设置维度信息
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    /**
     * 在ServerLevel构造函数结束时，为RandomState设置维度信息
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onServerLevelInit(
            MinecraftServer server,
            Executor executor,
            LevelStorageSource.LevelStorageAccess storage,
            ServerLevelData levelData,
            ResourceKey<Level> dimension,
            LevelStem levelStem,
            ChunkProgressListener progressListener,
            boolean isDebug,
            long seed,
            List<?> spawners,
            boolean shouldTickTime,
            RandomSequences randomSequences,
            CallbackInfo ci
    ) {
        ServerLevel level = (ServerLevel) (Object) this;

        // RandomState 每个维度一份，ChunkGenerator 可能被多个维度共用，维度信息只能记在前者上
        RandomStateAccessor accessor = (RandomStateAccessor) (Object) level.getChunkSource().randomState();
        accessor.maidspell$setDimensionKey(dimension);
        MaidSpellMod.LOGGER.debug("Initialized RandomState with dimension key: {}", dimension.location());
    }
}

