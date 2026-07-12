package com.github.yimeng261.maidspell.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidHealthWriteGuardTest {
    @Test
    void nestedBypassesRemainActiveUntilTheOutermostScopeEnds() {
        assertFalse(MaidHealthWriteGuard.isBypassing());
        MaidHealthWriteGuard.runBypassing(() -> {
            assertTrue(MaidHealthWriteGuard.isBypassing());
            MaidHealthWriteGuard.runBypassing(() ->
                assertTrue(MaidHealthWriteGuard.isBypassing()));
            assertTrue(MaidHealthWriteGuard.isBypassing());
        });
        assertFalse(MaidHealthWriteGuard.isBypassing());
    }

    @Test
    void bypassStateIsRestoredWhenTheActionThrows() {
        assertThrows(IllegalStateException.class, () ->
            MaidHealthWriteGuard.runBypassing(() -> {
                assertTrue(MaidHealthWriteGuard.isBypassing());
                throw new IllegalStateException("expected");
            }));
        assertFalse(MaidHealthWriteGuard.isBypassing());
    }

    @Test
    void bypassStateIsIsolatedBetweenThreads() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch workerEnteredBypass = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean workerInside = new AtomicBoolean();

        try {
            Future<Boolean> worker = executor.submit(() -> {
                assertFalse(MaidHealthWriteGuard.isBypassing());
                MaidHealthWriteGuard.runBypassing(() -> {
                    workerInside.set(MaidHealthWriteGuard.isBypassing());
                    workerEnteredBypass.countDown();
                    await(releaseWorker);
                });
                return MaidHealthWriteGuard.isBypassing();
            });

            assertTrue(workerEnteredBypass.await(5, TimeUnit.SECONDS));
            assertTrue(workerInside.get());
            assertFalse(MaidHealthWriteGuard.isBypassing());
            MaidHealthWriteGuard.runBypassing(() ->
                assertTrue(MaidHealthWriteGuard.isBypassing()));
            assertFalse(MaidHealthWriteGuard.isBypassing());

            releaseWorker.countDown();
            assertFalse(worker.get(5, TimeUnit.SECONDS));
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test synchronization");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test synchronization", exception);
        }
    }
}
