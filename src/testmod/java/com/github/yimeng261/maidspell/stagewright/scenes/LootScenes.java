package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.data.LootSnapshots;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Kills;
import com.github.yimeng261.maidspell.stagewright.support.LootReach;
import net.magicterra.stagewright.contract.Terrain;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

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
    static void checkKillDrops(SceneContext ctx, String entityId, String table) {
        MinecraftServer server = ctx.server();
        Kills.Result result = Kills.drops(ctx, entityId, KILLS);
        if (result == null) {
            return;
        }
        Set<String> dropped = result.dropKeys(server);
        ctx.record("drops", dropped);
        ctx.record("equipment", result.equipment());
        ctx.record("survivedLethalHit", result.stillAlive());
        Set<String> allowed = new TreeSet<>(LootReach.reachable(server, table));
        allowed.addAll(result.equipment());
        Set<String> foreign = new TreeSet<>(dropped);
        foreign.removeAll(allowed);
        ctx.check(dropped).as(entityId + " 击杀 " + KILLS + " 次的掉落").isNotEmpty();
        ctx.check(foreign).as(entityId + " 掉落中不属于 " + table + " 的物品").isEmpty();
    }
}
