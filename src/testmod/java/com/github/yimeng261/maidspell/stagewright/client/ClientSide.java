package com.github.yimeng261.maidspell.stagewright.client;

import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 集成服场景读写同一 JVM 里的客户端：把操作交给客户端线程执行，场景在服务端线程上等结果。
 * <p>只在集成拓扑的场景里使用；专用服上没有客户端。
 */
public final class ClientSide {
    /** 等客户端线程执行完的上限。 */
    private static final int WAIT_TICKS = 40;

    private ClientSide() {
    }

    /** 在客户端线程上运行 action，完成后在服务端线程上把结果交给 then。action 抛出的异常让场景失败。 */
    public static <T> void call(SceneContext ctx, Supplier<T> action, Consumer<T> then) {
        CompletableFuture<T> future = Minecraft.getInstance().submit(action);
        ctx.await(future::isDone).within(WAIT_TICKS).then(() -> then.accept(future.join()));
    }

    public static void run(SceneContext ctx, Runnable action, Runnable then) {
        call(ctx, () -> {
            action.run();
            return Boolean.TRUE;
        }, ignored -> then.run());
    }

    /** 当前界面里消息为该翻译键的第一个控件（没有时为 null）。只能在客户端线程上调用。 */
    public static AbstractWidget widget(Screen screen, String translationKey) {
        return widget(screen, w -> translationKey.equals(key(w.getMessage())));
    }

    public static AbstractWidget widget(Screen screen, Predicate<AbstractWidget> filter) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w && filter.test(w)) {
                return w;
            }
        }
        return null;
    }

    public static List<AbstractWidget> widgets(Screen screen) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
    }

    /** 文本组件的翻译键；不是翻译组件时为 null。 */
    public static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    /** 按指定语言加载本客户端的全部语言文件（与切换语言时一样，含所有模组）。只能在客户端线程上调用。 */
    public static ClientLanguage language(String code) {
        return ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), List.of(code), false);
    }
}
