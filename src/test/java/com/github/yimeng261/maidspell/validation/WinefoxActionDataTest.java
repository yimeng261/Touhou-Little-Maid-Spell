package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把 {@code WinefoxAction} 里写的动画名、时长、终止方式与模型包里的动画文件对账。
 *
 * <p>这三项以前各自散在三个地方：动画 JSON 的 {@code animation_length}、实体里的
 * {@code PHASE_TRANSITION_TICKS} 一类常量、客户端的 {@code hasAnimationFinished()}。
 * 集中到枚举里之后，还差一道把枚举与动画文件钉在一起的检查——就是这个类。
 * 在 Blockbench 里改了时长而没回来改代码，这里当场变红。
 *
 * <p><b>读源码而不是加载类。</b>测试运行时的 classpath 只有 JUnit 和 gson，
 * 没有 Minecraft 也没有 Forge；{@code WinefoxAction} 本身确实不引任何模组类型，
 * 但它和整个 {@code winefox} 包放在一起编译，直接 {@code values()} 会把编译产物
 * 连同它的邻居一起拖进来。正则扫源码反而稳。
 */
class WinefoxActionDataTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty(
        "maidspell.projectDir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
    private static final Path ACTION_SOURCE = PROJECT_ROOT.resolve(
        "src/main/java/com/github/yimeng261/maidspell/compat/irons_spellbooks/entity/winefox/"
            + "WinefoxAction.java");
    private static final Path ANIMATION_DIR = PROJECT_ROOT.resolve(
        "src/main/resources/assets/touhou_little_maid_spell/tlm_custom_pack/"
            + "star_witch_winefox-1.0.0/assets/touhou_little_maid_spell/animation");

    /** {@code NAME("track", 20, WinefoxTermination.ONE_SHOT} 或 {@code NAME(null, 0, ...}。 */
    private static final Pattern ENUM_CONSTANT = Pattern.compile(
        "^\\s{4}([A-Z][A-Z0-9_]*)\\(\\s*(?:\"([^\"]+)\"|null)\\s*,\\s*(\\d+)\\s*,"
            + "\\s*WinefoxTermination\\.([A-Z_]+)", Pattern.MULTILINE);

    /** 一 tick 20 分之一秒；枚举里的时长是 {@code ceil(animation_length * 20)}。 */
    private static final double TICKS_PER_SECOND = 20.0D;

    @Test
    void everyActionAnimationExistsExactlyOnceInTheModelPack() throws IOException {
        Map<String, Path> tracks = collectTracks();
        List<String> failures = new ArrayList<>();
        for (ActionEntry action : parseActions()) {
            if (action.animationName == null) {
                continue;
            }
            if (!tracks.containsKey(action.animationName)) {
                failures.add(action.constantName + " 指向的轨道 \"" + action.animationName
                    + "\" 在模型包里不存在");
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    @Test
    void actionDurationsMatchAnimationLength() throws IOException {
        Map<String, JsonObject> tracks = collectTrackBodies();
        List<String> failures = new ArrayList<>();
        for (ActionEntry action : parseActions()) {
            JsonObject track = action.animationName == null ? null : tracks.get(action.animationName);
            if (track == null) {
                continue;
            }
            JsonElement length = track.get("animation_length");
            if (length == null) {
                failures.add(action.constantName + " 对应的轨道 \"" + action.animationName
                    + "\" 没有 animation_length，无法与 " + action.durationTicks + "t 对账");
                continue;
            }
            int expected = (int) Math.ceil(length.getAsDouble() * TICKS_PER_SECOND);
            if (expected != action.durationTicks) {
                failures.add(action.constantName + " 写的是 " + action.durationTicks
                    + "t，而动画 \"" + action.animationName + "\" 是 " + length.getAsDouble()
                    + "s = " + expected + "t");
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    /**
     * {@code WinefoxTermination} 的三种写法与 JSON 里 {@code loop} 字段一一对应：
     * 没有该字段是 {@code ONE_SHOT}，{@code true} 是 {@code LOOP}，
     * {@code "hold_on_last_frame"} 是 {@code HOLD_LAST_FRAME}。
     */
    @Test
    void terminationsMatchTheLoopField() throws IOException {
        Map<String, JsonObject> tracks = collectTrackBodies();
        List<String> failures = new ArrayList<>();
        for (ActionEntry action : parseActions()) {
            JsonObject track = action.animationName == null ? null : tracks.get(action.animationName);
            if (track == null) {
                continue;
            }
            String actual = describeLoop(track.get("loop"));
            String expected = switch (action.termination) {
                case "ONE_SHOT" -> "ONE_SHOT";
                case "LOOP" -> "LOOP";
                case "HOLD_LAST_FRAME" -> "HOLD_LAST_FRAME";
                default -> null;
            };
            if (expected == null) {
                failures.add(action.constantName + " 用了未知的终止方式 " + action.termination);
            } else if (!expected.equals(actual)) {
                failures.add(action.constantName + " 声明 " + expected + "，而动画 \""
                    + action.animationName + "\" 的 loop 字段是 " + actual);
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    /** 动画中途的事件（击退、换手）必须落在这条动画自己的时长里。 */
    @Test
    void actionEventsFallInsideTheirAnimation() throws IOException {
        String source = Files.readString(ACTION_SOURCE, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("Event\\.at\\((\\d+),").matcher(source);
        List<Integer> eventTicks = new ArrayList<>();
        while (matcher.find()) {
            eventTicks.add(Integer.parseInt(matcher.group(1)));
        }
        assertTrue(!eventTicks.isEmpty(), "没有解析到任何 Event.at(...)，正则大概过时了");

        int phaseTransitionTicks = parseActions().stream()
            .filter(action -> "PHASE_TRANSITION".equals(action.constantName))
            .map(action -> action.durationTicks)
            .findFirst()
            .orElseThrow(() -> new AssertionError("WinefoxAction 里找不到 PHASE_TRANSITION"));
        for (int tick : eventTicks) {
            assertTrue(tick > 0 && tick < phaseTransitionTicks,
                () -> "动画事件落在 " + tick + "t，超出了 " + phaseTransitionTicks + "t 的动画");
        }
    }

    /** 正则一旦跟不上源码格式，上面几个测试会集体空跑，所以先确认解析到了东西。 */
    @Test
    void actionSourceIsStillParseable() throws IOException {
        List<ActionEntry> actions = parseActions();
        assertTrue(actions.size() >= 8,
            () -> "只从 WinefoxAction.java 解析出 " + actions.size() + " 项，正则大概过时了");
        assertEquals(1, actions.stream()
            .filter(action -> action.animationName == null)
            .count(), "只有 NONE 允许没有动画名");
    }

    private static String describeLoop(JsonElement loop) {
        if (loop == null) {
            return "ONE_SHOT";
        }
        if (loop.isJsonPrimitive() && loop.getAsJsonPrimitive().isBoolean()) {
            return loop.getAsBoolean() ? "LOOP" : "ONE_SHOT";
        }
        return "hold_on_last_frame".equals(loop.getAsString()) ? "HOLD_LAST_FRAME" : loop.toString();
    }

    private static List<ActionEntry> parseActions() throws IOException {
        String source = Files.readString(ACTION_SOURCE, StandardCharsets.UTF_8);
        List<ActionEntry> actions = new ArrayList<>();
        Matcher matcher = ENUM_CONSTANT.matcher(source);
        while (matcher.find()) {
            actions.add(new ActionEntry(matcher.group(1), matcher.group(2),
                Integer.parseInt(matcher.group(3)), matcher.group(4)));
        }
        return actions;
    }

    private static Map<String, Path> collectTracks() throws IOException {
        Map<String, Path> tracks = new LinkedHashMap<>();
        for (Path file : animationFiles()) {
            for (String name : readAnimations(file).keySet()) {
                Path previous = tracks.put(name, file);
                assertTrue(previous == null, () -> "轨道 \"" + name + "\" 在 "
                    + file.getFileName() + " 和 " + (previous == null ? "?" : previous.getFileName())
                    + " 里各定义了一次");
            }
        }
        return tracks;
    }

    private static Map<String, JsonObject> collectTrackBodies() throws IOException {
        Map<String, JsonObject> tracks = new LinkedHashMap<>();
        for (Path file : animationFiles()) {
            tracks.putAll(readAnimations(file));
        }
        return tracks;
    }

    private static Map<String, JsonObject> readAnimations(Path file) throws IOException {
        Map<String, JsonObject> animations = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject block = root.getAsJsonObject("animations");
            if (block == null) {
                return animations;
            }
            for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
                // 模型包作者拿几条空轨道当分隔线用（"————剑动画————"），不是动画。
                if (entry.getValue().isJsonObject()) {
                    animations.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        }
        return animations;
    }

    private static List<Path> animationFiles() throws IOException {
        try (var files = Files.list(ANIMATION_DIR)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".animation.json"))
                .sorted()
                .toList();
        }
    }

    private record ActionEntry(String constantName, String animationName, int durationTicks,
                               String termination) {
    }
}
