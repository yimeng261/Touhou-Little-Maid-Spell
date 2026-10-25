package com.github.yimeng261.maidspell.utils;

import com.github.yimeng261.maidspell.api.AnchorCoreWhitelist;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AnchorCoreProtection#isRemoveCallAllowed} 的单元测试（直接构造栈帧数组）。 */
class AnchorCoreRemoveCallAllowedTest {
    private static final String THIS_MOD_EVENT = "com.github.yimeng261.maidspell.event.MaidSpellEventHandler";
    private static final String THIS_MOD_PROTECTION = "com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection";
    private static final String TLM_MAID = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid";
    private static final String VANILLA_ENTITY = "net.minecraft.world.entity.Entity";
    private static final String VANILLA_LEVEL = "net.minecraft.server.level.ServerLevel";

    /** 未注册的外部模组帧，不能含 maid/backup/c2me 等内置放行关键字。 */
    private static final String UNREGISTERED_EXTERNAL = "com.example.anchorcoretest.removecall.UnregisteredFrame";
    /** 通过公共 API 注册后应被放行的外部帧。 */
    private static final String REGISTERED_EXTERNAL = "com.example.anchorcoretest.removecall.RegisteredFrame";
    /** 用于验证 ALLOW_HARD_REMOVAL 上下文的外部帧（不注册）。 */
    private static final String ALLOWED_BY_FLAG_EXTERNAL = "com.example.anchorcoretest.removecall.AllowedByFlagFrame";

    @Test
    void trustedStackTraceIsAllowed() {
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(frames(
                "java.lang.Thread",
                THIS_MOD_EVENT,
                THIS_MOD_PROTECTION,
                TLM_MAID,
                VANILLA_ENTITY,
                VANILLA_LEVEL), Entity.RemovalReason.DISCARDED));
    }

    @Test
    void unregisteredThirdPartyFrameIsRejected() {
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(frames(
                "java.lang.Thread",
                THIS_MOD_EVENT,
                VANILLA_ENTITY,
                UNREGISTERED_EXTERNAL), Entity.RemovalReason.DISCARDED));
    }

    @Test
    void thirdPartyFrameIsAllowedAfterRegistration() {
        StackTraceElement[] trace = frames(
                "java.lang.Thread",
                THIS_MOD_EVENT,
                VANILLA_ENTITY,
                REGISTERED_EXTERNAL);
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(trace, Entity.RemovalReason.DISCARDED));

        AnchorCoreWhitelist.addAllowedClass(REGISTERED_EXTERNAL);

        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(trace, Entity.RemovalReason.DISCARDED));
    }

    @Test
    void tardisFrameIsAllowedForChangedDimension() {
        // 跨维是正常行为，始终放行。
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(
                frames("whocraft.tardis_refined.mixin.SomeMixin"),
                Entity.RemovalReason.CHANGED_DIMENSION));
    }

    @Test
    void dimensionChangeIsAllowedEvenWithExternalFrames() {
        // 跨维是正常行为（原版/TARDIS 等），取消反而会让目标维度副本与原实体并存。
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", UNREGISTERED_EXTERNAL),
                Entity.RemovalReason.CHANGED_DIMENSION));
    }

    @Test
    void unloadRemovalsAreBlockedForUntrustedCallers() {
        // 强加载下 UNLOAD 型 remove 不应出现，不可信调用者按可疑移除拦截。
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", UNREGISTERED_EXTERNAL),
                Entity.RemovalReason.UNLOADED_TO_CHUNK));
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", UNREGISTERED_EXTERNAL),
                Entity.RemovalReason.UNLOADED_WITH_PLAYER));
    }

    @Test
    void unloadRemovalsAreAllowedForTrustedCallers() {
        // 可信调用者（原版/TLM/Carry On/白名单模组）仍可正常卸载，避免误伤兼容流程。
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", TLM_MAID, VANILLA_ENTITY),
                Entity.RemovalReason.UNLOADED_TO_CHUNK));
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", TLM_MAID, VANILLA_ENTITY),
                Entity.RemovalReason.UNLOADED_WITH_PLAYER));
    }

    @Test
    void nullReasonIsTreatedAsUntrustedRemoval() {
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", UNREGISTERED_EXTERNAL), null));
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", VANILLA_ENTITY), null));
    }

    @Test
    void tardisFrameIsAllowedForDiscardedBecauseBuiltinWhitelistContainsTardis() {
        // TARDIS 是内置白名单项，与 reason 无关。
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(
                frames("whocraft.tardis_refined.mixin.SomeMixin"),
                Entity.RemovalReason.DISCARDED));
    }

    @Test
    void thirdPartyClassEndingWithEntityMaidIsNotSkipped() {
        // 回归：类名以 "EntityMaid" 结尾不再被无条件跳过（旧实现存在该白名单绕过点）。
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(
                frames("java.lang.Thread", "com.example.anchorcoretest.removecall.FakeEntityMaid"),
                Entity.RemovalReason.DISCARDED));
    }

    @Test
    void untrustedHardRemovalContextAlwaysRejects() {
        MaidHardRemovalProtection.runAsUntrustedHardRemoval(() ->
                assertFalse(AnchorCoreProtection.isRemoveCallAllowed(frames(
                        "java.lang.Thread",
                        THIS_MOD_EVENT,
                        VANILLA_ENTITY), Entity.RemovalReason.DISCARDED)));
    }

    @Test
    void allowingHardRemovalContextAllowsExternalFrames() {
        MaidHardRemovalProtection.runAllowingHardRemoval(() ->
                assertTrue(AnchorCoreProtection.isRemoveCallAllowed(frames(
                        "java.lang.Thread",
                        ALLOWED_BY_FLAG_EXTERNAL), Entity.RemovalReason.DISCARDED)));
    }

    @Test
    void nestedRemovalContextFlagsRestoreOuterValue() {
        MaidHardRemovalProtection.runAllowingHardRemoval(() -> {
            MaidHardRemovalProtection.runAllowingHardRemoval(() ->
                    assertTrue(AnchorCoreProtection.isRemoveCallAllowed(frames(
                            "java.lang.Thread", ALLOWED_BY_FLAG_EXTERNAL), Entity.RemovalReason.DISCARDED)));
            // 内层退出后外层 ALLOW 仍然生效。
            assertTrue(AnchorCoreProtection.isRemoveCallAllowed(frames(
                    "java.lang.Thread", ALLOWED_BY_FLAG_EXTERNAL), Entity.RemovalReason.DISCARDED));
        });
        // 全部退出后不残留。
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(frames(
                "java.lang.Thread", ALLOWED_BY_FLAG_EXTERNAL), Entity.RemovalReason.DISCARDED));

        MaidHardRemovalProtection.runAsUntrustedHardRemoval(() -> {
            MaidHardRemovalProtection.runAsUntrustedHardRemoval(() ->
                    assertFalse(AnchorCoreProtection.isRemoveCallAllowed(frames(
                            "java.lang.Thread", VANILLA_ENTITY), Entity.RemovalReason.DISCARDED)));
            // 内层退出后外层 FORCE 仍然生效。
            assertFalse(AnchorCoreProtection.isRemoveCallAllowed(frames(
                    "java.lang.Thread", VANILLA_ENTITY), Entity.RemovalReason.DISCARDED));
        });
        // 退出后恢复为可信栈放行。
        assertTrue(AnchorCoreProtection.isRemoveCallAllowed(frames(
                "java.lang.Thread", VANILLA_ENTITY), Entity.RemovalReason.DISCARDED));
    }

    @Test
    void forcingContextRejectsNonDestructiveRemovalToo() {
        // FORCE 优先于“非销毁 reason 直接放行”，保留强制拦截能力。
        MaidHardRemovalProtection.runAsUntrustedHardRemoval(() ->
                assertFalse(AnchorCoreProtection.isRemoveCallAllowed(
                        frames("java.lang.Thread", VANILLA_ENTITY),
                        Entity.RemovalReason.CHANGED_DIMENSION)));
    }

    @Test
    void nullMaidIsNeverBlocked() {
        assertFalse(AnchorCoreProtection.shouldBlockMaidRemove(
                null, Entity.RemovalReason.DISCARDED, "test"));
    }

    @Test
    void nullOrEmptyStackTraceIsRejected() {
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(null, Entity.RemovalReason.DISCARDED));
        assertFalse(AnchorCoreProtection.isRemoveCallAllowed(
                new StackTraceElement[0], Entity.RemovalReason.DISCARDED));
    }

    private static StackTraceElement[] frames(String... classNames) {
        StackTraceElement[] trace = new StackTraceElement[classNames.length];
        for (int i = 0; i < classNames.length; i++) {
            trace[i] = new StackTraceElement(classNames[i], "testMethod", "TestSource.java", 1);
        }
        return trace;
    }
}
