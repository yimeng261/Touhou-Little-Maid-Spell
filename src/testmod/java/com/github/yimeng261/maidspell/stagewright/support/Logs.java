package com.github.yimeng261.maidspell.stagewright.support;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 读本次运行的 logs/latest.log（游戏目录下，启动时清空重写，写入即落盘），用来检查启动和加载数据包期间的报错。
 */
public final class Logs {
    /** 数据包条目解析失败、标签引用缺失、配方解析失败时原版与 NeoForge 打的日志片段。 */
    public static final List<String> DATA_LOAD_ERRORS = List.of(
            "Couldn't parse element", "Unknown registry key", "Couldn't load tag", "missing following references",
            "Parsing error loading recipe", "Failed to load", "Failed to parse");

    private static final Path LATEST = FMLPaths.GAMEDIR.get().resolve("logs").resolve("latest.log");

    private Logs() {
    }

    /** 读不到日志时返回 null。 */
    public static List<String> lines() {
        try {
            return Files.readAllLines(LATEST, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** 同时含 subject 与任一 markers 片段的 WARN/ERROR 行；读不到日志时返回 null。 */
    public static List<String> problems(String subject, List<String> markers) {
        List<String> lines = lines();
        if (lines == null) {
            return null;
        }
        return lines.stream()
                .filter(line -> line.contains("/WARN]") || line.contains("/ERROR]"))
                .filter(line -> line.contains(subject))
                .filter(line -> markers.stream().anyMatch(line::contains))
                .toList();
    }

    public static Path path() {
        return LATEST;
    }
}
