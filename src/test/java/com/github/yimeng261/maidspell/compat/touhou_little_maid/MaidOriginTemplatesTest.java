package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MaidOriginTemplatesTest {
    @Test
    void everyPackagedStructureMaidDeclaresOriginAndPermanentEffectsOptIn() throws Exception {
        Path root = Path.of(System.getProperty("maidspell.projectDir"),
                "src/main/resources/data/touhou_little_maid_spell/structures");
        Set<String> sources = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".nbt")).toList()) {
                CompoundTag template;
                try (var input = Files.newInputStream(path)) {
                    template = NbtIo.readCompressed(input);
                }
                for (Tag element : template.getList("entities", Tag.TAG_COMPOUND)) {
                    CompoundTag maid = ((CompoundTag) element).getCompound("nbt");
                    if (!"touhou_little_maid:maid".equals(maid.getString("id"))) {
                        continue;
                    }
                    String label = root.relativize(path).toString();
                    CompoundTag origin = MaidOriginData.getOrigin(maid);
                    assertEquals(1, origin.getInt("Version"), label);
                    assertFalse(origin.getString("Source").isEmpty(), label);
                    sources.add(origin.getString("Source"));
                    boolean permanent = maid.getList("ActiveEffects", Tag.TAG_COMPOUND).stream()
                            .anyMatch(effect -> ((CompoundTag) effect).getInt("Duration") == -1);
                    if (permanent) {
                        assertTrue(MaidOriginData.preservesPermanentEffects(maid), label);
                    }
                    if (MaidOriginData.HIDDEN_RETREAT_SOURCE.equals(origin.getString("Source"))) {
                        assertTrue(maid.getCompound("ForgeData").getBoolean(MaidOriginData.HIDDEN_RETREAT_MAID));
                        assertFalse(maid.contains("Owner"), "Waiting template must be unowned");
                    }
                }
            }
        }
        assertTrue(sources.containsAll(Set.of("touhou_little_maid_spell:elven_realm",
                "touhou_little_maid_spell:fairy_maid_cafe", "touhou_little_maid_spell:fallen_sanctum",
                "touhou_little_maid_spell:relic_sanctum", MaidOriginData.HIDDEN_RETREAT_SOURCE)));
    }
}
