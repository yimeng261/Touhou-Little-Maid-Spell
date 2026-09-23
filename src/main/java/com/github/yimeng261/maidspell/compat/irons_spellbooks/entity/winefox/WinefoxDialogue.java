package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    /** 普通连续台词之间的间隔。 */
    private static final int LINE_INTERVAL_TICKS = 140;

    /** 初次见面台词按设计每隔三秒发送一句。 */
    private static final int GREETING_LINE_INTERVAL_TICKS = 60;

    /** 播报半径。比擂台大一圈，站在边上看的人也听得到。 */
    private static final double BROADCAST_RADIUS = 48.0D;

    private final Deque<Component> pending = new ArrayDeque<>();
    private int delayTicks;
    private int lineIntervalTicks = LINE_INTERVAL_TICKS;
    private UUID targetPlayerId;

    /**
     * 排入一组台词，替换掉还没播完的上一组。
     *
     * <p>替换而不是追加：能触发对话的都是"这一场开始了"这类节点，
     * 上一组要是还没播完，说明状态已经变了，接着播反而错乱。
     */
    public void speak(List<Component> lines) {
        this.begin(lines, LINE_INTERVAL_TICKS, null);
    }

    /** 排入只发送给指定玩家的一组台词。 */
    public boolean speakTo(Player player, List<Component> lines, int intervalTicks) {
        if (!(player instanceof ServerPlayer) || this.isSpeaking()) {
            return false;
        }
        this.begin(lines, intervalTicks, player.getUUID());
        return true;
    }

    private void begin(List<Component> lines, int intervalTicks, UUID targetPlayerId) {
        this.pending.clear();
        this.pending.addAll(lines);
        this.delayTicks = 0;
        this.lineIntervalTicks = Math.max(1, intervalTicks);
        this.targetPlayerId = targetPlayerId;
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
        this.targetPlayerId = null;
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
        this.delayTicks = this.lineIntervalTicks;
        if (this.targetPlayerId != null) {
            Player target = level.getPlayerByUUID(this.targetPlayerId);
            if (target instanceof ServerPlayer serverPlayer) {
                send(serverPlayer, line);
            }
        } else {
            sendToNearby(speaker, line, BROADCAST_RADIUS);
        }
        if (this.pending.isEmpty()) {
            this.targetPlayerId = null;
        }
    }

    /** 将一行台词发送给实体附近指定半径内的玩家。 */
    public static void sendToNearby(Entity speaker, Component line, double radius) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) <= radiusSqr) {
                send(player, line);
            }
        }
    }

    /** 将一行普通聊天发送给指定玩家。 */
    public static void sendToPlayer(Player player, Component line) {
        if (player instanceof ServerPlayer serverPlayer) {
            send(serverPlayer, line);
        }
    }

    private static void send(ServerPlayer player, Component line) {
        player.sendSystemMessage(Component.translatable(
                "entity.touhou_little_maid_spell.stellar_witch.say", line)
            .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /**
     * 玩家被打服了：她收手回秋千。
     */
    public static List<Component> playerSubdued() {
        return List.of(randomTranslation("dialogue.touhou_little_maid_spell.winefox.challenge_lost_", 3));
    }

    /**
     * 开场白：玩家出示星芒短剑，三秒后开始切磋。
     */
    public static List<Component> challengeAccepted(boolean firstChallenge) {
        return List.of(Component.translatable(firstChallenge
                ? "dialogue.touhou_little_maid_spell.winefox.challenge_1"
                : randomKey("dialogue.touhou_little_maid_spell.winefox.challenge_repeat_", 2)));
    }

    public static List<Component> victory(boolean restricted, boolean trueDamage) {
        return List.of(trueDamage
            ? Component.translatable("dialogue.touhou_little_maid_spell.winefox.victory_true_damage")
            : restricted
                ? randomTranslation("dialogue.touhou_little_maid_spell.winefox.victory_maids_", 3)
                : randomTranslation("dialogue.touhou_little_maid_spell.winefox.victory_normal_", 3));
    }

    /** 初次靠近时发送三句开场白，句间间隔三秒。 */
    public boolean greet(Entity speaker, Player player) {
        return this.speakTo(player, List.of(
            Component.translatable("dialogue.touhou_little_maid_spell.winefox.greeting_1"),
            Component.translatable("dialogue.touhou_little_maid_spell.winefox.greeting_2"),
            Component.translatable("dialogue.touhou_little_maid_spell.winefox.greeting_3")),
            GREETING_LINE_INTERVAL_TICKS);
    }

    public static Component randomAmbientLine() {
        return randomTranslation("dialogue.touhou_little_maid_spell.winefox.ambient_", 5);
    }

    public static Component randomChatLine(boolean nearbyMaid) {
        int index = ThreadLocalRandom.current().nextInt(nearbyMaid ? 17 : 15);
        return index < 15
            ? translationAt("dialogue.touhou_little_maid_spell.winefox.chat_", index + 1)
            : translationAt("dialogue.touhou_little_maid_spell.winefox.chat_maid_", index - 14);
    }

    public static Component postVictoryChatLine() {
        return Component.translatable("dialogue.touhou_little_maid_spell.winefox.post_victory_chat");
    }

    public static Component tradeLine() {
        return Component.translatable("dialogue.touhou_little_maid_spell.winefox.trade");
    }

    public static List<Component> phaseTransition() {
        return List.of(randomTranslation("dialogue.touhou_little_maid_spell.winefox.phase_transition_", 4));
    }

    public static List<Component> maidDefeated() {
        return List.of(randomTranslation("dialogue.touhou_little_maid_spell.winefox.maid_defeated_", 5));
    }

    private static Component randomTranslation(String prefix, int count) {
        return Component.translatable(randomKey(prefix, count));
    }

    private static Component translationAt(String prefix, int index) {
        return Component.translatable(prefix + index);
    }

    private static String randomKey(String prefix, int count) {
        return prefix + (1 + ThreadLocalRandom.current().nextInt(count));
    }
}
