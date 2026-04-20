package com.github.yimeng261.maidspell.dimension;

import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;

/**
 * 归隐之地维度专用 LevelData。
 * <p>
 * 继承 DerivedLevelData 但覆盖时间和 TimerQueue 相关方法，
 * 使归隐之地拥有独立的昼夜循环和定时事件队列。
 * <p>
 * DerivedLevelData 的 getter 全部委托给主世界，setter 全部为 no-op。
 * 本类让时间相关的 getter/setter 和 scheduledEvents 独立于主世界。
 */
public class RetreatLevelData extends DerivedLevelData {

    private long gameTime;
    private long dayTime;
    private final TimerQueue<MinecraftServer> scheduledEvents = new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS);

    // 独立的世界出生点
    private int xSpawn;
    private int ySpawn;
    private int zSpawn;
    private float spawnAngle;

    // 独立的天气状态
    private int clearWeatherTime;
    private boolean isRaining;
    private int rainTime;
    private boolean isThundering;
    private int thunderTime;
    private Runnable dirtyListener = () -> {};

    public RetreatLevelData(WorldData worldData, ServerLevelData wrapped) {
        super(worldData, wrapped);
        // 初始化时从主世界复制当前时间
        this.gameTime = wrapped.getGameTime();
        this.dayTime = wrapped.getDayTime();
        // 初始化出生点
        this.xSpawn = wrapped.getXSpawn();
        this.ySpawn = wrapped.getYSpawn();
        this.zSpawn = wrapped.getZSpawn();
        this.spawnAngle = wrapped.getSpawnAngle();
        // 初始化天气状态
        this.clearWeatherTime = wrapped.getClearWeatherTime();
        this.isRaining = wrapped.isRaining();
        this.rainTime = wrapped.getRainTime();
        this.isThundering = wrapped.isThundering();
        this.thunderTime = wrapped.getThunderTime();
    }

    // ========== 时间 ==========

    @Override
    public int getXSpawn() {
        return this.xSpawn;
    }

    @Override
    public int getYSpawn() {
        return this.ySpawn;
    }

    @Override
    public int getZSpawn() {
        return this.zSpawn;
    }

    @Override
    public float getSpawnAngle() {
        return this.spawnAngle;
    }

    @Override
    public void setXSpawn(int xSpawn) {
        this.xSpawn = xSpawn;
        this.markDirty();
    }

    @Override
    public void setYSpawn(int ySpawn) {
        this.ySpawn = ySpawn;
        this.markDirty();
    }

    @Override
    public void setZSpawn(int zSpawn) {
        this.zSpawn = zSpawn;
        this.markDirty();
    }

    @Override
    public void setSpawnAngle(float spawnAngle) {
        this.spawnAngle = spawnAngle;
        this.markDirty();
    }

    @Override
    public void setSpawn(BlockPos blockPos, float angle) {
        this.xSpawn = blockPos.getX();
        this.ySpawn = blockPos.getY();
        this.zSpawn = blockPos.getZ();
        this.spawnAngle = angle;
        this.markDirty();
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public void setGameTime(long gameTime) {
        this.gameTime = gameTime;
        this.markDirty();
    }

    @Override
    public long getDayTime() {
        return this.dayTime;
    }

    @Override
    public void setDayTime(long dayTime) {
        this.dayTime = dayTime;
        this.markDirty();
    }

    // ========== 定时事件队列 ==========

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    // ========== 天气 ==========

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int time) {
        this.clearWeatherTime = time;
        this.markDirty();
    }

    @Override
    public boolean isRaining() {
        return this.isRaining;
    }

    @Override
    public void setRaining(boolean raining) {
        this.isRaining = raining;
        this.markDirty();
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int time) {
        this.rainTime = time;
        this.markDirty();
    }

    @Override
    public boolean isThundering() {
        return this.isThundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.isThundering = thundering;
        this.markDirty();
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        this.thunderTime = time;
        this.markDirty();
    }

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener != null ? dirtyListener : () -> {};
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
