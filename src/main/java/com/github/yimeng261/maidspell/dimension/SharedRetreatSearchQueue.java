package com.github.yimeng261.maidspell.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Server-thread queue for searches sharing a dimension-wide generation mailbox. */
final class SharedRetreatSearchQueue {
    private final Map<ResourceKey<Level>, CompletableFuture<Void>> tails = new HashMap<>();

    void enqueue(ResourceKey<Level> dimension, Executor server, CompletableFuture<?> result, Runnable start) {
        CompletableFuture<Void> previous = tails.getOrDefault(dimension, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> tail = previous.handle((ignored, failure) -> (Void) null)
                .thenComposeAsync(ignored -> {
                    if (!result.isDone()) {
                        try {
                            start.run();
                        } catch (Exception failure) {
                            result.completeExceptionally(failure);
                        }
                    }
                    return result.handle((position, failure) -> (Void) null);
                }, server);
        tails.put(dimension, tail);
        tail.whenCompleteAsync((ignored, failure) -> tails.remove(dimension, tail), server);
    }

    void clear() {
        tails.clear();
    }
}
