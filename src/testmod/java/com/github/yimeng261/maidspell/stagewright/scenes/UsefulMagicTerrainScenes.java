package com.github.yimeng261.maidspell.stagewright.scenes;

import cn.coostack.cooparticlesapi.barrages.BarrageHitResult;
import cn.coostack.cooparticlesapi.barrages.BarrageOption;
import cn.coostack.usefulmagic.barrages.entity.skill.GiantSwordLightBarrageMagic;
import cn.coostack.usefulmagic.entity.custom.dragon.eye.MagicHeartEntity;
import cn.coostack.usefulmagic.entity.custom.dragon.eye.MagicSubEyeEntity;
import cn.coostack.usefulmagic.gamerules.UsefulMagicGameRules;
import cn.coostack.usefulmagic.items.weapon.magic.useful.DiggingMagic;
import cn.coostack.usefulmagic.items.weapon.wands.MagicWand;
import cn.coostack.usefulmagic.meteorite.MeteoriteImpactHelper;
import cn.coostack.usefulmagic.meteorite.starry.StarrySubBarrage;
import cn.coostack.usefulmagic.utils.ExplosionUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.compat.usefulmagic.UsefulMagicTerrainProtection;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 女仆地形保护与实用魔法游戏规则独立，真实爆破、分批队列及挖掘在落地时遵循当前配置。 */
public final class UsefulMagicTerrainScenes {
    private UsefulMagicTerrainScenes() {
    }

    public static List<Scene> integratedServer() {
        List<Scene> scenes = new ArrayList<>();
        for (boolean protect : new boolean[]{true, false}) {
            for (boolean terrainRule : new boolean[]{true, false}) {
                for (boolean maid : new boolean[]{true, false}) {
                    String name = "spell.usefulmagic.terrain." + (maid ? "maid" : "player")
                            + ".protection" + protect + ".rule" + terrainRule;
                    scenes.add(Checks.superflat(name, 10, ctx -> matrix(ctx, protect, terrainRule, maid)));
                }
            }
        }
        scenes.add(Checks.superflat("spell.usefulmagic.terrain.pendingQueueUsesCurrentSetting", 30,
                UsefulMagicTerrainScenes::pendingQueue));
        scenes.add(Checks.superflat("spell.usefulmagic.terrain.maidOwnerChain", 10,
                UsefulMagicTerrainScenes::ownerChain));
        scenes.add(Checks.superflat("spell.usefulmagic.terrain.diggingAndGiantSword", 10,
                UsefulMagicTerrainScenes::diggingAndSword));
        return scenes;
    }

    private static void settings(SceneContext ctx, boolean protect, boolean terrainRule) {
        boolean previous = Config.usefulMagicMaidTerrainProtection;
        GameRules rules = ctx.level().getGameRules();
        boolean previousRule = rules.getBoolean(UsefulMagicGameRules.MAGIC_TERRAIN_DESTRUCTION);
        boolean previousGriefing = rules.getBoolean(GameRules.RULE_MOBGRIEFING);
        ctx.cleanup(() -> {
            Config.usefulMagicMaidTerrainProtection = previous;
            rules.getRule(UsefulMagicGameRules.MAGIC_TERRAIN_DESTRUCTION).set(previousRule, ctx.server());
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(previousGriefing, ctx.server());
        });
        Config.usefulMagicMaidTerrainProtection = protect;
        rules.getRule(UsefulMagicGameRules.MAGIC_TERRAIN_DESTRUCTION).set(terrainRule, ctx.server());
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(true, ctx.server());
    }

    private static void matrix(SceneContext ctx, boolean protect, boolean terrainRule, boolean maidSource) {
        settings(ctx, protect, terrainRule);
        LivingEntity source = maidSource ? Actors.stillMaid(ctx, 0, 0, -12)
                : Owners.visitor(ctx, "UmTerrainPlayer", 0, 0, -12);
        boolean destroys = terrainRule && !(protect && maidSource);
        BlockPos pos = ctx.rel(0, 2, 0);
        Vec3 center = Vec3.atCenterOf(pos);

        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        ArrayDeque<BlockPos> pending = new ArrayDeque<>(List.of(pos));
        MeteoriteImpactHelper.INSTANCE.processPendingExplosion(ctx.level(), pending, 1, source);
        checkBlock(ctx, pos, destroys, "陨石破坏队列");
        ctx.check(pending).as("陨石队列处理完毕").isEmpty();

        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        ExplosionUtil.INSTANCE.createSolidBallExplosion(1, ctx.level(), center, source, false, false);
        checkBlock(ctx, pos, destroys, "实心球爆破");
        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        ExplosionUtil.INSTANCE.createHollowSphereExplosion(1, ctx.level(), center, source, false, false);
        checkBlock(ctx, pos, destroys, "球壳爆破");
        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        ExplosionUtil.INSTANCE.createHollowSphereExplosionIgnoreWater(1, ctx.level(), center, source, false, false);
        checkBlock(ctx, pos, destroys, "忽略水的球壳爆破");
        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        ExplosionUtil.INSTANCE.createRoundExplosion(ctx.level(), center, 3.0F, 0.0D, source, 3,
                Level.ExplosionInteraction.TNT);
        checkBlock(ctx, pos, destroys, "环形爆炸");

        Mob target = Actors.spawn(ctx, "minecraft:husk", 0, 2, 1, true);
        float before = target.getHealth();
        // 爆心置于空气中，侧下方的方块验证地形交互，目标验证实体伤害。
        ctx.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        BlockPos side = pos.offset(1, -1, 0);
        ctx.level().setBlock(side, Blocks.DIRT.defaultBlockState(), 3);
        StarrySubBarrage barrage = new StarrySubBarrage(center, ctx.level(), new BarrageOption(), 10.0D);
        barrage.setShooter(source);
        barrage.setImmediateSize(2.0D);
        Reflect.call(barrage, StarrySubBarrage.class, "triggerImpactExplosion", new Class<?>[0]);
        checkBlock(ctx, side, destroys, "星陨爆炸");
        ctx.check(target.getHealth()).as("星陨仍造成实体伤害").isLessThan(before);
        ctx.check(UsefulMagicGameRules.INSTANCE.canDestroyTerrain(ctx.level()))
                .as("实用魔法的游戏规则未被改写").isEqualTo(terrainRule);
    }

    private static void checkBlock(SceneContext ctx, BlockPos pos, boolean destroys, String label) {
        ctx.check(ctx.level().getBlockState(pos).isAir()).as(label + " 是否破坏方块").isEqualTo(destroys);
    }

    private static void pendingQueue(SceneContext ctx) {
        settings(ctx, false, true);
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, -6);
        List<BlockPos> positions = List.of(ctx.rel(0, 2, 0), ctx.rel(1, 2, 0), ctx.rel(2, 2, 0));
        positions.forEach(pos -> ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3));
        ArrayDeque<BlockPos> pending = new ArrayDeque<>(positions);
        MeteoriteImpactHelper.INSTANCE.processPendingExplosion(ctx.level(), pending, 1, maid);
        checkBlock(ctx, positions.getFirst(), true, "保护关闭时第一批");
        Checks.after(ctx, 2, () -> {
            Config.usefulMagicMaidTerrainProtection = true;
            MeteoriteImpactHelper.INSTANCE.processPendingExplosion(ctx.level(), pending, 1, maid);
            ctx.check(pending).as("开启保护后终止整个待破坏队列").isEmpty();
            checkBlock(ctx, positions.get(1), false, "开启保护后第二批");
            checkBlock(ctx, positions.get(2), false, "开启保护后后续批次");
            Config.usefulMagicMaidTerrainProtection = false;
            MeteoriteImpactHelper.INSTANCE.processPendingExplosion(ctx.level(),
                    new ArrayDeque<>(List.of(positions.get(1))), 1, maid);
            checkBlock(ctx, positions.get(1), true, "重新关闭保护后恢复破坏");
        });
    }

    private static void ownerChain(SceneContext ctx) {
        settings(ctx, true, true);
        ServerPlayer player = Owners.visitor(ctx, "UmTerrainOwner", 0, 0, -8);
        EntityMaid maid = Owners.maid(ctx, player, 0, 0, -6);
        MagicHeartEntity heart = new MagicHeartEntity(ctx.level());
        heart.setNoAi(true);
        heart.setOwner(maid);
        heart.setPos(maid.position());
        ctx.level().addFreshEntity(heart);
        ctx.cleanup(() -> Actors.cleanupEntity(heart));
        MagicSubEyeEntity summon = new MagicSubEyeEntity(ctx.level());
        summon.setNoAi(true);
        summon.setOwner(heart);
        summon.setPos(maid.position());
        ctx.level().addFreshEntity(summon);
        ctx.cleanup(() -> Actors.cleanupEntity(summon));
        ctx.check(UsefulMagicTerrainProtection.blocksTerrainDamage(summon)).as("女仆名下的来源受保护").isTrue();
        BlockPos pos = ctx.rel(0, 2, 0);
        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        MeteoriteImpactHelper.INSTANCE.processPendingExplosion(ctx.level(), new ArrayDeque<>(List.of(pos)), 1, summon);
        checkBlock(ctx, pos, false, "女仆召唤物的陨石");
        heart.setOwner(player);
        MeteoriteImpactHelper.INSTANCE.processPendingExplosion(ctx.level(), new ArrayDeque<>(List.of(pos)), 1, summon);
        checkBlock(ctx, pos, true, "玩家名下来源仍遵循原规则");
    }

    private static void diggingAndSword(SceneContext ctx) {
        settings(ctx, true, true);
        EntityMaid maid = Actors.stillMaid(ctx, 0, 0, -8);
        ServerPlayer player = Owners.visitor(ctx, "UmSwordPlayer", 0, 0, -10);
        ItemStack ball = Actors.stack("usefulmagic:digging_magic");
        ItemStack wand = Actors.stack("usefulmagic:netherite_wand");
        ((MagicWand) wand.getItem()).setLoadedMagic(wand, ball);
        DiggingMagic digging = (DiggingMagic) ball.getItem();
        Mob target = Actors.spawn(ctx, "minecraft:husk", 0, 0, -5, true);
        maid.setYRot(0.0F);
        maid.setXRot(0.0F);
        float before = target.getHealth();
        digging.usingTick(maid, wand, ball, ctx.level(), 10);
        digging.stopUse(maid, ctx.level(), wand, ball, 10, false);
        ctx.check(target.getHealth()).as("地形保护开启时挖掘射线仍伤害实体").isLessThan(before);
        BlockPos pos = ctx.rel(0, 2, 0);
        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for (int i = 0; i < 100; i++) {
            dig(ctx, digging, maid, wand, ball, hit, i);
        }
        checkBlock(ctx, pos, false, "开启保护后的挖掘魔法");
        Config.usefulMagicMaidTerrainProtection = false;
        for (int i = 0; i < 100 && !ctx.level().getBlockState(pos).isAir(); i++) {
            dig(ctx, digging, maid, wand, ball, hit, i);
        }
        checkBlock(ctx, pos, true, "关闭保护后恢复挖掘");

        ctx.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        GiantSwordLightBarrageMagic sword = new GiantSwordLightBarrageMagic(null, 0,
                Vec3.atCenterOf(pos), ctx.level(), new BarrageOption(), 10.0D, player);
        sword.setShooter(maid);
        AABB box = AABB.ofSize(Vec3.atCenterOf(pos), 16, 16, 16);
        Config.usefulMagicMaidTerrainProtection = true;
        sword.onHitDamaged(new BarrageHitResult());
        ctx.check(ctx.level().getEntitiesOfClass(FallingBlockEntity.class, box)).as("女仆巨剑不产生掉落方块").isEmpty();
        sword.setShooter(player);
        sword.onHitDamaged(new BarrageHitResult());
        List<FallingBlockEntity> debris = ctx.level().getEntitiesOfClass(FallingBlockEntity.class, box);
        debris.forEach(entity -> ctx.cleanup(() -> Actors.cleanupEntity(entity)));
        ctx.check(debris).as("玩家巨剑仍产生掉落方块").isNotEmpty();
    }

    private static void dig(SceneContext ctx, DiggingMagic digging, LivingEntity shooter,
                            ItemStack wand, ItemStack ball, BlockHitResult hit, int time) {
        Reflect.call(digging, DiggingMagic.class, "tickDiggingState",
                new Class<?>[]{LivingEntity.class, ServerLevel.class, ItemStack.class, ItemStack.class, BlockHitResult.class, int.class},
                shooter, ctx.level(), wand, ball, hit, time);
    }
}
