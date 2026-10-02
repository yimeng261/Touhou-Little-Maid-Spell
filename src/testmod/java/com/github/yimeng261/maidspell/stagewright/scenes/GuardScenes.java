package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.item.bauble.fragrantIngenuity.FragrantIngenuityBauble;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 精灵守卫索敌与同盟、铁魔法雷暴按施法者归属保护友方女仆、随机增益效果黑名单默认含 goety:explosive。
 */
public final class GuardScenes {
    private static final String TEMPLAR = NS + "elf_templar";
    private static final String LIGHTNING = "irons_spellbooks:lightning_strike";
    /** 精灵守卫找目标的目标选择器每 10 tick 左右试一次，给足余量。 */
    private static final int ACQUIRE = 100;

    private GuardScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        for (String hostile : List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:pillager", "minecraft:ravager")) {
            scenes.add(Checks.superflat("elf_templar.targets." + path(hostile), ACQUIRE + 20, ctx -> targets(ctx, hostile, true)));
        }
        for (String neutral : List.of("minecraft:creeper", "minecraft:spider")) {
            scenes.add(Checks.superflat("elf_templar.ignores." + path(neutral), ACQUIRE + 20, ctx -> targets(ctx, neutral, false)));
        }
        scenes.add(Checks.superflat("elf_templar.ignoresPeacefulPlayerAndMaid", ACQUIRE + 20, GuardScenes::ignoresPlayerAndMaid));
        scenes.add(Checks.superflat("elf_templar.companionsRetaliate", ACQUIRE + 20, GuardScenes::companionsRetaliate));
        scenes.add(Checks.superflat("elf_templar.noInfighting", 60, GuardScenes::noInfighting));
        scenes.add(Checks.superflat("thunderstorm.ownerSparesOwnMaid", 20, ctx -> thunderstorm(ctx, true)));
        scenes.add(Checks.superflat("thunderstorm.enemyHitsMaid", 20, ctx -> thunderstorm(ctx, false)));
        scenes.add(Checks.scene("config.explosiveInEffectBlacklists", 5, GuardScenes::explosiveBlacklisted));
        return scenes;
    }

    private static String path(String id) {
        return ResourceLocation.parse(id).getPath();
    }

    private static Mob templar(SceneContext ctx, int dx, int dz) {
        Mob templar = Actors.spawn(ctx, TEMPLAR, dx, 0, dz, false);
        templar.setPersistenceRequired();
        return templar;
    }

    /** 精灵守卫旁边放一只（不动的）生物，看她会不会主动把它当目标。 */
    private static void targets(SceneContext ctx, String type, boolean expected) {
        Mob templar = templar(ctx, 0, 0);
        Mob other = Actors.spawn(ctx, type, 4, 0, 0, true);
        if (expected) {
            ctx.await(() -> templar.getTarget() == other).within(ACQUIRE)
                    .then(() -> ctx.passNote("精灵守卫主动攻击 " + type));
        } else {
            boolean[] targeted = {false};
            Checks.watch(ctx, ACQUIRE, () -> targeted[0] |= templar.getTarget() == other,
                    () -> ctx.check(targeted[0]).as("精灵守卫以 " + type + " 为目标").isFalse());
        }
    }

    private static void ignoresPlayerAndMaid(SceneContext ctx) {
        ServerPlayer player = Owners.visitor(ctx, "TlmsPasserby", 4, 0, 0);
        EntityMaid maid = Owners.maid(ctx, player, -4, 0, 0);
        Mob templar = templar(ctx, 0, 0);
        List<LivingEntity> seen = new ArrayList<>();
        Checks.watch(ctx, ACQUIRE, () -> {
            if (templar.getTarget() != null && !seen.contains(templar.getTarget())) {
                seen.add(templar.getTarget());
            }
        }, () -> {
            ctx.check(seen.contains(player)).as("精灵守卫以没惹她的玩家为目标").isFalse();
            ctx.check(seen.contains(maid)).as("精灵守卫以女仆为目标").isFalse();
        });
    }

    /** 玩家打其中一个，周围的同伴都把这名玩家当目标。 */
    private static void companionsRetaliate(SceneContext ctx) {
        ServerPlayer attacker = Owners.visitor(ctx, "TlmsAttacker", 6, 0, 0);
        Mob hit = templar(ctx, 0, 0);
        List<Mob> companions = List.of(templar(ctx, 0, 3), templar(ctx, 0, -3));
        attacker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        attacker.setHealth(1000);
        ctx.await(() -> hit.tickCount >= 2 && companions.stream().allMatch(c -> c.tickCount >= 2)).within(20).then(() -> {
            ctx.record("hitTick", hit.tickCount);
            ctx.check(hit.hurt(hit.damageSources().playerAttack(attacker), 1)).as("攻击命中精灵守卫").isTrue();
            ctx.await(() -> {
                ctx.record("retaliation", java.util.Map.of("hitTarget", String.valueOf(hit.getTarget()),
                        "companionTargets", companions.stream().map(c -> String.valueOf(c.getTarget())).toList(),
                        "attackerAlive", attacker.isAlive(), "hurtTimestamp", hit.getLastHurtByMobTimestamp()));
                return hit.getTarget() == attacker && companions.stream().allMatch(c -> c.getTarget() == attacker);
            }).within(ACQUIRE).then(() -> ctx.passNote("被打的精灵守卫和两名同伴都追击攻击者"));
        });
    }

    /** 一个精灵守卫的法术误伤另一个：两边都不把对方当目标。 */
    private static void noInfighting(SceneContext ctx) {
        Mob a = templar(ctx, 0, 0);
        Mob b = templar(ctx, 0, 3);
        ctx.check(a.isAlliedTo(b)).as("精灵守卫之间互为同盟").isTrue();
        a.hurt(a.damageSources().indirectMagic(b, b), 1);
        b.hurt(b.damageSources().mobAttack(a), 1);
        Checks.after(ctx, 40, () -> {
            ctx.check(a.getTarget() == b).as("被误伤的精灵守卫以同伴为目标").isFalse();
            ctx.check(b.getTarget() == a).as("被误伤的精灵守卫以同伴为目标").isFalse();
        });
    }

    /**
     * 让施法者身上的雷暴效果结算一次：范围内每个可命中的生物脚下落一道雷。
     * 施法者是女仆主人时不劈女仆，敌人施法时照常劈女仆；旁边的敌对生物作为落雷对照。
     */
    private static void thunderstorm(SceneContext ctx, boolean ownerCasts) {
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("irons_spellbooks:thunderstorm"));
        if (effect.isEmpty()) {
            ctx.fail("irons_spellbooks:thunderstorm 未注册");
            return;
        }
        ServerPlayer owner = Owners.visitor(ctx, "TlmsStormOwner", 0, 0, 0);
        LivingEntity caster = ownerCasts ? owner : Actors.spawn(ctx, "minecraft:zombie", 0, 0, -2, true);
        EntityMaid maid = Owners.maid(ctx, owner, 5, 0, 0);
        LivingEntity pig = Actors.spawn(ctx, "minecraft:husk", -5, 0, 0, true);
        effect.get().value().applyEffectTick(caster, 7);
        List<Entity> strikes = ctx.level().getEntities((Entity) null, new AABB(ctx.rel(0, 0, 0)).inflate(24),
                entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(LIGHTNING));
        strikes.forEach(Entity::discard);
        String who = ownerCasts ? "主人" : "敌人";
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(maid) < 0.5)).as(who + "的雷暴在女仆脚下落雷").isEqualTo(!ownerCasts);
        ctx.check(strikes.stream().anyMatch(strike -> strike.distanceTo(pig) < 0.5)).as(who + "的雷暴在敌对生物脚下落雷").isTrue();
    }

    /** 新生成的配置里，梦云水晶与馥郁巧思的随机增益黑名单都含 goety:explosive，候选效果里也就没有它。 */
    @SuppressWarnings("unchecked")
    private static void explosiveBlacklisted(SceneContext ctx) {
        String explosive = "goety:explosive";
        net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> crystalSpec =
                Reflect.field(null, Config.class, "DREAM_CRYSTAL_EFFECT_BLACKLIST");
        net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> fragrantSpec =
                Reflect.field(null, Config.class, "FRAGRANT_INGENUITY_EFFECT_BLACKLIST");
        var crystalDefaults = (List<String>) crystalSpec.getDefault();
        var fragrantDefaults = (List<String>) fragrantSpec.getDefault();
        ctx.check(crystalDefaults.contains(explosive)).as("梦云水晶默认黑名单含 " + explosive).isTrue();
        ctx.check(fragrantDefaults.contains(explosive)).as("馥郁巧思默认黑名单含 " + explosive).isTrue();
        var oldCrystal = Config.dreamCrystalEffectBlacklist;
        var oldFragrant = Config.fragrantIngenuityEffectBlacklist;
        try {
            Config.dreamCrystalEffectBlacklist = new ArrayList<>(crystalDefaults);
            Config.fragrantIngenuityEffectBlacklist = new ArrayList<>(fragrantDefaults);
            FragrantIngenuityBauble.refreshEffectsList();
            DreamCatCrystalBauble.invalidateBeneficialEffectsCache();
            List<Holder.Reference<MobEffect>> crystal = Reflect.call(null, DreamCatCrystalBauble.class, "getBeneficialEffects", new Class<?>[0]);
            var fragrant = FragrantIngenuityBauble.POSITIVE_EFFECTS;
            ctx.record("candidates", Map.of("dreamCrystal", crystal.size(), "fragrantIngenuity", fragrant.size()));
            ctx.check(crystal.stream().anyMatch(h -> h.is(ResourceLocation.parse(explosive)))).as("梦云水晶候选效果含 " + explosive).isFalse();
            ctx.check(fragrant.stream().anyMatch(h -> h.is(ResourceLocation.parse(explosive)))).as("馥郁巧思候选效果含 " + explosive).isFalse();
        } finally {
            Config.dreamCrystalEffectBlacklist = oldCrystal;
            Config.fragrantIngenuityEffectBlacklist = oldFragrant;
            FragrantIngenuityBauble.refreshEffectsList();
            DreamCatCrystalBauble.invalidateBeneficialEffectsCache();
        }
    }
}
