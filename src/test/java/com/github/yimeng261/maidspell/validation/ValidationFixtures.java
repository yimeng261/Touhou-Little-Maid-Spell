package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * 校验测试共用的地基：工程根、资源根、读 JSON、遍历文件、读可能是 gzip 的字节。
 *
 * <p>这几样原先在每个测试类里各抄一份，加一个测试类就多一份。放这儿之后，
 * {@code maidspell.projectDir} 这类约定只有一个地方需要改。
 */
final class ValidationFixtures {

    /** 工程根。Gradle 会把它作为系统属性传进来，直接跑测试时退回工作目录。 */
    static final Path PROJECT_ROOT = Path.of(System.getProperty(
        "maidspell.projectDir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
    static final Path RESOURCES = PROJECT_ROOT.resolve("src/main/resources");
    static final Path JAVA_SOURCES = PROJECT_ROOT.resolve("src/main/java");

    private ValidationFixtures() {
    }

    static JsonElement parseJson(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    static JsonObject parseObject(Path file) throws IOException {
        return parseJson(file).getAsJsonObject();
    }

    /** 排序过的稳定顺序：失败信息里的文件顺序不该跟着文件系统变。 */
    static List<Path> filesUnder(Path root, Predicate<Path> predicate) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(predicate)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    /** 结构 NBT 通常是 gzip 的，但不保证——结构方块导出时两种都见过。 */
    static byte[] readPossiblyGzipped(Path file) throws IOException {
        return gunzipIfNeeded(Files.readAllBytes(file));
    }

    static byte[] gunzipIfNeeded(byte[] raw) throws IOException {
        if (raw.length < 2 || (raw[0] & 0xFF) != 0x1F || (raw[1] & 0xFF) != 0x8B) {
            return raw;
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            return in.readAllBytes();
        }
    }

    static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int start = Math.max(from, 0); start <= haystack.length - needle.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }

    static int readUnsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /**
     * 把 Java 源码里的注释换成等量空白，行号与字符串字面量都不受影响。
     *
     * <p>用正则扫源码时必须先过这一道：注释掉的一行长得和活代码一模一样。
     */
    static String withoutJavaComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean inString = false;
        boolean inCharacter = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    result.append(current);
                } else {
                    result.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    i++;
                    inBlockComment = false;
                } else {
                    result.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }
            if (!inString && !inCharacter && current == '/' && next == '/') {
                result.append("  ");
                i++;
                inLineComment = true;
                continue;
            }
            if (!inString && !inCharacter && current == '/' && next == '*') {
                result.append("  ");
                i++;
                inBlockComment = true;
                continue;
            }

            result.append(current);
            if (escaped) {
                escaped = false;
            } else if ((inString || inCharacter) && current == '\\') {
                escaped = true;
            } else if (!inCharacter && current == '"') {
                inString = !inString;
            } else if (!inString && current == '\'') {
                inCharacter = !inCharacter;
            }
        }
        return result.toString();
    }

    static String relative(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        return absolute.startsWith(PROJECT_ROOT)
               ? PROJECT_ROOT.relativize(absolute).toString().replace('\\', '/')
               : absolute.toString();
    }
}
