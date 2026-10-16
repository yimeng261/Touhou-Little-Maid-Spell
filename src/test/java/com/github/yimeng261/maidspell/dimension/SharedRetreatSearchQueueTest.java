package com.github.yimeng261.maidspell.dimension;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class SharedRetreatSearchQueueTest {
    @Test
    void cancelledQueuedSearchCannotStartItsSuccessorBeforeTheActiveSearchCompletes() {
        SharedRetreatSearchQueue queue = new SharedRetreatSearchQueue();
        TestExecutor server = new TestExecutor();
        List<Integer> started = new ArrayList<>();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        CompletableFuture<Void> third = new CompletableFuture<>();
        queue.enqueue(Level.OVERWORLD, server, first, () -> started.add(1));
        queue.enqueue(Level.OVERWORLD, server, second, () -> started.add(2));
        queue.enqueue(Level.OVERWORLD, server, third, () -> started.add(3));
        server.drain();
        assertEquals(List.of(1), started);
        second.cancel(false);
        server.drain();
        assertEquals(List.of(1), started);
        first.complete(null);
        server.drain();
        assertEquals(List.of(1, 3), started);
        third.complete(null);
        server.drain();
    }

    @Test
    void failedSearchDoesNotBlockNextSearchAndDimensionsAreIndependent() {
        SharedRetreatSearchQueue queue = new SharedRetreatSearchQueue();
        TestExecutor server = new TestExecutor();
        CompletableFuture<Void> failed = new CompletableFuture<>();
        CompletableFuture<Void> next = new CompletableFuture<>();
        CompletableFuture<Void> otherDimension = new CompletableFuture<>();
        List<String> started = new ArrayList<>();
        queue.enqueue(Level.OVERWORLD, server, failed, () -> { throw new IllegalStateException("failed start"); });
        queue.enqueue(Level.OVERWORLD, server, next, () -> started.add("next"));
        queue.enqueue(Level.NETHER, server, otherDimension, () -> started.add("other"));
        server.drain();
        assertTrue(failed.isCompletedExceptionally());
        assertTrue(started.containsAll(List.of("next", "other")));
    }

    private static class TestExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void drain() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }
}
