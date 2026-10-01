package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinefoxMeleeTimingTest {
    private static final List<WinefoxAction> SWORD_ACTIONS = List.of(
            WinefoxAction.SWORD_ATTACK_1, WinefoxAction.SWORD_ATTACK_2,
            WinefoxAction.SWORD_ATTACK_3, WinefoxAction.SWORD_ATTACK_4);

    @Test
    void threeHitComboRecoversAfterItsLastVisibleSwing() {
        assertArrayEquals(new int[]{0, 12, 24, 42}, comboTimeline(3));
    }

    @Test
    void fourHitComboRecoversAfterItsLastVisibleSwing() {
        assertArrayEquals(new int[]{0, 12, 24, 36, 55}, comboTimeline(4));
    }

    @Test
    void onlySwordActionsAllowEarlyFollowUps() {
        for (WinefoxAction action : WinefoxAction.values()) {
            if (!SWORD_ACTIONS.contains(action)) {
                assertEquals(action.durationTicks(), action.followUpTicks(), action.name());
            }
        }
    }

    @Test
    void swordFollowUpsPreserveEventsAndFullAnimationRecovery() throws IOException {
        Path animationPath = Path.of(System.getProperty("maidspell.projectDir"),
                "src/main/resources/assets/touhou_little_maid_spell/tlm_custom_pack",
                "star_witch_winefox-1.0.0/assets/touhou_little_maid_spell/animation",
                "touhou_little_maid_spell.stellar_witch.arm.animation.json");
        try (Reader reader = Files.newBufferedReader(animationPath, StandardCharsets.UTF_8)) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("animations");
            for (WinefoxAction action : SWORD_ACTIONS) {
                double length = animations.getAsJsonObject(action.animationName())
                        .get("animation_length").getAsDouble();
                assertEquals((int) Math.ceil(length * 20), action.meleeCooldownTicks(false), action.name());
                assertTrue(action.followUpTicks() > 0 && action.followUpTicks() < action.durationTicks(),
                        action.name());
                for (WinefoxAction.Event event : action.events()) {
                    assertTrue(event.tick() < action.followUpTicks(), action.name() + ": " + event);
                }
            }
        }
    }

    // The last entry is the tick at which the completed combo releases the AI.
    private static int[] comboTimeline(int hits) {
        int[] ticks = new int[hits + 1];
        for (int i = 0; i < hits; i++) {
            WinefoxAction action = SWORD_ACTIONS.get(i);
            int cooldown = action.meleeCooldownTicks(i + 1 < hits);
            assertTrue(cooldown >= action.followUpTicks(), action.name());
            ticks[i + 1] = ticks[i] + cooldown;
        }
        assertEquals(hits, Arrays.stream(ticks).limit(hits).distinct().count());
        return ticks;
    }
}
