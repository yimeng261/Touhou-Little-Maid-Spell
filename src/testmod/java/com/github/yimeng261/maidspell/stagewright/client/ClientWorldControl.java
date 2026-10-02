package com.github.yimeng261.maidspell.stagewright.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 客户端测试的存档操作入口，所有 Minecraft 对象访问都在客户端线程执行。 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "touhou_little_maid_spell", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientWorldControl {
    private static boolean registered;

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (registered || net.magicterra.worlddriver.WorldDriverCommon.api() == null) return;
        var schema = net.magicterra.worlddriver.mcp.schema.Schemas.tool("maidspell.test.worlds",
                "客户端测试存档操作", net.magicterra.worlddriver.mcp.schema.Schemas.object()
                        .req("operation", net.magicterra.worlddriver.mcp.schema.Schemas.stringEnum(
                                "quit", "create", "open", "current", "done", "quitPending", "creationCanceled"))
                        .prop("levelId", net.magicterra.worlddriver.mcp.schema.Schemas.string())
                        .prop("seed", net.magicterra.worlddriver.mcp.schema.Schemas.integer())
                        .prop("player", net.magicterra.worlddriver.mcp.schema.Schemas.string())
                        .additionalProperties(false)).asHidden();
        net.magicterra.worlddriver.mcp.ToolCatalog.registerVerb(schema, params -> {
            try {
                return switch ((String) params.get("operation")) {
                    case "quit" -> { INSTANCE.quitToTitle(); yield java.util.Map.of("queued", true); }
                    case "create" -> {
                        INSTANCE.createFresh((String) params.get("levelId"), ((Number) params.get("seed")).longValue());
                        yield java.util.Map.of("queued", true);
                    }
                    case "open" -> { INSTANCE.open((String) params.get("levelId")); yield java.util.Map.of("queued", true); }
                    case "quitPending" -> {
                        INSTANCE.quitWithPendingRetreat((String) params.get("player")); yield java.util.Map.of("queued", true);
                    }
                    case "current" -> java.util.Map.of("levelId", INSTANCE.currentLevelId());
                    case "done" -> java.util.Map.of("done", INSTANCE.operationDone());
                    case "creationCanceled" -> java.util.Map.of("canceled", INSTANCE.pendingRetreatCanceled());
                    default -> throw new IllegalArgumentException("未知存档操作");
                };
            } catch (Exception e) {
                throw new IllegalStateException("客户端存档操作失败", e);
            }
        });
        registered = true;
    }
    public static final ClientWorldControl INSTANCE = new ClientWorldControl();
    private volatile CompletableFuture<?> pending = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<?> retreatCreation;

    private ClientWorldControl() {
    }

    public void quitToTitle() {
        queue(ClientWorldControl::quitNow);
    }

    private static void quitNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.disconnect();
        mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        mc.setScreen(new TitleScreen());
    }

    /** 在创建请求挂起的同一服务端任务里请求停止，随后让客户端退出。 */
    public void quitWithPendingRetreat(String playerName) {
        if (!operationDone()) throw new IllegalStateException("上一个存档操作尚未完成");
        Minecraft mc = Minecraft.getInstance();
        pending = mc.submit(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("未打开单人存档");
            return server.submit(() -> {
                var player = server.getPlayerList().getPlayerByName(playerName);
                if (player == null) throw new IllegalStateException("测试玩家不在线");
                player.gameMode.useItem(player, player.level(), player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND);
                retreatCreation = com.github.yimeng261.maidspell.dimension.PlayerRetreatManager
                        .getOrCreatePlayerRetreatAsync(server, player.getUUID());
                if (retreatCreation.isDone()) throw new IllegalStateException("归隐维度创建未处于挂起状态");
                server.halt(false);
                return mc.submit(ClientWorldControl::quitNow);
            });
        }).thenCompose(operation -> operation).thenCompose(operation -> operation);
    }

    public boolean pendingRetreatCanceled() {
        return retreatCreation != null && retreatCreation.isCompletedExceptionally();
    }

    public void createFresh(String levelId, long seed) {
        queue(() -> {
            LevelSettings settings = new LevelSettings(levelId, GameType.CREATIVE, false, Difficulty.NORMAL,
                    true, new GameRules(), WorldDataConfiguration.DEFAULT);
            Minecraft.getInstance().createWorldOpenFlows().createFreshLevel(levelId, settings,
                    new WorldOptions(seed, true, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
        });
    }

    public void open(String levelId) {
        queue(() -> Minecraft.getInstance().createWorldOpenFlows().openWorld(levelId,
                () -> Minecraft.getInstance().setScreen(new TitleScreen())));
    }

    public String currentLevelId() throws Exception {
        return Minecraft.getInstance().submit(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) throw new IllegalStateException("未打开单人存档");
            return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        }).get(30, TimeUnit.SECONDS);
    }

    public boolean operationDone() {
        if (!pending.isDone()) return false;
        pending.join();
        return true;
    }

    private void queue(Runnable operation) {
        if (!operationDone()) throw new IllegalStateException("上一个存档操作尚未完成");
        pending = Minecraft.getInstance().submit(operation);
    }
}
