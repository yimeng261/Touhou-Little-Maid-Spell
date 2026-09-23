package com.github.yimeng261.maidspell.validation;

import org.junit.jupiter.api.Test;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.RESOURCES;
import static com.github.yimeng261.maidspell.validation.ValidationFixtures.parseObject;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;

class WinefoxProgressionResourcesTest {
    @Test
    void challengeUsesTwoMaterialsAndCoreHasNoCraftingShortcut() throws IOException {
        var recipe = parseObject(RESOURCES.resolve("data/touhou_little_maid_spell/recipes/starglint_dagger.json"));
        assertEquals("touhou_little_maid_spell:starglint_dagger", recipe.getAsJsonObject("result").get("item").getAsString());
        var ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(2, ingredients.size());
        assertEquals("touhou_little_maid_spell:star_meteorite", ingredients.get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("touhou_little_maid_spell:ritual_hilt", ingredients.get(1).getAsJsonObject().get("item").getAsString());
        assertFalse(Files.exists(RESOURCES.resolve("data/touhou_little_maid_spell/recipes/altar/nebula_core.json")));
        for (String name : new String[]{"accessories", "crossover"}) {
            var crystal = parseObject(RESOURCES.resolve("data/touhou_little_maid_spell/recipes/altar/dream_cat_crystal_" + name + ".json"));
            assertTrue(crystal.getAsJsonArray("ingredients").asList().stream().anyMatch(
                ingredient -> ingredient.isJsonObject() && ingredient.getAsJsonObject().has("item")
                    && ingredient.getAsJsonObject().get("item").getAsString().equals("touhou_little_maid_spell:nebula_core")));
        }
    }

    @Test
    void endshoreUsesSmallIslandsWithLargeSeparation() throws IOException {
        var biome = parseObject(RESOURCES.resolve("data/touhou_little_maid_spell/tags/worldgen/biome/has_structure/stellar_endshore.json"));
        assertEquals("minecraft:small_end_islands", biome.getAsJsonArray("values").get(0).getAsString());
        var placement = parseObject(RESOURCES.resolve("data/touhou_little_maid_spell/worldgen/structure_set/stellar_endshore_set.json"))
            .getAsJsonObject("placement");
        assertTrue(placement.get("separation").getAsInt() * 16 >= 7000);
        assertTrue(placement.get("spacing").getAsInt() > placement.get("separation").getAsInt());
    }
}
