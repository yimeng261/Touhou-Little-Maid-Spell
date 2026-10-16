package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.effect.StaranchorStunEffect;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WinefoxStunAndSpellWeightTest {
    @Test
    void dizzyAnimationCoversTheWholePearlStun() throws IOException {
        Path path = Path.of(System.getProperty("maidspell.projectDir"),
                "src/main/resources/assets/touhou_little_maid_spell/tlm_custom_pack",
                "star_witch_winefox-1.0.0/assets/touhou_little_maid_spell/animation",
                "touhou_little_maid_spell.stellar_witch.main.animation.json");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("animations");
            double seconds = animations.getAsJsonObject(WinefoxAction.DIZZY.animationName())
                    .get("animation_length").getAsDouble();
            assertEquals(6.0D, seconds);
            assertEquals(StaranchorStunEffect.STUN_TICKS, (int) Math.ceil(seconds * 20));
            assertEquals(StaranchorStunEffect.STUN_TICKS, WinefoxAction.DIZZY.durationTicks());
        }
    }

    @Test
    void defaultSwordArrayWeightIsHalvedOnlyInPhaseTwo() {
        assertEquals(2, WinefoxBossSpellAction.SWORD_PRISON.defaultSelectionWeight(false));
        assertEquals(1, WinefoxBossSpellAction.SWORD_PRISON.defaultSelectionWeight(true));
        assertEquals(1, WinefoxBossSpellAction.BLACK_HOLE.defaultSelectionWeight(false));
        assertEquals(2, WinefoxBossSpellAction.BLACK_HOLE.defaultSelectionWeight(true));
        for (WinefoxBossSpellAction action : WinefoxBossSpellAction.values()) {
            if (action != WinefoxBossSpellAction.SWORD_PRISON && action != WinefoxBossSpellAction.BLACK_HOLE) {
                assertEquals(2, action.defaultSelectionWeight(false), action.name());
                assertEquals(2, action.defaultSelectionWeight(true), action.name());
            }
        }
    }

}
