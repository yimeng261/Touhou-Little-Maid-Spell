package com.github.yimeng261.maidspell.clienttest;

import com.google.gson.JsonObject;
import net.magicterra.stagewright.junit.StageWright;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户端存档操作通过测试运行时 RPC 执行，操作异常直接让测试失败。 */
final class ClientWorlds {
    private static final Duration TITLE = Duration.ofSeconds(60);

    private ClientWorlds() {
    }

    private static JsonObject call(StageWright sw, String operation, JsonObject params) {
        params.addProperty("operation", operation);
        return sw.call("maidspell.test.worlds", params);
    }

    private static JsonObject call(StageWright sw, String operation) {
        return call(sw, operation, new JsonObject());
    }

    private static void awaitTitle(StageWright sw) {
        sw.awaitCondition(() -> {
            if (!operationDone(sw)) return false;
            JsonObject info = sw.screenInfo();
            return info.has("type") && info.get("type").getAsString().contains("TitleScreen")
                    && !(info.has("worldOpen") && info.get("worldOpen").getAsBoolean());
        }, TITLE);
    }

    static void quitToTitle(StageWright sw) {
        call(sw, "quit");
        awaitTitle(sw);
    }

    static void quitWithPendingRetreat(StageWright sw, String player) {
        JsonObject params = new JsonObject();
        params.addProperty("player", player);
        call(sw, "quitPending", params);
        awaitTitle(sw);
        assertTrue(call(sw, "creationCanceled").get("canceled").getAsBoolean(),
                "退出时挂起的归隐维度创建请求应取消");
    }

    static void createFresh(StageWright sw, String name, long seed) {
        JsonObject params = new JsonObject();
        params.addProperty("levelId", name);
        params.addProperty("seed", seed);
        call(sw, "create", params);
    }

    static void open(StageWright sw, String levelId) {
        JsonObject params = new JsonObject();
        params.addProperty("levelId", levelId);
        call(sw, "open", params);
    }

    static boolean operationDone(StageWright sw) {
        return call(sw, "done").get("done").getAsBoolean();
    }

    static String currentLevelId(StageWright sw) {
        String id = call(sw, "current").get("levelId").getAsString();
        assertTrue(!id.isEmpty(), "不在单人存档里");
        return id;
    }
}
