package com.github.yimeng261.maidspell.compat.goety;

/** 给场景测试用的同包访问口：虚拟法杖当前存活的上下文数是包内可见。 */
public final class GoetyTestAccess {
    private GoetyTestAccess() {
    }

    public static int activeVirtualWandContexts() {
        return VirtualWandManager.activeContextCount();
    }
}
