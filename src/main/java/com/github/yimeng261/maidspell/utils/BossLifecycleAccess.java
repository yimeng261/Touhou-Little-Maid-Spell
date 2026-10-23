package com.github.yimeng261.maidspell.utils;

import net.minecraft.world.entity.Entity;
import java.util.ArrayDeque;
import java.util.Deque;

public final class BossLifecycleAccess {
    private static final ThreadLocal<Deque<Access>> ACCESS = ThreadLocal.withInitial(ArrayDeque::new);

    private BossLifecycleAccess() {
    }

    public static void withDataWrite(Entity entity, Runnable action) {
        run(entity, null, action);
    }

    public static void withAuthorizedTeardown(Entity entity, Entity.RemovalReason reason, Runnable action) {
        run(entity, reason, action);
    }

    public static boolean canWriteData(Entity entity) {
        return ACCESS.get().stream().anyMatch(access -> access.entity == entity);
    }

    public static boolean canRemove(Entity entity, Entity.RemovalReason reason) {
        return ACCESS.get().stream().anyMatch(access -> access.entity == entity && access.reason == reason);
    }

    private static void run(Entity entity, Entity.RemovalReason reason, Runnable action) {
        Deque<Access> stack = ACCESS.get();
        stack.push(new Access(entity, reason));
        try {
            action.run();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                ACCESS.remove();
            }
        }
    }

    private record Access(Entity entity, Entity.RemovalReason reason) {
    }
}
