package com.github.yimeng261.maidspell.stagewright.support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 测试代码访问原版非公开成员用的反射工具。
 * <p>NeoForge 1.21 开发和运行时都是 Mojang 名，按名字反射在两边一致。
 */
public final class Reflect {
    private Reflect() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T field(Object target, Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取 " + owner.getName() + "#" + name, e);
        }
    }

    public static void set(Object target, Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入 " + owner.getName() + "#" + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T call(Object target, Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法调用 " + owner.getName() + "#" + name, e);
        }
    }
}
