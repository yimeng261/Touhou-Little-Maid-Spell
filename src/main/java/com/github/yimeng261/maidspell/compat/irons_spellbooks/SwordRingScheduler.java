package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 tick 延迟执行的服务端任务队列，供剑牢这种"分圈次第落下"的法术使用。
 *
 * <p>{@code MinecraftServer.tell} / {@code level.getServer().execute} 都只把任务塞进"当前 tick"
 * 的队列，不支持指定未来 tick；{@code TickTask} 里的 tick 只被 {@code shouldRun} 当作"超时多久
 * 就无条件执行"的兜底，所以这里自己维护一个按目标 tick 排序的队列。
 */
public final class SwordRingScheduler {
    private static final List<ScheduledTask> PENDING = new ArrayList<>();
    private static boolean registered;
    private static boolean clockSynced;
    private static long serverTick;

    private SwordRingScheduler() {
    }

    /**
     * 在上一个任务之后追加一个延迟任务。
     *
     * @param chain 为 {@code true} 时接在当前队列末尾的任务之后再延迟；
     *              为 {@code false} 时从当前时刻起算延迟
     */
    public static void schedule(ServerLevel level, int delayTicks, boolean chain, Runnable task) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        ensureRegistered();

        // serverTick 只在队列非空时自增，所以拿真实 tickCount 对齐一次；
        // 否则一局游戏开了十分钟才第一次施法时，延迟会从 0 开始算而偏早。
        if (!clockSynced) {
            serverTick = server.getTickCount();
            clockSynced = true;
        }

        long executeAt;
        if (chain && !PENDING.isEmpty()) {
            ScheduledTask last = PENDING.get(PENDING.size() - 1);
            executeAt = last.executeAt() + delayTicks;
        } else {
            executeAt = serverTick + delayTicks;
        }

        PENDING.add(new ScheduledTask(server, level, executeAt, task));
    }

    private static void ensureRegistered() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(SwordRingScheduler.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        serverTick++;
        if (PENDING.isEmpty()) {
            return;
        }

        // 队列按 executeAt 递增排列，所以可以边跑边从头删。
        while (!PENDING.isEmpty() && PENDING.get(0).executeAt() <= serverTick) {
            ScheduledTask scheduled = PENDING.remove(0);
            if (scheduled.server().isRunning()
                    && scheduled.level().getServer() == scheduled.server()) {
                scheduled.task().run();
            }
        }
    }

    /** 关服时清空，避免把上一局的服务端、维度对象留在静态队列里。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        serverTick = 0L;
        clockSynced = false;
    }

    /** 同一法术的所有分圈任务共用一条队列，后面的任务链在它后面累计延迟。 */
    private record ScheduledTask(MinecraftServer server, ServerLevel level,
                                 long executeAt, Runnable task) {
    }
}
