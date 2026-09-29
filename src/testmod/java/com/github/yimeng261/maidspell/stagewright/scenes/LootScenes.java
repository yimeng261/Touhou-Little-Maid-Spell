package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.data.LootSnapshots;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.LootReach;
import net.magicterra.stagewright.contract.Terrain;
import net.magicterra.stagewright.scene.Scene;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 战利品表场景：每个结构一组表的可达集合快照、实体类型绑定的掉落表、真实击杀掉落。
 */
public final class LootScenes {
    /** 实体 → 默认掉落表。 */
    private static final Map<String, String> ENTITY_TABLES = Map.of(
            "touhou_little_maid_spell:shadow_assassin", "touhou_little_maid_spell:entities/shadow_assassin",
            "touhou_little_maid_spell:holy_construct", "touhou_little_maid_spell:entities/holy_construct",
            "touhou_little_maid_spell:corrupted_knight", "touhou_little_maid_spell:entities/corrupted_knight",
            "touhou_little_maid_spell:elf_templar", "touhou_little_maid_spell:entities/elf_templar");

    /** 每种实体击杀的次数：单次掉落可能为空，多次取并集。 */
    private static final int KILLS = 8;

    private LootScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        LootSnapshots.GROUPS.forEach((group, tables) ->
                scenes.add(Checks.scene("loot." + group, 20, ctx -> checkTables(ctx, tables))));
        scenes.add(Checks.scene("loot.entityTableBinding", 20, LootScenes::checkEntityBinding));
        ENTITY_TABLES.forEach((entity, table) -> scenes.add(
                Checks.scene("loot.killDrops." + ResourceLocation.parse(entity).getPath(), 40,
                        ctx -> checkKillDrops(ctx, entity, table)).withTerrain(Terrain.SUPERFLAT)));
        return scenes;
    }

    private static void checkTables(SceneContext ctx, List<String> tables) {
        MinecraftServer server = ctx.server();
        for (String table : tables) {
            boolean exists = ctx.loot().exists(table);
            ctx.check(exists).as("战利品表 " + table + " 已加载").isTrue();
            if (exists) {
                Checks.sameSet(ctx, table + " 可达物品", LootSnapshots.REACHABLE.get(table), LootReach.reachable(server, table));
            }
        }
    }

    private static void checkEntityBinding(SceneContext ctx) {
        ENTITY_TABLES.forEach((entity, table) -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entity));
            ctx.check(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()).as(entity + " 已注册").isEqualTo(entity);
            ctx.check(type.getDefaultLootTable().location().toString()).as(entity + " 的默认掉落表").isEqualTo(table);
        });
    }

    /** 用假玩家击杀若干次，掉落物都应来自该实体的掉落表，且并集非空。 */
    private static void checkKillDrops(SceneContext ctx, String entityId, String table) {
        ServerLevel level = ctx.level();
        MinecraftServer server = ctx.server();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityId));
        FakePlayer killer = FakePlayerFactory.getMinecraft(level);
        BlockPos spawn = ctx.rel(0, 1, 0);
        Set<String> dropped = new TreeSet<>();
        Set<String> equipment = new TreeSet<>();
        int stillAlive = 0;
        for (int i = 0; i < KILLS; i++) {
            Entity entity = type.create(level);
            if (!(entity instanceof LivingEntity living)) {
                ctx.fail(entityId + " 不是可击杀的生物");
                return;
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
                dropped.add(LootReach.keyOf(item.getItem(), server));
                item.discard();
            }
        }
        ctx.record("drops", dropped);
        ctx.record("equipment", equipment);
        ctx.record("survivedLethalHit", stillAlive);
        Set<String> allowed = new TreeSet<>(LootReach.reachable(server, table));
        allowed.addAll(equipment);
        Set<String> foreign = new TreeSet<>(dropped);
        foreign.removeAll(allowed);
        ctx.check(dropped).as(entityId + " 击杀 " + KILLS + " 次的掉落").isNotEmpty();
        ctx.check(foreign).as(entityId + " 掉落中不属于 " + table + " 的物品").isEmpty();
    }
}
