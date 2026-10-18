package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructurePlacements;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * 原版随机分散放置，排斥区换成 {@code exclusion_zones} 列表：可以同时避开多个结构集（原版的 {@code exclusion_zone} 只能写一个）。
 * 每一项和原版排斥区一样：{@code other_set} 的候选区块在 {@code chunk_count} 个区块以内时不生成。
 * {@code other_set} 只按 ID 记，当前维度没有这个结构集（比如它要的联动模组没装）时跳过，不会让数据包加载失败。
 */
public class MultiExclusionSpreadPlacement extends RandomSpreadStructurePlacement {
    public static final Codec<MultiExclusionSpreadPlacement> CODEC = ExtraCodecs.validate(
            RecordCodecBuilder.<MultiExclusionSpreadPlacement>mapCodec(instance -> instance.group(
                    Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO)
                            .forGetter(MultiExclusionSpreadPlacement::locateOffset),
                    FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method", FrequencyReductionMethod.DEFAULT)
                            .forGetter(MultiExclusionSpreadPlacement::frequencyReductionMethod),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F)
                            .forGetter(MultiExclusionSpreadPlacement::frequency),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(MultiExclusionSpreadPlacement::salt),
                    Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                    Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR)
                            .forGetter(RandomSpreadStructurePlacement::spreadType),
                    Zone.CODEC.listOf().fieldOf("exclusion_zones").forGetter(MultiExclusionSpreadPlacement::exclusionZones)
            ).apply(instance, MultiExclusionSpreadPlacement::new)),
            placement -> placement.spacing() <= placement.separation()
                    ? DataResult.error(() -> "Spacing has to be larger than separation")
                    : DataResult.success(placement)).codec();

    private final List<Zone> exclusionZones;

    public MultiExclusionSpreadPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency,
                                         int salt, int spacing, int separation, RandomSpreadType spreadType,
                                         List<Zone> exclusionZones) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, Optional.empty(), spacing, separation, spreadType);
        this.exclusionZones = List.copyOf(exclusionZones);
    }

    public List<Zone> exclusionZones() {
        return exclusionZones;
    }

    @Override
    public boolean isStructureChunk(@NotNull ChunkGeneratorStructureState structureState, int x, int z) {
        if (!super.isStructureChunk(structureState, x, z)) {
            return false;
        }
        for (Zone zone : exclusionZones) {
            for (Holder<StructureSet> set : structureState.possibleStructureSets()) {
                if (set.is(zone.otherSet()) && structureState.hasStructureChunkInRange(set, x, z, zone.chunkCount())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public @NotNull StructurePlacementType<?> type() {
        return MaidSpellStructurePlacements.MULTI_EXCLUSION_SPREAD.get();
    }

    /** 一个排斥区：other_set 的候选区块在 chunk_count 个区块以内时不生成。 */
    public record Zone(ResourceKey<StructureSet> otherSet, int chunkCount) {
        public static final Codec<Zone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.STRUCTURE_SET).fieldOf("other_set").forGetter(Zone::otherSet),
                Codec.intRange(1, 16).fieldOf("chunk_count").forGetter(Zone::chunkCount)
        ).apply(instance, Zone::new));
    }
}
