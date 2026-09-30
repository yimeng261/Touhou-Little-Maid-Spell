package com.github.yimeng261.maidspell.stagewright.support;

import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 用假玩家在场景原点上方真实击杀生物并收集掉落物。
 */
public final class Kills {
    private Kills() {
    }

    /**
     * @param drops       所有掉落物
     * @param equipment   生成时身上装备的物品键（装备也可能掉落，不算掉落表之外）
     * @param stillAlive  挨了致命一击仍活着、改用直接死亡处理的次数
     */
    public record Result(List<ItemStack> drops, Set<String> equipment, int stillAlive) {
        public Set<String> dropKeys(MinecraftServer server) {
            Set<String> keys = new TreeSet<>();
            drops.forEach(stack -> keys.add(LootReach.keyOf(stack, server)));
            return keys;
        }
    }

    /** 实体类型未注册或不是生物时让场景失败并返回 null。 */
    public static Result drops(SceneContext ctx, String entityId, int kills) {
        ServerLevel level = ctx.level();
        MinecraftServer server = ctx.server();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(entityId)).orElse(null);
        if (type == null) {
            ctx.fail(entityId + " 未注册");
            return null;
        }
        FakePlayer killer = FakePlayerFactory.getMinecraft(level);
        BlockPos spawn = ctx.rel(0, 1, 0);
        List<ItemStack> dropped = new ArrayList<>();
        Set<String> equipment = new TreeSet<>();
        int stillAlive = 0;
        for (int i = 0; i < kills; i++) {
            Entity entity = type.create(level);
            if (!(entity instanceof LivingEntity living)) {
                ctx.fail(entityId + " 不是可击杀的生物");
                return null;
            }
            living.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            level.addFreshEntity(living);
            living.getAllSlots().forEach(stack -> {
                if (!stack.isEmpty()) {
                    equipment.add(LootReach.keyOf(stack, server));
                }
            });
            DamageSource source = level.damageSources().playerAttack(killer);
            living.setLastHurtByPlayer(killer);
            living.hurt(source, Float.MAX_VALUE);
            if (living.isAlive()) {
                stillAlive++;
                living.setHealth(0);
                living.die(source);
            }
            living.discard();
            AABB box = new AABB(spawn).inflate(4);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
                dropped.add(item.getItem().copy());
                item.discard();
            }
        }
        return new Result(dropped, equipment, stillAlive);
    }
}
