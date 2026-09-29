package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellDataComponents;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 功能行为场景（专用服务器，无玩家）：三种花与盆栽的光环（效果、等级、范围边界、目标筛选）、
 * 镇石对生成检查事件的拦截、喋血之心/熔岩狐叶/春花-返/馥郁巧思的效果。
 * <p>主人相关的部分（喋血之心治疗主人、熔岩狐叶共享保护、馥郁巧思喂主人）需要玩家，放在 integratedServer。
 */
public final class FunctionScenes {
    /** 花的光环半径：AABB(花).inflate(3)，距花 3 格的生物在内、4 格的在外。 */
    private static final int INSIDE = 3;
    private static final int OUTSIDE = 4;
    /** 朱华/铃兰每 40 tick、幽兰每 20 tick 触发一次，按位置错开相位。 */
    private static final int SLOW_PULSE = 40;
    private static final int FAST_PULSE = 20;

    /** 镇石不拦截的生成方式（与实现一致的快照）。 */
    private static final Set<MobSpawnType> STONE_EXEMPT = EnumSet.of(MobSpawnType.BREEDING, MobSpawnType.MOB_SUMMONED,
            MobSpawnType.CONVERSION, MobSpawnType.BUCKET, MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND,
            MobSpawnType.DISPENSER, MobSpawnType.SPAWNER);
    /** 春花-返的"重击"：8 点，明显高于触发阈值（4 点或 10% 最大生命）。 */
    private static final float HEAVY = 8;
    /** 镇石压制范围：以镇石所在区块为中心 7×7 区块。 */
    private static final int STONE_CHUNK_RANGE = 3;

    private FunctionScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        for (String block : List.of("scarlet_zhuhua", "potted_scarlet_zhuhua")) {
            scenes.add(Checks.superflat("function." + block + ".aura", SLOW_PULSE + 40, ctx -> scarletZhuhua(ctx, block)));
        }
        for (String block : List.of("yue_linglan", "potted_yue_linglan")) {
            scenes.add(Checks.superflat("function." + block + ".aura", SLOW_PULSE + 40, ctx -> yueLinglan(ctx, block)));
        }
        for (String block : List.of("jingxu_youlan", "potted_jingxu_youlan")) {
            scenes.add(Checks.superflat("function." + block + ".purge", FAST_PULSE + 40, ctx -> jingxuYoulan(ctx, block)));
        }
        scenes.add(Checks.superflat("function.suppression_stone.positionCheck", 40, FunctionScenes::suppressionStone).withChunkRadius(1));
        scenes.add(Checks.superflat("function.bleeding_heart.healsMaidOnHit", 40, FunctionScenes::bleedingHeart));
        scenes.add(Checks.superflat("function.molten_fox_leaf.fireImmunity", 40, FunctionScenes::moltenImmunity));
        scenes.add(Checks.superflat("function.molten_fox_leaf.walksOnLava", 120, FunctionScenes::moltenLava));
        scenes.add(Checks.superflat("function.spring_bloom_return.stacksAndTrigger", 120, FunctionScenes::springBloom));
        scenes.add(Checks.superflat("function.spring_bloom_return.purgeAtFavorabilityLevel3", 40, FunctionScenes::springBloomPurge));
        scenes.add(Checks.superflat("function.spring_bloom_return.realIronsCast", 200, FunctionScenes::springBloomRealCast));
        scenes.add(Checks.superflat("function.fragrant_ingenuity.eatFavorability", 40, FunctionScenes::fragrantEat));
        return scenes;
    }

    // ---- 花 ----

    /**
     * 猩红朱华：范围内存活生物获得力量 II（100 tick，环境效果、无粒子）；非女仆、非入魔骑士额外获得凋零 I；
     * Iron's 的非敌对商人 NPC 不受影响；距花 4 格的生物不受影响。
     */
    private static void scarletZhuhua(SceneContext ctx, String block) {
        ctx.setBlock(0, 0, 0, block(block));
        EntityMaid maid = Actors.stillMaid(ctx, INSIDE, 0, 0);
        Mob cow = Actors.spawn(ctx, "minecraft:cow", -INSIDE, 0, 0, true);
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, INSIDE, true);
        Mob knight = Actors.spawn(ctx, NS + "corrupted_knight", 0, 0, -INSIDE, true);
        Mob npc = spawnIronsNpc(ctx, INSIDE, 0, INSIDE);
        EntityMaid farMaid = Actors.stillMaid(ctx, OUTSIDE, 0, OUTSIDE);
        Mob farCow = Actors.spawn(ctx, "minecraft:cow", -OUTSIDE, 0, 0, true);
        ctx.await(() -> maid.hasEffect(MobEffects.DAMAGE_BOOST)).within(SLOW_PULSE + 5).then(() -> {
            expectEffect(ctx, "女仆", maid, MobEffects.DAMAGE_BOOST, 1, true);
            expectNoEffect(ctx, "女仆", maid, MobEffects.WITHER);
            for (Map.Entry<String, Mob> entry : Map.of("牛", cow, "僵尸", zombie).entrySet()) {
                expectEffect(ctx, entry.getKey(), entry.getValue(), MobEffects.DAMAGE_BOOST, 1, true);
                expectEffect(ctx, entry.getKey(), entry.getValue(), MobEffects.WITHER, 0, true);
            }
            expectEffect(ctx, "入魔骑士", knight, MobEffects.DAMAGE_BOOST, 1, true);
            expectNoEffect(ctx, "入魔骑士", knight, MobEffects.WITHER);
            ctx.check(effectIds(npc)).as("Iron's 商人 NPC 的效果").isEmpty();
            ctx.check(effectIds(farMaid)).as("距花 4 格的女仆的效果").isEmpty();
            ctx.check(effectIds(farCow)).as("距花 4 格的牛的效果").isEmpty();
        });
    }

    /** 月铃兰：范围内女仆获得生命恢复 I 与抗性提升 I（100 tick，非环境效果）；其他生物、距花 4 格的女仆不受影响。 */
    private static void yueLinglan(SceneContext ctx, String block) {
        ctx.setBlock(0, 0, 0, block(block));
        EntityMaid maid = Actors.stillMaid(ctx, INSIDE, 0, 0);
        Mob cow = Actors.spawn(ctx, "minecraft:cow", -INSIDE, 0, 0, true);
        EntityMaid farMaid = Actors.stillMaid(ctx, 0, 0, OUTSIDE);
        ctx.await(() -> maid.hasEffect(MobEffects.REGENERATION)).within(SLOW_PULSE + 5).then(() -> {
            expectEffect(ctx, "女仆", maid, MobEffects.REGENERATION, 0, false);
            expectEffect(ctx, "女仆", maid, MobEffects.DAMAGE_RESISTANCE, 0, false);
            ctx.check(effectIds(cow)).as("范围内牛的效果").isEmpty();
            ctx.check(effectIds(farMaid)).as("距花 4 格的女仆的效果").isEmpty();
        });
    }

    /** 净墟幽兰：范围内女仆只移除有害效果；其他生物移除全部效果；距花 4 格的不受影响。 */
    private static void jingxuYoulan(SceneContext ctx, String block) {
        ctx.setBlock(0, 0, 0, block(block));
        EntityMaid maid = Actors.stillMaid(ctx, INSIDE, 0, 0);
        Mob cow = Actors.spawn(ctx, "minecraft:cow", -INSIDE, 0, 0, true);
        EntityMaid farMaid = Actors.stillMaid(ctx, 0, 0, OUTSIDE);
        for (LivingEntity living : List.of(maid, cow, farMaid)) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 1200, 0));
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0));
        }
        ctx.await(() -> !maid.hasEffect(MobEffects.POISON)).within(FAST_PULSE + 5).then(() -> {
            ctx.check(effectIds(maid)).as("范围内女仆剩下的效果").isEqualTo(List.of("minecraft:speed"));
            ctx.check(effectIds(cow)).as("范围内牛剩下的效果").isEmpty();
            ctx.check(effectIds(farMaid)).as("距花 4 格的女仆的效果").isEqualTo(List.of("minecraft:poison", "minecraft:speed"));
        });
    }

    private static Mob spawnIronsNpc(SceneContext ctx, int dx, int dy, int dz) {
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals("irons_spellbooks")) {
                continue;
            }
            Entity probe = type.create(ctx.level());
            boolean npc = probe instanceof Merchant && !(probe instanceof Enemy) && probe instanceof Mob;
            if (probe != null) {
                probe.discard();
            }
            if (npc) {
                ctx.record("ironsNpc", id.toString());
                return Actors.spawn(ctx, id.toString(), dx, dy, dz, true);
            }
        }
        throw new IllegalStateException("没有找到 Iron's 的非敌对商人 NPC");
    }

    // ---- 镇石 ----

    /**
     * 镇石：7×7 区块内敌对生物的生成位置检查被拒绝（豁免的生成方式除外），范围外、非敌对生物不受影响；
     * 拆掉镇石后不再拦截。
     */
    private static void suppressionStone(SceneContext ctx) {
        ctx.setBlock(0, 0, 0, block("suppression_stone"));
        ServerLevel level = ctx.level();
        BlockPos stone = ctx.rel(0, 0, 0);
        int stoneChunkX = stone.getX() >> 4;
        int y = stone.getY() + 1;
        // 区块中心的方块坐标：同区块、边界内第 3 个区块、边界外第 4 个区块
        int sameX = stoneChunkX * 16 + 8;
        int edgeX = (stoneChunkX + STONE_CHUNK_RANGE) * 16 + 8;
        int outsideX = (stoneChunkX + STONE_CHUNK_RANGE + 1) * 16 + 8;
        int z = (stone.getZ() >> 4) * 16 + 8;
        Checks.after(ctx, 2, () -> {
            Map<String, String> actual = new TreeMap<>();
            Map<String, String> expected = new TreeMap<>();
            for (MobSpawnType type : MobSpawnType.values()) {
                String want = STONE_EXEMPT.contains(type) ? "DEFAULT" : "FAIL";
                expected.put("僵尸@同区块 " + type, want);
                actual.put("僵尸@同区块 " + type, check(level, EntityType.ZOMBIE, sameX, y, z, type));
            }
            expected.put("僵尸@第3区块 NATURAL", "FAIL");
            actual.put("僵尸@第3区块 NATURAL", check(level, EntityType.ZOMBIE, edgeX, y, z, MobSpawnType.NATURAL));
            expected.put("僵尸@第4区块 NATURAL", "DEFAULT");
            actual.put("僵尸@第4区块 NATURAL", check(level, EntityType.ZOMBIE, outsideX, y, z, MobSpawnType.NATURAL));
            expected.put("牛@同区块 NATURAL", "DEFAULT");
            actual.put("牛@同区块 NATURAL", check(level, EntityType.COW, sameX, y, z, MobSpawnType.NATURAL));
            ServerLevel overworld = ctx.server().overworld();
            if (overworld != level) {
                expected.put("僵尸@其他维度同坐标 NATURAL", "DEFAULT");
                actual.put("僵尸@其他维度同坐标 NATURAL", check(overworld, EntityType.ZOMBIE, sameX, y, z, MobSpawnType.NATURAL));
            }
            ctx.check(actual).as("生成位置检查结果").isEqualTo(expected);

            ctx.setBlock(0, 0, 0, Blocks.AIR);
            Checks.after(ctx, 2, () ->
                    ctx.check(check(level, EntityType.ZOMBIE, sameX, y, z, MobSpawnType.NATURAL))
                            .as("拆掉镇石后同区块僵尸 NATURAL").isEqualTo("DEFAULT"));
        });
    }

    private static String check(ServerLevel level, EntityType<? extends Mob> type, int x, int y, int z, MobSpawnType spawnType) {
        Mob mob = type.create(level);
        mob.moveTo(x + 0.5, y, z + 0.5);
        MobSpawnEvent.PositionCheck event = new MobSpawnEvent.PositionCheck(mob, level, spawnType, null);
        NeoForge.EVENT_BUS.post(event);
        mob.discard();
        return event.getResult().name();
    }

    // ---- 饰品 ----

    /** 喋血之心：女仆造成伤害后按原始伤害的 10% 治疗自己；没有饰品的女仆不治疗。 */
    private static void bleedingHeart(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 2, 0, 0);
        EntityMaid plain = Actors.sittingMaid(ctx, -2, 0, 0);
        Actors.equipBauble(maid, NS + "bleeding_heart");
        Mob target = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 2, true);
        Mob otherTarget = Actors.spawn(ctx, "minecraft:zombie", 0, 0, -2, true);
        Checks.after(ctx, 2, () -> {
            Actors.setMaidHealth(maid, 10);
            Actors.setMaidHealth(plain, 10);
            target.invulnerableTime = 0;
            otherTarget.invulnerableTime = 0;
            target.hurt(maid.damageSources().mobAttack(maid), 8);
            otherTarget.hurt(plain.damageSources().mobAttack(plain), 8);
            ctx.check(maid.getHealth()).as("喋血之心女仆打出 8 点伤害后的生命").isCloseTo(10.8, 1e-4);
            ctx.check(plain.getHealth()).as("无饰品女仆打出 8 点伤害后的生命").isCloseTo(10, 1e-4);
        });
    }

    /** 熔岩狐叶：免疫熔岩/火焰/燃烧/灼热地面伤害并熄火；绕过无敌的伤害照常生效；无饰品女仆照常受伤。 */
    private static void moltenImmunity(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 2, 0, 0);
        EntityMaid plain = Actors.sittingMaid(ctx, -2, 0, 0);
        Actors.equipBauble(maid, NS + "molten_fox_leaf");
        Checks.after(ctx, 2, () -> {
            Map<String, DamageSource> sources = Map.of(
                    "lava", maid.damageSources().lava(),
                    "in_fire", maid.damageSources().inFire(),
                    "on_fire", maid.damageSources().onFire(),
                    "hot_floor", maid.damageSources().hotFloor());
            Map<String, Boolean> protectedHits = new TreeMap<>();
            Map<String, Boolean> plainHits = new TreeMap<>();
            sources.forEach((name, source) -> {
                maid.invulnerableTime = 0;
                plain.invulnerableTime = 0;
                protectedHits.put(name, maid.hurt(source, 2));
                plainHits.put(name, plain.hurt(plain.damageSources().source(source.typeHolder().unwrapKey().orElseThrow()), 2));
            });
            ctx.check(protectedHits).as("熔岩狐叶女仆受到各类火焰伤害").isEqualTo(Map.of("hot_floor", false, "in_fire", false, "lava", false, "on_fire", false));
            ctx.check(plainHits).as("无饰品女仆受到各类火焰伤害").isEqualTo(Map.of("hot_floor", true, "in_fire", true, "lava", true, "on_fire", true));
            maid.invulnerableTime = 0;
            ctx.check(maid.hurt(maid.damageSources().fellOutOfWorld(), 1)).as("熔岩狐叶女仆受到掉出世界伤害").isTrue();
            maid.igniteForSeconds(5);
            ctx.check(maid.isOnFire()).as("点燃后女仆着火").isTrue();
            ctx.await(() -> !maid.isOnFire()).within(5).then(() -> ctx.passNote("着火后已熄灭"));
        });
    }

    /** 熔岩狐叶：女仆站在熔岩池上不下沉、不受伤、不着火。 */
    private static void moltenLava(SceneContext ctx) {
        Actors.lavaPool(ctx, 3);
        EntityMaid maid = Actors.spawn(ctx, Actors.MAID, 0, 0, 0, false);
        maid.setPersistenceRequired();
        Actors.equipBauble(maid, NS + "molten_fox_leaf");
        float health = maid.getHealth();
        int lavaTop = ctx.originY();
        Checks.after(ctx, 80, () -> {
            ctx.record("maidY", maid.getY());
            ctx.check(maid.isAlive()).as("80 tick 后女仆存活").isTrue();
            ctx.check(maid.getHealth()).as("80 tick 后女仆生命").isAtLeast(health);
            ctx.check(maid.isOnFire()).as("80 tick 后女仆着火").isFalse();
            ctx.check(maid.getY()).as("女仆脚底高度（熔岩面 y=" + lavaTop + "）").isAtLeast(lavaTop - 0.25);
        });
    }

    /**
     * 春花-返：施法获得层数（20 tick 获取冷却、最多 3 层、每层 400 tick 后过期）；
     * 受到 ≥4 点或 ≥10% 最大生命的伤害时消耗一层，回复 5% 最大生命，进入 200 tick 触发冷却。
     */
    private static void springBloom(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        EntityMaid plain = Actors.sittingMaid(ctx, 3, 0, 0);
        ItemStack bauble = Actors.equipBauble(maid, NS + "spring_bloom_return");
        List<Long> casts = new ArrayList<>();
        Checks.after(ctx, 2, () -> {
            casts.add(Actors.castSpringBloom(maid));
            Actors.castSpringBloom(maid);
            ctx.check(Actors.springBloomExpiries(maid).size()).as("同一 tick 施法两次后的层数").isEqualTo(1);
            castEvery(ctx, maid, 20, 3, casts, () -> {
                // 最多 3 层：第 4 次施法不加层
                ctx.check(Actors.springBloomExpiries(maid)).as("施法 4 次（间隔 20 tick）后各层的过期时间")
                        .isEqualTo(casts.subList(0, 3).stream().map(t -> t + 400).toList());
                hurtAndCheck(ctx, maid, plain);
            });
        });
        ctx.record("bauble", bauble.getItem().toString());
    }

    /** 每隔 interval tick 施法一次、共 times 次，把施法时间追加到 casts，然后运行 next。 */
    private static void castEvery(SceneContext ctx, EntityMaid maid, int interval, int times, List<Long> casts, Runnable next) {
        if (times == 0) {
            next.run();
            return;
        }
        Checks.after(ctx, interval, () -> {
            casts.add(Actors.castSpringBloom(maid));
            castEvery(ctx, maid, interval, times - 1, casts, next);
        });
    }

    /** plain 为无饰品的对照女仆，用来得到实际扣血量（女仆自身可能有减伤）。 */
    private static void hurtAndCheck(SceneContext ctx, EntityMaid maid, EntityMaid plain) {
        float max = maid.getMaxHealth();
        float light = (float) (max * 0.10) - 0.1F;
        Actors.setMaidHealth(maid, max / 2);
        maid.invulnerableTime = 0;
        maid.hurt(maid.damageSources().generic(), light);
        ctx.check(Actors.springBloomExpiries(maid).size()).as("受到 " + light + " 点（低于阈值）伤害后的层数").isEqualTo(3);
        float before = maid.getHealth();
        Actors.setMaidHealth(plain, before);
        plain.invulnerableTime = 0;
        plain.hurt(plain.damageSources().generic(), HEAVY);
        float dealt = before - plain.getHealth();
        ctx.record("heavyDealt", dealt);
        maid.invulnerableTime = 0;
        maid.hurt(maid.damageSources().generic(), HEAVY);
        ctx.check(Actors.springBloomExpiries(maid).size()).as("受到重击后的层数").isEqualTo(2);
        ctx.check(maid.getHealth()).as("受到重击（实际扣 " + dealt + "）并回复 5% 最大生命后的生命")
                .isCloseTo(before - dealt + max * 0.05, 1e-3);
        ItemStack stack = maid.getMaidBauble().getStackInSlot(0);
        ctx.check(stack.get(MaidSpellDataComponents.SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_UNTIL.get()))
                .as("触发冷却截止时间").isEqualTo(maid.level().getGameTime() + 200);
        maid.invulnerableTime = 0;
        maid.hurt(maid.damageSources().generic(), HEAVY);
        ctx.check(Actors.springBloomExpiries(maid).size()).as("冷却中再受到重击后的层数").isEqualTo(2);
    }

    /** 春花-返：好感度等级 ≥3 时触发还会移除一个负面效果（持续时间最长的）。 */
    private static void springBloomPurge(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, NS + "spring_bloom_return");
        maid.setFavorability(maid.getFavorabilityManager().getPointByLevel(3));
        Checks.after(ctx, 2, () -> {
            ctx.check(maid.getFavorabilityManager().getLevel()).as("好感度等级").isEqualTo(3);
            Actors.castSpringBloom(maid);
            maid.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
            maid.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 0));
            Actors.setMaidHealth(maid, maid.getMaxHealth() / 2);
            maid.invulnerableTime = 0;
            maid.hurt(maid.damageSources().generic(), HEAVY);
            ctx.check(effectIds(maid)).as("触发后剩下的效果").isEqualTo(List.of("minecraft:weakness"));
        });
    }

    /** 春花-返：女仆用 Iron's 法术书真实施法一次后获得一层。 */
    private static void springBloomRealCast(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        Actors.equipBauble(maid, NS + "spring_bloom_return");
        ItemStack book = Actors.parse(ctx, "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
                + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:firebolt\",index:0,level:1,locked:false}]}]");
        maid.setItemSlot(EquipmentSlot.OFFHAND, book);
        Mob target = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 5, true);
        Checks.after(ctx, 2, () -> {
            SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
            manager.addSpellItem(maid, book);
            SpellBookManager.getProvider("irons_spellbooks").setTarget(maid, target);
            manager.castSpell(maid);
            ctx.await(() -> !Actors.springBloomExpiries(maid).isEmpty()).within(150).then(() ->
                    ctx.check(Actors.springBloomExpiries(maid).size()).as("真实施法后的层数").isEqualTo(1));
        });
    }

    /** 馥郁巧思：女仆进食后额外增加 2 点好感度；无饰品女仆不增加。 */
    private static void fragrantEat(SceneContext ctx) {
        EntityMaid maid = Actors.sittingMaid(ctx, 2, 0, 0);
        EntityMaid plain = Actors.sittingMaid(ctx, -2, 0, 0);
        Actors.equipBauble(maid, NS + "fragrant_ingenuity");
        Checks.after(ctx, 2, () -> {
            int before = maid.getFavorability();
            int plainBefore = plain.getFavorability();
            NeoForge.EVENT_BUS.post(new MaidAfterEatEvent(maid, new ItemStack(Items.BREAD)));
            NeoForge.EVENT_BUS.post(new MaidAfterEatEvent(plain, new ItemStack(Items.BREAD)));
            ctx.check(maid.getFavorability() - before).as("馥郁巧思女仆进食后好感度增量").isEqualTo(2);
            ctx.check(plain.getFavorability() - plainBefore).as("无饰品女仆进食后好感度增量").isEqualTo(0);
        });
    }

    // ---- 工具 ----

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(NS + path));
    }

    private static void expectEffect(SceneContext ctx, String who, LivingEntity entity, Holder<MobEffect> effect,
                                     int amplifier, boolean ambient) {
        MobEffectInstance instance = entity.getEffect(effect);
        String name = who + " 的 " + effect.getRegisteredName();
        ctx.check(instance).as(name).isNotNull();
        if (instance != null) {
            ctx.check(instance.getAmplifier()).as(name + " 等级").isEqualTo(amplifier);
            ctx.check(instance.getDuration()).as(name + " 剩余时间").isBetween(90, 100);
            ctx.check(instance.isAmbient()).as(name + " 是环境效果").isEqualTo(ambient);
            ctx.check(instance.isVisible()).as(name + " 显示粒子").isFalse();
        }
    }

    private static void expectNoEffect(SceneContext ctx, String who, LivingEntity entity, Holder<MobEffect> effect) {
        ctx.check(entity.hasEffect(effect)).as(who + " 有 " + effect.getRegisteredName()).isFalse();
    }

    private static List<String> effectIds(LivingEntity entity) {
        return entity.getActiveEffects().stream().map(instance -> instance.getEffect().getRegisteredName()).sorted().toList();
    }
}
