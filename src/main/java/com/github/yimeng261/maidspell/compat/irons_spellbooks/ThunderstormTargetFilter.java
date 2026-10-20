package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 判定哪些目标不应被 ISS 雷暴选中。
 *
 * <p>只保护无主的中立生物和施法者一方，而不是所有非玩家实体，敌对生物仍然是有效目标；
 * 女仆、宠物和召唤物只有属于施法者一方时才受保护，敌人施放的雷暴照常劈它们。</p>
 */
public final class ThunderstormTargetFilter {
    private static final int VEHICLE_TRACE_LIMIT = 8;

    private ThunderstormTargetFilter() {
    }

    public static boolean shouldProtect(@Nullable LivingEntity caster, @Nullable LivingEntity target) {
        if (target == null) {
            return false;
        }
        return isCasterSide(caster, target)
                || isNonCombatant(target)
                || isProtectedThroughVehicle(caster, target, new HashSet<>(), 0);
    }

    /** 施法者自己、未开战的星之魔女，以及与施法者同一阵营（主人链、队伍）的实体。 */
    private static boolean isCasterSide(@Nullable LivingEntity caster, LivingEntity target) {
        return target == caster
                || target instanceof MagicalWinefoxBossEntity boss && !boss.isBattleActive()
                || caster != null && MaidSpellAllyResolver.areFriendly(caster, target);
    }

    /**
     * 无主的中立生物（村民、傀儡、动物等）一律不劈，但正盯着玩家或女仆的、愤怒中的除外。有主人的生物（女仆、宠物、召唤物）不在此列，
     * 是否受保护取决于主人是不是施法者一方，由 {@link #isCasterSide} 判定，敌方的狼和召唤物照常落雷。
     */
    private static boolean isNonCombatant(LivingEntity target) {
        if (isOwned(target) || isAggressive(target)) {
            return false;
        }
        return target instanceof AbstractVillager
                || target instanceof IronGolem
                || target instanceof SnowGolem
                || target instanceof Allay
                || target instanceof Animal && !(target instanceof Enemy)
                || target instanceof WaterAnimal
                || target instanceof AmbientCreature;
    }

    /** 正盯着玩家或女仆的生物，以及处于愤怒状态的中立生物（北极熊等），虽然不是 Enemy 也照常落雷。 */
    private static boolean isAggressive(LivingEntity target) {
        if (target instanceof NeutralMob neutral && neutral.isAngry()) {
            return true;
        }
        return target instanceof Mob mob && (mob.getTarget() instanceof Player || mob.getTarget() instanceof EntityMaid);
    }

    private static boolean isOwned(LivingEntity target) {
        if (target instanceof EntityMaid) {
            return true;
        }
        if (target instanceof TamableAnimal tamable && tamable.isTame()) {
            return true;
        }
        if (target instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner instanceof Player || owner instanceof EntityMaid) {
                return true;
            }
        }
        // 覆盖通过主人链而非 OwnableEntity 暴露归属的模组召唤物（Goety、Ars Nouveau 等）；
        // isOwnedBy 会把实体自身算进去，所以玩家本人要先排除。
        return !(target instanceof Player)
                && (MaidSpellAllyResolver.isOwnedBy(target, Player.class)
                || MaidSpellAllyResolver.isOwnedBy(target, EntityMaid.class));
    }

    /**
     * 载具载着施法者一方的乘客时同样受保护；反过来，骑在施法者一方载具上的乘客也受保护。
     * 中立生物只保护它自己，敌对骑手骑在中立载具上（鸡骑士等）照常落雷。载具链有环或异常时按深度上限截断。
     */
    private static boolean isProtectedThroughVehicle(@Nullable LivingEntity caster, LivingEntity target,
                                                     Set<UUID> visited, int depth) {
        if (depth > VEHICLE_TRACE_LIMIT || !visited.add(target.getUUID())) {
            return false;
        }

        Entity vehicle = target.getVehicle();
        if (vehicle instanceof LivingEntity living
                && (isCasterSide(caster, living)
                || isProtectedThroughVehicle(caster, living, visited, depth + 1))) {
            return true;
        }

        Entity rootVehicle = target.getRootVehicle();
        if (rootVehicle != target && rootVehicle instanceof LivingEntity living
                && (isCasterSide(caster, living)
                || isProtectedThroughVehicle(caster, living, visited, depth + 1))) {
            return true;
        }

        for (Entity passenger : target.getPassengers()) {
            if (passenger instanceof LivingEntity living
                    && (isCasterSide(caster, living)
                    || isProtectedThroughVehicle(caster, living, visited, depth + 1))) {
                return true;
            }
        }
        return false;
    }
}
