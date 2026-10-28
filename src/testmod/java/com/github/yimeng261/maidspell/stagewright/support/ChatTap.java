package com.github.yimeng261.maidspell.stagewright.support;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 客户端实际收到的系统消息（聊天栏与动作栏）。集成服拓扑下由客户端监听器在客户端线程写入，
 * 场景在服务器线程读取；只依赖通用类，专用服务器上也能加载（只是永远为空）。
 * <p>用法：动作前取 {@link #mark()}，之后用 {@link #since}/{@link #find} 只看这之后收到的消息。
 */
public final class ChatTap {
    /** 只追加不删除，所以 mark 就是当时的条数，之后的消息从这个下标开始。 */
    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();

    private ChatTap() {
    }

    /** @param overlay true 为动作栏消息 */
    public record Received(Component message, boolean overlay) {
        /** 可翻译文本的键；不是可翻译文本时为 null。 */
        public String key() {
            return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
        }

        public Object[] args() {
            return message.getContents() instanceof TranslatableContents translatable ? translatable.getArgs() : new Object[0];
        }

        /** 第 i 个参数按整数读取（参数经网络传输后可能是数字、字符串或文本组件）。 */
        public int intArg(int index) {
            Object arg = args()[index];
            if (arg instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(arg instanceof Component component ? component.getString() : String.valueOf(arg));
        }

        @Override
        public String toString() {
            return (overlay ? "[动作栏] " : "[聊天] ") + (key() != null ? key() : message.getString());
        }
    }

    public static void add(Component message, boolean overlay) {
        RECEIVED.add(new Received(message, overlay));
    }

    public static long mark() {
        return RECEIVED.size();
    }

    public static List<Received> since(long mark) {
        return collect(mark, r -> true);
    }

    public static List<Received> since(long mark, boolean overlay) {
        return collect(mark, r -> r.overlay() == overlay);
    }

    /** mark 之后第一条带该键的消息。 */
    public static Optional<Received> find(long mark, String key, boolean overlay) {
        for (int i = (int) mark, size = RECEIVED.size(); i < size; i++) {
            Received received = RECEIVED.get(i);
            if (received.overlay() == overlay && key.equals(received.key())) {
                return Optional.of(received);
            }
        }
        return Optional.empty();
    }

    /** mark 之后收到的消息键（不是可翻译文本的按原文），按收到顺序。 */
    public static List<String> keys(long mark, boolean overlay) {
        return since(mark, overlay).stream().map(r -> r.key() != null ? r.key() : r.message().getString()).toList();
    }

    private static List<Received> collect(long mark, Predicate<Received> filter) {
        List<Received> out = new ArrayList<>();
        for (int i = (int) mark, size = RECEIVED.size(); i < size; i++) {
            Received received = RECEIVED.get(i);
            if (filter.test(received)) {
                out.add(received);
            }
        }
        return out;
    }
}
