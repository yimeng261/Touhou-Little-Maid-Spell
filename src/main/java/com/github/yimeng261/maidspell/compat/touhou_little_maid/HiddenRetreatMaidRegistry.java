package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.dimension.HiddenRetreatMaidData;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 隐世女仆的导入对接口与线程安全的模板女仆守卫，不依赖任何可选模组。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class HiddenRetreatMaidRegistry {
    private static final Map<MinecraftServer, HiddenRetreatMaidData> LOADED = new ConcurrentHashMap<>();

    private HiddenRetreatMaidRegistry() {
    }

    private static HiddenRetreatMaidData load(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HiddenRetreatMaidData::new, HiddenRetreatMaidData::load),
                HiddenRetreatMaidData.DATA_NAME);
    }

    /** 只在服务端主线程调用；工作线程只能使用已经加载好的实例。 */
    public static HiddenRetreatMaidData get(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Retreat maid data must be loaded on the server thread");
        }
        return LOADED.computeIfAbsent(server, ignored -> load(server.overworld()));
    }

    /**
     * 导入成功后调用，传入解析出的目标主人，以及女仆的完整实体 NBT、MaidInfo 或持久数据，
     * 以物品形式保存的导入也适用。返回是否新记录了一条认领。必须在服务端主线程调用。
     */
    public static boolean registerImportedMaid(MinecraftServer server, UUID destinationOwner, CompoundTag maidData) {
        return MaidOriginData.isHiddenRetreatMaid(maidData) && get(server).claim(destinationOwner);
    }

    public static void recognizeMaid(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !MaidOriginData.isHiddenRetreatMaid(maid)) {
            return;
        }
        UUID owner = maid.getOwnerUUID();
        if (owner != null) {
            MinecraftServer server = level.getServer();
            server.execute(() -> get(server).claim(owner));
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) {
            // 直接用事件里的维度：启动阶段 server.overworld() 可能还没赋值。
            LOADED.put(level.getServer(), load(level));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMaidJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            recognizeMaid(maid);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED.remove(event.getServer());
    }

    /** 在结构里任何区块进入装饰阶段之前，先把搜索者记为这座结构的归属。 */
    public static void bindStructure(ServerLevel level, BlockPos locatePos, UUID player) {
        HiddenRetreatMaidData data = get(level.getServer());
        if (!TheRetreatDimension.isSharedRetreatDimension(level.dimension().location())) {
            return;
        }
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "hidden_retreat"));
        ChunkPos pos = new ChunkPos(locatePos);
        var chunk = level.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, false);
        if (structure == null || chunk == null) {
            return;
        }
        var start = level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
        if (start != null && start.isValid()) {
            var box = start.getBoundingBox();
            data.bindStructure(new HiddenRetreatMaidData.StructureOwner(level.dimension().location(),
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), player));
        }
    }

    @Nullable
    static UUID privateOwner(ResourceLocation dimension) {
        if (!TheRetreatDimension.isPrivateRetreatDimension(dimension)) {
            return null;
        }
        return UUID.fromString(dimension.getPath().substring("the_retreat_".length()).replace('_', '-'));
    }

    /** 只对新生成、无主、带明确标记的模板女仆调用，导入和重新加载不走这里。 */
    public static Optional<Entity> createTemplateMaid(ServerLevelAccessor accessor, CompoundTag tag, BlockPos pos) {
        MinecraftServer server = accessor.getLevel().getServer();
        HiddenRetreatMaidData data = LOADED.get(server);
        ResourceLocation dimension = accessor.getLevel().dimension().location();
        UUID owner = privateOwner(dimension);
        if (owner == null && data != null) {
            owner = data.ownerAt(dimension, pos);
        }
        if (owner == null || data == null) {
            // 还没归属的共享结构不能消耗别的玩家的名额。
            return EntityType.create(tag, accessor.getLevel());
        }
        if (!data.tryBeginSpawn(owner)) {
            return Optional.empty();
        }
        try {
            Optional<Entity> result = EntityType.create(tag, accessor.getLevel());
            return data.hasClaim(owner) ? Optional.empty() : result;
        } finally {
            // 构造失败既不算遇到过，也不算认领，始终允许重试。
            data.finishSpawn(owner);
        }
    }
}
