package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * 按 tick 延迟执行的服务端任务队列，供剑牢这种"分圈次第落下"的法术使用。
 *
 * <p>{@code MinecraftServer.tell} / {@code level.getServer().execute} 都只把任务塞进"当前 tick"
 * 的队列，不支持指定未来 tick；{@code TickTask} 里的 tick 只被 {@code shouldRun} 当作"超时多久
 * 就无条件执行"的兜底，所以这里自己维护一个按目标 tick 排序的队列。
 *
 * <p>每个任务的延迟都从调度那一刻起算，各次施法互不拖延；同一次施法的分段由调用方按段数乘间隔给出延迟。
 */
public final class SwordRingScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PriorityQueue<ScheduledTask> PENDING = new PriorityQueue<>(
            Comparator.comparingLong(ScheduledTask::executeAt).thenComparingLong(ScheduledTask::sequence));
    private static boolean registered;
    private static long nextSequence;

    private SwordRingScheduler() {
    }

    /**
     * 在 {@code delayTicks} 个 tick 后执行任务，同一 tick 到期的任务按调度顺序执行。
     */
    public static void schedule(ServerLevel level, int delayTicks, Runnable task) {
        ensureRegistered();
        PENDING.add(new ScheduledTask(level.getServer().getTickCount() + delayTicks, nextSequence++, task));
    }

    private static void ensureRegistered() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(SwordRingScheduler.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        while (!PENDING.isEmpty() && PENDING.peek().executeAt() <= now) {
            Runnable task = PENDING.poll().task();
            try {
                task.run();
            } catch (RuntimeException e) {
                // 单个法术出错只丢掉这一段，不影响其他任务，也不抛进服务端 tick
                LOGGER.error("[MaidSpell] 延迟法术任务执行失败", e);
            }
        }
    }

    /** 关服时清空，避免把上一局的服务端、维度对象留在静态队列里。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        nextSequence = 0L;
    }

    private record ScheduledTask(long executeAt, long sequence, Runnable task) {
    }
}
