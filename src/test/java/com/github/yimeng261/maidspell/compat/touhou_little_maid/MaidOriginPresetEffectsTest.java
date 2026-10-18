package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class MaidOriginPresetEffectsTest {
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
    void templateCreationSnapshotsOnlyInitialInfiniteEffectsWithNamesAndLevels() {
        CompoundTag template = template();
        ListTag active = new ListTag();
        CompoundTag permanent = effect("minecraft:regeneration", -1, 1);
        permanent.putInt("Id", 10);
        permanent.put("HiddenEffect", effect("minecraft:regeneration", 400, 0));
        active.add(permanent);
        active.add(effect("minecraft:speed", 200, 0));
        active.add(effect("absent_mod:blessing", -1, 3));
        template.put("ActiveEffects", active);

        MaidOriginData.captureTemplatePresetEffects(template);
        ListTag preset = MaidOriginData.getOrigin(template).getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND);
        assertEquals(2, preset.size());
        assertEquals("minecraft:regeneration", preset.getCompound(0).getString("forge:id"));
        assertEquals(1, preset.getCompound(0).getByte("Amplifier"));
        assertFalse(preset.getCompound(0).contains("HiddenEffect"));
        assertFalse(preset.getCompound(0).contains("Id"));
        assertEquals("absent_mod:blessing", preset.getCompound(1).getString("forge:id"));

        permanent.putByte("Amplifier", (byte) 8);
        active.add(effect("minecraft:strength", -1, 5));
        CompoundTag restored = new CompoundTag();
        MaidOriginData.restoreTransportData(template, restored);
        assertEquals(preset, MaidOriginData.getOrigin(restored).getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND));
        assertEquals(2, MaidOriginData.getOrigin(restored).getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND).size());
    }

    @Test
    void placingDataPackOverrideUsesItsInitialEffectsRatherThanOutdatedEmbeddedSnapshot() {
        CompoundTag template = template();
        ListTag original = new ListTag();
        original.add(effect("minecraft:regeneration", -1, 1));
        template.getCompound("ForgeData").getCompound(MaidOriginData.ORIGIN).put(MaidOriginData.PRESET_EFFECTS, original);
        ListTag replacement = new ListTag();
        replacement.add(effect("minecraft:strength", -1, 2));
        template.put("ActiveEffects", replacement);
        MaidOriginData.captureTemplatePresetEffects(template);
        assertEquals(replacement, MaidOriginData.getOrigin(template).get(MaidOriginData.PRESET_EFFECTS));
    }

    @Test
    void ordinaryUnmarkedTemplateDoesNotAcquireOriginOrMaintenance() {
        CompoundTag ordinary = new CompoundTag();
        ordinary.putString("id", "touhou_little_maid:maid");
        ListTag active = new ListTag();
        active.add(effect("minecraft:strength", -1, 5));
        ordinary.put("ActiveEffects", active);
        CompoundTag before = ordinary.copy();
        MaidOriginData.captureTemplatePresetEffects(ordinary);
        assertEquals(before, ordinary);
    }

    @Test
    void legacyMigrationUsesTemplatePresetsOnceAndNeverReplacesExplicitEmptySnapshot() {
        CompoundTag persistent = new CompoundTag();
        CompoundTag origin = MaidOriginData.createOrigin("touhou_little_maid_spell:elven_realm", true);
        origin.remove(MaidOriginData.PRESET_EFFECTS);
        persistent.put(MaidOriginData.ORIGIN, origin);
        ListTag templateEffects = new ListTag();
        templateEffects.add(effect("minecraft:regeneration", -1, 1));
        MaidOriginData.initializeLegacyPresets(persistent, null);
        assertFalse(origin.contains(MaidOriginData.PRESET_EFFECTS));
        MaidOriginData.initializeLegacyPresets(persistent, templateEffects);
        templateEffects.add(effect("minecraft:strength", -1, 5));
        MaidOriginData.initializeLegacyPresets(persistent, templateEffects);
        assertEquals(1, origin.getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND).size());
        origin.put(MaidOriginData.PRESET_EFFECTS, new ListTag());
        MaidOriginData.initializeLegacyPresets(persistent, templateEffects);
        assertTrue(origin.getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND).isEmpty());
    }

    @Test
    void transportRestoresMissingPresetsEvenAfterEffectsWereRemovedAndPreservesUnrelatedEffects() {
        CompoundTag persistent = new CompoundTag();
        ListTag preset = new ListTag();
        preset.add(effect("minecraft:regeneration", -1, 1));
        preset.add(effect("absent_mod:blessing", -1, 3));
        persistent.put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin("touhou_little_maid_spell:elven_realm", true, preset));
        CompoundTag data = new CompoundTag();
        ListTag current = new ListTag();
        current.add(effect("minecraft:strength", 100, 5));
        data.put("ActiveEffects", current);
        MaidOriginData.mergePresetEffects(persistent, data);
        assertEquals(3, data.getList("ActiveEffects", Tag.TAG_COMPOUND).size());
        assertEquals(current.getCompound(0), data.getList("ActiveEffects", Tag.TAG_COMPOUND).getCompound(0));
        MaidOriginData.mergePresetEffects(persistent, data);
        assertEquals(3, data.getList("ActiveEffects", Tag.TAG_COMPOUND).size());
        assertEquals(2, MaidOriginData.getOrigin(persistent).getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND).size());
    }

    @Test
    void strongerFiniteEffectCanExpireBackToInitialInfiniteEffectWithoutMakingTheStrongerLevelPermanent() {
        MobEffectInstance expected = new MobEffectInstance(MobEffects.REGENERATION, -1, 1);
        MobEffectInstance current = new MobEffectInstance(MobEffects.REGENERATION, 200, 4);
        assertTrue(MaidOriginData.needsPresetEffect(null, expected));
        assertTrue(MaidOriginData.needsPresetEffect(current, expected));
        current.update(expected);
        assertEquals(4, current.getAmplifier());
        assertEquals(200, current.getDuration());
        CompoundTag saved = current.save(new CompoundTag());
        assertEquals(-1, saved.getCompound("HiddenEffect").getInt("Duration"));
        assertEquals(1, saved.getCompound("HiddenEffect").getByte("Amplifier"));
        assertFalse(MaidOriginData.needsPresetEffect(expected, expected));
        assertFalse(MaidOriginData.needsPresetEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 4), expected));
        assertTrue(MaidOriginData.needsPresetEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 0), expected));
    }

    @Test
    void absentNamedEffectNeverFallsBackToUnrelatedNumericEffectId() {
        CompoundTag absent = effect("absent_mod:blessing", -1, 3);
        absent.putInt("Id", 10);
        assertNull(MaidOriginData.loadPresetEffect(absent));
        assertNotNull(MaidOriginData.loadPresetEffect(effect("minecraft:regeneration", -1, 1)));
    }

    @Test
    void multipleTemplatesWithDifferentInitialEffectsAreAmbiguousForLegacyMigration() {
        var sources = new HashMap<String, ListTag>();
        ListTag first = new ListTag();
        first.add(effect("minecraft:regeneration", -1, 1));
        ListTag second = new ListTag();
        second.add(effect("minecraft:strength", -1, 1));
        assertTrue(MaidOriginPresetReloadListener.mergeSource(sources, "mod:source", first));
        assertTrue(MaidOriginPresetReloadListener.mergeSource(sources, "mod:source", first.copy()));
        assertFalse(MaidOriginPresetReloadListener.mergeSource(sources, "mod:source", second));
    }

    private static CompoundTag template() {
        CompoundTag data = new CompoundTag();
        data.putString("id", "touhou_little_maid:maid");
        CompoundTag persistent = new CompoundTag();
        persistent.put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin("touhou_little_maid_spell:elven_realm", true));
        data.put("ForgeData", persistent);
        return data;
    }

    private static CompoundTag effect(String id, int duration, int amplifier) {
        CompoundTag effect = new CompoundTag();
        effect.putString("forge:id", id);
        effect.putInt("Duration", duration);
        effect.putByte("Amplifier", (byte) amplifier);
        effect.putBoolean("ShowParticles", false);
        return effect;
    }
}
