package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 在最终伤害阶段保护正式挑战者的最低生命，并调整酒狐对女仆的伤害。
 * 虚空相变的追加伤害通过 {@link #duelFollowUpLimit} 共用同一份生命额度。
 * 由铁魔法兼容入口注册，避免缺少依赖时加载此类。
 */
public final class WinefoxNonLethalGuard {

    private WinefoxNonLethalGuard() {
    }

    /**
     * 坐姿只限制玩家的短剑邀战，不再让其他生物把她当作不可选中的目标。对方一旦锁定她，就切入普通生物战斗；该入口会自行排除玩家和正式挑战。
     *
     * <p>玩家女仆生态是例外：坐姿待机时她们连目标都不该选上——女仆的索敌（{@code StartAttacking}）会为 {@code LivingChangeTargetEvent} 的取消让路，
     * 取消之后 {@code ATTACK_TARGET} 记忆根本不会落下去，于是女仆既不会主动开战，也不会触发她起身；战斗只能由玩家递星芒短剑开始，打起来之后女仆才作为挑战参与者下场，拿得到下面这条 1 点血的保护。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void activateNormalMobCombat(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof MagicalWinefoxBossEntity boss)) {
            return;
        }
        LivingEntity attacker = event.getEntity();
        if (boss.isSeated() && MaidSpellAllyResolver.isOwnedBy(attacker, EntityMaid.class)) {
            event.setCanceled(true);
            return;
        }
        boss.beginMobCombat(attacker);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockRetiredMaidInteraction(InteractMaidEvent event) {
        if (MagicalWinefoxBossEntity.isRetiredMaid(event.getMaid())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockRetiredMaidInteraction(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof EntityMaid maid
            && MagicalWinefoxBossEntity.isRetiredMaid(maid)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockRetiredMaidInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof EntityMaid maid
            && MagicalWinefoxBossEntity.isRetiredMaid(maid)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    /**
     * 先放缩酒狐对女仆生态的伤害，再按正式挑战的生命地板裁剪。
     * Pre 阶段的伤害尚未扣除吸收，倍率与地板都只作用于吸收之后真正扣血的部分。
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        // 女仆生态：女仆本尊，以及她名下的召唤物。
        boolean maidSide = victim instanceof EntityMaid
            || MaidSpellAllyResolver.isOwnedBy(victim, EntityMaid.class);
        if (!(victim instanceof Player) && !maidSide) {
            return;
        }
        if (victim.level().isClientSide) {
            return;
        }
        if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        float absorbed = absorbedPart(victim, event.getNewDamage());
        if (maidSide && event.getNewDamage() - absorbed > 0.0F) {
            // 放在地板判定之前，理由见方法注释。追不到她就一点不动：这一发不是她打的。
            MagicalWinefoxBossEntity attacker = findBoss(event.getSource());
            if (attacker != null) {
                float scaled = (float) ((event.getNewDamage() - absorbed) * attacker.maidspell$damageToMaidMultiplier());
                event.setNewDamage(scaled + absorbed);
                absorbed = absorbedPart(victim, event.getNewDamage());
            }
        }
        MagicalWinefoxBossEntity boss = resolveDuelBoss(victim, event.getSource());
        // 不致死规则只属于正式挑战的参与者；普通战斗中她和其他敌对生物一样能击杀玩家。
        if (boss == null || !boss.isChallengeParticipant(victim)) {
            return;
        }
        if (!boss.isBattleActive()) {
            event.setNewDamage(0.0F);
            return;
        }
        float survivable = Math.max(0.0F,
            victim.getHealth() - MagicalWinefoxBossEntity.duelSurvivalFloor());
        if (event.getNewDamage() - absorbed >= survivable) {
            event.setNewDamage(survivable + absorbed);
            // 玩家判负由 tickBattleOver 读取实际血量；后续监听器仍可能取消本次伤害。
            if (victim instanceof EntityMaid maid) {
                // 女仆是原地劝退，不收场（见 retireMaidFromChallenge），而且她那一侧没有
                // tickBattleOver 那样的逐 tick 复查口，收在这里才收得掉，所以照旧。
                boss.retireMaidFromChallenge(maid);
            }
        }
    }

    /**
     * 追加伤害须扣除同一次事件中尚未写血的主伤害，否则两次独立限额会击穿生命地板。
     * @param pendingDamage 当前事件削减后、尚未落账的主伤害
     * @return 允许追加的伤害上限
     */
    public static float duelFollowUpLimit(LivingEntity victim, DamageSource source,
                                          float pendingDamage, float bonus) {
        if (bonus <= 0.0F || victim.level().isClientSide) {
            return bonus;
        }
        if (!(victim instanceof Player) && !(victim instanceof EntityMaid)) {
            return bonus;
        }
        MagicalWinefoxBossEntity boss = resolveDuelBoss(victim, source);
        if (boss == null || !boss.isChallengeParticipant(victim)) {
            return bonus;
        }
        // 收场中与上面同一条口径：这一场已经不打了，追加伤害整个不算。
        if (!boss.isBattleActive()) {
            return 0.0F;
        }
        float room = victim.getHealth() - pendingDamage - MagicalWinefoxBossEntity.duelSurvivalFloor();
        return Math.max(0.0F, Math.min(bonus, room));
    }

    /** 这次伤害里会先被吸收生命值抵掉的部分。 */
    public static float absorbedPart(LivingEntity victim, float damage) {
        return Math.max(0.0F, Math.min(victim.getAbsorptionAmount(), damage));
    }

    /** 找出伤害来源对应的酒狐：直接来源、弹体，或召唤物 owner 链。 */
    private static MagicalWinefoxBossEntity findBoss(DamageSource source) {
        for (Entity entity : new Entity[]{source.getEntity(), source.getDirectEntity()}) {
            if (entity instanceof MagicalWinefoxBossEntity boss) return boss;
            Entity owner = MaidSpellAllyResolver.resolveResponsibleEntity(entity).orElse(null);
            if (owner instanceof MagicalWinefoxBossEntity boss) return boss;
        }
        return null;
    }

    /**
     * 优先按伤害来源查酒狐；无归属的虚空伤害不按附近正赛推定，避免拦截 /kill 或掉出世界。
     */
    private static MagicalWinefoxBossEntity resolveDuelBoss(LivingEntity victim, DamageSource source) {
        MagicalWinefoxBossEntity boss = findBoss(source);
        if (boss == null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            boss = victim.level().getEntitiesOfClass(MagicalWinefoxBossEntity.class,
                victim.getBoundingBox().inflate(64.0D), candidate -> candidate.isBattleActive()
                    && candidate.isChallengeParticipant(victim)).stream().findFirst().orElse(null);
        }
        return boss;
    }
}
