package com.github.yimeng261.maidspell.client.overlay;

import net.minecraft.client.renderer.Rect2i;

import java.util.List;
import java.util.function.Supplier;

/** Optional bridge for HUD layout protocols such as JEI global GUI handlers. */
public final class HudExclusionAreas {
    private static volatile Supplier<List<Rect2i>> provider = List::of;

    private HudExclusionAreas() {
    }

    public static void setProvider(Supplier<List<Rect2i>> newProvider) {
        provider = newProvider == null ? List::of : newProvider;
    }

    public static List<Rect2i> get() {
        try {
            return List.copyOf(provider.get());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
