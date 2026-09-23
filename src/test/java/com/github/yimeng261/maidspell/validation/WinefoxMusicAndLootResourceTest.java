package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

import static com.github.yimeng261.maidspell.validation.ValidationFixtures.RESOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinefoxMusicAndLootResourceTest {
    private static final Path LOOT = RESOURCES.resolve(
        "data/touhou_little_maid_spell/loot_tables/chests");
    private static final Path SOUNDS = RESOURCES.resolve(
        "assets/touhou_little_maid_spell/sounds.json");
    private static final Path BGM = RESOURCES.resolve(
        "assets/touhou_little_maid_spell/sounds/music/stellar_witch.ogg");
    private static final Path STARGLINT_TEXTURE = RESOURCES.resolve(
        "assets/touhou_little_maid_spell/textures/item/starglint_dagger.png");

    @Test
    void suppliedStarwatchLootTablesAreInstalled() throws IOException {
        for (int index = 1; index <= 7; index++) {
            Path table = LOOT.resolve("starwatch_tower_" + index + ".json");
            assertTrue(Files.isRegularFile(table), "Missing starwatch loot table " + table);
            JsonObject root = parseObject(table);
            assertEquals("minecraft:chest", root.get("type").getAsString());
            assertTrue(root.getAsJsonArray("pools").size() > 0);
            assertTrue(!root.toString().contains("touhou_little_maid_spell:ritual_hilt"),
                "The hilt is placed in the structure by its author, not added to loot tables");
        }
    }

    @Test
    void battleMusicIsAStreamedLoopableOggResource() throws IOException {
        JsonObject sounds = parseObject(SOUNDS).getAsJsonObject("music.stellar_witch");
        JsonArray entries = sounds.getAsJsonArray("sounds");
        assertEquals(1, entries.size());
        JsonObject entry = entries.get(0).getAsJsonObject();
        assertEquals("touhou_little_maid_spell:music/stellar_witch",
            entry.get("name").getAsString());
        assertTrue(entry.get("stream").getAsBoolean());
        assertTrue(Files.size(BGM) > 1_000_000, "BGM OGG is unexpectedly small");
        try (InputStream input = Files.newInputStream(BGM)) {
            byte[] header = input.readNBytes(4);
            assertTrue(Arrays.equals(new byte[]{'O', 'g', 'g', 'S'}, header),
                "BGM must be an OGG stream");
        }
    }

    @Test
    void starglintTextureUsesTheSuppliedItemResolution() throws IOException {
        var texture = ImageIO.read(STARGLINT_TEXTURE.toFile());
        assertEquals(16, texture.getWidth());
        assertEquals(16, texture.getHeight());
    }
}
