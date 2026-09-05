package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 万法酒狐的台词排队播报。
 *
 * <p>一次塞进若干句，之后每 {@link #LINE_INTERVAL_TICKS} tick 吐一句，
 * 播给擂台附近的所有玩家——不是只给邀战那一位，同行的人也该听见。
 *
 * <p>先做聊天栏。TLM 的气泡是女仆自己那套 GUI，酒狐虽然实现了 {@code IMaid}
 * 但没有女仆的交互界面，硬接要牵出一整条渲染链；等真需要再换，
 * 换的时候只动这一个类。
 */
public final class WinefoxDialogue {

    /** 两句之间隔多久。2 秒，够读完一行又不至于让开场白拖成过场动画。 */
    private static final int LINE_INTERVAL_TICKS = 40;

    /** 播报半径。比擂台大一圈，站在边上看的人也听得到。 */
    private static final double BROADCAST_RADIUS = 48.0D;

    private final Deque<Component> pending = new ArrayDeque<>();
    private int delayTicks;

    /**
     * 排入一组台词，替换掉还没播完的上一组。
     *
     * <p>替换而不是追加：能触发对话的都是"这一场开始了"这类节点，
     * 上一组要是还没播完，说明状态已经变了，接着播反而错乱。
     */
    public void speak(List<Component> lines) {
        this.pending.clear();
        this.pending.addAll(lines);
        this.delayTicks = 0;
    }

    /**
     * 接着上一段说下去，不清队列。
     *
     * <p>{@link #speak} 是「换个话题」，会把没播完的顶掉。同一 tick 里连着调两次
     * 就是把前一段整段吞了 —— 接受挑战那条路正好是这样：先排开场白，
     * 紧接着 {@code applyOmenLevel} 又排驯服台词，玩家永远听不到开场那三句。
     */
    public void continueWith(List<Component> lines) {
        this.pending.addAll(lines);
    }

    /**
     * 还有没有没播完的。
     */
    public boolean isSpeaking() {
        return !this.pending.isEmpty();
    }

    public void clear() {
        this.pending.clear();
        this.delayTicks = 0;
    }

    /**
     * 每 tick 调一次；只在服务端有意义。
     */
    public void tick(Entity speaker) {
        if (this.pending.isEmpty() || !(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.delayTicks > 0) {
            this.delayTicks--;
            return;
        }
        Component line = this.pending.poll();
        this.delayTicks = LINE_INTERVAL_TICKS;
        Component prefixed = Component.translatable(
                "entity.touhou_little_maid_spell.magical_winefox_boss.say", line);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) <= BROADCAST_RADIUS * BROADCAST_RADIUS) {
                player.sendSystemMessage(prefixed.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    /**
     * 玩家被打服了：她收手回秋千。
     */
    public static List<Component> playerSubdued() {
        return List.of(
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.subdued_1"),
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.subdued_2"));
    }

    /**
     * 带着不祥之兆来的：她听出了弦外之音。
     */
    public static List<Component> tamingChallenge() {
        return List.of(
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.taming_1"),
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.taming_2"));
    }

    /**
     * 战败坐下、驯服窗口打开。
     */
    public static List<Component> tamingWindowOpen() {
        return List.of(
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.taming_window_1"),
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.taming_window_2"));
    }

    /**
     * 驯服成功。
     */
    public static List<Component> tamed() {
        return List.of(Component.translatable("dialogue.touhou_little_maid_spell.winefox.tamed"));
    }

    /**
     * 开场白：玩家递上星云核心，她起身应战。
     */
    public static List<Component> challengeAccepted() {
        return List.of(
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.challenge_1"),
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.challenge_2"),
                Component.translatable("dialogue.touhou_little_maid_spell.winefox.challenge_3"));
    }
}
