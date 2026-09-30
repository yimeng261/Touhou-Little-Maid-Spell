package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.CompanionBlackHoleEntity;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.magicterra.stagewright.scene.Scene;
import net.magicterra.stagewright.scene.SceneContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 本模组的铁魔法法术实体：女仆黑洞只吞敌方的箭；改造飞弹能被实体选择器选中。
 */
public final class SpellEntityScenes {
    private static final float RADIUS = 3;

    private SpellEntityScenes() {
    }

    public static List<Scene> dedicatedServer() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(Checks.superflat("black_hole.consumesEnemyArrowsOnly", 20, ctx -> blackHole(ctx, false)));
        scenes.add(Checks.superflat("black_hole.sparesOwnerSideArrows", 20, ctx -> blackHole(ctx, true)));
        scenes.add(Checks.superflat("modified_magic_missile.selectableByType", 10, SpellEntityScenes::missileSelectable));
        return scenes;
    }

    /**
     * 女仆放出黑洞，球心处停着几支不同主人的箭。一 tick 后：
     * ownerSide 为 false 时看施法女仆自己的箭（留下）与野生女仆、别的玩家女仆的箭（吞掉）；
     * 为 true 时看主人和同主人女仆的箭（都应留下）。
     */
    private static void blackHole(SceneContext ctx, boolean ownerSide) {
        ServerLevel level = ctx.level();
        ServerPlayer owner = Owners.visitor(ctx, "TlmsHoleOwner", 3, 0, 0);
        ServerPlayer stranger = Owners.visitor(ctx, "TlmsHoleStranger", -3, 0, 0);
        EntityMaid caster = Owners.maid(ctx, owner, 0, 0, 0);
        CompanionBlackHoleEntity hole = new CompanionBlackHoleEntity(level, caster);
        hole.setRadius(RADIUS);
        Vec3 eyes = caster.getEyePosition();
        hole.setPos(eyes.x, CompanionBlackHoleEntity.placementY(eyes.y, RADIUS), eyes.z);
        Vec3 center = hole.position().add(0, RADIUS, 0);

        Map<String, LivingEntity> shooters = new LinkedHashMap<>();
        Map<String, Boolean> expected = new LinkedHashMap<>();
        if (ownerSide) {
            shooters.put("主人", owner);
            shooters.put("同主人的女仆", Owners.maid(ctx, owner, 0, 0, 3));
            shooters.keySet().forEach(k -> expected.put(k, false));
        } else {
            shooters.put("施法女仆", caster);
            expected.put("施法女仆", false);
            shooters.put("野生女仆", Actors.stillMaid(ctx, 0, 0, -3));
            expected.put("野生女仆", true);
            shooters.put("别的玩家的女仆", Owners.maid(ctx, stranger, 0, 0, 5));
            expected.put("别的玩家的女仆", true);
        }
        Map<String, Arrow> arrows = new LinkedHashMap<>();
        shooters.forEach((name, shooter) -> {
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(shooter);
            arrow.setNoGravity(true);
            arrow.setPos(center);
            arrow.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(arrow);
            ctx.cleanup(arrow::discard);
            arrows.put(name, arrow);
        });
        level.addFreshEntity(hole);
        ctx.cleanup(hole::discard);
        Checks.after(ctx, 2, () -> {
            Map<String, Boolean> consumed = new LinkedHashMap<>();
            arrows.forEach((name, arrow) -> consumed.put(name, arrow.isRemoved()));
            ctx.check(consumed).as("各方的箭是否被黑洞吞掉").isEqualTo(expected);
        });
    }

    private static void missileSelectable(SceneContext ctx) {
        ServerLevel level = ctx.level();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(NS + "modified_magic_missile")).orElse(null);
        if (type == null) {
            ctx.fail(NS + "modified_magic_missile 未注册");
            return;
        }
        Entity missile = type.create(level);
        Vec3 at = Vec3.atCenterOf(ctx.rel(0, 3, 0));
        missile.setPos(at);
        missile.setNoGravity(true);
        level.addFreshEntity(missile);
        ctx.cleanup(missile::discard);
        CommandSourceStack source = ctx.server().createCommandSourceStack().withSuppressedOutput().withLevel(level).withPosition(at);
        int found;
        try {
            found = ctx.server().getCommands().getDispatcher()
                    .execute("execute if entity @e[type=" + NS + "modified_magic_missile,distance=..2]", source);
        } catch (CommandSyntaxException e) {
            ctx.fail("选择器执行失败：" + e.getMessage());
            return;
        }
        ctx.check(found).as("@e[type=" + NS + "modified_magic_missile] 选中的数量").isEqualTo(1);
    }
}
