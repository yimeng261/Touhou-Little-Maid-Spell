package com.github.yimeng261.maidspell.worldgen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
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
    void rejectsSameChunkAndAllEightNeighborsWithoutGeneratingStructures() {
        var state = state(set(gridPlacement(), other));
        for (int anchorX : new int[]{-64, 0, 64}) {
            for (int anchorZ : new int[]{-64, 0, 64}) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        assertTrue(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(
                                state, garden, new ChunkPos(anchorX + dx, anchorZ + dz)));
                    }
                }
            }
        }
    }

    @Test
    void permitsCandidatesAtLeastTwoChunksAwayIncludingNegativeCoordinates() {
        var state = state(set(gridPlacement(), other));
        for (ChunkPos candidate : List.of(new ChunkPos(2, 0), new ChunkPos(-2, 0),
                new ChunkPos(0, 2), new ChunkPos(0, -2), new ChunkPos(-62, 66))) {
            assertFalse(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state, garden, candidate));
        }
    }

    @Test
    void ignoresItsOwnSetsButProtectsOtherEntriesInMixedSets() {
        var ownSet = set(everyChunkPlacement(), garden);
        assertFalse(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state(ownSet), garden,
                new ChunkPos(0, 0)));
        assertTrue(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(
                state(ownSet, set(everyChunkPlacement(), garden, other)), garden, new ChunkPos(0, 0)));
    }

    @Test
    void resultDoesNotDependOnStructureSetOrder() {
        var ownSet = set(everyChunkPlacement(), garden);
        var otherSet = set(gridPlacement(), other);
        assertTrue(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state(ownSet, otherSet), garden,
                new ChunkPos(1, -1)));
        assertTrue(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state(otherSet, ownSet), garden,
                new ChunkPos(1, -1)));
    }

    @Test
    void respectsPlacementFrequencyAndExclusionZones() {
        var disabled = new RandomSpreadStructurePlacement(Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT, 0.0F, 123,
                Optional.empty(), 1, 0, RandomSpreadType.LINEAR);
        assertFalse(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(state(set(disabled, other)), garden,
                new ChunkPos(0, 0)));

        var ownSet = set(everyChunkPlacement(), garden);
        var excluded = new RandomSpreadStructurePlacement(Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, 123,
                Optional.of(new StructurePlacement.ExclusionZone(ownSet, 1)), 1, 0, RandomSpreadType.LINEAR);
        assertFalse(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(
                state(ownSet, set(excluded, other)), garden, new ChunkPos(0, 0)));
    }

    @Test
    void ignoresStructureSetsUnavailableInTheCurrentDimension() {
        Structure unavailable = new TestStructure(biome());
        assertFalse(StarfallGardenPlacementGuard.hasNearbyStructureAttempt(
                state(set(everyChunkPlacement(), unavailable)), garden, new ChunkPos(0, 0)));
    }

    @Test
    void savedValidStartsBlockRetrofitButFailedStartsAndTheGardenItselfDoNot() {
        ProtoChunk chunk = chunk();
        chunk.setStartForStructure(garden, start(garden));
        chunk.setStartForStructure(other, StructureStart.INVALID_START);
        assertFalse(StarfallGardenPlacementGuard.hasOtherStructure(chunk, garden));

        chunk.setStartForStructure(other, start(other));
        assertTrue(StarfallGardenPlacementGuard.hasOtherStructure(chunk, garden));
    }

    @Test
    void savedReferencesBlockRetrofitEvenWhenTheStructureStartsInADistantChunk() {
        ProtoChunk chunk = chunk();
        chunk.addReferenceForStructure(garden, ChunkPos.asLong(0, 0));
        // 原版查询一个没有引用的结构会返回空集合，不能把它当成占用。
        chunk.getReferencesForStructure(other);
        assertFalse(StarfallGardenPlacementGuard.hasOtherStructure(chunk, garden));

        chunk.addReferenceForStructure(other, ChunkPos.asLong(5, -5));
        assertTrue(StarfallGardenPlacementGuard.hasOtherStructure(chunk, garden));
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

    @SafeVarargs
    private final ChunkGeneratorStructureState state(Holder<StructureSet>... sets) {
        return ChunkGeneratorStructureState.createForFlat(null, 42L, new FixedBiomeSource(biome),
                Arrays.stream(sets));
    }

    private static Holder<StructureSet> set(StructurePlacement placement, Structure... structures) {
        return Holder.direct(new StructureSet(Arrays.stream(structures)
                .map(structure -> new StructureSet.StructureSelectionEntry(Holder.direct(structure), 1))
                .toList(), placement));
    }

    private static RandomSpreadStructurePlacement gridPlacement() {
        // separation=spacing-1 使落点固定在 64 的倍数，精确覆盖区块边界与负坐标。
        return new RandomSpreadStructurePlacement(64, 63, RandomSpreadType.LINEAR, 123);
    }

    private static RandomSpreadStructurePlacement everyChunkPlacement() {
        return new RandomSpreadStructurePlacement(1, 0, RandomSpreadType.LINEAR, 123);
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
            throw new AssertionError("避让检查不能尝试生成其他结构");
        }

        @Override
        public StructureType<?> type() {
            throw new AssertionError("避让检查只需要结构集的 placement");
        }
    }
}
