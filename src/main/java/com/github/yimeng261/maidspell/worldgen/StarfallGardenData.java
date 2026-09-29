package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 记录星落之庭是否已生成，保证一份存档只有一座。
 * 世界生成线程只读取内存闸门；SavedData 的读写回到服务器主线程。
 * 主世界加载时恢复闸门，早于出生点区块生成。
 */
public class StarfallGardenData extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_starfall_garden";

    /** 内存闸门：唯一的名额是否已被占用。世界生成和补生成都靠它抢名额。 */
    private static final AtomicBoolean PLACED = new AtomicBoolean(false);

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

    public static StarfallGardenData load(CompoundTag tag, HolderLookup.Provider registries) {
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
                new SavedData.Factory<>(StarfallGardenData::new, StarfallGardenData::load),
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
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        tag.putBoolean("Generated", this.generated);
        if (this.position != null) {
            tag.putLong("Position", this.position.asLong());
        }
        tag.putBoolean("RetrofitAttempted", this.retrofitAttempted);
        return tag;
    }

    // ========== 运行期闸门 ==========

    /** 名额是否已被占用。 */
    public static boolean isPlaced() {
        return PLACED.get();
    }

    /**
     * 抢占唯一名额并落盘，见 {@code StarfallGardenStructure#generate}。
     * 并发的几个候选只有一个能抢到，其余返回 false，调用方必须放弃这一座。
     */
    public static boolean tryMarkPlaced(BlockPos pos) {
        if (!tryReserve()) {
            return false;
        }
        persistPlaced(pos);
        return true;
    }

    /**
     * 补生成分帧放置专用：只在内存里抢占名额、不落盘，放置期间挡住世界生成。
     * 方块全部写完后再调 {@link #markPlaced} 落盘；中途失败或停服则本次会话保持关闸，下次开服重试。
     */
    public static boolean tryReserve() {
        return PLACED.compareAndSet(false, true);
    }

    /** 认领存档里已有的庭院或补生成放置完成：无条件关闸并落盘。 */
    public static void markPlaced(BlockPos pos) {
        PLACED.set(true);
        persistPlaced(pos);
    }

    /** 落盘必须在主线程，这里只排一个任务。 */
    private static void persistPlaced(BlockPos pos) {
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
        PLACED.set(false);
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
            PLACED.set(data != null && data.isGenerated());
        } catch (RuntimeException e) {
            RESTORED.set(false);
            MaidSpellMod.LOGGER.error("读取星落之庭存档标记失败，本次服务器会话不启用「一存档一座」限制", e);
        }
    }
}
