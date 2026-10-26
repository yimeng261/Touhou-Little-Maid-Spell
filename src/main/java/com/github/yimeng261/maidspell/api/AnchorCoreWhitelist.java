package com.github.yimeng261.maidspell.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 锚定核心调用者白名单扩展 API。
 *
 * <p>锚定核心在拦截女仆的序列化与移除时会检查调用栈类名，栈上每一帧都须可信；其他模组可在此注册自己的
 * 处理类，避免正常机制被误判。注册为进程级、线程安全、实时生效；请只注册本模组的类，
 * 过宽前缀（如 {@code "com."}）会削弱防护。</p>
 *
 * <pre>{@code
 * AnchorCoreWhitelist.addAllowedClass(MyHandler.class);
 * AnchorCoreWhitelist.addAllowedPackage("com.example.mymod.compat.maid.");
 * }</pre>
 */
public final class AnchorCoreWhitelist {
    /** 第三方注册的类名，连同其嵌套类一起放行。 */
    private static final Set<String> EXTRA_CLASSES = ConcurrentHashMap.newKeySet();

    /** 第三方注册的包/类名前缀。 */
    private static final Set<String> EXTRA_PACKAGES = ConcurrentHashMap.newKeySet();

    /** 第三方注册的自定义匹配谓词。 */
    private static final List<Predicate<String>> EXTRA_PREDICATES = new CopyOnWriteArrayList<>();

    private AnchorCoreWhitelist() {
    }

    /** 注册受信任的调用者类，其内部类、匿名类（{@code MyHandler$1} 等）一并放行。 */
    public static void addAllowedClass(Class<?> callerClass) {
        Objects.requireNonNull(callerClass, "callerClass");
        addAllowedClass(callerClass.getName());
    }

    /**
     * 按完整类名注册，匹配该类本身及其嵌套类（{@code 类名$...}）。
     *
     * @throws NullPointerException     当 {@code className} 为 {@code null}
     * @throws IllegalArgumentException 当 {@code className} 为空白字符串
     */
    public static void addAllowedClass(String className) {
        EXTRA_CLASSES.add(requireName(className, "className"));
    }

    /**
     * 按包/类名前缀注册，前缀原样 {@code startsWith} 匹配。
     *
     * @throws NullPointerException     当 {@code packagePrefix} 为 {@code null}
     * @throws IllegalArgumentException 当 {@code packagePrefix} 为空白字符串
     */
    public static void addAllowedPackage(String packagePrefix) {
        EXTRA_PACKAGES.add(requireName(packagePrefix, "packagePrefix"));
    }

    /** 注册自定义类名谓词；谓词抛出的异常按不匹配处理。 */
    public static void addAllowedCaller(Predicate<String> predicate) {
        EXTRA_PREDICATES.add(Objects.requireNonNull(predicate, "predicate"));
    }

    /** 判断类名是否可信：内置白名单 → 注册的精确类名 → 前缀 → 谓词。 */
    public static boolean isCallerAllowed(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }

        // 绝大多数调用栈帧都会命中内置白名单，先走内置判断可以让热路径更短。
        if (isBuiltInAllowed(className)) {
            return true;
        }

        if (!EXTRA_CLASSES.isEmpty() && isRegisteredClassOrNested(className)) {
            return true;
        }

        if (!EXTRA_PACKAGES.isEmpty()) {
            for (String prefix : EXTRA_PACKAGES) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
        }

        if (!EXTRA_PREDICATES.isEmpty()) {
            for (Predicate<String> predicate : EXTRA_PREDICATES) {
                try {
                    if (predicate.test(className)) {
                        return true;
                    }
                } catch (Exception | LinkageError ignored) {
                    // 外部谓词异常/类缺失不应影响存档/移除主流程，按“不匹配”继续。
                }
            }
        }

        return false;
    }

    /** 类名本身或其任一外层类（{@code a.b.Outer$Inner$1} 依次查 {@code Outer$Inner$1}、{@code Outer$Inner}、{@code Outer}）已注册。 */
    private static boolean isRegisteredClassOrNested(String className) {
        if (EXTRA_CLASSES.contains(className)) {
            return true;
        }
        int simpleNameStart = className.lastIndexOf('.') + 1;
        for (int i = className.lastIndexOf('$'); i > simpleNameStart; i = className.lastIndexOf('$', i - 1)) {
            if (EXTRA_CLASSES.contains(className.substring(0, i))) {
                return true;
            }
        }
        return false;
    }

    private static String requireName(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    /** 内置默认白名单：对所有防护层、所有 reason 一律信任。 */
    private static boolean isBuiltInAllowed(String className) {
        return className.startsWith("net.minecraft") ||
                className.startsWith("net.neoforged") ||
                className.startsWith("java") ||
                className.startsWith("jdk.") ||
                className.startsWith("sun.reflect") ||
                className.startsWith("it.unimi.dsi") ||
                className.startsWith("com.github.tartaricacid") ||
                className.startsWith("com.github.yimeng261") ||
                className.startsWith("com.google") ||
                className.startsWith("com.mojang") ||
                className.startsWith("io.redspace.ironsspellbooks") ||
                className.startsWith("whocraft.tardis_refined") ||
                className.startsWith("top.theillusivec4.curios") ||
                className.startsWith("tschipp.carryon") ||
                className.contains("backup") ||
                className.contains("maid") ||
                className.contains("c2me");
    }
}
