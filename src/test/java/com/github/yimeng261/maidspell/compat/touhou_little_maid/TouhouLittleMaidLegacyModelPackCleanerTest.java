package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TouhouLittleMaidLegacyModelPackCleanerTest {
    @TempDir
    Path gameDirectory;

    @Test
    void deletesOnlyMaidSpellLegacyPack() throws IOException {
        Path legacyPack = gameDirectory.resolve(
                TouhouLittleMaidLegacyModelPackCleaner.LEGACY_PACK_PATH);
        Path tlmPack = gameDirectory.resolve("tlm_custom_pack/touhou_little_maid-1.0.0");
        Path userPack = gameDirectory.resolve("tlm_custom_pack/my_custom_models");
        Files.createDirectories(legacyPack.resolve("assets/geckolib/models/entity"));
        Files.createDirectories(tlmPack);
        Files.createDirectories(userPack);
        Files.writeString(legacyPack.resolve("assets/geckolib/models/entity/winefox_saint.json"), "{}");
        Files.writeString(tlmPack.resolve("maid_model.json"), "{}");
        Files.writeString(userPack.resolve("maid_model.json"), "{}");

        assertTrue(TouhouLittleMaidLegacyModelPackCleaner.deleteLegacyPack(gameDirectory));

        assertFalse(Files.exists(legacyPack));
        assertTrue(Files.exists(tlmPack.resolve("maid_model.json")));
        assertTrue(Files.exists(userPack.resolve("maid_model.json")));
    }

    @Test
    void missingLegacyPackNeedsNoCleanup() throws IOException {
        assertFalse(TouhouLittleMaidLegacyModelPackCleaner.deleteLegacyPack(gameDirectory));
    }
}
