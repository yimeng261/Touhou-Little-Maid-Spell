package com.github.yimeng261.maidspell.validation;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static com.github.yimeng261.maidspell.validation.ValidationFixtures.RESOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarShadowStrikeResourceTest {
    private static final Path ASSETS = RESOURCES.resolve("assets/touhou_little_maid_spell");

    @Test
    void suppliedSpellTexturesHaveTheExpectedDimensions() throws IOException {
        Path icon = ASSETS.resolve("textures/gui/spell_icons/star_shadow_strike.png");
        assertImageSize(icon, 16, 16);

        for (int frame = 1; frame <= 4; frame++) {
            Path texture = ASSETS.resolve("textures/entity/star_shadow_strike/star_shadow_strike_"
                    + frame + ".png");
            assertImageSize(texture, 128, 128);
        }
    }

    @Test
    void suppliedSoundIsRegisteredAsAnOggResource() throws IOException {
        Path sound = ASSETS.resolve("sounds/spell/star_shadow_strike.ogg");
        assertTrue(Files.isRegularFile(sound));
        try (InputStream input = Files.newInputStream(sound)) {
            assertTrue(Arrays.equals(new byte[]{'O', 'g', 'g', 'S'}, input.readNBytes(4)));
        }

        JsonObject soundDefinition = parseObject(ASSETS.resolve("sounds.json"))
                .getAsJsonObject("star_shadow_strike");
        assertEquals("subtitle.touhou_little_maid_spell.star_shadow_strike",
                soundDefinition.get("subtitle").getAsString());
        assertEquals("touhou_little_maid_spell:spell/star_shadow_strike",
                soundDefinition.getAsJsonArray("sounds").get(0).getAsJsonObject()
                        .get("name").getAsString());
    }

    @Test
    void spellHasEnglishAndChineseDisplayText() throws IOException {
        for (String language : new String[]{"en_us", "zh_cn"}) {
            JsonObject translations = parseObject(ASSETS.resolve("lang/" + language + ".json"));
            assertTrue(translations.has("spell.touhou_little_maid_spell.star_shadow_strike"));
            assertTrue(translations.has("spell.touhou_little_maid_spell.star_shadow_strike.guide"));
        }
    }

    private static void assertImageSize(Path path, int width, int height) throws IOException {
        assertTrue(Files.isRegularFile(path), "Missing texture " + path);
        var image = ImageIO.read(path.toFile());
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
    }
}
