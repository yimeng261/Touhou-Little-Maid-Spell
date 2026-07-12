package com.github.yimeng261.maidspell.item.bauble.woundRimeBlade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;

import com.github.yimeng261.maidspell.utils.TrueDamageUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import oshi.util.tuples.Pair;

/**
 * 破愈咒锋饰品实现
 * 监听周围敌对实体的治疗并阻止
 */
public class WoundRimeBladeBauble implements IMaidBauble {

    private static final Map<UUID, ConcurrentHashMap<TargetKey, Pair<Float, Integer>>> maidWoundRimeBladeMap = new ConcurrentHashMap<>();

    static {
        Global.registerBaubleHurtHeadHandler(MaidSpellItems.WOUND_RIME_BLADE, context -> {
            EntityMaid maid = context.getSourceMaid();
            if (maid != null) {
                updateWoundRimeMap(maid, context.getTarget(), context.getAmount());
            }
        });
    }

    public WoundRimeBladeBauble() {
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        if (!maid.level().isClientSide()) {
            cleanupMaid(maid.getUUID());
        }
    }

    public static void updateWoundRimeMap(EntityMaid maid, LivingEntity entity, float damage) {
        if (!(maid.level() instanceof ServerLevel)
                || !(entity.level() instanceof ServerLevel)
                || !BaubleStateManager.hasBauble(maid, MaidSpellItems.WOUND_RIME_BLADE)) {
            return;
        }
        if (entity instanceof Player || entity instanceof EntityMaid) {
            return;
        }

        ConcurrentHashMap<TargetKey, Pair<Float, Integer>> targets = maidWoundRimeBladeMap.computeIfAbsent(
                maid.getUUID(), ignored -> new ConcurrentHashMap<>());
        TargetKey targetKey = TargetKey.of(entity);
        float nowHealth = entity.getHealth();
        Pair<Float, Integer> record = targets.getOrDefault(
                targetKey, new Pair<>(nowHealth, Config.woundRimeBladeRecordTimes));
        float recordHealth = record.getA();
        if (nowHealth > recordHealth) {
            TrueDamageUtil.setNewHealth(entity, recordHealth, maid);
        }
        float newHealth = Math.min(recordHealth, nowHealth) - damage;
        targets.put(targetKey, new Pair<>(newHealth, record.getB() + Config.woundRimeBladeRecordTimes));
    }

    /**
     * 处理破愈咒锋饰品
     * @param entity 实体
     * @param health 健康值
     * @return 是否取消治疗
     */
    public static boolean handleWoundRimeMap(LivingEntity entity, float health) {
        if (!(entity.level() instanceof ServerLevel)) {
            return false;
        }

        AtomicBoolean shouldCancel = new AtomicBoolean(false);
        TargetKey targetKey = TargetKey.of(entity);
        maidWoundRimeBladeMap.forEach((maidId, targets) -> {
            targets.computeIfPresent(targetKey, (ignored, record) -> {
                shouldCancel.set(true);
                int remaining = record.getB() - 1;
                return remaining <= 0 ? null : new Pair<>(health, remaining);
            });
            if (targets.isEmpty()) {
                maidWoundRimeBladeMap.remove(maidId, targets);
            }
        });
        return shouldCancel.get();
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID maidId = maid.getUUID();
        ConcurrentHashMap<TargetKey, Pair<Float, Integer>> targets = maidWoundRimeBladeMap.get(maidId);
        if (targets == null) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        targets.forEach((targetKey, record) -> {
            if (record.getB() <= 0 || !isLoadedAndAlive(server, targetKey)) {
                targets.remove(targetKey, record);
            }
        });
        if (targets.isEmpty()) {
            maidWoundRimeBladeMap.remove(maidId, targets);
        }
    }

    public static void cleanupMaid(UUID maidId) {
        maidWoundRimeBladeMap.remove(maidId);
    }

    public static void clearSession() {
        maidWoundRimeBladeMap.clear();
    }

    private static boolean isLoadedAndAlive(MinecraftServer server, TargetKey targetKey) {
        ServerLevel level = server.getLevel(targetKey.dimension());
        if (level == null) {
            return false;
        }
        Entity entity = level.getEntity(targetKey.entityId());
        return entity instanceof LivingEntity livingEntity && livingEntity.isAlive();
    }

    private record TargetKey(ResourceKey<Level> dimension, UUID entityId) {
        private static TargetKey of(LivingEntity entity) {
            return new TargetKey(entity.level().dimension(), entity.getUUID());
        }
    }
}
