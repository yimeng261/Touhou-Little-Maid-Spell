package com.github.yimeng261.maidspell;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One-time migration from the legacy root-level Forge config files. */
final class ConfigMigration {
    private static final String LEGACY_COMMON = MaidSpellMod.MOD_ID + "-common.toml";
    private static final String LEGACY_CLIENT = MaidSpellMod.MOD_ID + "-client.toml";

    private ConfigMigration() {
    }

    static void migrate() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        try {
            migrateBossConfig(configDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not migrate winefox.toml to boss.toml", exception);
        }
        migrateCommon(configDir.resolve(LEGACY_COMMON));
        migrateClient(configDir.resolve(LEGACY_CLIENT));
    }

    static void migrateBossConfig(Path configDir) throws IOException {
        Path directory = configDir.resolve(MaidSpellMod.MOD_ID);
        Path legacy = directory.resolve("winefox.toml");
        Path target = directory.resolve("boss.toml");
        if (Files.isRegularFile(legacy) && !Files.exists(target)) {
            Files.move(legacy, target);
        }
    }

    private static void migrateCommon(Path legacy) {
        if (!Files.isRegularFile(legacy)) {
            return;
        }

        Path[] targets = {
                configDir(legacy).resolve(MaidSpellMod.MOD_ID).resolve("combat.toml"),
                configDir(legacy).resolve(MaidSpellMod.MOD_ID).resolve("baubles.toml"),
                configDir(legacy).resolve(MaidSpellMod.MOD_ID).resolve("retreat.toml"),
                configDir(legacy).resolve(MaidSpellMod.MOD_ID).resolve("compat.toml"),
                configDir(legacy).resolve(MaidSpellMod.MOD_ID).resolve("boss.toml")
        };
        String[] sections = {"combat", "baubles", "retreat_dimension", "compat", "winefox"};
        migrateSections(legacy, targets, sections);
    }

    private static void migrateClient(Path legacy) {
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        Path target = configDir(legacy).resolve(MaidSpellMod.MOD_ID).resolve("client").resolve("ender_pocket.toml");
        if (Files.exists(target)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            try (CommentedFileConfig source = CommentedFileConfig.builder(legacy).preserveInsertionOrder().build();
                 CommentedFileConfig destination = CommentedFileConfig.builder(target).preserveInsertionOrder().build()) {
                source.load();
                destination.putAll(source);
                destination.save();
            }
            moveLegacyAside(legacy);
            MaidSpellMod.LOGGER.info("Migrated legacy client config to {}", target);
        } catch (Exception exception) {
            MaidSpellMod.LOGGER.warn("Could not migrate legacy client config {}", legacy, exception);
        }
    }

    private static void migrateSections(Path legacy, Path[] targets, String[] sections) {
        try (CommentedFileConfig source = CommentedFileConfig.builder(legacy).preserveInsertionOrder().build()) {
            source.load();
            for (int i = 0; i < sections.length; i++) {
                if (Files.exists(targets[i]) || source.get(sections[i]) == null) {
                    continue;
                }
                Files.createDirectories(targets[i].getParent());
                try (CommentedFileConfig destination = CommentedFileConfig.builder(targets[i])
                        .preserveInsertionOrder().build()) {
                    destination.add(sections[i], source.get(sections[i]));
                    destination.putAllComments(source);
                    destination.save();
                }
            }
            moveLegacyAside(legacy);
            MaidSpellMod.LOGGER.info("Migrated legacy common config {} into categorized files", legacy);
        } catch (Exception exception) {
            MaidSpellMod.LOGGER.warn("Could not migrate legacy common config {}", legacy, exception);
        }
    }

    private static Path configDir(Path legacy) {
        return legacy.getParent();
    }

    private static void moveLegacyAside(Path legacy) throws IOException {
        Path backup = legacy.resolveSibling(legacy.getFileName() + ".migrated");
        if (!Files.exists(backup)) {
            Files.move(legacy, backup);
        }
    }
}
