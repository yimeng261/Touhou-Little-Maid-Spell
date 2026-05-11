package com.github.yimeng261.maidspell.spell.providers.slashblade;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.data.MaidSlashBladeData;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.providers.slashblade.SlashBladeDirectSkills.DirectSkill;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 直接技能命中确认与攻击实体追踪。
 * 从 SlashBladeProvider 中分离，避免主类职责过重。
 */
public final class SlashBladeDirectSkillHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DIRECT_ATTACK_DAMAGE_RATIO = 0.85D;
    static final int DIRECT_SKILL_HIT_CONFIRM_TICKS = 8;

    private SlashBladeDirectSkillHandler() {
    }

    // ==================== 命中确认入口 ====================

    public static void processPendingDirectSkillHit(EntityMaid maid, MaidSlashBladeData data) {
        if (!data.hasPendingDirectSkillHit()) {
            return;
        }
        if (maid.level().getGameTime() > data.getPendingDirectSkillExpiryTime()) {
            LOGGER.debug("[MaidSpell][SlashBlade] direct hit confirm expired: maid={} skill={} targetId={}",
                    maid.getId(), data.getPendingDirectSkillName(), data.getPendingDirectSkillTargetId());
            data.clearPendingDirectSkillHit();
            return;
        }

        Entity targetEntity = maid.level().getEntity(data.getPendingDirectSkillTargetId());
        if (!(targetEntity instanceof LivingEntity target) || !isValidTarget(target)) {
            data.clearPendingDirectSkillHit();
            return;
        }

        for (Integer entityId : new ArrayList<>(data.getPendingDirectSkillEntityIds())) {
            Entity attackEntity = maid.level().getEntity(entityId);
            if (!didDirectSkillAttackEntityHitTarget(attackEntity, target)) {
                continue;
            }

            target.invulnerableTime = 0;
            boolean hurt = target.hurt(maid.damageSources().mobAttack(maid), data.getPendingDirectSkillDamage());
            target.invulnerableTime = 0;
            LOGGER.debug("[MaidSpell][SlashBlade] direct hit confirmed: maid={} skill={} target={} damage={} hurt={}",
                    maid.getId(), data.getPendingDirectSkillName(), describeTarget(target), data.getPendingDirectSkillDamage(), hurt);
            data.clearPendingDirectSkillHit();
            return;
        }
    }

    public static void armDirectSkillHitConfirmation(EntityMaid maid, MaidSlashBladeData data,
                                               LivingEntity target, DirectSkill skill,
                                               List<Integer> attackEntityIds) {
        if (!isValidTarget(target) || attackEntityIds.isEmpty()) {
            return;
        }

        if (BaubleStateManager.hasBauble(maid, MaidSpellItems.DREAM_CAT_CRYSTAL)) {
            return;
        }

        float damage = (float) Math.max(1.0D, maid.getAttributeValue(Attributes.ATTACK_DAMAGE) * DIRECT_ATTACK_DAMAGE_RATIO);
        data.armPendingDirectSkillHit(
                attackEntityIds,
                target.getId(),
                damage,
                maid.level().getGameTime() + DIRECT_SKILL_HIT_CONFIRM_TICKS,
                skill.getDisplayName()
        );
    }

    // ==================== 攻击实体收集 ====================

    public static List<Integer> collectNewDirectSkillAttackEntities(EntityMaid maid, Runnable action) {
        Set<Integer> before = new HashSet<>(findOwnedDirectSkillAttackEntityIds(maid));
        action.run();
        List<Integer> created = new ArrayList<>();
        for (Integer entityId : findOwnedDirectSkillAttackEntityIds(maid)) {
            if (!before.contains(entityId)) {
                created.add(entityId);
            }
        }
        return created;
    }

    private static List<Integer> findOwnedDirectSkillAttackEntityIds(EntityMaid maid) {
        List<Integer> entityIds = new ArrayList<>();
        for (Entity entity : maid.level().getEntities(maid, maid.getBoundingBox().inflate(16.0D),
                candidate -> isTrackedDirectSkillAttackEntity(maid, candidate))) {
            entityIds.add(entity.getId());
        }
        return entityIds;
    }

    private static boolean isTrackedDirectSkillAttackEntity(EntityMaid maid, Entity entity) {
        if (entity instanceof EntitySlashEffect slashEffect) {
            return slashEffect.getOwner() == maid;
        }
        if (entity instanceof EntityAbstractSummonedSword summonedSword) {
            return summonedSword.getOwner() == maid;
        }
        return false;
    }

    private static boolean didDirectSkillAttackEntityHitTarget(Entity attackEntity, LivingEntity target) {
        if (attackEntity instanceof EntitySlashEffect slashEffect) {
            return slashEffect.getAlreadyHits().contains(target);
        }
        if (attackEntity instanceof EntityAbstractSummonedSword summonedSword) {
            return summonedSword.getHitEntity() == target;
        }
        return false;
    }

    // ==================== 共享工具方法 ====================

    public static boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isDeadOrDying() && !target.isRemoved();
    }

    public static String describeTarget(LivingEntity target) {
        if (target == null) {
            return "null";
        }
        return target.getType() + "#" + target.getId() + " alive=" + target.isAlive() + " removed=" + target.isRemoved();
    }
}
