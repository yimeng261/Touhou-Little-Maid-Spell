package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.accessor.MinecraftServerAccessor;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Mixin到MinecraftServer，添加动态创建和删除维度的功能
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantBlockableEventLoop<Runnable> implements MinecraftServerAccessor {
    
    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;
    
    @Shadow
    @Final
    private Executor executor;
    
    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;
    
    @Final
    @Shadow
    private LayeredRegistryAccess<RegistryLayer> registries;
    
    @Shadow
    public abstract RegistryAccess.Frozen registryAccess();
    
    public MinecraftServerMixin(String name) {
        super(name);
    }
    
    @Override
    public boolean maidspell$createWorld(ResourceKey<Level> key, ResourceLocation dimensionTypeKey) {
        try {
            MinecraftServer server = (MinecraftServer) (Object) this;
            
            // 获取注册表访问
            RegistryAccess registryAccess = server.registryAccess();
            Registry<LevelStem> dimensionRegistry = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
            
            // 检查维度类型是否存在
            if (!dimensionRegistry.containsKey(dimensionTypeKey)) {
                MaidSpellMod.LOGGER.warn("Dimension type not found: {}", dimensionTypeKey);
                return false;
            }
            
            // 获取主世界作为模板
            ServerLevel overworld = levels.get(Level.OVERWORLD);
            if (overworld == null) {
                MaidSpellMod.LOGGER.error("Overworld not found, cannot create dimension");
                return false;
            }
            
            // 获取LevelStem
            LevelStem levelStem = dimensionRegistry.get(dimensionTypeKey);
            if (levelStem == null) {
                MaidSpellMod.LOGGER.warn("LevelStem not found for: {}", dimensionTypeKey);
                return false;
            }
            
            // 创建ServerLevel
            ServerLevelData serverLevelData = (ServerLevelData) overworld.getLevelData();
            long seed = BiomeManager.obfuscateSeed((long)(0x66ccff*Math.random()));
            
            // 创建一个简单的ChunkProgressListener
            ChunkProgressListener progressListener = new ChunkProgressListener() {
                @Override
                public void updateSpawnPos(net.minecraft.world.level.@NotNull ChunkPos pos) {}

                @Override
                public void onStatusChange(@NotNull ChunkPos pChunkPosition, @Nullable ChunkStatus pNewStatus) {}
                
                @Override
                public void start() {}
                
                @Override
                public void stop() {}
            };
            
            ServerLevel newLevel = new ServerLevel(
                server,
                executor,
                storageSource,
                serverLevelData,
                key,
                levelStem,
                progressListener,
                overworld.isDebug(),
                seed,
                ImmutableList.of(),
                true,
                overworld.getRandomSequences()
            );
            
            // 添加到世界Map
            levels.put(key, newLevel);

            // 注册世界边界监听器
            server.getPlayerList().addWorldborderListener(newLevel);

            // 触发 Forge 的世界加载事件，这是确保世界正常 Tick 和实体加载的关键
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));

            // 尝试调用 Forge 注入的 markWorldsDirty 方法
            // 该方法通知服务器世界列表已更改，确保 getAllLevels() 等方法能获取到最新列表
            try {
                server.getClass().getMethod("markWorldsDirty").invoke(server);
            } catch (Exception ignored) {
                // 如果方法不存在，说明是不同版本的 Forge，忽略即可
            }

            MaidSpellMod.LOGGER.info("Successfully created dimension: {}", key.location());
            return true;
            
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to create dimension: {}", key.location(), e);
            return false;
        }
    }
    
    @Override
    public void maidspell$removeWorld(ResourceKey<Level> key) {
        try {
            ServerLevel level = levels.remove(key);
            if (level != null) {
                MaidSpellMod.LOGGER.info("Removed dimension: {}", key.location());
            }
        } catch (Exception e) {
            MaidSpellMod.LOGGER.error("Failed to remove dimension: {}", key.location(), e);
        }
    }
}

