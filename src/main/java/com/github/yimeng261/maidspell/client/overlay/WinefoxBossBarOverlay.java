package com.github.yimeng261.maidspell.client.overlay;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxBossBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 酒狐 Boss 血条的自绘贴图。
 *
 * <h2>为什么不去 Mixin {@code BossHealthOverlay}</h2>
 * Forge 在画每一条血条之前会派发 {@link CustomizeGuiOverlayEvent.BossEventProgress}，
 * 取消它就连名字一起不画，而 {@code setIncrement} 决定下一条从哪儿开始。
 * 原版 {@code drawBar} 是私有的、还分成两个重载，Mixin 进去要盯混淆名；
 * 这个事件本来就是给「换一套血条外观」准备的，用它不碰任何原版方法。
 *
 * <h2>怎么认出「这条是酒狐的」</h2>
 * 服务端把血条名写成 {@code translatable(WinefoxBossBar.NAME_KEY, 称号)}，
 * 键名跟着组件序列化一路到客户端，这里比一下 {@link TranslatableContents#getKey()} 就行。
 * 认不出来的（普通 Boss、别的模组的血条）原样交给原版画，出问题最多是退回原版外观。
 *
 * <h2>贴图怎么摆</h2>
 * 两张图都是 256x96 的画布，但画布只有上面 {@value #BAR_HEIGHT} 行是画面本体
 * （作者「居中置顶」放进去的 256x56），下面 40 行整片透明。所以：
 * <ul>
 *   <li>{@code base.png} 是整块底板——左右水晶端饰、中间那圈冠冕与挂在血槽上的月牙，
 *       血槽本身在 {@code y=30..40}、槽内是暗紫色空底；</li>
 *   <li>{@code layer.png} 是同一套坐标下的填充，只占 {@code y=33..37}、{@code x=18..237}。
 *       按血量从左往右裁，满血时正好铺满整条槽。</li>
 * </ul>
 * 本体只画 {@value #BAR_HEIGHT} 行：多画那 40 行透明像素除了浪费带宽没有任何作用，
 * 但 blit 的纹理尺寸仍要报整张画布的 256x96，否则 UV 会按 256x56 去算、贴图被拉长。
 *
 * <h2>名字画在哪</h2>
 * 上一版贴图把画面压到画布下半部分，上边留出的透明区正好放名字；这一版画面顶到了
 * 画布第 0 行，顶上没有位置了，于是名字挪到<b>本体下方</b>：{@value #NAME_Y_OFFSET} 起、
 * 高 9 格的一行，仍然水平居中，颜色改成紫色（{@code #C77DFF}，取自填充层的亮紫）。
 * 名字不属于贴图内容，它是每 tick 现画的文本，所以贴图里那 40 行留白只是画布余量。
 *
 * <p>顶上既然空了，整条血条也就跟着顶到屏幕最上面（见 {@link #VANILLA_BAR_TOP_Y}）：
 * 一对一开打时本体占 {@code y=0..55}、名字占 {@code y=58..66}。上一版是从 {@code y=12}
 * 起画满整张 96 行，可见的画面落在 {@code y=24..96}，名字在 {@code y=14}。
 *
 * <p>代价是 {@code increment} 按「本体 + 名字行 + 间距」报（{@value #NAME_Y_OFFSET} + 9 + 4）：
 * 原版 {@code BossHealthOverlay.render} 里每画一条就 {@code j += increment}，然后
 * {@code if (j >= guiHeight()/3) break;} —— 屏幕只有三分之一高，这条血条后面的别家血条
 * 就不会再画了。这是一场一对一 Boss 战，同屏多血条的场面本来不存在，换来的是贴图不被裁。
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WinefoxBossBarOverlay {

    private static final ResourceLocation BASE_TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/gui/boss_bar/base.png");
    private static final ResourceLocation LAYER_TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/gui/boss_bar/layer.png");

    /** 两张 PNG 画布的实际尺寸，blit 时当纹理尺寸报给 OpenGL。 */
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 96;

    /**
     * 画面本体：{@code 256x56}，贴在画布顶部。下边 {@code 96 - 56 = 40} 行是作者留的画布余量，
     * 除了给名字腾地方之外没有任何像素。
     */
    private static final int BAR_WIDTH = 256;
    private static final int BAR_HEIGHT = 56;

    /**
     * 血槽（{@code layer.png} 的填充区）在本体里的位置。
     *
     * <p>{@code x=18..237} 是 220 格宽——{@code base.png} 在 {@code y=35} 那一行的暗色
     * （{@code 52,38,63}）恰好只铺在这一段上，左右各 18 格是水晶端饰。
     * {@code layer.png} 的内容也正好落在这个矩形里（{@code y=33..37}），两张图共用同一套坐标。
     * 换贴图就得回来重新量：{@code WinefoxBossResourceValidationTest} 会拿这两张图核对这些数字。
     */
    private static final int TRACK_X = 18;
    private static final int TRACK_Y = 33;
    private static final int TRACK_WIDTH = 220;
    private static final int TRACK_HEIGHT = 5;

    /** 名字画在本体下方 2 格处；文字高 9，见 {@link #FONT_LINE_HEIGHT}。 */
    private static final int NAME_Y_OFFSET = BAR_HEIGHT + 2;

    /**
     * 原版第一条 Boss 血条的 y——{@code BossHealthOverlay.render} 里的 {@code int i = 12;}
     * 就是从这里发给 {@code event.getY()} 的。那 12 格原本是给名字留的（原版把名字画在
     * 血条上方 9 格），我们的名字挪到了本体下方，顶上就空出来了。
     *
     * <p>减掉它只是为了让「第二条及以后」的血条仍然按 {@code increment} 往下排——同屏
     * 恰好还有别的 Boss 血条时不会糊在一起；一对一开打时 {@code getY()} 就是 12，
     * 结果就是血条顶着屏幕顶端（{@code y=0}）。{@code max(0, ...)} 是保险：这个基线
     * 万一不是 12，也只会往下偏，不会把贴图画到屏幕外。
     */
    private static final int VANILLA_BAR_TOP_Y = 12;

    /** 紫色显示名，取自 {@code layer.png} 的亮紫填充（{@code #B38EF3}），提亮一点保证可读。 */
    private static final int NAME_COLOR = 0xC77DFF;

    /** 原版字体行高，用来算留给下一条血条的距离。 */
    private static final int FONT_LINE_HEIGHT = 9;

    /** 名字下面再留 4 格，免得两条血条贴在一起。 */
    private static final int NEXT_BAR_GAP = 4;

    private WinefoxBossBarOverlay() {
    }

    @SubscribeEvent
    public static void onBossEventProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        BossEvent bossEvent = event.getBossEvent();
        if (!isWinefoxBossBar(bossEvent)) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = graphics.guiWidth() / 2;
        int originX = centerX - BAR_WIDTH / 2;
        int originY = Math.max(0, event.getY() - VANILLA_BAR_TOP_Y);

        // 只画本体那 56 行；纹理尺寸仍旧报整张 256x96 的画布。
        graphics.blit(BASE_TEXTURE, originX, originY, 0, 0,
                BAR_WIDTH, BAR_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        // 从左往右裁：空血槽已经画在底板上了，这里只补有血的那一截。
        int filled = Mth.ceil(TRACK_WIDTH * Mth.clamp(bossEvent.getProgress(), 0.0F, 1.0F));
        if (filled > 0) {
            graphics.blit(LAYER_TEXTURE, originX + TRACK_X, originY + TRACK_Y,
                    TRACK_X, TRACK_Y, filled, TRACK_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        Component name = bossEvent.getName();
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, name,
                centerX - minecraft.font.width(name) / 2, originY + NAME_Y_OFFSET, NAME_COLOR);

        event.setIncrement(NAME_Y_OFFSET + FONT_LINE_HEIGHT + NEXT_BAR_GAP);
        event.setCanceled(true);
    }

    private static boolean isWinefoxBossBar(BossEvent bossEvent) {
        return bossEvent.getName().getContents() instanceof TranslatableContents contents
                && WinefoxBossBar.NAME_KEY.equals(contents.getKey());
    }
}
