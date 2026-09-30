package com.github.yimeng261.maidspell.stagewright.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.client.gui.EnderPocketHudEditorScreen;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen;
import com.github.yimeng261.maidspell.client.overlay.EnderPocketHudOverlay;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 末影腰包的客户端界面（集成服，直接操作同一 JVM 里的客户端）：面板的状态栏开关、每只女仆的显示勾选框、
 * 超过 8 只时的滚动、锚定女仆行的传送按钮布局；状态栏位置编辑界面的输入、重置、保存、取消与越界限制；
 * 状态栏数据每秒刷新；客户端配置文件生成并写回。
 */
public final class EnderPocketClientScenes {
    private static final Path CLIENT_CONFIG = FMLPaths.CONFIGDIR.get().resolve("touhou_little_maid_spell-client.toml");

    private EnderPocketClientScenes() {
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("client.config.clientConfigGenerated", 5, EnderPocketClientScenes::configGenerated));
        scenes.add(Players.hostScene("client.config.everyKeyTranslated", 10, EnderPocketClientScenes::configTranslated));
        scenes.add(Players.hostScene("client.ender_pocket.hudToggleButton", 20, EnderPocketClientScenes::hudToggle));
        scenes.add(Players.hostScene("client.ender_pocket.maidVisibilityCheckbox", 20, EnderPocketClientScenes::maidCheckbox));
        scenes.add(Players.hostScene("client.ender_pocket.allHiddenLeavesPlaceholder", 20, EnderPocketClientScenes::allHidden));
        scenes.add(Players.hostScene("client.ender_pocket.listScrollsBeyondEight", 20, EnderPocketClientScenes::scrolls));
        scenes.add(Players.hostScene("client.ender_pocket.anchorRowLayout", 20, EnderPocketClientScenes::anchorRow));
        scenes.add(Players.hostScene("client.ender_pocket.hudEditorSaveResetCancel", 30, EnderPocketClientScenes::editor));
        scenes.add(Players.hostScene("client.ender_pocket.hudEditorClampsPosition", 20, EnderPocketClientScenes::editorClamps));
        scenes.add(Players.hostScene("client.ender_pocket.hudDataRefreshes", 200, EnderPocketClientScenes::hudRefreshes));
        return scenes;
    }

    private static EnderPocketService.EnderPocketMaidInfo info(String name, boolean anchor) {
        return new EnderPocketService.EnderPocketMaidInfo(UUID.nameUUIDFromBytes(name.getBytes()), name, Level.OVERWORLD, -1,
                20, 20, 0, 0, 64, 0, anchor, "touhou_little_maid:hakurei_reimu");
    }

    private static List<EnderPocketService.EnderPocketMaidInfo> infos(int count) {
        return IntStream.range(0, count).mapToObj(i -> info("TlmsHud" + i, false)).toList();
    }

    /** 打开末影腰包面板；场景结束时关掉界面，并恢复状态栏开关与隐藏名单。 */
    private static void open(SceneContext ctx, List<EnderPocketService.EnderPocketMaidInfo> infos, Runnable then) {
        ClientSide.run(ctx, () -> {
            boolean enabled = EnderPocketClientConfig.HUD_ENABLED.get();
            List<String> hidden = List.copyOf(EnderPocketClientConfig.HUD_HIDDEN_MAIDS.get());
            int x = EnderPocketClientConfig.HUD_X.get();
            int y = EnderPocketClientConfig.HUD_Y.get();
            ctx.cleanup(() -> Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(null);
                EnderPocketClientConfig.HUD_HIDDEN_MAIDS.set(hidden);
                EnderPocketClientConfig.setHudEnabled(enabled);
                EnderPocketClientConfig.setPosition(x, y);
            }));
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos));
        }, then);
    }

    private static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    private static String configText() {
        try {
            return Files.readString(CLIENT_CONFIG);
        } catch (IOException e) {
            return "<读不到 " + CLIENT_CONFIG + ": " + e.getMessage() + ">";
        }
    }

    /** 客户端配置文件已生成，含状态栏的四项。 */
    private static void configGenerated(SceneContext ctx, ServerPlayer player) {
        String text = configText();
        for (String key : List.of("hudEnabled", "hudX", "hudY", "hudHiddenMaidUuids")) {
            ctx.check(text.contains(key)).as(CLIENT_CONFIG.getFileName() + " 含 " + key).isTrue();
        }
    }

    /** 配置界面里每个配置项与分组（通用与客户端）在中英文下都有标题；铁魔法法术黑名单的标题点明是铁魔法。 */
    private static void configTranslated(SceneContext ctx, ServerPlayer player) {
        List<String> keys = new ArrayList<>();
        collectKeys(Config.SPEC.getSpec(), keys);
        collectKeys(EnderPocketClientConfig.SPEC.getSpec(), keys);
        ClientSide.call(ctx, () -> {
            List<String> result = new ArrayList<>();
            for (String code : List.of("en_us", "zh_cn")) {
                ClientLanguage language = ClientSide.language(code);
                keys.stream().filter(key -> !language.has(key)).forEach(key -> result.add(code + " 缺 " + key));
                result.add(code + " 黑名单=" + language.getOrDefault(NS.replace(':', '.') + "configuration.spellBlacklist"));
            }
            return result;
        }, result -> {
            ctx.record("keys", keys.size());
            ctx.check(result.stream().filter(line -> line.contains(" 缺 ")).toList()).as("缺翻译的配置键").isEmpty();
            String en = result.stream().filter(line -> line.startsWith("en_us 黑名单=")).findFirst().orElse("");
            String zh = result.stream().filter(line -> line.startsWith("zh_cn 黑名单=")).findFirst().orElse("");
            ctx.check(en.contains("Iron")).as(en).isTrue();
            ctx.check(zh.contains("铁魔法")).as(zh).isTrue();
        });
    }

    private static void collectKeys(UnmodifiableConfig spec, Collection<String> keys) {
        spec.valueMap().forEach((key, value) -> {
            keys.add(NS.replace(':', '.') + "configuration." + key);
            if (value instanceof UnmodifiableConfig section) {
                collectKeys(section, keys);
            }
        });
    }

    /** 面板左上角的"关闭/显示"按钮切换状态栏开关，文字随之切换，并写回配置文件。 */
    private static void hudToggle(SceneContext ctx, ServerPlayer player) {
        open(ctx, infos(1), () -> ClientSide.call(ctx, () -> {
            EnderPocketClientConfig.setHudEnabled(true);
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos(1)));
            AbstractWidget toggle = ClientSide.widget(screen(), "gui.maidspell.ender_pocket.hud_hide");
            if (toggle == null) {
                return "找不到状态栏开关按钮";
            }
            toggle.onClick(toggle.getX() + 1, toggle.getY() + 1);
            return EnderPocketClientConfig.HUD_ENABLED.get() + "|" + ClientSide.key(toggle.getMessage());
        }, result -> {
            ctx.check(result).as("点一下后的开关状态与按钮文字").isEqualTo("false|gui.maidspell.ender_pocket.hud_show");
            ctx.check(configText().contains("hudEnabled = false")).as("配置文件写回 hudEnabled = false").isTrue();
        }));
    }

    /** 取消某只女仆的勾选：写进隐藏名单与配置文件，提示变为"隐藏"；编辑预览只按可见的女仆计高度。 */
    private static void maidCheckbox(SceneContext ctx, ServerPlayer player) {
        List<EnderPocketService.EnderPocketMaidInfo> infos = infos(3);
        UUID first = infos.getFirst().maidUUID;
        open(ctx, infos, () -> ClientSide.call(ctx, () -> {
            EnderPocketHudOverlay.update(infos);
            int before = EnderPocketHudOverlay.getEditorPreviewHeight(1000, 0);
            AbstractWidget box = ClientSide.widget(screen(), "gui.maidspell.ender_pocket.hud_maid_visible");
            if (box == null) {
                return List.<Object>of("找不到勾选框");
            }
            box.onClick(box.getX() + 1, box.getY() + 1);
            return List.<Object>of(EnderPocketClientConfig.isMaidVisible(first), ClientSide.key(box.getMessage()),
                    before, EnderPocketHudOverlay.getEditorPreviewHeight(1000, 0));
        }, result -> {
            ctx.record("result", result);
            ctx.check(result.size()).as("勾选框操作结果 " + result).isEqualTo(4);
            if (result.size() != 4) {
                return;
            }
            ctx.check(result.get(0)).as("取消勾选后第一只女仆可见").isEqualTo(false);
            ctx.check(result.get(1)).as("取消勾选后的提示").isEqualTo("gui.maidspell.ender_pocket.hud_maid_hidden");
            ctx.check((int) result.get(3)).as("预览高度（3 只 → 2 只可见）").isLessThan((int) result.get(2));
            ctx.check(configText().contains(first.toString())).as("配置文件写回隐藏的女仆").isTrue();
        }));
    }

    /** 全部取消勾选：编辑预览退回一个占位框的高度。 */
    private static void allHidden(SceneContext ctx, ServerPlayer player) {
        List<EnderPocketService.EnderPocketMaidInfo> infos = infos(2);
        open(ctx, infos, () -> ClientSide.call(ctx, () -> {
            EnderPocketHudOverlay.update(infos);
            infos.forEach(info -> EnderPocketClientConfig.setMaidVisible(info.maidUUID, false));
            return EnderPocketHudOverlay.getEditorPreviewHeight(1000, 0);
        }, height -> ctx.check(height).as("全部隐藏后的预览高度（一个占位框）").isEqualTo(44)));
    }

    /** 超过 8 只时列表只显示 8 行，滚轮向下翻出第 9、10 只；不超过 8 只时滚轮无效。 */
    private static void scrolls(SceneContext ctx, ServerPlayer player) {
        List<EnderPocketService.EnderPocketMaidInfo> infos = infos(10);
        open(ctx, infos, () -> ClientSide.call(ctx, () -> {
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos));
            Screen screen = screen();
            List<String> before = names(screen);
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
            List<String> after = names(screen());
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos(5)));
            Screen few = screen();
            List<String> fewBefore = names(few);
            few.mouseScrolled(few.width / 2.0, few.height / 2.0, 0, -1);
            return List.of(before, after, fewBefore, names(few));
        }, result -> {
            ctx.check(result.get(0).size()).as("10 只时显示的行数").isEqualTo(8);
            ctx.check(result.get(1).contains("TlmsHud9")).as("向下滚两格后显示第 10 只").isTrue();
            ctx.check(result.get(1).contains("TlmsHud0")).as("向下滚两格后仍显示第 1 只").isFalse();
            ctx.check(result.get(3)).as("5 只时滚动后的行").isEqualTo(result.get(2));
        }));
    }

    private static List<String> names(Screen screen) {
        return ClientSide.widgets(screen).stream()
                .filter(w -> ClientSide.key(w.getMessage()) == null && !w.getMessage().getString().isEmpty())
                .map(w -> w.getMessage().getString()).toList();
    }

    /** 锚定女仆的行多一个传送按钮；勾选框、名字按钮、传送按钮互不重叠。 */
    private static void anchorRow(SceneContext ctx, ServerPlayer player) {
        List<EnderPocketService.EnderPocketMaidInfo> infos = List.of(info("TlmsAnchor", true), info("TlmsPlain", false));
        open(ctx, infos, () -> ClientSide.call(ctx, () -> {
            Screen screen = screen();
            List<AbstractWidget> teleports = ClientSide.widgets(screen).stream()
                    .filter(w -> "gui.maidspell.ender_pocket.teleport".equals(ClientSide.key(w.getMessage()))).toList();
            AbstractWidget name = ClientSide.widget(screen, w -> w.getMessage().getString().equals("TlmsAnchor"));
            AbstractWidget box = ClientSide.widget(screen, w -> "gui.maidspell.ender_pocket.hud_maid_visible".equals(ClientSide.key(w.getMessage())));
            List<String> overlaps = new ArrayList<>();
            if (teleports.size() == 1 && name != null && box != null) {
                AbstractWidget teleport = teleports.getFirst();
                if (box.getRight() > name.getX()) {
                    overlaps.add("勾选框与名字");
                }
                if (name.getRight() > teleport.getX()) {
                    overlaps.add("名字与传送");
                }
            }
            return teleports.size() + "|" + overlaps;
        }, result -> ctx.check(result).as("传送按钮个数|重叠的控件").isEqualTo("1|[]")));
    }

    /** 编辑界面：输入坐标后保存写回配置；重置回 (6, 6)；取消不保存。 */
    private static void editor(SceneContext ctx, ServerPlayer player) {
        open(ctx, infos(1), () -> ClientSide.call(ctx, () -> {
            EnderPocketClientConfig.setPosition(6, 6);
            Screen parent = screen();
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            setInputs(screen(), "40", "30");
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.save");
            String saved = EnderPocketClientConfig.HUD_X.get() + "," + EnderPocketClientConfig.HUD_Y.get() + "|" + (screen() == parent);
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            setInputs(screen(), "90", "80");
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.cancel");
            String cancelled = EnderPocketClientConfig.HUD_X.get() + "," + EnderPocketClientConfig.HUD_Y.get();
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.reset");
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.save");
            String reset = EnderPocketClientConfig.HUD_X.get() + "," + EnderPocketClientConfig.HUD_Y.get();
            return List.of(saved, cancelled, reset);
        }, result -> {
            ctx.check(result).as("保存 / 取消 / 重置后保存 的位置").isEqualTo(List.of("40,30|true", "40,30", "6,6"));
            ctx.check(configText().contains("hudX = 6") && configText().contains("hudY = 6")).as("配置文件写回位置").isTrue();
        }));
    }

    /** 输入越界的坐标：预览不出屏，也不压到底部工具栏上。 */
    private static void editorClamps(SceneContext ctx, ServerPlayer player) {
        open(ctx, infos(1), () -> ClientSide.call(ctx, () -> {
            Screen parent = screen();
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            Screen editor = screen();
            setInputs(editor, "99999", "99999");
            press(editor, "gui.maidspell.ender_pocket.hud_editor.save");
            int x = EnderPocketClientConfig.HUD_X.get();
            int y = EnderPocketClientConfig.HUD_Y.get();
            int maxX = editor.width - EnderPocketHudOverlay.ROW_WIDTH;
            int previewBottom = editor.height - 30 - 4;
            return List.of(x, maxX, y + EnderPocketHudOverlay.getEditorPreviewHeight(previewBottom, y), previewBottom);
        }, result -> {
            ctx.check(result.get(0)).as("x 被限制在屏幕内（最大 " + result.get(1) + "）").isEqualTo(result.get(1));
            ctx.check(result.get(2)).as("预览底边不压到工具栏（" + result.get(3) + "）").isAtMost(result.get(3));
        }));
    }

    private static void setInputs(Screen editor, String x, String y) {
        List<EditBox> boxes = ClientSide.widgets(editor).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
        boxes.get(0).setValue(x);
        boxes.get(1).setValue(y);
    }

    private static void press(Screen screen, String key) {
        AbstractWidget button = ClientSide.widget(screen, key);
        if (button == null) {
            throw new IllegalStateException("界面里没有按钮 " + key);
        }
        button.onClick(button.getX() + 1, button.getY() + 1);
    }

    /** 自己戴末影腰包的女仆出现在状态栏数据里，生命变化约 1 秒内刷新。 */
    private static void hudRefreshes(SceneContext ctx, ServerPlayer player) {
        EntityMaid maid = Players.ownedMaid(ctx, player, 3, 0, 0, NS + "ender_pocket");
        ClientSide.run(ctx, () -> EnderPocketClientConfig.setHudEnabled(true), () ->
                awaitHudHealth(ctx, maid, maid.getMaxHealth(), 100, () -> {
                    Actors.setMaidHealth(maid, maid.getMaxHealth() / 2);
                    awaitHudHealth(ctx, maid, maid.getMaxHealth() / 2, 40, () -> ctx.passNote("状态栏生命已刷新"));
                }));
    }

    private static void awaitHudHealth(SceneContext ctx, EntityMaid maid, float health, int within, Runnable then) {
        float[] seen = {Float.NaN};
        ctx.await(() -> {
            java.util.concurrent.CompletableFuture<Float> future = Minecraft.getInstance().submit(() -> {
                List<EnderPocketService.EnderPocketMaidInfo> infos = Reflect.field(null, EnderPocketHudOverlay.class, "maidInfos");
                return infos.stream().filter(info -> info.maidUUID.equals(maid.getUUID())).map(info -> info.health).findFirst().orElse(Float.NaN);
            });
            seen[0] = future.join();
            return Math.abs(seen[0] - health) < 1e-3;
        }).within(within).then(then);
        ctx.record("hudHealthLastSeen", seen);
    }
}
