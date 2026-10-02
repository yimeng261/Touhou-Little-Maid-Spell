package com.github.yimeng261.maidspell.winefox;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 星之魔女挑战的玩家进度：已击败、挑战进行中。
 * 按 UUID 记在主世界存档里，挑战者离线或在别的维度时 Boss 也能记账和收尾。
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class WinefoxChallengeProgress extends SavedData {
    private static final String DATA_NAME = MaidSpellMod.MOD_ID + "_winefox_challenge";

    /** 旧版本写在玩家数据里的标记，读到时搬进存档。 */
    private static final String LEGACY_DEFEATED_TAG = "MaidSpellWinefoxDefeated";
    private static final String LEGACY_ACTIVE_TAG = "MaidSpellWinefoxChallengeActive";

    private final Set<UUID> defeated = new HashSet<>();
    private final Set<UUID> active = new HashSet<>();

    private WinefoxChallengeProgress() {
    }

    private static WinefoxChallengeProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        WinefoxChallengeProgress data = new WinefoxChallengeProgress();
        readUuids(tag.getList("Defeated", Tag.TAG_INT_ARRAY), data.defeated);
        readUuids(tag.getList("Active", Tag.TAG_INT_ARRAY), data.active);
        return data;
    }

    private static WinefoxChallengeProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WinefoxChallengeProgress::new, WinefoxChallengeProgress::load),
                DATA_NAME);
    }

    public static boolean hasDefeated(ServerPlayer player) {
        WinefoxChallengeProgress data = get(player.server);
        CompoundTag playerData = player.getPersistentData();
        CompoundTag persisted = playerData.getCompound(Player.PERSISTED_NBT_TAG);
        if (playerData.getBoolean(LEGACY_DEFEATED_TAG) || persisted.getBoolean(LEGACY_DEFEATED_TAG)) {
            playerData.remove(LEGACY_DEFEATED_TAG);
            persisted.remove(LEGACY_DEFEATED_TAG);
            data.update(data.defeated, player.getUUID(), true);
        }
        return data.defeated.contains(player.getUUID());
    }

    public static void markDefeated(MinecraftServer server, UUID playerId) {
        WinefoxChallengeProgress data = get(server);
        data.update(data.defeated, playerId, true);
    }

    public static boolean hasActiveChallenge(ServerPlayer player) {
        // 旧版本的挑战中标记可能因为挑战者离线或换维度而残留，直接丢弃
        player.getPersistentData().remove(LEGACY_ACTIVE_TAG);
        return get(player.server).active.contains(player.getUUID());
    }

    public static void markChallengeActive(MinecraftServer server, UUID playerId) {
        WinefoxChallengeProgress data = get(server);
        data.update(data.active, playerId, true);
    }

    public static void clearChallengeActive(MinecraftServer server, @Nullable UUID playerId) {
        if (playerId != null) {
            WinefoxChallengeProgress data = get(server);
            data.update(data.active, playerId, false);
        }
    }

    /**
     * 挑战者死亡时清掉自己的挑战中标记，Boss 实体丢失时标记也不会一直卡住短剑配置。
     * 这一场本身仍由 Boss 收场：没有可打的目标 30 秒后判玩家落败，女仆还在打时继续到分出胜负，
     * 收场时再清一次是空操作；这一场的规则开战时已存进 Boss，中途改短剑配置不影响战局。
     * 最低优先级且不接收已取消的事件，被其他模组复活拦下的死亡不算。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearChallengeActive(player.server, player.getUUID());
        }
    }

    private void update(Set<UUID> set, UUID playerId, boolean present) {
        if (present ? set.add(playerId) : set.remove(playerId)) {
            this.setDirty();
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        tag.put("Defeated", writeUuids(this.defeated));
        tag.put("Active", writeUuids(this.active));
        return tag;
    }

    private static void readUuids(ListTag list, Set<UUID> out) {
        UUIDUtil.CODEC_SET.parse(NbtOps.INSTANCE, list).resultOrPartial(MaidSpellMod.LOGGER::error).ifPresent(out::addAll);
    }

    private static Tag writeUuids(Set<UUID> uuids) {
        return UUIDUtil.CODEC_SET.encodeStart(NbtOps.INSTANCE, uuids).getOrThrow();
    }
}
