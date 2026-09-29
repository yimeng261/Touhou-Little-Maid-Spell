package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.github.yimeng261.maidspell.worldgen.StarfallGardenData;
import com.github.yimeng261.maidspell.worldgen.accessor.RandomStateAccessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 星落之庭只在主世界生成，按出生点水平距离选址，再按地表高度抬升为空岛。
 * 距离平方使用 long；只有结构确实生成后才占用单实例名额。
 */
public class StarfallGardenStructure extends Structure {
    public static final MapCodec<StarfallGardenStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Codec.intRange(0, 30).fieldOf("size").forGetter(structure -> structure.size),
                    Codec.intRange(1, 512)
                            .optionalFieldOf("max_distance_from_center", 256)
                            .forGetter(structure -> structure.maxDistanceFromCenter),
                    // 环带的两条边做成数据包字段而不是写死的常量：不同整合包的世界尺度差很多，
                    // 想改成 500~1000 只要动 JSON，不用重新编译。
                    Codec.intRange(0, 100000)
                            .optionalFieldOf("min_spawn_distance", 200)
                            .forGetter(structure -> structure.minSpawnDistance),
                    Codec.intRange(1, 100000)
                            .optionalFieldOf("max_spawn_distance", 500)
                            .forGetter(structure -> structure.maxSpawnDistance),
                    // 相对地表抬升；默认 72 格覆盖向下延伸 42 格的岛底并留出净空。
                    Codec.intRange(0, 384)
                            .optionalFieldOf("height_offset", 72)
                            .forGetter(structure -> structure.heightOffset),
                    // 旧存档补生成开关。放数据包而不是 Config：这是世界生成语义，该跟着存档/整合包走；
                    // Config 是全局的，改一次会影响所有存档。
                    Codec.BOOL
                            .optionalFieldOf("retrofit_on_load", true)
                            .forGetter(structure -> structure.retrofitOnLoad),
                    // 与 retrofit_on_load 分开控制是否向已生成区块写入结构。
                    Codec.BOOL
                            .optionalFieldOf("retrofit_place_in_explored", true)
                            .forGetter(structure -> structure.retrofitPlaceInExplored)
            ).apply(instance, StarfallGardenStructure::new)
    );

    /** 拼图会减去 groundLevelDelta=1，传入 Y 时须加回以对齐底层。 */
    private static final int GROUND_LEVEL_DELTA = 1;

    /**
     * 起始件底层往上最多还有多高，用来把落点夹在世界上界以内（不然塔顶会被顶出世界高度，拼图块直接消失）。
     * <p>
     * 实测形体（读模板里的 jigsaw 朝向算出来的，不是拍脑袋）：起始件 1 号是一层 4 格高的花园平台，
     * 2~4 号在它同一高度上往外接；4 号顶面有个朝上的接口接 5 号（48x47 的高塔），
     * 5 号顶层再朝上接 8 号（47x42），所以最高处在起始件底层之上约 4+46+40 ≈ 90 格。取 96 留余量。
     */
    private static final int MAX_STRUCTURE_HEIGHT = 96;

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;
    private final int minSpawnDistance;
    private final int maxSpawnDistance;
    private final int heightOffset;
    private final boolean retrofitOnLoad;
    private final boolean retrofitPlaceInExplored;

    public StarfallGardenStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
                                   int maxDistanceFromCenter, int minSpawnDistance, int maxSpawnDistance,
                                   int heightOffset, boolean retrofitOnLoad, boolean retrofitPlaceInExplored) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.minSpawnDistance = minSpawnDistance;
        this.maxSpawnDistance = maxSpawnDistance;
        this.heightOffset = heightOffset;
        this.retrofitOnLoad = retrofitOnLoad;
        this.retrofitPlaceInExplored = retrofitPlaceInExplored;
    }

    public int getMinSpawnDistance() {
        return this.minSpawnDistance;
    }

    public int getMaxSpawnDistance() {
        return this.maxSpawnDistance;
    }

    /** 旧存档补生成开关，供 {@code StarfallGardenRetrofit} 读取。 */
    public boolean isRetrofitOnLoad() {
        return this.retrofitOnLoad;
    }

    /** 补生成是否允许在已探明区块里主动落一座，供 {@code StarfallGardenRetrofit} 读取。 */
    public boolean isRetrofitPlaceInExplored() {
        return this.retrofitPlaceInExplored;
    }

    /**
     * 世界出生点，拿不到时退化成原点。
     * 走 {@link ServerLifecycleHooks} 而不是缓存一份坐标，是因为出生点可以被 {@code /setworldspawn} 改，也能被床重设；每次判定现取，改完立刻生效。
     * 服务器为 null 不是异常情况：数据生成（{@code runData}）只解析结构 JSON、不生成区块，结构模板校验也可能在没有服务器实例的线程里跑。
     * 这种时候返回 {@link BlockPos#ZERO}，让距离判定退化成一个「离原点 200~500 格」的普通条件，既不会崩，也不会把结构整个吞掉。
     */
    private static BlockPos overworldSpawnOrOrigin() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            return BlockPos.ZERO;
        }
        return server.overworld().getSharedSpawnPos();
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        // 「一存档一座」的第一道闸门：只读内存里的标记。这里跑在 worldgen worker 线程上、每个候选区块都要问一次，
        // 所以不能碰 SavedData（会读盘，而且 DimensionDataStorage 不是线程安全的）；标记什么时候写见 generate()。
        if (StarfallGardenData.isPlaced()) {
            return Optional.empty();
        }
        // 名额和出生点都属于主世界，其他维度（包括放开全部结构的归隐之地）不能占用
        ResourceKey<Level> dimension = RandomStateAccessor.dimensionOf(context.randomState());
        if (dimension != null && !dimension.equals(Level.OVERWORLD)) {
            return Optional.empty();
        }

        int centerX = context.chunkPos().getMiddleBlockX();
        int centerZ = context.chunkPos().getMiddleBlockZ();

        BlockPos spawn = overworldSpawnOrOrigin();
        // 只算水平距离：出生点的 Y 在超平坦/洞穴世界里毫无意义，纵向差也不该影响
        // 「这座庭院离出生点多远」的观感。
        long dx = (long) centerX - spawn.getX();
        long dz = (long) centerZ - spawn.getZ();
        long distSq = dx * dx + dz * dz;

        // 平方比较，省掉每次候选区块的 sqrt。下界必须用 min*min——写成 min 会把
        // 「200 格以内」误判成「200 格以内全都不生成」，环带就退化成一个空心圆了。
        // 这里强转 long 是因为坐标最大可到 3e7，平方后是 9e14，int 会溢出成负数，
        // 那样远在环带之外的结构反而会被判定为「在环带内」。
        long minSq = (long) this.minSpawnDistance * this.minSpawnDistance;
        long maxSq = (long) this.maxSpawnDistance * this.maxSpawnDistance;
        if (distSq < minSq || distSq > maxSq) {
            return Optional.empty();
        }

        // 区块中心的地表高度。和观星塔那种多采样取中位数的做法不同：这里只锚定一格。
        // WORLD_SURFACE_WG 走的是噪声高度图（不是区块里的 heightmap），所以 STRUCTURE_STARTS 阶段就能算，
        // 旧存档补生成把区块只读到 STRUCTURE_STARTS 时也一样能拿到值。
        int surface = context.chunkGenerator().getFirstFreeHeight(
                centerX, centerZ,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),
                context.randomState()
        );

        // floorY 是起始件「最底层该占的那一格」。空岛就是在它上面再加 heightOffset 格空气：
        // 拼图把底层放在 startPos.getY() - 1，所以这里给的是底层本身，不是底层之上。
        int minBuildY = context.heightAccessor().getMinBuildHeight();
        int maxBuildY = Math.max(minBuildY, context.heightAccessor().getMaxBuildHeight() - MAX_STRUCTURE_HEIGHT);
        int floorY = Mth.clamp(surface + this.heightOffset, minBuildY, maxBuildY);

        // GROUND_LEVEL_DELTA 的账没变：传进去的 Y 加回这 1，最底层才正好落在 floorY（见字段注释）。
        BlockPos startPos = new BlockPos(centerX, floorY + GROUND_LEVEL_DELTA, centerZ);
        return JigsawPlacement.addPieces(
                context,
                this.startPool,
                Optional.empty(),
                this.size,
                startPos,
                false,
                Optional.empty(),
                this.maxDistanceFromCenter,
                PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO,
                LiquidSettings.APPLY_WATERLOGGING
        );
    }

    /**
     * 星落之庭没有生物群系限制。原版默认实现会用调用方传入的群系条件再筛一遍落点，
     * StructureCheck 传的是 {@code structure.biomes()::contains}，会让它和依赖它的定位逻辑把候选提前判空。
     */
    @Override
    public @NotNull Optional<GenerationStub> findValidGenerationPoint(@NotNull GenerationContext context) {
        return this.findGenerationPoint(context);
    }

    /**
     * 只在 StructureStart 有效时占用名额。findGenerationPoint 可能被试探性调用，
     * 不能在那里占用；手动 /place structure 同样计入一座。抢占规则见 {@link StarfallGardenData#tryMarkPlaced}。
     */
    @Override
    public @NotNull StructureStart generate(RegistryAccess registryAccess, ChunkGenerator chunkGenerator,
                                           BiomeSource biomeSource, RandomState randomState,
                                           StructureTemplateManager templateManager, long seed, ChunkPos chunkPos,
                                           int references, LevelHeightAccessor heightAccessor,
                                           Predicate<Holder<Biome>> validBiome) {
        // 星落之庭不限制具体生物群系；结构的生物群系标签只用来让结构集进入主世界生成状态。
        StructureStart start = computeStart(registryAccess, chunkGenerator, biomeSource, randomState,
                templateManager, seed, chunkPos, references, heightAccessor, holder -> true);
        if (start.isValid() && !StarfallGardenData.tryMarkPlaced(start.getBoundingBox().getCenter())) {
            return StructureStart.INVALID_START;
        }
        return start;
    }

    /**
     * 按世界生成的同一套落点算出结构起点，不占名额。补生成用它选点，
     * 两条路落点一致；通过区块预算检查后再由补生成自己占名额。
     */
    public @NotNull StructureStart computeStart(RegistryAccess registryAccess, ChunkGenerator chunkGenerator,
                                                BiomeSource biomeSource, RandomState randomState,
                                                StructureTemplateManager templateManager, long seed, ChunkPos chunkPos,
                                                int references, LevelHeightAccessor heightAccessor,
                                                Predicate<Holder<Biome>> validBiome) {
        return super.generate(registryAccess, chunkGenerator, biomeSource, randomState,
                templateManager, seed, chunkPos, references, heightAccessor, validBiome);
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.STARFALL_GARDEN.get();
    }
}
