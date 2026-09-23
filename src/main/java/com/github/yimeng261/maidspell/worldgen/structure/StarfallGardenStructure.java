package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.worldgen.MaidSpellStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 星落之庭结构。
 *
 * <p>它的落点规则和本模组其它地表建筑都不一样：不看地形、不看生物群系细节，
 * 只认<b>离世界出生点的水平距离</b>。原因写在需求里——这座庭院是给刚出生的玩家留的
 * 一个「往外走一段路才能撞见」的目标：太近了会压着出生点、把新手村那一圈改成废墟；
 * 太远了又变成边境内容，正常流程里根本走不到。200~500 格这个环带正好是「先探索一会儿
 * 再遇到」的距离。
 *
 * <p>实现上有两个坑，都在下面各自的方法上写着：一是距离必须用<b>水平</b>距离并且用
 * {@code long} 平方比较（出生点和候选点的坐标差可以到千万级，平方后远超 int），
 * 二是出生点在数据生成、单元测试期间是拿不到的，必须能退化而不是抛异常。
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
                            .forGetter(structure -> structure.maxSpawnDistance)
            ).apply(instance, StarfallGardenStructure::new)
    );

    /**
     * 拼图把起始件的底层放在 {@code startPos.getY() - groundLevelDelta} 上，
     * 见 {@code JigsawPlacement.addPieces} 里那句
     * {@code piece.move(0, startY - (bbox.minY() + groundLevelDelta), 0)}。
     * 单件池元素的 {@code getGroundLevelDelta()} 恒为 1，所以传进去的 Y 要先加回这 1，
     * 底层才会正好落在我们算出来的地表那一格上（{@code getFirstFreeHeight} 返回的本来就是
     * 地表之上第一格空气的 Y，也就是地板该占的那一格）。
     */
    private static final int GROUND_LEVEL_DELTA = 1;

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int maxDistanceFromCenter;
    private final int minSpawnDistance;
    private final int maxSpawnDistance;

    public StarfallGardenStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
                                   int maxDistanceFromCenter, int minSpawnDistance, int maxSpawnDistance) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.minSpawnDistance = minSpawnDistance;
        this.maxSpawnDistance = maxSpawnDistance;
    }

    /**
     * 世界出生点，拿不到时退化成原点。
     *
     * <p>走 {@link ServerLifecycleHooks} 而不是缓存一份坐标，是因为出生点可以被
     * {@code /setworldspawn} 改，也能被床重设；每次判定现取，改完立刻生效。
     *
     * <p>服务器为 null 不是异常情况：数据生成（{@code runData}）只解析结构 JSON、
     * 不生成区块，结构模板校验也可能在没有服务器实例的线程里跑。这种时候返回
     * {@link BlockPos#ZERO}，让距离判定退化成一个「离原点 200~500 格」的普通条件，
     * 既不会崩，也不会把结构整个吞掉。
     */
    private static BlockPos overworldSpawnOrOrigin() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return BlockPos.ZERO;
        }
        return server.overworld().getSharedSpawnPos();
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
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

        // 区块中心的地表高度。和观星塔那种多采样取中位数的做法不同：这里只锚定一格，
        // 地形起伏交给数据包里的 terrain_adaptation: beard_thin 去抹平。
        int surface = context.chunkGenerator().getFirstFreeHeight(
                centerX, centerZ,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),
                context.randomState()
        );

        BlockPos startPos = new BlockPos(centerX, surface + GROUND_LEVEL_DELTA, centerZ);
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
        return MaidSpellStructures.STARFALL_GARDEN.get();
    }
}
