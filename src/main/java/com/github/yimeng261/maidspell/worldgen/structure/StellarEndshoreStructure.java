package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 星途终岸结构。
 *
 * <p>悬在末地外岛上空的固定高度，<b>完全不看地形</b>：末地的岛顶大约在 Y60-70，
 * 我们生在 150，脚下是岛、是空、还是别的结构都无所谓，所以这里没有
 * {@code RelicSanctumStructure} 那套地形取样，也不需要 {@code terrain_adaptation}
 * ——没有地面要削平，加了反而会在半空糊出一坨基座。
 *
 * <p>高度做成可配的 {@link #baseHeight} 加抖动，是因为一整片末地全在同一个 Y 上
 * 会看出是刷出来的。抖动后仍会夹住上界，见 {@link #MAX_STRUCTURE_HEIGHT}。
 */
public class StellarEndshoreStructure extends Structure {
    public static final Codec<StellarEndshoreStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size),
                    Codec.intRange(1, 256)
                            .optionalFieldOf("max_distance_from_center", 256)
                            .forGetter(structure -> structure.maxDistanceFromCenter),
                    Codec.intRange(0, 320)
                            .optionalFieldOf("base_height", 150)
                            .forGetter(structure -> structure.baseHeight),
                    Codec.intRange(0, 64)
                            .optionalFieldOf("height_jitter", 5)
                            .forGetter(structure -> structure.heightJitter)
            ).apply(instance, StellarEndshoreStructure::new)
    );

    /**
     * 起始块往上最多能长多高：起始件 30 格，其上叠的塔 47 格。
     * 拿它把落点夹在世界上界以内，抖动才不会把塔顶顶出去。
     */
    private static final int MAX_STRUCTURE_HEIGHT = 80;

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;
    private final int baseHeight;
    private final int heightJitter;

    public StellarEndshoreStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
                                    int maxDistanceFromCenter, int baseHeight, int heightJitter) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.baseHeight = baseHeight;
        this.heightJitter = heightJitter;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        int x = context.chunkPos().getMinBlockX() + 8;
        int z = context.chunkPos().getMinBlockZ() + 8;

        int y = this.baseHeight;
        if (this.heightJitter > 0) {
            y += context.random().nextInt(this.heightJitter * 2 + 1) - this.heightJitter;
        }
        y = Mth.clamp(
                y,
                context.heightAccessor().getMinBuildHeight(),
                context.heightAccessor().getMaxBuildHeight() - MAX_STRUCTURE_HEIGHT
        );

        BlockPos startPos = new BlockPos(x, y, z);
        return JigsawPlacement.addPieces(
                context,
                this.startPool,
                Optional.empty(),
                this.size,
                startPos,
                false,
                Optional.empty(),
                this.maxDistanceFromCenter
        );
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.STELLAR_ENDSHORE.get();
    }
}
