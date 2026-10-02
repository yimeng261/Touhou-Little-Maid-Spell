package com.github.yimeng261.maidspell.stagewright.support;

import net.magicterra.stagewright.scene.SceneContext;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 把耗时的逐项计算分摊到多个 tick：每 tick 最多运行 {@link #TICK_BUDGET_MS} 毫秒，
 * 避免单个 tick 过长触发专用服务器看门狗。
 */
public final class Batch {
    private static final long TICK_BUDGET_MS = 250;

    private Batch() {
    }

    /**
     * 逐项处理 items，work 返回 true 表示可以提前结束；全部完成后运行 done。
     * @param maxTicks 等待上限（需小于场景预算）
     */
    public static <T> void run(SceneContext ctx, List<T> items, Predicate<T> work, int maxTicks, Runnable done) {
        runAsync(ctx, items, item -> CompletableFuture.completedFuture(work.test(item)), maxTicks, done);
    }

    /**
     * 同 {@link #run}，但每项返回一个 future（例如异步加载区块）：等它完成再处理下一项，等待期间不阻塞服务器 tick。
     * future 的结果为 true 表示可以提前结束。
     */
    public static <T> void runAsync(SceneContext ctx, List<T> items, Function<T, CompletableFuture<Boolean>> work,
                                    int maxTicks, Runnable done) {
        Progress<T> progress = new Progress<>(items.iterator(), work);
        long started = System.currentTimeMillis();
        ctx.await(progress::advance).within(maxTicks).then(() -> {
            ctx.record("batchMs", System.currentTimeMillis() - started);
            done.run();
        });
    }

    /** 逐项推进的状态：同一时刻最多一个未完成的 future。 */
    private static final class Progress<T> {
        private final Iterator<T> iterator;
        private final Function<T, CompletableFuture<Boolean>> work;
        private CompletableFuture<Boolean> pending;
        private boolean stopped;

        Progress(Iterator<T> iterator, Function<T, CompletableFuture<Boolean>> work) {
            this.iterator = iterator;
            this.work = work;
        }

        /** 在本 tick 的时间预算内尽量推进，返回是否全部完成（或提前结束）。 */
        boolean advance() {
            long deadline = System.currentTimeMillis() + TICK_BUDGET_MS;
            while (!stopped && System.currentTimeMillis() < deadline) {
                if (pending != null) {
                    if (!pending.isDone()) {
                        return false;
                    }
                    stopped = pending.join();
                    pending = null;
                    continue;
                }
                if (!iterator.hasNext()) {
                    break;
                }
                pending = work.apply(iterator.next());
            }
            return stopped || (pending == null && !iterator.hasNext());
        }
    }
}
