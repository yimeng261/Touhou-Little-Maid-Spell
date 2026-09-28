package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 记录星落之庭是否已生成，保证一份存档只有一座。
 * 世界生成线程只读取内存闸门；SavedData 的读写回到服务器主线程。
 * 主世界加载时恢复闸门，早于出生点区块生成。
 */
public class StarfallGardenData extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_starfall_garden";

    /** 世界生成和旧存档补生成需要不同的开闸状态。 */
    private enum Gate {
        /** 世界生成可出候选。 */
        OPEN,
        /** 世界生成停止出候选。 */
        CLOSED,
        /** 仅允许补生成主动放置。 */
        UNLOCKED_FOR_RETROFIT
    }

    private static final AtomicReference<Gate> GATE = new AtomicReference<>(Gate.OPEN);

    /** 本次服务器会话是否已经从存档读过一次；避免重复读盘，也避免读失败时反复重试。 */
    private static final AtomicBoolean RESTORED = new AtomicBoolean(false);

    private boolean generated;

    /** 庭院所在的区块原点，只用于日志和排查；不参与任何判定。 */
    @Nullable
    private BlockPos position;

    /** 记录补生成尝试，避免每次开服重复扫描候选区块。 */
    private boolean retrofitAttempted;

    public StarfallGardenData() {
    }

    public static StarfallGardenData load(CompoundTag tag) {
        StarfallGardenData data = new StarfallGardenData();
        data.generated = tag.getBoolean("Generated");
        if (tag.contains("Position")) {
            data.position = BlockPos.of(tag.getLong("Position"));
        }
        data.retrofitAttempted = tag.getBoolean("RetrofitAttempted");
        return data;
    }

    /**
     * 取主世界 data/ 目录下的这一份记录。拿不到主世界时返回 null，调用方自己决定怎么退化。
     */
    @Nullable
    private static StarfallGardenData dataOrNull(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return null;
        }
        return overworld.getDataStorage().computeIfAbsent(
                StarfallGardenData::load,
                StarfallGardenData::new,
                DATA_NAME
        );
    }

    public boolean isGenerated() {
        return this.generated;
    }

    public boolean isRetrofitAttempted() {
        return this.retrofitAttempted;
    }

    public void setGenerated(BlockPos pos) {
        this.generated = true;
        this.position = pos;
        this.setDirty();
    }

    public void setRetrofitAttempted() {
        this.retrofitAttempted = true;
        this.setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putBoolean("Generated", this.generated);
        if (this.position != null) {
            tag.putLong("Position", this.position.asLong());
        }
        tag.putBoolean("RetrofitAttempted", this.retrofitAttempted);
        return tag;
    }

    // ========== 运行期闸门 ==========

    /**
     * 是否已经有庭院了 —— 世界生成预热用。{@code CLOSED} 与 {@code UNLOCKED_FOR_RETROFIT}
     * 都算「有主了」，两条路都不该再出候选。
     */
    public static boolean isPlaced() {
        return GATE.get() != Gate.OPEN;
    }

    /**
     * 补生成专用的放置窗口：把闸门从 {@code CLOSED} 抬到 {@code UNLOCKED_FOR_RETROFIT}。
     *
     * <p>为什么不能直接绕开闸门去造 {@code StructureStart}：落点必须由 mod 自己的
     * {@code structure.generate(...)} 算出来，否则「补生成放的那一座」和「世界生成会放的那一座」
     * 会落在不同位置 —— 补生成一跑，存档里就多出一座谁也算不出来的东西。抬闸门而不是改判定，
     * 保证两条路用的是同一套落点算术。
     */
    public static void unlockForRetrofit() {
        GATE.set(Gate.UNLOCKED_FOR_RETROFIT);
    }

    /**
     * 放置窗口用完了：立刻恢复到「已有主」。
     *
     * <p>必须成对调用，且要在 {@link #markPlaced} 之前或之后都行 —— 两个动作都把闸门留在
     * 关闭侧，差别只在有没有落盘。用 {@code try/finally} 包住，别让一次生成异常把闸门
     * 永久留在敞开状态（那会让整座存档到处长庭院）。
     */
    public static void relockAfterRetrofit() {
        GATE.set(Gate.CLOSED);
    }

    /**
     * 结构真的生成之后调用，见 {@code StarfallGardenStructure#generate}。
     *
     * <p>先把内存闸门关上：并发跑的其他候选区块必须立刻看到「已经有了」，否则同一个 tick 里就能多放几座。
     * 落盘是异步的、必须在主线程，所以这里只排一个任务。
     */
    public static void markPlaced(BlockPos pos) {
        GATE.set(Gate.CLOSED);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // 数据生成 / 结构模板校验这类没有服务器实例的场合：内存闸门照关，但没有存档可写，只能放弃持久化。
            return;
        }
        server.execute(() -> {
            try {
                StarfallGardenData data = dataOrNull(server);
                if (data != null) {
                    data.setGenerated(pos);
                }
            } catch (RuntimeException e) {
                // 跑在服务器任务队列里，异常会被包成崩溃报告，这里必须自己咽掉。
                MaidSpellMod.LOGGER.error("写入星落之庭生成标记失败，重启后可能重复生成", e);
            }
        });
    }

    /** 主线程调用：补生成跑完一次后记账。 */
    public static void markRetrofitAttempted(MinecraftServer server) {
        try {
            StarfallGardenData data = dataOrNull(server);
            if (data != null) {
                data.setRetrofitAttempted();
            }
        } catch (RuntimeException e) {
            MaidSpellMod.LOGGER.error("写入星落之庭补生成标记失败，下次开服会再试一次", e);
        }
    }

    public static boolean isRetrofitAttempted(MinecraftServer server) {
        StarfallGardenData data = dataOrNull(server);
        return data != null && data.isRetrofitAttempted();
    }

    /**
     * 新会话开始：清掉内存状态。必须在读存档之前调用，否则上一个存档的「已有庭院」会带到这一个存档来。
     */
    public static void resetSession() {
        GATE.set(Gate.OPEN);
        RESTORED.set(false);
    }

    /**
     * 把存档里的标记搬回内存。只生效一次；读失败时把位子让出来并报错，宁愿下次再读也不能在这里崩掉世界加载。
     */
    public static void restore(MinecraftServer server) {
        if (!RESTORED.compareAndSet(false, true)) {
            return;
        }
        try {
            StarfallGardenData data = dataOrNull(server);
            GATE.set(data != null && data.isGenerated() ? Gate.CLOSED : Gate.OPEN);
        } catch (RuntimeException e) {
            RESTORED.set(false);
            MaidSpellMod.LOGGER.error("读取星落之庭存档标记失败，本次服务器会话不启用「一存档一座」限制", e);
        }
    }
}
