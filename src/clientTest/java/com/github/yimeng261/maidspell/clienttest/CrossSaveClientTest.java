package com.github.yimeng261.maidspell.clienttest;

import com.google.gson.JsonObject;
import net.magicterra.stagewright.junit.Face;
import net.magicterra.stagewright.junit.RequiresFace;
import net.magicterra.stagewright.junit.StageWright;
import net.magicterra.stagewright.junit.StageWrightExtension;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨存档流程：进入归隐之地后退出到标题、立即新建另一个存档再用寻风之铃；刚摇铃（维度还在创建）就退出，
 * 重新打开同一存档再进归隐之地。两者都要求客户端不崩溃、能照常进入归隐之地。
 * <p>会离开 hold 住的存档，跑完后这个 hold 不能再给别的测试用。默认不跑，带 {@code -PcrossSave} 才跑：
 * 先 {@code ./gradlew stagewrightIntegratedServerHold}，再
 * {@code TESTKIT_ENDPOINT=run-stagewright-integratedServer/stagewright/stagewright-endpoint.json ./gradlew clientTest -PcrossSave}。
 */
@Tag("crossSave")
@ExtendWith(StageWrightExtension.class)
@RequiresFace(Face.CLIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossSaveClientTest {
    private static final String BELL = "touhou_little_maid_spell:wind_seeking_bell";
    private static final Duration ENTER = Duration.ofSeconds(60);
    private static final Duration WORLD = Duration.ofSeconds(180);
    private static final String SECOND_WORLD = "TlmsCrossSave-" + java.util.UUID.randomUUID();

    private final StageWright sw;

    CrossSaveClientTest(StageWright sw) {
        this.sw = sw;
    }

    /** 当前存档进入归隐之地 → 退出到标题 → 新建另一个存档 → 在新存档里摇铃照常进入归隐之地。 */
    @Test
    @Order(1)
    void retreatAfterSwitchingSaves() {
        enterRetreatWithBell();
        ClientWorlds.quitToTitle(sw);
        ClientWorlds.createFresh(sw, SECOND_WORLD, 20260901L);
        awaitInWorld();
        enterRetreatWithBell();
        assertAlive();
    }

    /** 确认维度创建挂起后退出→ 重新打开同一存档 → 再摇铃照常进入。 */
    @Test
    @Order(2)
    void quitWhileRetreatCreating() {
        ClientWorlds.quitToTitle(sw);
        ClientWorlds.createFresh(sw, "TlmsPendingSave-" + java.util.UUID.randomUUID(), 20260902L);
        awaitInWorld();
        holdBell();
        String level = ClientWorlds.currentLevelId(sw);
        ClientWorlds.quitWithPendingRetreat(sw, playerName());
        ClientWorlds.open(sw, level);
        awaitInWorld();
        sw.exec("execute in minecraft:overworld run tp " + playerName() + " 0 100 0");
        enterRetreatWithBell();
        assertAlive();
    }

    private void enterRetreatWithBell() {
        holdBell();
        sw.call("mc.bot.useItem");
        sw.awaitCondition(() -> dimension().contains("retreat"), ENTER);
    }

    private void holdBell() {
        sw.exec("give " + playerName() + " " + BELL);
        JsonObject params = new JsonObject();
        params.addProperty("item", BELL);
        sw.call("mc.bot.holdItem", params);
        var hotbar = sw.observePlayer().getAsJsonArray("hotbar");
        int bellSlot = -1;
        for (int slot = 0; slot < hotbar.size(); slot++) {
            JsonObject stack = hotbar.get(slot).getAsJsonObject();
            if (stack.has("id") && BELL.equals(stack.get("id").getAsString())) bellSlot = slot;
        }
        assertTrue(bellSlot >= 0, "铃在快捷栏中");
        JsonObject selected = new JsonObject();
        selected.addProperty("slot", bellSlot);
        sw.call("mc.client.input.setHotbarSlot", selected);
        sw.awaitCondition(() -> {
            var hand = sw.call("mc.client.player").getAsJsonObject("mainHand");
            return hand.has("id") && BELL.equals(hand.get("id").getAsString());
        }, Duration.ofSeconds(10));
        JsonObject settle = new JsonObject();
        settle.addProperty("ticks", 5);
        sw.call("mc.system.waitTicks", settle);
    }

    private void awaitInWorld() {
        sw.awaitCondition(() -> {
            if (!ClientWorlds.operationDone(sw)) return false;
            JsonObject info = sw.screenInfo();
            return info.has("worldOpen") && info.get("worldOpen").getAsBoolean()
                    && info.has("hasPlayer") && info.get("hasPlayer").getAsBoolean();
        }, WORLD);
    }

    private String dimension() {
        JsonObject player = sw.observePlayer();
        return player.has("dimension") ? player.get("dimension").getAsString() : "";
    }

    private String playerName() {
        return sw.observePlayer().get("name").getAsString();
    }

    private void assertAlive() {
        JsonObject version = sw.call("mc.system.version");
        assertTrue(version != null && version.size() > 0, "客户端仍在响应：" + version);
    }
}
