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
 * 使用 Forge Boss 血条事件绘制酒狐专属贴图，按翻译键识别目标血条。
 * 贴图画布为 256x96，实际绘制 56 行；blit 仍须使用完整画布尺寸计算 UV。
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
     * 原版第一条 Boss 血条的 y：{@code BossHealthOverlay.render} 里的 {@code int i = 12;}，那 12 格原本是给名字留的
     * （原版把名字画在血条上方 9 格），我们的名字挪到了本体下方，顶上就空出来了。减掉它只为让第二条及以后仍按
     * {@code increment} 往下排，不会糊在一起；{@code max(0, ...)} 是保险，基线万一不是 12 也只会往下偏，不会把贴图画到屏幕外。
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
