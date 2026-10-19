package com.github.yimeng261.maidspell.stagewright.scenes;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 配置按功能拆成多个文件后的文件层行为：boss.toml 由旧的 winefox.toml 改名而来且已有新文件时不覆盖，
 * 旧的通用、客户端配置迁入对应的新文件并保留玩家改过的值，baubles.toml 里旧分组的项挪到现在的分组，
 * 新文件的每一项在补全后都带中英文注释。
 */
public final class ConfigFileScenes {
    private static final String MIGRATION = Config.class.getPackageName() + ".ConfigMigration";

    private ConfigFileScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(io("config.winefoxFileRenamedToBoss", 5, ConfigFileScenes::bossRenamed));
        scenes.add(io("config.existingBossFileWins", 5, ConfigFileScenes::bossExists));
        scenes.add(io("config.legacyCommonSplitsIntoFiles", 5, ConfigFileScenes::legacyCommon));
        scenes.add(io("config.existingSplitFileWinsOverLegacy", 5, ConfigFileScenes::legacyCommonExistingWins));
        scenes.add(io("config.legacyClientMoves", 5, ConfigFileScenes::legacyClient));
        scenes.add(io("config.baublesOldGroupsMoveToUtilityAndSpecial", 5, ConfigFileScenes::baublesLayout));
        scenes.add(Checks.scene("config.starfallGardenSwitchDefaultsOnAndKeepsFalse", 5, ConfigFileScenes::starfallGardenSwitch));
        scenes.add(Checks.scene("config.everyEntryBilingualAfterCorrect", 5, ConfigFileScenes::bilingualComments));
        return scenes;
    }

    private interface IoBody {
        void run(SceneContext ctx) throws IOException;
    }

    private static Scene io(String name, int budget, IoBody body) {
        return Checks.scene(name, budget, ctx -> {
            try {
                body.run(ctx);
            } catch (IOException e) {
                ctx.fail("读写临时配置文件失败: " + e.getMessage());
            }
        });
    }

    private static Class<?> migration() {
        try {
            return Class.forName(MIGRATION);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到配置迁移类", e);
        }
    }

    private static Path tempDir(SceneContext ctx) throws IOException {
        Path dir = Files.createTempDirectory("tlms-config-");
        ctx.cleanup(() -> {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (IOException ignored) {
                // 临时目录删不掉不影响判定
            }
        });
        return dir;
    }

    private static void migrateBoss(Path configDir) {
        Reflect.call(null, migration(), "migrateBossConfig", new Class<?>[]{Path.class}, configDir);
    }

    private static void migrateCommon(Path legacy) {
        Reflect.call(null, migration(), "migrateCommon", new Class<?>[]{Path.class}, legacy);
    }

    private static void migrateBaublesLayout(Path file) {
        Reflect.call(null, migration(), "migrateBaublesLayout", new Class<?>[]{Path.class}, file);
    }

    private static void migrateClient(Path legacy) {
        Reflect.call(null, migration(), "migrateClient", new Class<?>[]{Path.class}, legacy);
    }

    private static CommentedConfig load(Path file) {
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).build()) {
            config.load();
            CommentedConfig copy = TomlFormat.instance().createConfig();
            copy.putAll(config);
            return copy;
        }
    }

    /** 旧的 winefox.toml 原样改名为 boss.toml，重复迁移不出错。 */
    private static void bossRenamed(SceneContext ctx) throws IOException {
        Path directory = Files.createDirectories(tempDir(ctx).resolve(MaidSpellMod.MOD_ID));
        Path oldFile = directory.resolve("winefox.toml");
        String contents = "# Custom boss settings\n[winefox]\nwinefoxMaxHealth = 900.0\n";
        Files.writeString(oldFile, contents, StandardCharsets.UTF_8);

        migrateBoss(directory.getParent());
        migrateBoss(directory.getParent());

        ctx.check(Files.exists(oldFile)).as("winefox.toml 迁移后仍在").isFalse();
        ctx.check(Files.readString(directory.resolve("boss.toml"), StandardCharsets.UTF_8)).as("boss.toml 内容").isEqualTo(contents);
    }

    /** boss.toml 已存在时以它为准，旧文件保留不动。 */
    private static void bossExists(SceneContext ctx) throws IOException {
        Path directory = Files.createDirectories(tempDir(ctx).resolve(MaidSpellMod.MOD_ID));
        Path oldFile = directory.resolve("winefox.toml");
        Path newFile = directory.resolve("boss.toml");
        Files.writeString(oldFile, "old settings");
        Files.writeString(newFile, "new settings");

        migrateBoss(directory.getParent());

        ctx.check(Files.readString(newFile)).as("boss.toml 内容").isEqualTo("new settings");
        ctx.check(Files.readString(oldFile)).as("winefox.toml 内容").isEqualTo("old settings");
    }

    private static final String LEGACY_COMMON = """
            [combat]
            \tmaxSpellRange = 30.0
            [baubles]
            \tspringBloomReturnMaxStacks = 5
            \t[baubles.dream_crystal]
            \t\tdreamCrystalExtraTrueDamageEnabled = false
            [retreat_dimension]
            \tenablePrivateDimensions = false
            \tallowAllStructures = true
            [compat]
            \tautoInstallTlmModelPack = false
            [winefox]
            \twinefoxMaxHealth = 900.0
            """;

    /** 旧的通用配置按分组拆进五个文件，玩家改过的值（含 1.21 独有的项）挪到现在的分组并补全后依然保留，旧文件改名留作备份。 */
    private static void legacyCommon(SceneContext ctx) throws IOException {
        Path configDir = tempDir(ctx);
        Path legacy = configDir.resolve(MaidSpellMod.MOD_ID + "-common.toml");
        Files.writeString(legacy, LEGACY_COMMON, StandardCharsets.UTF_8);

        migrateCommon(legacy);
        Path directory = configDir.resolve(MaidSpellMod.MOD_ID);
        migrateBaublesLayout(directory.resolve("baubles.toml"));

        ctx.check(Files.exists(legacy)).as("旧通用配置仍在原处").isFalse();
        ctx.check(Files.exists(configDir.resolve(MaidSpellMod.MOD_ID + "-common.toml.migrated"))).as("旧通用配置备份").isTrue();
        ctx.check(corrected(Config.COMBAT_SPEC, directory.resolve("combat.toml")).<Double>get("combat.maxSpellRange"))
                .as("combat.maxSpellRange").isEqualTo(30.0);
        CommentedConfig baubles = corrected(Config.BAUBLES_SPEC, directory.resolve("baubles.toml"));
        ctx.check(baubles.<Integer>get("baubles.utility.springBloomReturnMaxStacks")).as("springBloomReturnMaxStacks").isEqualTo(5);
        ctx.check(baubles.<Boolean>get("baubles.special.dreamCrystalExtraTrueDamageEnabled"))
                .as("dreamCrystalExtraTrueDamageEnabled").isFalse();
        CommentedConfig retreat = corrected(Config.RETREAT_SPEC, directory.resolve("retreat.toml"));
        ctx.check(retreat.<Boolean>get("retreat_dimension.enablePrivateDimensions")).as("enablePrivateDimensions").isFalse();
        ctx.check(retreat.<Boolean>get("retreat_dimension.allowAllStructures")).as("allowAllStructures").isTrue();
        ctx.check(corrected(Config.COMPAT_SPEC, directory.resolve("compat.toml")).<Boolean>get("compat.autoInstallTlmModelPack"))
                .as("autoInstallTlmModelPack").isFalse();
        ctx.check(corrected(Config.WINEFOX_SPEC, directory.resolve("boss.toml")).<Double>get("winefox.winefoxMaxHealth"))
                .as("winefoxMaxHealth").isEqualTo(900.0);
    }

    /** 新文件已存在的分组以新文件为准；其余分组照常迁入。 */
    private static void legacyCommonExistingWins(SceneContext ctx) throws IOException {
        Path configDir = tempDir(ctx);
        Path legacy = configDir.resolve(MaidSpellMod.MOD_ID + "-common.toml");
        Files.writeString(legacy, LEGACY_COMMON, StandardCharsets.UTF_8);
        Path directory = Files.createDirectories(configDir.resolve(MaidSpellMod.MOD_ID));
        Files.writeString(directory.resolve("combat.toml"), "[combat]\n\tmaxSpellRange = 12.0\n");

        migrateCommon(legacy);

        ctx.check(corrected(Config.COMBAT_SPEC, directory.resolve("combat.toml")).<Double>get("combat.maxSpellRange"))
                .as("已有 combat.toml 的 maxSpellRange").isEqualTo(12.0);
        ctx.check(corrected(Config.RETREAT_SPEC, directory.resolve("retreat.toml"))
                .<Boolean>get("retreat_dimension.enablePrivateDimensions")).as("其余分组照常迁入").isFalse();
    }

    /** 已按旧分组写好的 baubles.toml：回春返照挪进 utility、梦云水晶挪进 special，新位置已有的值不被覆盖。 */
    private static void baublesLayout(SceneContext ctx) throws IOException {
        Path file = Files.createDirectories(tempDir(ctx).resolve(MaidSpellMod.MOD_ID)).resolve("baubles.toml");
        Files.writeString(file, """
                [baubles]
                \tspringBloomReturnMaxStacks = 6
                \tspringBloomReturnHealRatio = 0.3
                \t[baubles.utility]
                \t\tspringBloomReturnHealRatio = 0.4
                \t\tflowCoreTickInterval = 7
                \t[baubles.special]
                \t\tchaosBookDamageSplitCount = 9
                \t[baubles.dream_crystal]
                \t\tdreamCrystalUseEffectWhitelist = false
                \t\tdreamCrystalEffectBlacklist = ["minecraft:poison"]
                """, StandardCharsets.UTF_8);

        migrateBaublesLayout(file);

        CommentedConfig raw = load(file);
        ctx.check(raw.contains("baubles.springBloomReturnMaxStacks")).as("根上的 springBloomReturnMaxStacks 仍在").isFalse();
        ctx.check(raw.contains("baubles.dream_crystal")).as("dream_crystal 分组仍在").isFalse();
        CommentedConfig baubles = corrected(Config.BAUBLES_SPEC, file);
        ctx.check(baubles.<Integer>get("baubles.utility.springBloomReturnMaxStacks")).as("springBloomReturnMaxStacks").isEqualTo(6);
        ctx.check(baubles.<Double>get("baubles.utility.springBloomReturnHealRatio")).as("新位置已有的 springBloomReturnHealRatio").isEqualTo(0.4);
        ctx.check(baubles.<Integer>get("baubles.utility.flowCoreTickInterval")).as("flowCoreTickInterval").isEqualTo(7);
        ctx.check(baubles.<Integer>get("baubles.special.chaosBookDamageSplitCount")).as("chaosBookDamageSplitCount").isEqualTo(9);
        ctx.check(baubles.<Boolean>get("baubles.special.dreamCrystalUseEffectWhitelist")).as("dreamCrystalUseEffectWhitelist").isFalse();
        ctx.check(baubles.<List<String>>get("baubles.special.dreamCrystalEffectBlacklist"))
                .as("dreamCrystalEffectBlacklist").isEqualTo(List.of("minecraft:poison"));
    }

    /** 旧的客户端配置整体搬到 client/ender_pocket.toml。 */
    private static void legacyClient(SceneContext ctx) throws IOException {
        Path configDir = tempDir(ctx);
        Path legacy = configDir.resolve(MaidSpellMod.MOD_ID + "-client.toml");
        Files.writeString(legacy, "hudEnabled = false\nhudX = 40\nhudY = 52\n", StandardCharsets.UTF_8);

        migrateClient(legacy);

        Path target = configDir.resolve(MaidSpellMod.MOD_ID).resolve("client").resolve("ender_pocket.toml");
        CommentedConfig config = corrected(EnderPocketClientConfig.SPEC, target);
        ctx.check(Files.exists(legacy)).as("旧客户端配置仍在原处").isFalse();
        ctx.check(config.<Boolean>get("hudEnabled")).as("hudEnabled").isFalse();
        ctx.check(config.<Integer>get("hudX")).as("hudX").isEqualTo(40);
        ctx.check(config.<Integer>get("hudY")).as("hudY").isEqualTo(52);
    }

    private static CommentedConfig corrected(ModConfigSpec spec, Path file) {
        CommentedConfig config = load(file);
        spec.correct(config);
        return config;
    }

    /** 星落之庭生成开关缺省为开启，显式写成关闭时补全后仍保持关闭。 */
    private static void starfallGardenSwitch(SceneContext ctx) {
        CommentedConfig config = TomlFormat.instance().createConfig();
        Config.WORLDGEN_SPEC.correct(config);
        ctx.check(config.<Boolean>get("worldgen.enableStarfallGarden")).as("缺省 enableStarfallGarden").isTrue();

        config.set("worldgen.enableStarfallGarden", false);
        Config.WORLDGEN_SPEC.correct(config);
        ctx.check(config.<Boolean>get("worldgen.enableStarfallGarden")).as("显式关闭后 enableStarfallGarden").isFalse();
    }

    /** 空配置和清掉注释的配置补全后，每一项都带中文和英文注释，已填的值不被覆盖。 */
    private static void bilingualComments(SceneContext ctx) {
        List<String> problems = new ArrayList<>();
        for (ModConfigSpec spec : List.of(Config.COMBAT_SPEC, Config.BAUBLES_SPEC, Config.RETREAT_SPEC,
                Config.COMPAT_SPEC, Config.WINEFOX_SPEC, Config.WORLDGEN_SPEC, EnderPocketClientConfig.SPEC)) {
            CommentedConfig config = TomlFormat.instance().createConfig();
            spec.correct(config);
            verifyComments(config, "", problems);

            clearComments(config);
            spec.correct(config);
            verifyComments(config, "", problems);
        }
        ctx.check(problems).as("缺中文或英文注释的配置项").isEmpty();

        CommentedConfig boss = TomlFormat.instance().createConfig();
        boss.set("winefox.winefoxMaxHealth", 700.0);
        Config.WINEFOX_SPEC.correct(boss);
        ctx.check(boss.<Double>get("winefox.winefoxMaxHealth")).as("补全后 winefoxMaxHealth").isEqualTo(700.0);
    }

    private static void verifyComments(CommentedConfig config, String prefix, List<String> problems) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String path = prefix + entry.getKey();
            if (entry.getValue() instanceof CommentedConfig section) {
                verifyComments(section, path + ".", problems);
                continue;
            }
            String comment = config.getComment(entry.getKey());
            boolean chinese = comment != null && comment.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff);
            boolean latin = comment != null && comment.codePoints().anyMatch(c -> c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z');
            if (!chinese || !latin) {
                problems.add(path);
            }
        }
    }

    private static void clearComments(CommentedConfig config) {
        config.clearComments();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            if (entry.getValue() instanceof CommentedConfig section) {
                clearComments(section);
            }
        }
    }
}
