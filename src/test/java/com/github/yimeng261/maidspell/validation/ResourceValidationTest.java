package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceValidationTest {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty(
        "maidspell.projectDir", System.getProperty("user.dir"))).toAbsolutePath().normalize();
    private static final Path RESOURCES = PROJECT_ROOT.resolve("src/main/resources");
    private static final Path JAVA_SOURCES = PROJECT_ROOT.resolve("src/main/java");
    private static final Path EN_US = RESOURCES.resolve(
        "assets/touhou_little_maid_spell/lang/en_us.json");
    private static final Path ZH_CN = RESOURCES.resolve(
        "assets/touhou_little_maid_spell/lang/zh_cn.json");
    private static final Pattern LITERAL_TRANSLATION = Pattern.compile(
        "\\bComponent\\s*\\.\\s*translatable\\s*\\(\\s*\"([^\"\\r\\n]+)\"");
    private static final Pattern RAW_ANIMATION_TRACK = Pattern.compile(
        "\\.\\s*then(?:Loop|Play|PlayAndHold|Wait)?\\s*\\(\\s*\"([^\"\\r\\n]+)\"");
    private static final Set<String> EXTERNAL_TRANSLATION_PREFIXES = Set.of(
        "ui.irons_spellbooks."
    );
    private static final Map<String, String> OPTIONAL_LOOT_NAMESPACES = Map.of(
        "irons_spellbooks:", "irons_spellbooks",
        "youkaishomecoming:", "youkaishomecoming"
    );

    @Test
    void allJsonAndMcmetaFilesParse() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path file : filesUnder(RESOURCES, ResourceValidationTest::isJsonResource)) {
            try {
                parseJson(file);
            } catch (IOException | JsonParseException exception) {
                failures.add(relative(file) + ": " + exception.getMessage());
            }
        }

        assertTrue(failures.isEmpty(), () -> "Invalid JSON resources:\n" + String.join("\n", failures));
    }

    @Test
    void englishAndChineseLanguageKeysMatchExactly() throws IOException {
        Set<String> english = new TreeSet<>(parseObject(EN_US).keySet());
        Set<String> chinese = new TreeSet<>(parseObject(ZH_CN).keySet());

        Set<String> missingFromChinese = new TreeSet<>(english);
        missingFromChinese.removeAll(chinese);
        Set<String> missingFromEnglish = new TreeSet<>(chinese);
        missingFromEnglish.removeAll(english);

        assertEquals(Set.of(), missingFromChinese,
            "zh_cn.json is missing keys present in en_us.json");
        assertEquals(Set.of(), missingFromEnglish,
            "en_us.json is missing keys present in zh_cn.json");
    }

    @Test
    void literalComponentTranslationKeysExistInBothLanguages() throws IOException {
        Set<String> english = parseObject(EN_US).keySet();
        Set<String> chinese = parseObject(ZH_CN).keySet();
        Set<String> literalKeys = new TreeSet<>();

        for (Path source : filesUnder(JAVA_SOURCES, path -> path.toString().endsWith(".java"))) {
            String javaSource = Files.readString(source, StandardCharsets.UTF_8);
            Matcher matcher = LITERAL_TRANSLATION.matcher(withoutJavaComments(javaSource));
            while (matcher.find()) {
                String key = matcher.group(1);
                // Compatibility providers own these translations in their own language assets.
                if (EXTERNAL_TRANSLATION_PREFIXES.stream().noneMatch(key::startsWith)) {
                    literalKeys.add(key);
                }
            }
        }

        Set<String> missingFromEnglish = new TreeSet<>(literalKeys);
        missingFromEnglish.removeAll(english);
        Set<String> missingFromChinese = new TreeSet<>(literalKeys);
        missingFromChinese.removeAll(chinese);

        assertTrue(missingFromEnglish.isEmpty(),
            () -> "Literal Component.translatable keys missing from en_us.json: " + missingFromEnglish);
        assertTrue(missingFromChinese.isEmpty(),
            () -> "Literal Component.translatable keys missing from zh_cn.json: " + missingFromChinese);
    }

    @Test
    void legacyWorldgenBiomeModifierDirectoryDoesNotExist() {
        Path legacyDirectory = RESOURCES.resolve(
            "data/touhou_little_maid_spell/worldgen/biome_modifier");
        assertFalse(Files.exists(legacyDirectory),
            "Forge biome modifiers must live under data/<namespace>/forge/biome_modifier: "
                + relative(legacyDirectory));
    }

    @Test
    void optionalModLootReferencesHaveTopLevelModLoadedConditions() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path lootTable : filesUnder(RESOURCES, ResourceValidationTest::isLootTable)) {
            JsonElement root = parseJson(lootTable);
            for (Map.Entry<String, String> dependency : OPTIONAL_LOOT_NAMESPACES.entrySet()) {
                if (containsStringPrefix(root, dependency.getKey())
                    && !hasTopLevelModLoadedCondition(root, dependency.getValue())) {
                    failures.add(relative(lootTable) + " references " + dependency.getKey()
                        + " without a top-level forge:mod_loaded condition for " + dependency.getValue());
                }
            }
        }

        assertTrue(failures.isEmpty(),
            () -> "Optional-mod loot tables are not guarded:\n" + String.join("\n", failures));
    }

    /**
     * 代码里 {@code RawAnimation} 引用的轨道必须真的存在于某个动画文件里。
     *
     * <p>控制器播一条不存在的轨道时 GeckoLib 会在渲染那一刻抛异常 —— 之前法帽的图标
     * 就是这么把客户端崩掉的，这条测试把它挪到构建期。
     */
    @Test
    void rawAnimationTracksReferencedInCodeExistInAnimationFiles() throws IOException {
        Set<String> available = new TreeSet<>();
        for (Path animationFile : filesUnder(RESOURCES,
                path -> isAnimationFile(path) && isGeckoLib4Resource(path))) {
            JsonElement animations = parseObject(animationFile).get("animations");
            if (animations != null && animations.isJsonObject()) {
                available.addAll(animations.getAsJsonObject().keySet());
            }
        }

        Set<String> referenced = new TreeSet<>();
        for (Path source : filesUnder(JAVA_SOURCES, path -> path.toString().endsWith(".java"))) {
            String javaSource = Files.readString(source, StandardCharsets.UTF_8);
            Matcher matcher = RAW_ANIMATION_TRACK.matcher(withoutJavaComments(javaSource));
            while (matcher.find()) {
                referenced.add(matcher.group(1));
            }
        }

        Set<String> missing = new TreeSet<>(referenced);
        missing.removeAll(available);
        assertTrue(missing.isEmpty(),
            () -> "These animation tracks are played from code but exist in no animation file: " + missing);
    }

    /**
     * GeckoLib 的 {@code FormatVersion} 只认 1.12.0 / 1.14.0 / 1.21.0 / 1.21.2，
     * 而且只有 1.12.0 不会打「Unsupported geometry json version」。
     *
     * <p>Blockbench 5 的 bedrock 导出会在工程带 display 设置时把版本抬到 1.21.110，
     * 导模型前要先临时关掉 display 模式 —— 这条就是防止那种导出偷偷混进来。
     */
    @Test
    void everyGeckoLibModelDeclaresTheOnlyGeometryVersionGeckoLibSupports() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path model : filesUnder(RESOURCES, ResourceValidationTest::isGeoModel)) {
            JsonElement version = parseObject(model).get("format_version");
            String declared = version == null ? "<missing>" : version.getAsString();
            if (!"1.12.0".equals(declared)) {
                failures.add(relative(model) + ": " + declared);
            }
        }

        assertTrue(failures.isEmpty(), () -> "These geo models must be re-exported as 1.12.0"
            + " (turn off the Display tab in Blockbench before exporting):\n"
            + String.join("\n", failures));
    }

    /**
     * GeckoLib 的 {@code FileLoader.getFileContents} 用 {@code Charset.defaultCharset()} 读整个文件，
     * 而 1.20.1 跑在 Java 17 上，默认字集跟系统走 —— 中文 Windows 就是 GBK。
     * UTF-8 存的中文轨道名、骨骼名会被读成乱码，
     * {@code AnimationController} 找不到轨道只会写一行日志然后静静停掉，
     * 表现就是「动画不播」。所以这两类文件必须全 ASCII。
     */
    @Test
    void geckoLibResourcesStayAsciiBecauseGeckoLibReadsThemInThePlatformCharset() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path file : filesUnder(RESOURCES,
                path -> (isAnimationFile(path) || isGeoModel(path)) && isGeckoLib4Resource(path))) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Set<String> offenders = new TreeSet<>();
            content.codePoints().filter(codePoint -> codePoint > 0x7F)
                .forEach(codePoint -> offenders.add(new String(Character.toChars(codePoint))));
            if (!offenders.isEmpty()) {
                failures.add(relative(file) + ": " + offenders);
            }
        }

        assertTrue(failures.isEmpty(), () -> "GeckoLib 按平台字符集读这些文件，"
            + "里面不能出现非 ASCII 字符（在 Blockbench 里把轨道名/骨骼名改成英文再导出）：\n"
            + String.join("\n", failures));
    }

    private static List<Path> filesUnder(Path root, java.util.function.Predicate<Path> predicate)
        throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(predicate)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    private static boolean isJsonResource(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".json") || fileName.endsWith(".mcmeta");
    }

    /**
     * 随 mod 内置、解压给 TLM 的模型包不走 GeckoLib 4。
     *
     * <p>TLM 读包的三个入口（{@code CustomPackLoader} /
     * {@code GeckoModelLoader} / {@code ServerCustomPackLoader}）全部是
     * {@code new InputStreamReader(stream, StandardCharsets.UTF_8)}，显式 UTF-8，
     * 所以包里的中文轨道名合法 —— 下面两条
     * GeckoLib 4 专属的检查要绕开它。
     *
     * <p>（包里那些中文动画名最终还是要改成 TLM 的命名规范，
     * 但那是为了让 {@code ConditionalHold} 等机制能认出来，不是字符集问题。）
     */
    private static boolean isGeckoLib4Resource(Path path) {
        return !relative(path).contains("/tlm_custom_pack/");
    }

    private static boolean isAnimationFile(Path path) {
        return path.toString().replace('\\', '/').endsWith(".animation.json");
    }

    private static boolean isGeoModel(Path path) {
        return path.toString().replace('\\', '/').endsWith(".geo.json");
    }

    private static boolean isLootTable(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".json") && normalized.contains("/loot_tables/");
    }

    private static JsonElement parseJson(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static JsonObject parseObject(Path file) throws IOException {
        JsonElement root = parseJson(file);
        assertTrue(root.isJsonObject(), () -> relative(file) + " must contain a JSON object");
        return root.getAsJsonObject();
    }

    private static boolean containsStringPrefix(JsonElement element, String prefix) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString().startsWith(prefix);
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsStringPrefix(child, prefix)) {
                    return true;
                }
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
                if (containsStringPrefix(child.getValue(), prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTopLevelModLoadedCondition(JsonElement root, String modId) {
        if (root == null || !root.isJsonObject()) {
            return false;
        }
        JsonElement conditionsElement = root.getAsJsonObject().get("forge:conditions");
        if (conditionsElement == null || !conditionsElement.isJsonArray()) {
            return false;
        }
        JsonArray conditions = conditionsElement.getAsJsonArray();
        for (JsonElement conditionElement : conditions) {
            if (!conditionElement.isJsonObject()) {
                continue;
            }
            JsonObject condition = conditionElement.getAsJsonObject();
            if ("forge:mod_loaded".equals(stringValue(condition, "type"))
                && modId.equals(stringValue(condition, "modid"))) {
                return true;
            }
        }
        return false;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString()
            : null;
    }

    private static String withoutJavaComments(String source) {
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

    private static String relative(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        return absolute.startsWith(PROJECT_ROOT)
            ? PROJECT_ROOT.relativize(absolute).toString().replace('\\', '/')
            : absolute.toString();
    }
}
