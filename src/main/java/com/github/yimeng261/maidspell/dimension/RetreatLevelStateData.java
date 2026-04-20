package com.github.yimeng261.maidspell.dimension;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 按维度持久化归隐之地的动态 LevelData。
 * 使用当前维度自己的 data/ 目录保存，避免重启后时间、天气和世界出生点回退到主世界快照。
 */
public class RetreatLevelStateData extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_retreat_level_state";

    private long gameTime;
    private long dayTime;
    private int xSpawn;
    private int ySpawn;
    private int zSpawn;
    private float spawnAngle;
    private int clearWeatherTime;
    private boolean isRaining;
    private int rainTime;
    private boolean isThundering;
    private int thunderTime;
    private boolean initialized;
    private transient RetreatLevelData attachedLevelData;

    public RetreatLevelStateData() {
    }

    public static RetreatLevelStateData load(CompoundTag tag) {
        RetreatLevelStateData data = new RetreatLevelStateData();
        data.initialized = tag.getBoolean("Initialized");
        data.gameTime = tag.getLong("GameTime");
        data.dayTime = tag.getLong("DayTime");
        data.xSpawn = tag.getInt("SpawnX");
        data.ySpawn = tag.getInt("SpawnY");
        data.zSpawn = tag.getInt("SpawnZ");
        data.spawnAngle = tag.getFloat("SpawnAngle");
        data.clearWeatherTime = tag.getInt("ClearWeatherTime");
        data.isRaining = tag.getBoolean("IsRaining");
        data.rainTime = tag.getInt("RainTime");
        data.isThundering = tag.getBoolean("IsThundering");
        data.thunderTime = tag.getInt("ThunderTime");
        return data;
    }

    public static RetreatLevelStateData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            RetreatLevelStateData::load,
            RetreatLevelStateData::new,
            DATA_NAME
        );
    }

    public void attach(RetreatLevelData levelData) {
        this.attachedLevelData = levelData;
        levelData.setDirtyListener(this::setDirty);

        // 首次创建存档文件时，当前 levelData 视为权威来源。
        if (!this.initialized) {
            this.capture(levelData);
            this.initialized = true;
            this.setDirty();
            return;
        }

        this.applyTo(levelData);
    }

    public void capture(RetreatLevelData levelData) {
        this.gameTime = levelData.getGameTime();
        this.dayTime = levelData.getDayTime();
        this.xSpawn = levelData.getXSpawn();
        this.ySpawn = levelData.getYSpawn();
        this.zSpawn = levelData.getZSpawn();
        this.spawnAngle = levelData.getSpawnAngle();
        this.clearWeatherTime = levelData.getClearWeatherTime();
        this.isRaining = levelData.isRaining();
        this.rainTime = levelData.getRainTime();
        this.isThundering = levelData.isThundering();
        this.thunderTime = levelData.getThunderTime();
    }

    public void applyTo(RetreatLevelData levelData) {
        levelData.setDirtyListener(null);
        levelData.setGameTime(this.gameTime);
        levelData.setDayTime(this.dayTime);
        levelData.setSpawn(new BlockPos(this.xSpawn, this.ySpawn, this.zSpawn), this.spawnAngle);
        levelData.setClearWeatherTime(this.clearWeatherTime);
        levelData.setRaining(this.isRaining);
        levelData.setRainTime(this.rainTime);
        levelData.setThundering(this.isThundering);
        levelData.setThunderTime(this.thunderTime);
        levelData.setDirtyListener(this::setDirty);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        if (this.attachedLevelData != null) {
            this.capture(this.attachedLevelData);
        }
        tag.putBoolean("Initialized", true);
        tag.putLong("GameTime", this.gameTime);
        tag.putLong("DayTime", this.dayTime);
        tag.putInt("SpawnX", this.xSpawn);
        tag.putInt("SpawnY", this.ySpawn);
        tag.putInt("SpawnZ", this.zSpawn);
        tag.putFloat("SpawnAngle", this.spawnAngle);
        tag.putInt("ClearWeatherTime", this.clearWeatherTime);
        tag.putBoolean("IsRaining", this.isRaining);
        tag.putInt("RainTime", this.rainTime);
        tag.putBoolean("IsThundering", this.isThundering);
        tag.putInt("ThunderTime", this.thunderTime);
        return tag;
    }
}
