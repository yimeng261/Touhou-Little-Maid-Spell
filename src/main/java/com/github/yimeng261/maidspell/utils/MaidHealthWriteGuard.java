package com.github.yimeng261.maidspell.utils;

public final class MaidHealthWriteGuard {
    private static final ThreadLocal<Integer> BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);

    private MaidHealthWriteGuard() {
    }

    public static boolean isBypassing() {
        return BYPASS_DEPTH.get() > 0;
    }

    public static void runBypassing(Runnable action) {
        BYPASS_DEPTH.set(BYPASS_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            int depth = BYPASS_DEPTH.get() - 1;
            if (depth <= 0) {
                BYPASS_DEPTH.remove();
            } else {
                BYPASS_DEPTH.set(depth);
            }
        }
    }
}
