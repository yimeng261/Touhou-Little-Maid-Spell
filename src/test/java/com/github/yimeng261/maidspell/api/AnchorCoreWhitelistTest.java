package com.github.yimeng261.maidspell.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnchorCoreWhitelist} 行为测试；各用例使用不同类名，避免静态注册表互相污染。
 */
class AnchorCoreWhitelistTest {

    @Test
    void builtInCallersAreAllowed() {
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("net.minecraft.world.entity.Entity"));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("net.minecraftforge.network.NetworkHooks"));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed(
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid"));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("java.lang.Thread"));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed(
                "com.github.yimeng261.maidspell.utils.AnchorCoreProtection"));
    }

    @Test
    void unregisteredThirdPartyCallerIsRejected() {
        assertFalse(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.UnregisteredHandler"));
    }

    @Test
    void nullOrEmptyCallerNameIsRejected() {
        assertFalse(AnchorCoreWhitelist.isCallerAllowed(null));
        assertFalse(AnchorCoreWhitelist.isCallerAllowed(""));
    }

    @Test
    void addAllowedClassByNameAllowsExactlyThatClass() {
        String exactClassName = "com.example.anchorcoretest.exact.ExactHandler";
        assertFalse(AnchorCoreWhitelist.isCallerAllowed(exactClassName));

        AnchorCoreWhitelist.addAllowedClass(exactClassName);

        assertTrue(AnchorCoreWhitelist.isCallerAllowed(exactClassName));
        // 精确类名注册不应放行“前缀相同”或“同包”的其它类。
        assertFalse(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.exact.ExactHandlerSibling"));
        assertFalse(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.exact.ExactHandler$Inner"));
    }

    @Test
    void addAllowedClassByClassRegistersItsBinaryName() throws Exception {
        // 内部类名命中内置白名单，只能直接检查注册表内容。
        AnchorCoreWhitelist.addAllowedClass(RegisteredMarker.class);

        assertTrue(registeredExtraClasses().contains(RegisteredMarker.class.getName()));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed(RegisteredMarker.class.getName()));
    }

    @Test
    void addAllowedPackageAllowsClassesUnderPrefix() {
        AnchorCoreWhitelist.addAllowedPackage("com.example.anchorcoretest.pkg.");

        assertTrue(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.pkg.DirectHandler"));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.pkg.deep.NestedHandler"));
        // 前缀必须按原样匹配，pkgother 不属于 pkg. 包。
        assertFalse(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.pkgother.NotUnderPrefix"));
    }

    @Test
    void addAllowedCallerPredicateIsHonoured() {
        String markedClassName = "com.example.anchorcoretest.predicate.PredicateMarkerHandler";
        assertFalse(AnchorCoreWhitelist.isCallerAllowed(markedClassName));

        AnchorCoreWhitelist.addAllowedCaller(className -> className.contains("PredicateMarker"));

        assertTrue(AnchorCoreWhitelist.isCallerAllowed(markedClassName));
        assertFalse(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.predicate.PlainHandler"));
    }

    @Test
    void throwingPredicateIsTreatedAsNonMatch() {
        assertDoesNotThrow(() -> AnchorCoreWhitelist.addAllowedCaller(className -> {
            throw new IllegalStateException("predicate boom for " + className);
        }));

        // 抛异常的谓词按不匹配处理，且不影响内置白名单与后续谓词。
        assertFalse(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.throwing.NotAllowedHandler"));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("net.minecraft.world.level.Level"));

        String allowedLater = "com.example.anchorcoretest.throwing.AllowedLaterHandler";
        AnchorCoreWhitelist.addAllowedCaller(className -> className.endsWith("AllowedLaterHandler"));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed(allowedLater));
    }

    @Test
    void linkageErrorFromPredicateIsTreatedAsNonMatch() {
        // 可选模组未安装时谓词可能抛 NoClassDefFoundError，必须按不匹配处理。
        AnchorCoreWhitelist.addAllowedCaller(className -> {
            throw new NoClassDefFoundError("optional mod class missing for " + className);
        });

        assertDoesNotThrow(() -> assertFalse(
                AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.linkage.NotAllowedHandler")));
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("com.mojang.logging.LogUtils"));
    }

    @Test
    void registrationNamesAreTrimmed() {
        AnchorCoreWhitelist.addAllowedClass("  com.example.anchorcoretest.trim.TrimmedHandler  ");
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.trim.TrimmedHandler"));

        AnchorCoreWhitelist.addAllowedPackage("  com.example.anchorcoretest.trim.pkg.  ");
        assertTrue(AnchorCoreWhitelist.isCallerAllowed("com.example.anchorcoretest.trim.pkg.Handler"));
    }

    @Test
    void nullAndBlankArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> AnchorCoreWhitelist.addAllowedClass((String) null));
        assertThrows(NullPointerException.class, () -> AnchorCoreWhitelist.addAllowedClass((Class<?>) null));
        assertThrows(NullPointerException.class, () -> AnchorCoreWhitelist.addAllowedPackage(null));
        assertThrows(NullPointerException.class, () -> AnchorCoreWhitelist.addAllowedCaller(null));

        assertThrows(IllegalArgumentException.class, () -> AnchorCoreWhitelist.addAllowedClass(""));
        assertThrows(IllegalArgumentException.class, () -> AnchorCoreWhitelist.addAllowedClass("   "));
        assertThrows(IllegalArgumentException.class, () -> AnchorCoreWhitelist.addAllowedPackage(" "));
        assertThrows(IllegalArgumentException.class, () -> AnchorCoreWhitelist.addAllowedPackage("\t\n"));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> registeredExtraClasses() throws ReflectiveOperationException {
        Field field = AnchorCoreWhitelist.class.getDeclaredField("EXTRA_CLASSES");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }

    /** 用于验证 {@link AnchorCoreWhitelist#addAllowedClass(Class)} 的内部静态类。 */
    private static final class RegisteredMarker {
    }
}
