package com.github.yimeng261.maidspell.clienttest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.magicterra.stagewright.junit.Face;
import net.magicterra.stagewright.junit.RequiresFace;
import net.magicterra.stagewright.junit.StageWright;
import net.magicterra.stagewright.junit.StageWrightExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附着到 hold 住的 integratedServer 客户端，按玩家的操作方式驱动界面：
 * 右键法术白名单打开法术收集界面；按末影腰包按键打开女仆列表，点选后打开女仆背包。
 * <p>先 {@code ./gradlew stagewrightIntegratedServerHold}，再
 * {@code TESTKIT_ENDPOINT=run-stagewright-integratedServer/stagewright/stagewright-endpoint.json ./gradlew clientTest}。
 */
@ExtendWith(StageWrightExtension.class)
@RequiresFace(Face.CLIENT)
class MaidSpellClientTest {
    private static final String NS = "touhou_little_maid_spell:";
    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final String POCKET_MAID = "TlmsPocketMaid";

    private final StageWright sw;

    MaidSpellClientTest(StageWright sw) {
        this.sw = sw;
    }

    @AfterEach
    void closeScreen() {
        sw.call("mc.client.screen.close");
    }

    /** 手持法术白名单右键：打开"法术白名单 - 法术收集"界面，27 个卷轴格加玩家背包 36 格。 */
    @Test
    void blueNoteOpensSpellCollection() throws IOException {
        String player = playerName();
        sw.exec("give " + player + " " + NS + "blue_note");
        try {
            assertOk(sw.call("mc.bot.holdItem", params("item", NS + "blue_note")), "手持法术白名单");
            sw.call("mc.bot.useItem");
            sw.awaitCondition(() -> screenType().contains("SpellWhiteList"), WAIT);
            String title = sw.screenInfo().get("title").getAsString();
            assertTrue(translations("container.maidspell.blue_note").contains(title), "界面标题：" + title);
            JsonObject container = sw.call("mc.observe.container");
            assertEquals(27 + 36, container.getAsJsonArray("slots").size(), "界面格数");
        } finally {
            sw.exec("clear " + player + " " + NS + "blue_note");
        }
    }

    /** 按末影腰包按键：列出装备末影腰包的自有女仆，点选后打开她的背包界面。 */
    @Test
    void enderPocketKeyOpensMaidBackpack() {
        JsonObject player = sw.observePlayer();
        String name = player.get("name").getAsString();
        JsonObject pos = player.getAsJsonObject("pos");
        UUID uuid = UUID.fromString(player.get("uuid").getAsString());
        sw.exec(String.format("summon touhou_little_maid:maid %.1f %.1f %.1f {NoAI:1b,PersistenceRequired:1b,"
                        + "CustomName:'\"%s\"',Owner:%s,MaidBaubleInventory:{Items:[{Slot:0,id:\"%sender_pocket\",count:1}]}}",
                pos.get("x").getAsDouble() + 2, pos.get("y").getAsDouble(), pos.get("z").getAsDouble(),
                POCKET_MAID, intArray(uuid), NS));
        try {
            // 列表由服务端在按键时生成，打开后不会刷新；女仆刚召唤时可能还没登记，列表里没有她就关掉重按
            sw.awaitCondition(() -> {
                if (screenType().contains("EnderPocket")) {
                    if (findWidget(sw.screenTree(), POCKET_MAID).isPresent()) {
                        return true;
                    }
                    sw.call("mc.client.screen.close");
                }
                sw.call("mc.client.input.keybind", params("name", "key.touhou_little_maid_spell.open_ender_pocket"));
                return false;
            }, WAIT);
            JsonObject entry = findWidget(sw.screenTree(), POCKET_MAID).orElseThrow();
            sw.click(entry.get("x").getAsInt() + entry.get("width").getAsInt() / 2,
                    entry.get("y").getAsInt() + entry.get("height").getAsInt() / 2);
            // 打开的是 TLM 的女仆背包界面（按背包类型不同，类名如 EmptyBackpackContainerScreen、MaidMainContainerScreen）
            sw.awaitCondition(() -> {
                String type = screenType();
                return type.endsWith("ContainerScreen") && !type.contains("EnderPocket");
            }, WAIT);
        } finally {
            sw.exec("tp @e[type=touhou_little_maid:maid,name=" + POCKET_MAID + "] ~ -200 ~");
            sw.exec("kill @e[type=touhou_little_maid:maid,name=" + POCKET_MAID + "]");
        }
    }

    // ---- 工具 ----

    private String playerName() {
        return sw.observePlayer().get("name").getAsString();
    }

    private String screenType() {
        JsonObject info = sw.screenInfo();
        return info.has("type") ? info.get("type").getAsString() : "";
    }

    /** 控件树里按显示文字找控件（递归）。 */
    private static Optional<JsonObject> findWidget(JsonObject node, String message) {
        if (node.has("message") && node.get("message").getAsString().contains(message)) {
            return Optional.of(node);
        }
        if (node.has("children")) {
            for (JsonElement child : node.getAsJsonArray("children")) {
                Optional<JsonObject> found = findWidget(child.getAsJsonObject(), message);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** 中英文语言文件里该键的译文（客户端语言不固定）。 */
    private static Set<String> translations(String key) throws IOException {
        Path lang = Path.of("src/main/resources/assets/touhou_little_maid_spell/lang");
        return Set.of(read(lang.resolve("zh_cn.json")).get(key).getAsString(),
                read(lang.resolve("en_us.json")).get(key).getAsString());
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String intArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return "[I;" + (int) (most >> 32) + "," + (int) most + "," + (int) (least >> 32) + "," + (int) least + "]";
    }

    private static JsonObject params(String key, String value) {
        JsonObject params = new JsonObject();
        params.addProperty(key, value);
        return params;
    }

    private static void assertOk(JsonObject result, String what) {
        assertTrue(!result.has("ok") || result.get("ok").getAsBoolean(), what + "：" + result);
    }
}
