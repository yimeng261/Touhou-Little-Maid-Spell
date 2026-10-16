package com.github.yimeng261.maidspell;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 把旧版放在 config 根目录下的通用、客户端配置文件一次性迁移到按功能拆分后的新位置，
 * 并把 baubles.toml 里仍按旧分组存放的项挪到现在的分组。
 * <p>读写都用同步模式：NightConfig 默认的异步写盘在 close 时不保证落盘，随后改名旧文件就会丢掉配置。
 */
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
            MaidSpellMod.LOGGER.warn("Could not migrate winefox.toml to boss.toml", exception);
        }
        migrateCommon(configDir.resolve(LEGACY_COMMON));
        migrateBaublesLayout(configDir.resolve(MaidSpellMod.MOD_ID).resolve("baubles.toml"));
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
            try (CommentedFileConfig source = CommentedFileConfig.builder(legacy).preserveInsertionOrder().sync().build();
                 CommentedFileConfig destination = CommentedFileConfig.builder(target).preserveInsertionOrder().sync().build()) {
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

    /**
     * 回春返照各项从 baubles 根挪进 utility 组，梦云水晶组 dream_crystal 整体并进 special 组；
     * 新位置已有值时以新位置为准，旧位置的值直接丢弃。
     */
    static void migrateBaublesLayout(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).preserveInsertionOrder().sync().build()) {
            config.load();
            if (!(config.get("baubles") instanceof CommentedConfig baubles)) {
                return;
            }
            boolean moved = false;
            for (String key : List.copyOf(baubles.valueMap().keySet())) {
                if (key.startsWith("springBloomReturn")) {
                    relocate(baubles, baubles, key, "utility");
                    moved = true;
                }
            }
            if (baubles.get(List.of("dream_crystal")) instanceof CommentedConfig dreamCrystal) {
                for (String key : List.copyOf(dreamCrystal.valueMap().keySet())) {
                    relocate(dreamCrystal, baubles, key, "special");
                }
                baubles.remove(List.of("dream_crystal"));
                moved = true;
            }
            if (moved) {
                config.save();
                MaidSpellMod.LOGGER.info("Moved Spring Bloom Return and Dream Crystal entries in {} to their new groups", file);
            }
        } catch (Exception exception) {
            MaidSpellMod.LOGGER.warn("Could not move bauble config entries in {}", file, exception);
        }
    }

    private static void relocate(CommentedConfig from, CommentedConfig baubles, String key, String group) {
        Object value = from.remove(List.of(key));
        if (baubles.get(List.of(group, key)) == null) {
            baubles.set(List.of(group, key), value);
        }
    }

    private static void migrateSections(Path legacy, Path[] targets, String[] sections) {
        try (CommentedFileConfig source = CommentedFileConfig.builder(legacy).preserveInsertionOrder().sync().build()) {
            source.load();
            for (int i = 0; i < sections.length; i++) {
                if (Files.exists(targets[i]) || !(source.get(sections[i]) instanceof UnmodifiableCommentedConfig section)) {
                    continue;
                }
                Files.createDirectories(targets[i].getParent());
                // 复制一份再写入：直接挂接原分组会让目标与来源共用同一个子配置，写入时报「不能把注释放进自己」
                try (CommentedFileConfig destination = CommentedFileConfig.builder(targets[i])
                        .preserveInsertionOrder().sync().build()) {
                    destination.set(sections[i], CommentedConfig.copy(section));
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
