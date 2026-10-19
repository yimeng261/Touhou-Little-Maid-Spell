package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * <p>只保护非战斗生物和施法者一方的主从关系，而不是所有非玩家实体，敌对生物仍然是有效目标。</p>
 */
public final class ThunderstormTargetFilter {
    private static final int VEHICLE_TRACE_LIMIT = 8;

    private ThunderstormTargetFilter() {
    }

    public static boolean shouldProtect(@Nullable LivingEntity caster, @Nullable LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (target == caster || target instanceof EntityMaid) {
            return true;
        }
        if (target instanceof MagicalWinefoxBossEntity boss && !boss.isBattleActive()) {
            return true;
        }
        if (caster != null && MaidSpellAllyResolver.areFriendly(caster, target)) {
            return true;
        }
        if (isNonCombatant(target)) {
            return true;
        }
        return isProtectedThroughVehicle(caster, target, new HashSet<>(), 0);
    }

    private static boolean isNonCombatant(LivingEntity target) {
        if (target instanceof AbstractVillager
                || target instanceof IronGolem
                || target instanceof SnowGolem
                || target instanceof Allay
                || target instanceof Animal && !(target instanceof Enemy)
                || target instanceof WaterAnimal
                || target instanceof AmbientCreature) {
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
     * 载具（扫帚、船类坐骑等）载着受保护的乘客时同样受保护；
     * 反过来，骑在受保护载具上的乘客也受保护。载具链有环或异常时按深度上限截断。
     */
    private static boolean isProtectedThroughVehicle(@Nullable LivingEntity caster, LivingEntity target,
                                                     Set<UUID> visited, int depth) {
        if (depth > VEHICLE_TRACE_LIMIT || !visited.add(target.getUUID())) {
            return false;
        }

        Entity vehicle = target.getVehicle();
        if (vehicle instanceof LivingEntity living
                && (isProtectedDirect(caster, living)
                || isProtectedThroughVehicle(caster, living, visited, depth + 1))) {
            return true;
        }

        Entity rootVehicle = target.getRootVehicle();
        if (rootVehicle != target && rootVehicle instanceof LivingEntity living
                && (isProtectedDirect(caster, living)
                || isProtectedThroughVehicle(caster, living, visited, depth + 1))) {
            return true;
        }

        for (Entity passenger : target.getPassengers()) {
            if (passenger instanceof LivingEntity living
                    && (isProtectedDirect(caster, living)
                    || isProtectedThroughVehicle(caster, living, visited, depth + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProtectedDirect(@Nullable LivingEntity caster, LivingEntity target) {
        return target == caster
                || target instanceof EntityMaid
                || target instanceof MagicalWinefoxBossEntity boss && !boss.isBattleActive()
                || caster != null && MaidSpellAllyResolver.areFriendly(caster, target)
                || isNonCombatant(target);
    }
}
