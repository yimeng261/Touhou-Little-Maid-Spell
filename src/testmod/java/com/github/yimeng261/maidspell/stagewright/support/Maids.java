package com.github.yimeng261.maidspell.stagewright.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.backpack.BaubleContainer;
import com.github.yimeng261.maidspell.item.bauble.silverCercis.SilverCercisBauble;
import com.github.yimeng261.maidspell.item.bauble.soulBook.SoulBookBauble;
import com.github.yimeng261.maidspell.item.bauble.woundRimeBlade.WoundRimeBladeBauble;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

/** 女仆生命周期与饰品运行态的读取工具：致死、存取、按 UUID 找回、各饰品的内部记录。 */
public final class Maids {
    private Maids() {
    }

    /**
     * 先经写血旁路压到 0.5，再施加绕过原版无敌的大伤害；饰品免疫或复活仍会生效。
     */
    public static void kill(EntityMaid maid) {
        Actors.setMaidHealth(maid, 0.5F);
        maid.invulnerableTime = 0;
        maid.hurt(maid.damageSources().genericKill(), 1000);
    }

    /** 清空所有饰品槽（与玩家在饰品栏拿走饰品一样触发卸下）。 */
    public static void takeOffAll(EntityMaid maid) {
        for (int slot = 0; slot < maid.getMaidBauble().getSlots(); slot++) {
            takeOff(maid, slot);
        }
    }

    /** 使用饰品栏槽的实际回调取下物品。 */
    public static ItemStack takeOff(EntityMaid maid, int index) {
        var slot = new BaubleContainer.BaubleSlot(maid, index, 0, 0);
        ItemStack removed = slot.remove(slot.getItem().getCount());
        slot.onShiftTakeoff(null, removed);
        return removed;
    }

    public static ItemStack putOn(EntityMaid maid, int index, ItemStack stack) {
        if (!maid.getMaidBauble().getStackInSlot(index).isEmpty()) takeOff(maid, index);
        new BaubleContainer.BaubleSlot(maid, index, 0, 0).setByPlayer(stack);
        return maid.getMaidBauble().getStackInSlot(index);
    }

    /** 依次放进饰品槽 0、1、2……，返回女仆身上实际的那几份物品。 */
    public static ItemStack[] equip(EntityMaid maid, String... ids) {
        ItemStack[] stacks = new ItemStack[ids.length];
        for (int slot = 0; slot < ids.length; slot++) {
            putOn(maid, slot, Actors.stack(ids[slot]));
            stacks[slot] = maid.getMaidBauble().getStackInSlot(slot);
        }
        return stacks;
    }

    /** 当前关卡里按 UUID 找实体（魂符放出、读档后是新的实体对象）。 */
    public static Entity find(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    /** 经原版 EntityStorage 写入独立的测试区块存档，再按区块卸载移除并重建实体。 */
    public static void reload(SceneContext ctx, Entity entity, int delay, java.util.function.Consumer<Entity> then) {
        ServerLevel level = (ServerLevel) entity.level();
        try {
            var path = java.nio.file.Files.createTempDirectory("tlms-entity-reload-");
            var region = new net.minecraft.world.level.chunk.storage.SimpleRegionStorage(
                    new net.minecraft.world.level.chunk.storage.RegionStorageInfo("stagewright", level.dimension(), "entities"),
                    path, level.getServer().getFixerUpper(), false, net.minecraft.util.datafix.DataFixTypes.ENTITY_CHUNK);
            var storage = new net.minecraft.world.level.chunk.storage.EntityStorage(region, level, Runnable::run);
            ctx.cleanup(() -> {
                try {
                    storage.close();
                    try (var files = java.nio.file.Files.walk(path)) {
                        for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList())
                            java.nio.file.Files.deleteIfExists(file);
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("无法清理实体测试存档", e);
                }
            });
            storage.storeEntities(new net.minecraft.world.level.entity.ChunkEntities<>(entity.chunkPosition(), java.util.List.of(entity)));
            var saved = region.read(entity.chunkPosition());
            entity.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            Checks.after(ctx, delay, () -> ctx.await(saved::isDone).within(40).then(() -> {
                var entries = saved.join().orElseThrow().getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
                if (entries.size() != 1) {
                    ctx.fail("区块存档实体数应为 1，实际为 " + entries.size());
                    return;
                }
                Entity loaded = EntityType.loadEntityRecursive(entries.getCompound(0), level, e -> e);
                if (loaded == null) {
                    ctx.fail("无法按区块存档数据重建实体");
                    return;
                }
                level.addFreshEntity(loaded);
                ctx.cleanup(() -> Actors.cleanupEntity(loaded));
                then.accept(loaded);
            }));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建实体测试存档", e);
        }
    }

    /** 按原版实体转移流程复制到另一维度，保留 UUID、背包和饰品组件。 */
    public static Entity copyToDimension(SceneContext ctx, Entity entity, ServerLevel destination,
                                         net.minecraft.world.phys.Vec3 position) {
        Entity copied = entity.getType().create(destination);
        if (copied == null) throw new IllegalStateException("无法在目标维度创建实体");
        copied.restoreFrom(entity);
        copied.moveTo(position.x, position.y, position.z, entity.getYRot(), entity.getXRot());
        entity.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        destination.addDuringTeleport(copied);
        ctx.cleanup(() -> Actors.cleanupEntity(copied));
        return copied;
    }

    /** 魂之书记录的上次受伤时间是否还在。 */
    public static boolean soulBookRecorded(UUID maid) {
        Map<UUID, Integer> map = Reflect.field(null, SoulBookBauble.class, "lastHurtTimeMap");
        return map.containsKey(maid);
    }

    /** 破愈咒锋是否还有这只女仆打出的禁疗记录。 */
    public static boolean woundRimeRecorded(UUID maid) {
        Map<UUID, ?> map = Reflect.field(null, WoundRimeBladeBauble.class, "maidWoundRimeBladeMap");
        return map.containsKey(maid);
    }

    /** 紫荆银冠的受伤计数，没有记录时为 null。 */
    public static Integer silverCercisCount(UUID maid) {
        Map<UUID, oshi.util.tuples.Pair<Integer, Integer>> map = Reflect.field(null, SilverCercisBauble.class, "maidCercisMap");
        oshi.util.tuples.Pair<Integer, Integer> record = map.get(maid);
        return record == null ? null : record.getA();
    }
}
