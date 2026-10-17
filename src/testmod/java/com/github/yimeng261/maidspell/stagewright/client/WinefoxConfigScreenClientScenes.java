package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.client.gui.WinefoxChallengeConfigScreen;
import com.github.yimeng261.maidspell.stagewright.support.Mismatches;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfig;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengeConfigMenu;
import com.github.yimeng261.maidspell.winefox.WinefoxChallengePresets;
import io.netty.buffer.Unpooled;
import net.magicterra.stagewright.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.gui.widget.ScrollPanel;

import java.util.List;
import java.util.function.Consumer;

/**
 * 酒狐挑战配置界面的输入路由：基础参数输入框反复选中仍能输入，滚动条拖动与松开，滚动后命中区域跟着移动，切换模式时释放隐藏输入框的焦点。
 * <p>界面只初始化、不显示，直接调用它的输入回调。
 */
public final class WinefoxConfigScreenClientScenes {
    private WinefoxConfigScreenClientScenes() {
    }

    public static List<Scene> integratedServer() {
        return List.of(
                scene("client.winefoxConfigScreen.basicInputsKeepFocus", WinefoxConfigScreenClientScenes::inputsKeepFocus),
                scene("client.winefoxConfigScreen.scrollbarDragAndOutsideRelease", WinefoxConfigScreenClientScenes::scrollbarDrag),
                scene("client.winefoxConfigScreen.scrollMovesHitboxes", WinefoxConfigScreenClientScenes::scrollMovesHitboxes),
                scene("client.winefoxConfigScreen.modeSwitchReleasesHiddenFocus", WinefoxConfigScreenClientScenes::modeSwitch));
    }

    private static Scene scene(String name, Consumer<Mismatches> body) {
        return Players.hostScene(name, 10, (ctx, player) -> ClientSide.verify(ctx, name, body));
    }

    private static final class TestScreen extends WinefoxChallengeConfigScreen {
        private TestScreen(WinefoxChallengeConfigMenu menu, Inventory inventory) {
            super(menu, inventory, Component.literal("Challenge"));
        }

        private int left() {
            return leftPos;
        }

        private int top() {
            return topPos;
        }

        private int inventoryY() {
            return imageHeight - 86;
        }
    }

    /** 按客户端发来的数据构造菜单，宽 400、指定高度初始化界面。只能在客户端线程上调用。 */
    private static TestScreen screen(int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Inventory inventory = minecraft.player.getInventory();
        WinefoxChallengeConfig config = new WinefoxChallengeConfig(600, 12, 1, 1, 0.08, 6,
                0.5, 5, 0.8, false, false, 25, List.of(), List.of(), false);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(0);
            config.writeToBuffer(buffer);
            config.writeToBuffer(buffer);
            WinefoxChallengePresets.writeBuffer(buffer, WinefoxChallengePresets.empty());
            TestScreen screen = new TestScreen(WinefoxChallengeConfigMenu.createClientSide(1, inventory, buffer), inventory);
            screen.init(minecraft, 400, height);
            return screen;
        } finally {
            buffer.release();
        }
    }

    private static EditBox[] fields(TestScreen screen) {
        return Reflect.field(screen, WinefoxChallengeConfigScreen.class, "fields");
    }

    /** 两个输入框来回选中多次，每次都直接收到界面的字符输入。 */
    private static void inputsKeepFocus(Mismatches m) {
        TestScreen screen = screen(240);
        EditBox[] fields = fields(screen);
        int step = 0;
        for (int index : new int[]{0, 1, 0, 1, 1}) {
            String label = "第 " + ++step + " 次选中输入框 " + index;
            EditBox field = fields[index];
            m.check(label + " 直接接收界面事件", screen.children().contains(field));
            screen.setFocused(field);
            m.check(label + " 成为界面焦点", screen.getFocused() == field);
            m.check(label + " 处于聚焦状态", field.isFocused());
            field.setValue("");
            m.check(label + " 接受字符 1", screen.charTyped('1', 0));
            m.check(label + " 接受字符 8", screen.charTyped('8', 0));
            m.equal(label + " 的内容", "18", field.getValue());
        }
    }

    /** 拖动滚动条使输入框上移；在面板外松开后不再残留拖动状态。 */
    private static void scrollbarDrag(Mismatches m) {
        TestScreen screen = screen(240);
        EditBox[] fields = fields(screen);
        int before = fields[0].getY();
        m.check("按下滚动条", screen.mouseClicked(screen.left() + 171, screen.top() + 45, 0));
        m.check("拖动滚动条", screen.mouseDragged(screen.left() + 171, screen.top() + 70, 0, 0, 25));
        m.check("拖动后输入框上移（" + before + " → " + fields[0].getY() + "）", fields[0].getY() < before);
        m.check("在面板外松开", screen.mouseReleased(screen.left() + 300, screen.top() + 150, 0));
        int released = fields[0].getY();
        ScrollPanel panel = Reflect.field(screen, WinefoxChallengeConfigScreen.class, "basicPanel");
        m.check("松开后面板不再响应拖动", !panel.mouseDragged(screen.left() + 171, screen.top() + 90, 0, 0, 20));
        m.equal("松开后输入框位置不变", released, fields[0].getY());
    }

    /** 滚轮滚动后命中区域跟着移动，滚出可见区的输入框不接收点击，滚进来的输入框可以输入且在背包区之上。 */
    private static void scrollMovesHitboxes(Mismatches m) {
        TestScreen screen = screen(180);
        EditBox[] fields = fields(screen);
        int before = fields[0].getY();
        m.check("滚轮被面板处理", screen.mouseScrolled(screen.left() + 40, screen.top() + 45, 0, -20));
        m.check("滚动后输入框上移（" + before + " → " + fields[0].getY() + "）", fields[0].getY() < before);
        m.check("滚出可见区的输入框不接收点击", !fields[0].mouseClicked(fields[0].getX() + 5, fields[0].getY() + 5, 0));
        screen.setFocused(fields[8]);
        m.check("第 9 个输入框聚焦", fields[8].isFocused());
        fields[8].setValue("");
        m.check("第 9 个输入框接受字符", screen.charTyped('1', 0));
        m.equal("第 9 个输入框的内容", "1", fields[8].getValue());
        m.check("第 9 个输入框在背包区之上", fields[8].getY() + fields[8].getHeight() < screen.top() + screen.inventoryY());
    }

    /** 切到高级模式时隐藏的输入框交出焦点并停用；切回后恢复编辑。 */
    private static void modeSwitch(Mismatches m) {
        TestScreen screen = screen(240);
        EditBox[] fields = fields(screen);
        screen.setFocused(fields[1]);
        Reflect.set(screen, WinefoxChallengeConfigScreen.class, "defaultsPlaced", true);
        Button mode = Reflect.field(screen, WinefoxChallengeConfigScreen.class, "modeButton");
        mode.onPress();
        m.check("切到高级模式后界面没有焦点", screen.getFocused() == null);
        m.check("基础输入框隐藏", !fields[1].visible);
        m.check("基础输入框停用", !fields[1].active);
        mode.onPress();
        m.check("切回后基础输入框可见", fields[1].visible);
        screen.setFocused(fields[1]);
        fields[1].setValue("");
        m.check("切回后接受字符", screen.charTyped('9', 0));
        m.equal("切回后的内容", "9", fields[1].getValue());
    }
}
