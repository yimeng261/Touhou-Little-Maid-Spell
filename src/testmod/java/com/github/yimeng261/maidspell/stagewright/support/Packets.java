package com.github.yimeng261.maidspell.stagewright.support;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 以某名玩家的身份在服务端直接处理一个收到的数据包：造一个只实现处理函数会用到的方法的
 * {@link IPayloadContext}（发送者、排队到主线程——这里就地执行、方向）。场景本来就在服务端主线程上。
 */
public final class Packets {
    private Packets() {
    }

    public static IPayloadContext from(ServerPlayer sender) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> sender;
                    case "flow" -> PacketFlow.SERVERBOUND;
                    case "enqueueWork" -> {
                        Object task = args[0];
                        if (task instanceof Runnable runnable) {
                            runnable.run();
                            yield CompletableFuture.completedFuture(null);
                        }
                        yield CompletableFuture.completedFuture(((Supplier<?>) task).get());
                    }
                    case "toString" -> "TestPayloadContext[" + sender.getScoreboardName() + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("测试用数据包上下文不支持 " + method.getName());
                });
    }
}
