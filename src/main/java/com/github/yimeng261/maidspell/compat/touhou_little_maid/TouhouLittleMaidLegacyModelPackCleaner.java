package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class TouhouLittleMaidLegacyModelPackCleaner {
    static final Path LEGACY_PACK_PATH = Path.of(
            "tlm_custom_pack", "maidspell_geckolib_models-1.0.0");

    private TouhouLittleMaidLegacyModelPackCleaner() {
    }

    public static void cleanGameDirectory() {
        Path gameDirectory = FMLPaths.GAMEDIR.get();
        try {
            if (deleteLegacyPack(gameDirectory)) {
                MaidSpellMod.LOGGER.info("Removed obsolete Touhou Little Maid compatibility model pack at {}",
                        gameDirectory.resolve(LEGACY_PACK_PATH));
            }
        } catch (IOException e) {
            MaidSpellMod.LOGGER.warn("Failed to remove obsolete Touhou Little Maid compatibility model pack at {}",
                    gameDirectory.resolve(LEGACY_PACK_PATH), e);
        }
    }

    static boolean deleteLegacyPack(Path gameDirectory) throws IOException {
        Path normalizedGameDirectory = gameDirectory.toAbsolutePath().normalize();
        Path legacyPack = normalizedGameDirectory.resolve(LEGACY_PACK_PATH).normalize();
        if (!legacyPack.startsWith(normalizedGameDirectory) || !Files.exists(legacyPack)) {
            return false;
        }

        Files.walkFileTree(legacyPack, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
        return true;
    }
}
