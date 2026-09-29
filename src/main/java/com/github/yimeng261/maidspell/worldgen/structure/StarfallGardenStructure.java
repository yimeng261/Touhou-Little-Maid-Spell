package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.github.yimeng261.maidspell.worldgen.StarfallGardenData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 星落之庭按出生点水平距离选址，再按地表高度抬升为空岛。
 * 距离平方使用 long；只有结构确实生成后才写入单实例标记。
 */
public class StarfallGardenStructure extends Structure {
    public static final Codec<StarfallGardenStructure> CODEC = RecordCodecBuilder.create(instance ->
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
                    // Config 是全局的，改一次会影响所有存档，而且别的 agent 正在动 Config。
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
    private static final ThreadLocal<Boolean> INSIDE_GENERATE =
            ThreadLocal.withInitial(() -> false);

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

    /**
     * 结构注册表是跨维度共享的；用当前维度的 ChunkGenerator 身份筛掉自定义维度，
     * 避免固定 cherry_grove 的维度也吃到这座主世界结构。生物群系不参与选址，
     * 只由结构 JSON 的主世界标签保证结构集能被纳入正常 worldgen。
     */
    private static boolean isOverworldGeneration(GenerationContext context) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            // 数据生成和没有服务器实例的模板校验没有维度上下文，保留原来的可解析行为。
            return true;
        }
        return server.overworld().getChunkSource().getGenerator() == context.chunkGenerator();
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        // StructureCheck 会缓存这里的结果；已被其它线程占用或已经生成后，探测必须立即返回空。
        // 真正的 generate() 已经通过 CAS 抢到名额，并由 INSIDE_GENERATE 放行。
        if (StarfallGardenData.isPlaced() && !INSIDE_GENERATE.get()) {
            return Optional.empty();
        }
        if (!isOverworldGeneration(context)) {
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
                this.maxDistanceFromCenter
        );
    }

    /**
     * 星落之庭没有生物群系限制。原版默认实现会在这里再次调用
     * {@code structure.biomes()::contains}，导致 StructureCheck 和部分定位逻辑把候选提前判空。
     */
    @Override
    public @NotNull Optional<GenerationStub> findValidGenerationPoint(@NotNull GenerationContext context) {
        return this.findGenerationPoint(context);
    }

    /**
     * 只在 StructureStart 有效时标记已生成。findGenerationPoint 可能被试探性调用，
     * 不能在那里占用名额；手动 /place structure 同样计入一座。
     */
    @Override
    public @NotNull StructureStart generate(RegistryAccess registryAccess, ChunkGenerator chunkGenerator,
                                           BiomeSource biomeSource, RandomState randomState,
                                           StructureTemplateManager templateManager, long seed, ChunkPos chunkPos,
                                           int references, LevelHeightAccessor heightAccessor,
                                           Predicate<Holder<Biome>> validBiome) {
        if (!StarfallGardenData.tryBeginGeneration()) {
            return StructureStart.INVALID_START;
        }
        INSIDE_GENERATE.set(true);
        try {
            // 星落之庭不限制具体生物群系；结构的 biome tag 仅用于让结构集进入主世界生成状态。
            StructureStart start = super.generate(registryAccess, chunkGenerator, biomeSource, randomState,
                    templateManager, seed, chunkPos, references, heightAccessor, holder -> true);
            if (start.isValid()) {
                // 补生成要先检查覆盖区块并写入结构索引，成功落地后由 Retrofit 显式记账。
                if (!StarfallGardenData.isRetrofitGeneration()) {
                    StarfallGardenData.markPlaced(start.getBoundingBox().getCenter());
                }
            } else {
                StarfallGardenData.abortGeneration();
            }
            return start;
        } catch (RuntimeException e) {
            StarfallGardenData.abortGeneration();
            throw e;
        } finally {
            INSIDE_GENERATE.remove();
        }
    }

    @Override
    public @NotNull StructureType<?> type() {
        return MaidSpellStructures.STARFALL_GARDEN.get();
    }
}
