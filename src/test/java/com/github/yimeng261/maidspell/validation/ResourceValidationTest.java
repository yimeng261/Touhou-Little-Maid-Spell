package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import static com.github.yimeng261.maidspell.validation.ValidationFixtures.JAVA_SOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.RESOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.filesUnder;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.gunzipIfNeeded;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.indexOf;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseJson;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseObject;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.readPossiblyGzipped;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.readUnsignedShort;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.relative;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.withoutJavaComments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceValidationTest {
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
    /** SNBT 里 {@code "键":123b} 这种条目；键名带引号是因为我们的标记键含冒号。 */
    private static final Pattern SNBT_BYTE_ENTRY = Pattern.compile(
        "\"([^\"]+)\"\\s*:\\s*-?\\d+[bB]\\b");
    private static final String OWN_NAMESPACE_PREFIX = "touhou_little_maid_spell:";
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

    /**
     * {@code forge:partial_nbt} 的 nbt 字段必须写成 SNBT 字符串，不能写成 JSON 对象。
     *
     * <p>{@code CraftingHelper.getNBT} 对 JSON 对象走 {@code TagParser.parseTag(GSON.toJson(...))}，
     * 于是 <code>{"k": 1}</code> 里那个 1 会解析成 <b>IntTag</b>；而我们打在旅行日记上的标记是
     * <b>ByteTag</b>。{@code NbtUtils.compareNbt} 第一步就是
     * {@code !tag.getClass().equals(other.getClass())} 直接返回 false ——
     * 配方不会报错，只是<b>永远匹配不上</b>。写成字符串才能带 {@code 1b} 后缀把类型定死。
     */
    @Test
    void partialNbtIngredientsSpellOutTheirTagTypesAsSnbtStrings() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path file : filesUnder(RESOURCES, ResourceValidationTest::isRecipe)) {
            JsonElement root = parseJson(file);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("ingredients")) {
                continue;
            }
            JsonArray ingredients = root.getAsJsonObject().getAsJsonArray("ingredients");
            for (int i = 0; i < ingredients.size(); i++) {
                JsonElement entry = ingredients.get(i);
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject ingredient = entry.getAsJsonObject();
                if (!"forge:partial_nbt".equals(stringValue(ingredient, "type"))) {
                    continue;
                }
                JsonElement nbt = ingredient.get("nbt");
                if (nbt == null || !nbt.isJsonPrimitive() || !nbt.getAsJsonPrimitive().isString()) {
                    failures.add(relative(file) + " ingredients[" + i + "]");
                }
            }
        }

        assertTrue(failures.isEmpty(), () -> "forge:partial_nbt 的 nbt 必须是 SNBT 字符串（如 "
            + "\"{\\\"ns:key\\\":1b}\"）而不是 JSON 对象，否则 1 会变成 IntTag、匹配 ByteTag 标记时静默失败：\n"
            + String.join("\n", failures));
    }

    /**
     * 配方里点名的 NBT 标记键，必须真的存在于结构 nbt 里，且类型对得上。
     *
     * <p>旅行日记的标记不是靠战利品表发的，而是写死在 11 个结构文件里 ——
     * 改名、漏打、类型写错都不会有任何报错，只会让星云核心永远合不出来。
     * 这里不解析整棵 NBT 树，只在解压后的字节流里按 <b>命名标签的二进制布局</b>
     * （{@code [类型:1][名长:2][名字:N]}）找那个键，顺带校验它前面那个类型字节是不是 TAG_Byte。
     */
    @Test
    void nbtMarkersNamedByRecipesExistInStructuresWithTheRightTagType() throws IOException {
        Map<String, Integer> requiredCounts = new java.util.TreeMap<>();
        for (Path file : filesUnder(RESOURCES, ResourceValidationTest::isRecipe)) {
            JsonElement root = parseJson(file);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("ingredients")) {
                continue;
            }
            for (JsonElement entry : root.getAsJsonObject().getAsJsonArray("ingredients")) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject ingredient = entry.getAsJsonObject();
                if (!"forge:partial_nbt".equals(stringValue(ingredient, "type"))) {
                    continue;
                }
                for (String key : snbtByteKeys(stringValue(ingredient, "nbt"))) {
                    requiredCounts.merge(key, 1, Integer::sum);
                }
            }
        }

        List<Path> structures = filesUnder(RESOURCES, path -> path.toString().endsWith(".nbt"));
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Integer> required : requiredCounts.entrySet()) {
            int carriers = 0;
            for (Path structure : structures) {
                if (containsNamedByteTag(Files.readAllBytes(structure), required.getKey())) {
                    carriers++;
                }
            }
            if (carriers < required.getValue()) {
                failures.add("标记 " + required.getKey() + " 配方需要 " + required.getValue()
                    + " 份，但只有 " + carriers + " 个结构文件带着它");
            }
        }

        assertTrue(failures.isEmpty(), () -> "配方点名的 NBT 标记在结构里找不到（或不是 TAG_Byte）：\n"
            + String.join("\n", failures));
    }

    /**
     * 从一段 SNBT 里挑出所有 {@code "键":<数字>b} 形式的键名。
     *
     * <p>只认带 {@code b} 后缀的，因为这个断言专门盯 ByteTag 标记；
     * 别的类型有别的比对方式，不在这条测试的射程内。
     */
    private static Set<String> snbtByteKeys(String snbt) {
        Set<String> keys = new TreeSet<>();
        if (snbt == null) {
            return keys;
        }
        Matcher matcher = SNBT_BYTE_ENTRY.matcher(snbt);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * 在 nbt 文件（gzip 或裸流）里找一个 TAG_Byte 的命名标签。
     *
     * <p>命名标签的二进制布局是 {@code [类型:1][名长:2][名字:N][负载]}，
     * 所以名字出现的位置往前数三个字节就是类型，往前两个字节是大端的名字长度。
     * 两处都对上才算数——只搜名字的话，别的地方偶然出现同样的字符串也会误判。
     */
    /**
     * 解压后的字节里有没有一个名为 {@code name} 的 TAG_Byte。
     *
     * <p>NBT 标签的排布是 {@code [类型][名字长度:2][名字]}，所以命中处往前三个字节
     * 就是类型（1 = TAG_Byte）与名字长度，两个都对上才算数。
     */
    private static boolean containsNamedByteTag(byte[] file, String name) throws IOException {
        byte[] data = gunzipIfNeeded(file);
        byte[] needle = name.getBytes(StandardCharsets.UTF_8);
        for (int start = indexOf(data, needle, 3); start >= 3;
             start = indexOf(data, needle, start + 1)) {
            if (data[start - 3] == 1 && readUnsignedShort(data, start - 2) == needle.length) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRecipe(Path path) {
        return path.toString().endsWith(".json")
            && path.getParent() != null
            && path.toString().replace('\\', '/').contains("/recipes/");
    }

    /**
     * 数据文件里不许出现 JSON null。
     *
     * <p>原版的 {@code GsonHelper} 大多不做 null 校验：一个
     * {@code "max": null} 会安安静静地装出一个 max 为 null 的 {@code UniformGenerator}，
     * 加载期一声不吭，等到那条 entry 被摇中才在 tick 里抛 NPE 崩服。
     * 手写 JSON 不容易犯，脚本生成时把参数喂错位就会 —— 这张网就是为脚本准备的。
     */
    @Test
    void dataFilesContainNoJsonNulls() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path file : filesUnder(RESOURCES, ResourceValidationTest::isJsonResource)) {
            collectNullPaths(parseJson(file), "", failures, relative(file));
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    private static void collectNullPaths(JsonElement element, String path,
                                         List<String> failures, String file) {
        if (element == null || element.isJsonNull()) {
            failures.add(file + " 的 " + (path.isEmpty() ? "<root>" : path) + " 是 null");
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectNullPaths(entry.getValue(), path + "." + entry.getKey(), failures, file);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                collectNullPaths(array.get(index), path + "[" + index + "]", failures, file);
            }
        }
    }

    /**
     * 结构 NBT 里的箱子指名的战利品表必须真的存在。
     *
     * <p>缺了不会报错，只会安安静静地生成一屋子空箱子，外加一行服务端日志 ——
     * 观星塔就这么带着七张不存在的表进了分支。结构文件是二进制的，
     * 没有别的地方会替我们看一眼，只能在这儿扫。
     */
    @Test
    void lootTablesNamedByStructuresExist() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path structure : filesUnder(RESOURCES, ResourceValidationTest::isStructureFile)) {
            for (String id : ownLootTableIds(readPossiblyGzipped(structure))) {
                Path table = RESOURCES.resolve("data/touhou_little_maid_spell/loot_tables/"
                    + id.substring(OWN_NAMESPACE_PREFIX.length()) + ".json");
                if (!Files.isRegularFile(table)) {
                    failures.add(relative(structure) + " 指名了不存在的战利品表 " + id);
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    /**
     * 这条线上两个「必须在世界里找得到」的实体，得真的躺在各自的结构模板里。
     *
     * <p>两个都没有自然生成规则，唯一的入世方式就是被烤进结构 NBT。
     * 一旦哪次重新导出结构把它们弄丢了，表现是整条链在生存模式里静默断掉 ——
     * 守塔人不生成 → 观星罗盘拿不到 → 星途终岸找不到 → 见不着酒狐，
     * 而游戏里不会有任何报错。
     */
    @Test
    void structuresStillCarryTheEntitiesThatGateTheStarWitchLine() throws IOException {
        Map<String, String> required = Map.of(
            "data/touhou_little_maid_spell/structures/starwatch_tower/starwatch_tower_1.nbt",
            "touhou_little_maid_spell:guardian_witch",
            "data/touhou_little_maid_spell/structures/stellar_endshore/stellar_endshore_1.nbt",
            "touhou_little_maid_spell:magical_winefox_boss");

        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> entry : required.entrySet()) {
            Path structure = RESOURCES.resolve(entry.getKey());
            if (!Files.isRegularFile(structure)) {
                failures.add("结构文件不存在：" + entry.getKey());
                continue;
            }
            byte[] data = readPossiblyGzipped(structure);
            byte[] id = entry.getValue().getBytes(StandardCharsets.UTF_8);
            if (indexOf(data, id, 0) < 0) {
                failures.add(entry.getKey() + " 里没有 " + entry.getValue()
                    + "，这条线在生存模式下就断了");
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    private static boolean isStructureFile(Path path) {
        return path.getFileName().toString().endsWith(".nbt")
            && path.toString().replace('\\', '/').contains("/structures/");
    }


    /**
     * 从解压后的字节里捞出本模组命名空间下的战利品表 id。
     *
     * <p>不解析 NBT：测试的 classpath 上没有 NBT 库。但也不能光按命名空间扫字符串 ——
     * 拼图方块里的模板池 id 长得和战利品表一模一样，扫出来全是误报。
     * 改为认 {@code LootTable} 这个键：NBT 字符串标签的排布是
     * {@code [类型][名字长度:2][名字][值长度:2][值]}，键名长度对上 9、
     * 且名字正好是 {@code LootTable}（不是 {@code LootTableSeed}）时，
     * 紧随其后的那个字符串就是要找的 id。
     */
    private static Set<String> ownLootTableIds(byte[] data) {
        Set<String> ids = new TreeSet<>();
        byte[] key = "LootTable".getBytes(StandardCharsets.UTF_8);
        for (int start = indexOf(data, key, 0); start >= 0; start = indexOf(data, key, start + 1)) {
            // 名字长度那两个字节必须写着 9，否则这是 LootTableSeed 之类的更长的键，或者是别的巧合。
            if (start < 2 || readUnsignedShort(data, start - 2) != key.length) {
                continue;
            }
            int valueStart = start + key.length;
            if (valueStart + 2 > data.length) {
                continue;
            }
            int valueLength = readUnsignedShort(data, valueStart);
            if (valueLength <= 0 || valueStart + 2 + valueLength > data.length) {
                continue;
            }
            String value = new String(data, valueStart + 2, valueLength, StandardCharsets.UTF_8);
            if (value.startsWith(OWN_NAMESPACE_PREFIX)) {
                ids.add(value);
            }
        }
        return ids;
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


}
