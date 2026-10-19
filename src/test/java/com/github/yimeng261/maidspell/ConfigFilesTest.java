package com.github.yimeng261.maidspell;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFilesTest {
    @TempDir
    Path configDir;

    @Test
    void renamesWinefoxConfigWithoutChangingItsContents() throws Exception {
        Path directory = Files.createDirectories(configDir.resolve(MaidSpellMod.MOD_ID));
        Path oldFile = directory.resolve("winefox.toml");
        String contents = "# Custom boss settings\n[winefox]\nwinefoxMaxHealth = 900.0\n";
        Files.writeString(oldFile, contents, StandardCharsets.UTF_8);

        ConfigMigration.migrateBossConfig(configDir);
        ConfigMigration.migrateBossConfig(configDir);

        assertFalse(Files.exists(oldFile));
        assertEquals(contents, Files.readString(directory.resolve("boss.toml"), StandardCharsets.UTF_8));
    }

    @Test
    void existingBossConfigTakesPrecedenceAndOldFileIsPreserved() throws Exception {
        Path directory = Files.createDirectories(configDir.resolve(MaidSpellMod.MOD_ID));
        Path oldFile = directory.resolve("winefox.toml");
        Path newFile = directory.resolve("boss.toml");
        Files.writeString(oldFile, "old settings");
        Files.writeString(newFile, "new settings");

        ConfigMigration.migrateBossConfig(configDir);

        assertEquals("new settings", Files.readString(newFile));
        assertEquals("old settings", Files.readString(oldFile));
    }

    @Test
    void correctingExistingTomlRestoresBilingualCommentsAndPreservesValues() {
        for (ForgeConfigSpec spec : List.of(ConfigCombat.SPEC, ConfigBaubles.SPEC,
                ConfigRetreat.SPEC, ConfigCompat.SPEC, ConfigWinefox.SPEC, ConfigWorldgen.SPEC,
                EnderPocketClientConfig.SPEC)) {
            CommentedConfig config = TomlFormat.instance().createConfig();
            spec.correct(config);
            verifyComments(config);

            clearCommentsRecursively(config);
            spec.correct(config);
            verifyComments(config);
        }

        CommentedConfig bossConfig = TomlFormat.instance().createConfig();
        bossConfig.set("winefox.winefoxMaxHealth", 900.0);
        ConfigWinefox.SPEC.correct(bossConfig);
        assertEquals(900.0, bossConfig.<Double>get("winefox.winefoxMaxHealth"));
        assertEquals(12.0, bossConfig.<Double>get("winefox.winefoxArmor"));
        bossConfig.set("winefox.winefoxArmor", 24.0);
        ConfigWinefox.SPEC.correct(bossConfig);
        assertEquals(24.0, bossConfig.<Double>get("winefox.winefoxArmor"));
    }

    @Test
    void starfallGardenGenerationDefaultsToEnabledAndKeepsExplicitFalse() {
        CommentedConfig config = TomlFormat.instance().createConfig();
        ConfigWorldgen.SPEC.correct(config);
        assertEquals(true, config.<Boolean>get("worldgen.enableStarfallGarden"));

        config.set("worldgen.enableStarfallGarden", false);
        ConfigWorldgen.SPEC.correct(config);
        assertEquals(false, config.<Boolean>get("worldgen.enableStarfallGarden"));
    }

    private static void verifyComments(CommentedConfig config) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            if (entry.getValue() instanceof CommentedConfig section) {
                verifyComments(section);
            } else {
                String comment = config.getComment(List.of(entry.getKey()));
                assertNotNull(comment, entry.getKey());
                assertTrue(comment.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff), entry.getKey());
                assertTrue(comment.codePoints().anyMatch(c -> c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'),
                        entry.getKey());
            }
        }
    }

    private static void clearCommentsRecursively(CommentedConfig config) {
        config.clearComments();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            if (entry.getValue() instanceof CommentedConfig section) {
                clearCommentsRecursively(section);
            }
        }
    }
}
