package com.github.yimeng261.maidspell.validation;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxAction;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxTermination;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.yimeng261.maidspell.validation.ValidationFixtures.RESOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.filesUnder;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseObject;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把 {@link WinefoxAction} 里写的动画名、时长、终止方式与模型包里的动画文件对账。
 *
 * <p>这三项以前各自散在三个地方：动画 JSON 的 {@code animation_length}、实体里的
 * {@code PHASE_TRANSITION_TICKS} 一类常量、客户端的 {@code hasAnimationFinished()}。
 * 集中到枚举里之后，还差一道把枚举与动画文件钉在一起的检查——就是这个类。
 * 在 Blockbench 里改了时长而没回来改代码，这里当场变红。
 *
 * <p><b>直接 {@code values()}，不扫源码。</b>测试的 classpath 上没有 Minecraft，
 * 但主源集的编译产物是在的，而类加载是按需的：{@link WinefoxAction} 只引
 * {@code java.util.List} 与 {@link WinefoxTermination}，两者都不碰模组类型——
 * 这正是当初把这份数据抽成无依赖枚举的目的。读枚举而不是正则扫源码，
 * 省掉四条正则、一套"从源码字节偏移倒推事件归属"的推导，以及三条专门用来
 * 检测正则失效的哨兵断言。
 */
class WinefoxActionDataTest {

    private static final Path ANIMATION_DIR = RESOURCES.resolve(
        "assets/touhou_little_maid_spell/tlm_custom_pack/"
            + "star_witch_winefox-1.0.0/assets/touhou_little_maid_spell/animation");

    /** 一 tick 20 分之一秒；枚举里的时长是 {@code ceil(animation_length * 20)}。 */
    private static final double TICKS_PER_SECOND = 20.0D;

    @Test
    void everyActionAnimationExistsInTheModelPack() throws IOException {
        Map<String, JsonObject> tracks = collectTracks();
        List<String> failures = new ArrayList<>();
        for (WinefoxAction action : WinefoxAction.values()) {
            if (action.hasOwnAnimation() && !tracks.containsKey(action.animationName())) {
                failures.add(action + " 指向的轨道 \"" + action.animationName() + "\" 在模型包里不存在");
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    @Test
    void actionDurationsMatchAnimationLength() throws IOException {
        Map<String, JsonObject> tracks = collectTracks();
        List<String> failures = new ArrayList<>();
        for (WinefoxAction action : WinefoxAction.values()) {
            JsonObject track = trackOf(action, tracks);
            if (track == null) {
                continue;
            }
            JsonElement length = track.get("animation_length");
            if (length == null) {
                failures.add(action + " 对应的轨道没有 animation_length，无法与 "
                    + action.durationTicks() + "t 对账");
                continue;
            }
            int expected = (int) Math.ceil(length.getAsDouble() * TICKS_PER_SECOND);
            if (expected != action.durationTicks()) {
                failures.add(action + " 写的是 " + action.durationTicks() + "t，而动画 \""
                    + action.animationName() + "\" 是 " + length.getAsDouble() + "s = " + expected + "t");
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    /**
     * {@link WinefoxTermination} 的三种写法与 JSON 里 {@code loop} 字段一一对应：
     * 没有该字段是 {@code ONE_SHOT}，{@code true} 是 {@code LOOP}，
     * {@code "hold_on_last_frame"} 是 {@code HOLD_LAST_FRAME}。
     */
    @Test
    void terminationsMatchTheLoopField() throws IOException {
        Map<String, JsonObject> tracks = collectTracks();
        List<String> failures = new ArrayList<>();
        for (WinefoxAction action : WinefoxAction.values()) {
            JsonObject track = trackOf(action, tracks);
            if (track == null) {
                continue;
            }
            String actual = describeLoop(track.get("loop"));
            if (!action.termination().name().equals(actual)) {
                failures.add(action + " 声明 " + action.termination() + "，而动画 \""
                    + action.animationName() + "\" 的 loop 字段是 " + actual);
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    /** 动画中途的事件（击退、换手）必须落在声明它的那条动画的时长里。 */
    @Test
    void actionEventsFallInsideTheirAnimation() {
        List<String> failures = new ArrayList<>();
        for (WinefoxAction action : WinefoxAction.values()) {
            for (WinefoxAction.Event event : action.events()) {
                if (event.tick() <= 0 || event.tick() >= action.durationTicks()) {
                    failures.add(action + " 的 " + event.kind() + " 落在 " + event.tick()
                        + "t，而这条动画只有 " + action.durationTicks() + "t");
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    private static JsonObject trackOf(WinefoxAction action, Map<String, JsonObject> tracks) {
        return action.hasOwnAnimation() ? tracks.get(action.animationName()) : null;
    }

    private static String describeLoop(JsonElement loop) {
        if (loop == null) {
            return WinefoxTermination.ONE_SHOT.name();
        }
        if (loop.isJsonPrimitive() && loop.getAsJsonPrimitive().isBoolean()) {
            return (loop.getAsBoolean() ? WinefoxTermination.LOOP : WinefoxTermination.ONE_SHOT).name();
        }
        return "hold_on_last_frame".equals(loop.getAsString())
               ? WinefoxTermination.HOLD_LAST_FRAME.name()
               : loop.toString();
    }

    /**
     * 模型包里所有动画轨道，轨道名不许在两个文件里各定义一次。
     *
     * <p>作者拿几条没有 body 的轨道当分隔线用（{@code "————剑动画————"}），跳过。
     */
    private static Map<String, JsonObject> collectTracks() throws IOException {
        Map<String, JsonObject> tracks = new LinkedHashMap<>();
        for (Path file : filesUnder(ANIMATION_DIR,
                path -> path.getFileName().toString().endsWith(".animation.json"))) {
            JsonObject block = parseObject(file).getAsJsonObject("animations");
            if (block == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject previous = tracks.put(entry.getKey(), entry.getValue().getAsJsonObject());
                assertTrue(previous == null,
                    () -> "轨道 \"" + entry.getKey() + "\" 在模型包里定义了不止一次");
            }
        }
        return tracks;
    }
}
