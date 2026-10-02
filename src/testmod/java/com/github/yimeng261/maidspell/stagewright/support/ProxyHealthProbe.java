package com.github.yimeng261.maidspell.stagewright.support;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.client.ProxyHealthClient;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 客户端读取远程代理生命值后经网络回传，场景按玩家和请求编号匹配应答。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ProxyHealthProbe {
    private record Pending(UUID player, Float health) { }
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int nextRequest;

    private ProxyHealthProbe() { }

    public record Request(int request, int entityId) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "test_proxy_health_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::request, ByteBufCodecs.VAR_INT, Request::entityId, Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Reply(int request, float health) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "test_proxy_health_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::request, ByteBufCodecs.FLOAT, Reply::health, Reply::new);
        @Override public Type<Reply> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Request.TYPE, Request.CODEC,
                (request, context) -> context.enqueueWork(() -> ProxyHealthClient.handle(request)));
        registrar.playToServer(Reply.TYPE, Reply.CODEC, (reply, context) -> context.enqueueWork(() -> {
            Pending pending = PENDING.get(reply.request());
            if (pending != null && pending.player().equals(context.player().getUUID())) {
                PENDING.put(reply.request(), new Pending(pending.player(), reply.health()));
            }
        }));
    }

    public static void awaitHealth(SceneContext ctx, ServerPlayer player, int entityId,
                                   float expected, int ticks, Runnable next) {
        int request = ++nextRequest;
        PENDING.put(request, new Pending(player.getUUID(), null));
        ctx.cleanup(() -> PENDING.remove(request));
        PacketDistributor.sendToPlayer(player, new Request(request, entityId));
        ctx.await(() -> {
            Pending pending = PENDING.get(request);
            if (pending.health() == null) return false;
            ctx.record("clientProxyHealth", pending.health());
            if (Math.abs(pending.health() - expected) < 0.001f) return true;
            PENDING.put(request, new Pending(player.getUUID(), null));
            PacketDistributor.sendToPlayer(player, new Request(request, entityId));
            return false;
        }).within(ticks).then(() -> {
            ctx.check(PENDING.get(request).health()).as("客户端代理生命值").isCloseTo(expected, 0.001f);
            PENDING.remove(request);
            next.run();
        });
    }
}
