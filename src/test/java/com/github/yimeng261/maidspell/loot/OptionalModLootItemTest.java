package com.github.yimeng261.maidspell.loot;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.GsonAdapterFactory;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraftforge.common.ForgeHooks;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionalModLootItemTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        if (!BuiltInRegistries.PAINTING_VARIANT.containsKey(new ResourceLocation("minecraft", "kebab"))) {
            BuiltInRegistries.bootStrap();
        }
    }

    @Test
    void absentItemCanDeserializeAndDoesNotEnterTheLootRoll() {
        OptionalModLootItem entry = deserialize("missing_integration:food");
        List<LootPoolEntry> rolls = new ArrayList<>();
        assertFalse(entry.expand(null, rolls::add));
        assertTrue(rolls.isEmpty());
        List<ItemStack> drops = new ArrayList<>();
        entry.createItemStack(drops::add, null);
        assertTrue(drops.isEmpty());
    }

    @Test
    void presentItemPreservesTheNormalItemDropAndWeight() {
        OptionalModLootItem entry = deserialize("minecraft:diamond");
        List<LootPoolEntry> rolls = new ArrayList<>();
        assertTrue(entry.expand(null, rolls::add));
        assertEquals(1, rolls.size());
        assertEquals(5, rolls.get(0).getWeight(1.0F));
        List<ItemStack> drops = new ArrayList<>();
        rolls.get(0).createItemStack(drops::add, null);
        assertEquals(1, drops.size());
        assertTrue(drops.get(0).is(Items.DIAMOND));
        assertEquals(1, drops.get(0).getCount());
    }

    @Test
    void importedTablesDeserializeWithFarmersDelightAndIronSpellbooksAbsent() throws Exception {
        // Forge normally registers this type before registry freeze. Use a private
        // registry here because other headless tests may already have frozen vanilla.
        var entries = new MappedRegistry<LootPoolEntryType>(Registries.LOOT_POOL_ENTRY_TYPE, Lifecycle.stable());
        BuiltInRegistries.LOOT_POOL_ENTRY_TYPE.entrySet().forEach(entry ->
                Registry.register(entries, entry.getKey().location(), entry.getValue()));
        Registry.register(entries, new ResourceLocation("touhou_little_maid_spell", "optional_mod_item"),
                new LootPoolEntryType(new OptionalModLootItem.Serializer()));
        Gson gson = Deserializers.createLootTableSerializer()
                .registerTypeHierarchyAdapter(LootPoolEntryContainer.class,
                        GsonAdapterFactory.builder(entries, "entry", "type", LootPoolEntryContainer::getType).build())
                .create();
        Path chests = Path.of(System.getProperty("maidspell.projectDir"),
                "src/main/resources/data/touhou_little_maid_spell/loot_tables/chests");
        assertFalse(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("farmersdelight", "cabbage")));
        assertFalse(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("irons_spellbooks", "hogskin")));
        for (String name : List.of("cherry_cabin_food_1", "cherry_cabin_food_2", "cherry_cabin_food_3",
                "cherry_cabin_asset_without_iss", "enchantress_footsteps_nether_1",
                "enchantress_footsteps_nether_2", "enchantress_footsteps_nether_3",
                "enchantress_footsteps_nether_4")) {
            var json = JsonParser.parseString(Files.readString(chests.resolve(name + ".json")));
            var id = new ResourceLocation("touhou_little_maid_spell", "chests/" + name);
            assertNotNull(ForgeHooks.loadLootTable(gson, id, json, true), name);
        }
    }

    private static OptionalModLootItem deserialize(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("weight", 3);
        json.addProperty("quality", 2);
        return new OptionalModLootItem.Serializer().deserializeCustom(json, null, new LootItemCondition[0]);
    }
}
