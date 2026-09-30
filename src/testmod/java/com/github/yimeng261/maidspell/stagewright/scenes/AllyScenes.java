package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Hits;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 盟友判定与友伤（集成服，宿主玩家为主人 P，模拟玩家 Q 为另一名玩家）：
 * 女仆、宠物、各模组召唤物和法术实体按主人链判定互不伤害、互不索敌；别人的一方照常能打；
 * 记分板队伍与友伤开关；主人的间接伤害不打自己一方，近战交给车万女仆原有规则。
 */
public final class AllyScenes {
    private static final String ISS_SUMMON = "irons_spellbooks:summoned_zombie";
    private static final String GOETY_SERVANT = "goety:zombie_servant";
    private static final String ARS_SUMMON = "ars_nouveau:summon_wolf";
    /** 既不是弹射物也不是召唤物的法术实体（含原版可追溯主人的实体）。 */
    private static final List<String> SPELL_ENTITIES = List.of(
            "irons_spellbooks:sculk_tentacle", "irons_spellbooks:root", "irons_spellbooks:wall_of_fire", "irons_spellbooks:ice_tomb",
            "goety:fang", "goety:corrupted_beam", "goety:lightning_trap", "goety:spike", "goety:brew_effect_cloud", "goety:brew_effect_gas",
            "minecraft:area_effect_cloud", "minecraft:evoker_fangs", "minecraft:tnt");

    private AllyScenes() {
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Players.hostScene("ally.maidSparesOwnerSide", 20, AllyScenes::maidSparesOwnerSide));
        scenes.add(Players.hostScene("ally.maidDoesNotTargetOwnerSide", 20, AllyScenes::maidDoesNotTargetOwnerSide));
        scenes.add(Players.hostScene("ally.summonsSpareOwnerMaids", 20, AllyScenes::summonsSpareOwnerMaids));
        scenes.add(Players.hostScene("ally.otherPlayerSideIsHittable", 20, AllyScenes::otherPlayerSideIsHittable));
        scenes.add(Players.hostScene("ally.sameTeamSpared", 20, AllyScenes::sameTeamSpared));
        scenes.add(Players.hostScene("ally.playersAreNotOwned", 20, AllyScenes::playersAreNotOwned));
        scenes.add(Players.hostScene("ally.spellEntitiesTraceToCaster", 20, AllyScenes::spellEntitiesTraceToCaster));
        scenes.add(Players.hostScene("ally.wildTraceableHitsPets", 20, AllyScenes::wildTraceableHitsPets));
        // 结论交人工判断：UsefulMagic 召唤物是否需要额外适配
        scenes.add(Players.hostScene("knownDefect.usefulMagicSummonsSpared", 20, AllyScenes::usefulMagicSummons).withRequired(false));
        scenes.add(Players.hostScene("friendlyFire.ownerIndirectSparesOwnSide", 20, AllyScenes::ownerIndirectSparesOwnSide));
        scenes.add(Players.hostScene("friendlyFire.ownerMeleeFollowsMaidRule", 20, AllyScenes::ownerMeleeFollowsMaidRule));
        scenes.add(Players.hostScene("friendlyFire.ownerHitsOthers", 20, AllyScenes::ownerHitsOthers));
        scenes.add(Players.hostScene("friendlyFire.teamFriendlyFireOff", 20, AllyScenes::teamFriendlyFireOff));
        scenes.add(Players.hostScene("friendlyFire.playersFollowVanilla", 20, AllyScenes::playersFollowVanilla));
        scenes.add(Players.hostScene("friendlyFire.goetyServantsOfOwnerSide", 20, AllyScenes::goetyServantsOfOwnerSide));
        return scenes;
    }

    /** P 这一方：P、P 的两只女仆、狼、猫、铁魔法召唤物、Goety 仆从、新生魔艺召唤物。 */
    private static Map<String, LivingEntity> ownerSide(SceneContext ctx, ServerPlayer p, EntityMaid caster) {
        Map<String, LivingEntity> side = new LinkedHashMap<>();
        side.put("主人", p);
        side.put("同主人的另一只女仆", Owners.maid(ctx, p, 2, 0, 2));
        side.put("主人的狼", Owners.pet(ctx, "minecraft:wolf", p, -2, 0, 2));
        side.put("主人的猫", Owners.pet(ctx, "minecraft:cat", p, 2, 0, -2));
        side.put("主人的铁魔法召唤物", (LivingEntity) Owners.owned(ctx, ISS_SUMMON, p, -2, 0, -2));
        side.put("主人的 Goety 仆从", (LivingEntity) Owners.owned(ctx, GOETY_SERVANT, p, 4, 0, 0));
        side.put("主人的新生魔艺召唤物", (LivingEntity) Owners.owned(ctx, ARS_SUMMON, p, -4, 0, 0));
        side.values().removeIf(entity -> entity == null || entity == caster);
        return side;
    }

    /** Q 这一方：Q 的女仆、狼、铁魔法召唤物，以及没有主人的野生女仆。 */
    private static Map<String, LivingEntity> otherSide(SceneContext ctx, ServerPlayer q) {
        Map<String, LivingEntity> side = new LinkedHashMap<>();
        side.put("另一名玩家的女仆", Owners.maid(ctx, q, 0, 0, 6));
        side.put("另一名玩家的狼", Owners.pet(ctx, "minecraft:wolf", q, 2, 0, 6));
        side.put("另一名玩家的铁魔法召唤物", (LivingEntity) Owners.owned(ctx, ISS_SUMMON, q, -2, 0, 6));
        side.put("野生女仆", Actors.stillMaid(ctx, 4, 0, 6));
        side.values().removeIf(java.util.Objects::isNull);
        return side;
    }

    /** P 的女仆施法（法术伤害、弹射物）打不到 P 这一方。 */
    private static void maidSparesOwnerSide(SceneContext ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> side = ownerSide(ctx, p, caster);
        Hits.expectAll(ctx, "女仆法术伤害打主人一方", side, Hits.magic(caster, caster), false);
        Arrow arrow = arrow(ctx, caster);
        Hits.expectAll(ctx, "女仆射出的箭打主人一方", side,
                Hits.source(ctx.level(), DamageTypes.ARROW, arrow, caster), false);
        Hits.expectAll(ctx, "女仆的持续法术（无记账者）打主人一方", side, Hits.magic(caster, null), false);
    }

    /** P 的女仆不会以 P 这一方为目标。 */
    private static void maidDoesNotTargetOwnerSide(SceneContext ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, String> targets = new TreeMap<>();
        ownerSide(ctx, p, caster).forEach((name, entity) -> targets.put(name, String.valueOf(Owners.aim(caster, entity))));
        Map<String, String> expected = new TreeMap<>();
        targets.keySet().forEach(name -> expected.put(name, "null"));
        ctx.check(targets).as("女仆以主人一方为目标后的实际目标").isEqualTo(expected);
    }

    /** P 的召唤物、仆从、宠物打不到也不以 P 的女仆为目标。 */
    private static void summonsSpareOwnerMaids(SceneContext ctx, ServerPlayer p) {
        EntityMaid maid = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> side = ownerSide(ctx, p, maid);
        side.remove("主人");
        side.remove("同主人的另一只女仆");
        Map<String, Boolean> lands = new TreeMap<>();
        Map<String, String> aims = new TreeMap<>();
        side.forEach((name, attacker) -> {
            lands.put(name, Hits.lands(maid, maid.damageSources().mobAttack(attacker)));
            if (attacker instanceof Mob mob) {
                aims.put(name, String.valueOf(Owners.aim(mob, maid)));
            }
        });
        ctx.check(lands.containsValue(true)).as("主人一方的召唤物打到主人女仆 " + lands).isFalse();
        ctx.check(aims.values().stream().allMatch("null"::equals)).as("主人一方的召唤物以主人女仆为目标 " + aims).isTrue();
    }

    /** 另一名玩家的女仆、宠物、召唤物和野生女仆照常能被 P 的女仆打到。 */
    private static void otherPlayerSideIsHittable(SceneContext ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Hits.expectAll(ctx, "女仆法术伤害打另一名玩家一方", otherSide(ctx, q), Hits.magic(caster, caster), true);
    }

    /** P、Q 同队：双方的女仆和召唤物互不伤害；Q 换到另一支队伍后按主人判定，又能打到。 */
    private static void sameTeamSpared(SceneContext ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        Owners.team(ctx, "tlms_ally_a", true, p, q);
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> others = otherSide(ctx, q);
        others.remove("野生女仆");
        Hits.expectAll(ctx, "同队时女仆打另一名玩家一方", others, Hits.magic(caster, caster), false);
        Owners.team(ctx, "tlms_ally_b", true, q);
        Hits.expectAll(ctx, "不同队时女仆打另一名玩家一方", others, Hits.magic(caster, caster), true);
        ctx.check(MaidSpellAllyResolver.areFriendly(caster, Owners.maid(ctx, p, 2, 0, 3)))
                .as("不同队时同主人的两只女仆仍是友方").isTrue();
    }

    /** 玩家本身不再被视为有主人：两名不同队的玩家不是友方，P 的女仆能打到 Q。 */
    private static void playersAreNotOwned(SceneContext ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        ctx.check(MaidSpellAllyResolver.areFriendly(p, q)).as("两名玩家是友方").isFalse();
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        ctx.check(MaidSpellAllyResolver.areFriendly(caster, q)).as("女仆与另一名玩家是友方").isFalse();
    }

    /**
     * 女仆放出的法术实体（铁魔法触手/根须/火墙/冰墓，Goety 尖牙/光束/陷阱/地刺/药雾，原版药水云/唤魔者尖牙/TNT）
     * 以实体本身为直接来源、没有记账者时，仍追溯到女仆：打不到主人一方，能打到野生僵尸；女仆不以它们为目标。
     */
    private static void spellEntitiesTraceToCaster(SceneContext ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, LivingEntity> side = ownerSide(ctx, p, caster);
        side.put("施法女仆", caster);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -6, true);
        Map<String, Map<String, Boolean>> spared = new TreeMap<>();
        Map<String, Boolean> hitsEnemy = new TreeMap<>();
        Map<String, String> targeted = new TreeMap<>();
        int x = -6;
        for (String type : SPELL_ENTITIES) {
            Entity spell = Owners.owned(ctx, type, caster, x++, 0, -3);
            if (spell == null) {
                continue;
            }
            DamageSource source = Hits.magic(spell, null);
            Map<String, Boolean> matrix = Hits.matrix(side, source);
            matrix.values().removeIf(landed -> !landed);
            if (!matrix.isEmpty()) {
                spared.put(type, matrix);
            }
            hitsEnemy.put(type, Hits.lands(zombie, source));
            if (spell instanceof LivingEntity living) {
                targeted.put(type, String.valueOf(Owners.aim(caster, living)));
            }
        }
        ctx.record("hitsEnemy", hitsEnemy);
        ctx.check(spared).as("法术实体打到主人一方（类型 → 被打到的目标）").isEmpty();
        ctx.check(hitsEnemy.containsValue(false)).as("有法术实体打不到野生僵尸 " + hitsEnemy).isFalse();
        ctx.check(targeted.values().stream().allMatch("null"::equals)).as("女仆以自己的法术实体为目标 " + targeted).isTrue();
    }

    /** 原版 TraceableEntity 按其主人判定：野生唤魔者的尖牙照常伤害玩家的狼。 */
    private static void wildTraceableHitsPets(SceneContext ctx, ServerPlayer p) {
        LivingEntity wolf = Owners.pet(ctx, "minecraft:wolf", p, 2, 0, 2);
        Mob evoker = Actors.spawn(ctx, "minecraft:evoker", -3, 0, 0, true);
        Entity fangs = Owners.owned(ctx, "minecraft:evoker_fangs", evoker, 0, 0, 2);
        ctx.check(Hits.lands(wolf, Hits.magic(fangs, evoker))).as("野生唤魔者的尖牙打主人的狼").isTrue();
        ctx.check(Hits.lands(wolf, Hits.magic(fangs, null))).as("野生唤魔者的尖牙（无记账者）打主人的狼").isTrue();
    }

    /** UsefulMagic 的召唤物：主人设为 P 后，P 的女仆是否打不到它（结论交人工判断是否需要适配）。 */
    private static void usefulMagicSummons(SceneContext ctx, ServerPlayer p) {
        EntityMaid caster = Owners.maid(ctx, p, 0, 0, 3);
        Map<String, Boolean> lands = new TreeMap<>();
        Map<String, String> unsupported = new TreeMap<>();
        int x = -6;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals("usefulmagic")) {
                continue;
            }
            Entity probe = type.create(ctx.level());
            boolean living = probe instanceof LivingEntity;
            if (probe != null) {
                probe.discard();
            }
            if (!living) {
                continue;
            }
            try {
                Entity summon = Owners.owned(ctx, id.toString(), p, x++, 0, -4);
                lands.put(id.toString(), Hits.lands((LivingEntity) summon, Hits.magic(caster, caster)));
            } catch (IllegalStateException e) {
                unsupported.put(id.toString(), e.getMessage());
            }
        }
        ctx.record("noOwnerSetter", unsupported);
        ctx.record("maidHitsSummon", lands);
        ctx.check(lands.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList())
                .as("被主人自己的女仆打到的 UsefulMagic 召唤物").isEmpty();
    }

    /** 主人的弓箭、三叉戟、法术、TNT（间接伤害）打不到自己的女仆、狼、召唤物，也不会引起反击。 */
    private static void ownerIndirectSparesOwnSide(SceneContext ctx, ServerPlayer p) {
        Map<String, LivingEntity> side = ownerSide(ctx, p, null);
        side.remove("主人");
        Map<String, DamageSource> sources = new LinkedHashMap<>();
        sources.put("箭", Hits.source(ctx.level(), DamageTypes.ARROW, arrow(ctx, p), p));
        ThrownTrident trident = new ThrownTrident(ctx.level(), p, new ItemStack(Items.TRIDENT));
        ctx.cleanup(trident::discard);
        sources.put("三叉戟", Hits.source(ctx.level(), DamageTypes.TRIDENT, trident, p));
        sources.put("法术", Hits.source(ctx.level(), DamageTypes.INDIRECT_MAGIC, Owners.owned(ctx, "minecraft:area_effect_cloud", p, 0, 0, -3), p));
        Entity tnt = Owners.owned(ctx, "minecraft:tnt", p, 0, 0, -5);
        sources.put("TNT", ctx.level().damageSources().explosion(tnt, p));
        sources.forEach((name, source) -> Hits.expectAll(ctx, "主人的" + name + "打自己一方", side, source, false));
        side.forEach((name, entity) -> {
            if (entity instanceof Mob mob) {
                ctx.check(mob.getTarget()).as(name + " 被误伤后的目标").isNull();
            }
        });
    }

    /** 主人近战打自己的女仆：本模组不按友伤取消，交给车万女仆原有规则处理。 */
    private static void ownerMeleeFollowsMaidRule(SceneContext ctx, ServerPlayer p) {
        EntityMaid maid = Owners.maid(ctx, p, 0, 0, 2);
        ctx.check(MaidSpellAllyResolver.isFriendlyDamage(maid, p, p)).as("主人近战打自己女仆算友伤").isFalse();
        ctx.record("maidDamageFromOwnerMelee", Hits.dealt(maid, p.damageSources().playerAttack(p), Hits.PROBE));
    }

    /** 主人对野生女仆、另一名玩家的女仆和宠物照常造成伤害（近战和远程）。 */
    private static void ownerHitsOthers(SceneContext ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        Map<String, LivingEntity> others = otherSide(ctx, q);
        Hits.expectAll(ctx, "主人近战打别人一方", others, p.damageSources().playerAttack(p), true);
        Hits.expectAll(ctx, "主人射箭打别人一方", others, Hits.source(ctx.level(), DamageTypes.ARROW, arrow(ctx, p), p), true);
    }

    /** P、Q 同队且关闭友伤：P 近战和远程都打不到 Q 的女仆、狼、召唤物；打开友伤后照常能打。 */
    private static void teamFriendlyFireOff(SceneContext ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        var team = Owners.team(ctx, "tlms_ally_ff", false, p, q);
        Map<String, LivingEntity> others = otherSide(ctx, q);
        others.remove("野生女仆");
        DamageSource arrow = Hits.source(ctx.level(), DamageTypes.ARROW, arrow(ctx, p), p);
        Hits.expectAll(ctx, "关闭友伤时主人近战打队友一方", others, p.damageSources().playerAttack(p), false);
        Hits.expectAll(ctx, "关闭友伤时主人射箭打队友一方", others, arrow, false);
        team.setAllowFriendlyFire(true);
        Hits.expectAll(ctx, "打开友伤后主人近战打队友一方", others, p.damageSources().playerAttack(p), true);
        Hits.expectAll(ctx, "打开友伤后主人射箭打队友一方", others, arrow, true);
    }

    /** 玩家之间的伤害不经本模组判定，仍按原版队伍规则。 */
    private static void playersFollowVanilla(SceneContext ctx, ServerPlayer p) {
        ServerPlayer q = Owners.visitor(ctx, "TlmsAllyQ", 0, 0, 8);
        ctx.check(MaidSpellAllyResolver.isFriendlyDamage(q, p, p)).as("无队伍时玩家打玩家算友伤").isFalse();
        Owners.team(ctx, "tlms_ally_pvp", false, p, q);
        ctx.check(MaidSpellAllyResolver.isFriendlyDamage(q, p, p)).as("同队关闭友伤时玩家打玩家算本模组友伤").isFalse();
    }

    /** 装 Goety 时：玩家的间接法术伤害不打自己的仆从、也不打属于自己女仆的仆从；女仆把这些仆从当友方。 */
    private static void goetyServantsOfOwnerSide(SceneContext ctx, ServerPlayer p) {
        EntityMaid maid = Owners.maid(ctx, p, 0, 0, 3);
        LivingEntity own = (LivingEntity) Owners.owned(ctx, GOETY_SERVANT, p, 2, 0, 0);
        LivingEntity maids = (LivingEntity) Owners.owned(ctx, GOETY_SERVANT, maid, -2, 0, 0);
        if (own == null || maids == null) {
            ctx.skip("Goety 未加载");
            return;
        }
        Entity cloud = Owners.owned(ctx, "minecraft:area_effect_cloud", p, 0, 0, -3);
        DamageSource indirect = Hits.source(ctx.level(), DamageTypes.INDIRECT_MAGIC, cloud, p);
        ctx.check(Hits.lands(own, indirect)).as("玩家间接法术打自己的仆从").isFalse();
        ctx.check(Hits.lands(maids, indirect)).as("玩家间接法术打自己女仆的仆从").isFalse();
        ctx.check(MaidSpellAllyResolver.areFriendly(maid, own)).as("女仆与主人的仆从是友方").isTrue();
        ctx.check(MaidSpellAllyResolver.areFriendly(maid, maids)).as("女仆与自己的仆从是友方").isTrue();
    }

    private static Arrow arrow(SceneContext ctx, LivingEntity shooter) {
        Arrow arrow = new Arrow(ctx.level(), shooter, new ItemStack(Items.ARROW), null);
        ctx.cleanup(arrow::discard);
        return arrow;
    }
}
