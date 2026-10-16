package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 由可选联动模组控制是否生成的单个刚性模板结构。
 * 下界模板按洞穴地面选址，不用高度图，否则会选到基岩顶。
 */
public class OptionalModTemplateStructure extends Structure {
    public static final MapCodec<OptionalModTemplateStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.STRING.fieldOf("required_mod").forGetter(structure -> structure.requiredMod),
                    Codec.BOOL.optionalFieldOf("nether", false).forGetter(structure -> structure.nether),
                    Codec.intRange(0, 1).optionalFieldOf("surface_offset", 1)
                            .forGetter(structure -> structure.surfaceOffset),
                    Codec.intRange(0, 16).optionalFieldOf("max_terrain_step", 3)
                            .forGetter(structure -> structure.maxTerrainStep),
                    TemplateFallback.CODEC.optionalFieldOf("fallback").forGetter(structure -> structure.fallback)
            ).apply(instance, OptionalModTemplateStructure::new));

    // 原版下界顶部基岩从 Y=123 开始，模板不能碰到它。
    private static final int MAX_NETHER_TEMPLATE_Y = 122;

    private final Holder<StructureTemplatePool> startPool;
    private final String requiredMod;
    private final boolean nether;
    private final int surfaceOffset;
    private final int maxTerrainStep;
    private final Optional<TemplateFallback> fallback;

    public OptionalModTemplateStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool,
                                        String requiredMod, boolean nether, int surfaceOffset, int maxTerrainStep,
                                        Optional<TemplateFallback> fallback) {
        super(settings);
        this.startPool = startPool;
        this.requiredMod = requiredMod;
        this.nether = nether;
        this.surfaceOffset = surfaceOffset;
        this.maxTerrainStep = maxTerrainStep;
        this.fallback = fallback;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        // 先判断模组是否存在，再加载模板方块表和方块实体。
        if (!ModList.get().isLoaded(requiredMod)) {
            return Optional.empty();
        }

        StructurePoolElement element;
        if (fallback.isPresent() && !ModList.get().isLoaded(fallback.get().mod())) {
            element = StructurePoolElement.single(fallback.get().template().toString())
                    .apply(StructureTemplatePool.Projection.RIGID);
        } else {
            element = startPool.value().getRandomTemplate(context.random());
        }
        if (element == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }
        Rotation rotation = Rotation.getRandom(context.random());
        if (element.getSize(context.structureTemplateManager(), rotation).getY() <= 0) {
            return Optional.empty();
        }

        BoundingBox relativeBox = element.getBoundingBox(context.structureTemplateManager(), BlockPos.ZERO, rotation);
        int centerX = context.chunkPos().getMinBlockX() + 8;
        int centerZ = context.chunkPos().getMinBlockZ() + 8;
        BlockPos horizontalOrigin = new BlockPos(
                centerX - (relativeBox.minX() + relativeBox.maxX()) / 2, 0,
                centerZ - (relativeBox.minZ() + relativeBox.maxZ()) / 2);
        BoundingBox footprint = element.getBoundingBox(context.structureTemplateManager(), horizontalOrigin, rotation);
        int[] sampleX = sampleCoordinates(footprint.minX(), centerX, footprint.maxX());
        int[] sampleZ = sampleCoordinates(footprint.minZ(), centerZ, footprint.maxZ());

        OptionalInt floor = nether
                ? findNetherFloor(context, sampleX, sampleZ, relativeBox.getYSpan())
                : findSurfaceFloor(context, sampleX, sampleZ);
        if (floor.isEmpty()) {
            return Optional.empty();
        }

        BlockPos origin = horizontalOrigin.atY(floor.getAsInt() + surfaceOffset);
        BoundingBox box = element.getBoundingBox(context.structureTemplateManager(), origin, rotation);
        if (box.minY() < context.heightAccessor().getMinBuildHeight()
                || box.maxY() >= context.heightAccessor().getMaxBuildHeight()
                || (nether && box.maxY() > MAX_NETHER_TEMPLATE_Y)) {
            return Optional.empty();
        }

        // 即使模板第一层是花、家具等地面装饰，地形适配的地面基准也要落在自然地面上。
        PoolElementStructurePiece piece = new PoolElementStructurePiece(context.structureTemplateManager(),
                element, origin, -surfaceOffset, rotation, box, LiquidSettings.APPLY_WATERLOGGING);
        BlockPos center = new BlockPos(centerX, origin.getY(), centerZ);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(piece)));
    }

    private static int[] sampleCoordinates(int min, int center, int max) {
        int inset = Math.min(2, (max - min) / 2);
        return new int[]{min + inset, center, max - inset};
    }

    private OptionalInt findSurfaceFloor(GenerationContext context, int[] sampleX, int[] sampleZ) {
        int minFloor = Integer.MAX_VALUE;
        int maxFloor = Integer.MIN_VALUE;
        for (int x : sampleX) {
            for (int z : sampleZ) {
                int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
                if (!isDryGround(column.getBlock(y))) {
                    return OptionalInt.empty();
                }
                minFloor = Math.min(minFloor, y);
                maxFloor = Math.max(maxFloor, y);
                if (maxFloor - minFloor > maxTerrainStep) {
                    return OptionalInt.empty();
                }
            }
        }
        return OptionalInt.of(maxFloor);
    }

    private OptionalInt findNetherFloor(GenerationContext context, int[] sampleX, int[] sampleZ, int height) {
        NoiseColumn[] columns = new NoiseColumn[sampleX.length * sampleZ.length];
        int index = 0;
        for (int x : sampleX) {
            for (int z : sampleZ) {
                columns[index++] = context.chunkGenerator().getBaseColumn(x, z,
                        context.heightAccessor(), context.randomState());
            }
        }
        int minFloor = Math.max(context.chunkGenerator().getSeaLevel(),
                context.heightAccessor().getMinBuildHeight() + 8);
        int maxTop = Math.min(MAX_NETHER_TEMPLATE_Y, context.heightAccessor().getMaxBuildHeight() - 2);
        return findNetherFloor(columns, minFloor, maxTop, height, surfaceOffset, maxTerrainStep);
    }

    // 包可见，便于不开服用合成地形做测试。
    static OptionalInt findNetherFloor(NoiseColumn[] columns, int minFloor, int maxTop,
                                      int height, int surfaceOffset, int maxTerrainStep) {
        for (int floor = maxTop - height + 1 - surfaceOffset; floor >= minFloor; floor--) {
            // 以采样到的最高地面为锚点；较低的相邻列由地形适配补足支撑，不强求洞穴地面完全平整。
            boolean anchored = false;
            for (NoiseColumn column : columns) {
                anchored |= isNetherGround(column.getBlock(floor));
            }
            if (!anchored) {
                continue;
            }
            boolean suitable = true;
            for (NoiseColumn column : columns) {
                boolean supported = false;
                for (int y = floor; y >= floor - maxTerrainStep; y--) {
                    BlockState state = column.getBlock(y);
                    if (isNetherGround(state)) {
                        supported = true;
                        break;
                    }
                    if (!isOpenSpace(state)) {
                        break;
                    }
                }
                if (!supported) {
                    suitable = false;
                    break;
                }
                int top = floor + surfaceOffset + height - 1;
                for (int y = floor + 1; y <= top + 1; y++) {
                    if (!isOpenSpace(column.getBlock(y))) {
                        suitable = false;
                        break;
                    }
                }
                if (!suitable) {
                    break;
                }
            }
            if (suitable) {
                return OptionalInt.of(floor);
            }
        }
        return OptionalInt.empty();
    }

    private static boolean isDryGround(BlockState state) {
        return state.blocksMotion() && state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK);
    }

    private static boolean isNetherGround(BlockState state) {
        return state.is(Blocks.NETHERRACK) || state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.CRIMSON_NYLIUM) || state.is(Blocks.WARPED_NYLIUM);
    }

    private static boolean isOpenSpace(BlockState state) {
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.OPTIONAL_MOD_TEMPLATE.get();
    }

    public record TemplateFallback(String mod, ResourceLocation template) {
        private static final Codec<TemplateFallback> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("mod").forGetter(TemplateFallback::mod),
                ResourceLocation.CODEC.fieldOf("template").forGetter(TemplateFallback::template)
        ).apply(instance, TemplateFallback::new));
    }
}
