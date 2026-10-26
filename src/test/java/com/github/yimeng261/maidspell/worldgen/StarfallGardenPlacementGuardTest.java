package com.github.yimeng261.maidspell.worldgen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarfallGardenPlacementGuardTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        SharedConstants.tryDetectVersion();
        // 单元测试只初始化原版注册表；完整 Forge bootstrap 的网络监听器需要 ModLauncher。
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        if (!BuiltInRegistries.PAINTING_VARIANT.containsKey(new ResourceLocation("minecraft", "kebab"))) {
            BuiltInRegistries.bootStrap();
        }
    }

    private final Holder<Biome> biome = biome();
    private final Structure garden = new TestStructure(biome);
    private final Structure other = new TestStructure(biome);

    @Test
    void savedStartsOnlyBlockWhenTheirVerticalRangeOverlapsTheGarden() {
        ProtoChunk chunk = chunk();
        StructureStart otherStart = start(other);
        chunk.setStartForStructure(other, otherStart);
        BoundingBox otherBox = otherStart.getBoundingBox();

        // 庭院完全在对方上方：地下/地面结构不应该再拦补生成。
        BoundingBox above = new BoundingBox(otherBox.minX(), otherBox.maxY() + 20, otherBox.minZ(),
                otherBox.maxX(), otherBox.maxY() + 60, otherBox.maxZ());
        assertFalse(StarfallGardenPlacementGuard.hasOtherStructure(chunk, garden, above));

        // 竖直范围重叠：同高度/伸进庭院带的真实结构仍然要挡。
        BoundingBox overlapping = new BoundingBox(otherBox.minX(), otherBox.minY(), otherBox.minZ(),
                otherBox.maxX(), otherBox.maxY(), otherBox.maxZ());
        assertTrue(StarfallGardenPlacementGuard.hasOtherStructure(chunk, garden, overlapping));
    }

    @Test
    void invalidStartsAndTheGardenItselfDoNotBlock() {
        ProtoChunk chunk = chunk();
        chunk.setStartForStructure(garden, start(garden));
        chunk.setStartForStructure(other, StructureStart.INVALID_START);
        assertFalse(StarfallGardenPlacementGuard.hasOtherStructure(
                chunk, garden, start(garden).getBoundingBox()));
    }

    @Test
    void savedReferencesAloneNoLongerBlock() {
        ProtoChunk chunk = chunk();
        chunk.addReferenceForStructure(other, ChunkPos.asLong(5, -5));
        // 轻量版不解析 References：引用所在的区块不一定等于结构起点，拿不到可靠高度就先不挡。
        assertFalse(StarfallGardenPlacementGuard.hasOtherStructure(
                chunk, garden, start(garden).getBoundingBox()));
    }

    private ProtoChunk chunk() {
        Registry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Registry.register(biomes, Biomes.PLAINS, biome.value());
        return new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 384), biomes, null);
    }

    private static StructureStart start(Structure structure) {
        return new StructureStart(structure, new ChunkPos(0, 0), 0, new PiecesContainer(
                List.of(new BuriedTreasurePieces.BuriedTreasurePiece(new BlockPos(0, 64, 0)))));
    }

    private static Holder<Biome> biome() {
        return Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(true).temperature(0.8F).downfall(0.4F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build());
    }

    private static final class TestStructure extends Structure {
        private TestStructure(Holder<Biome> biome) {
            super(new StructureSettings(HolderSet.direct(biome), Map.of(),
                    GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        }

        @Override
        protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            throw new AssertionError("避让检查不能尝试生成结构");
        }

        @Override
        public StructureType<?> type() {
            throw new AssertionError("避让检查只需要已保存的结构数据");
        }
    }
}
