package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 正式玩家挑战中，万法酒狐的攻击不会真的打死人：把挑战者留在 1 点血。
 *
 * <p>盖住所有出伤口径——近战、法术、弹体，只要伤害源头能追溯到她。
 * 法术伤害由铁魔法自己发，我们插不进它的计算，所以拦在<b>承伤方</b>这一侧。
 *
 * <p>挂 {@code LivingDamageEvent} 而不是 {@code LivingHurtEvent}：前者拿到的是护甲、
 * 抗性、吸收全部结算完、马上就要扣到血条上的那个数，后者是结算<b>之前</b>的原始伤害。
 * 按原始伤害去削，护甲会再砍一刀，玩家的血只会渐近 1 而永远碰不到 1——那样
 * {@code isViableTarget} 就一直认为他还能打，她会追着一个永远打不服的人不放。
 *
 * <p>只保护正式挑战者。普通生物战斗不走这里，目标该死还是得死，
 * 否则召唤物永远清不掉。
 *
 * <p><b>不挂 {@code @Mod.EventBusSubscriber}</b>，由
 * {@code IronsSpellbooksCompat.register} 在确认铁魔法在场之后手动注册——
 * 与同目录下另外两个守卫一样。注解是 Forge 扫描整个 jar 自动登记的，缺铁魔法时照样会挂上去；
 * 而处理器要解析 {@link MagicalWinefoxBossEntity} 的字面量，那个类的父类
 * {@code AbstractSpellCastingMob} 不在，玩家第一次挨打就是 NoClassDefFoundError。
 * 注册这个类本身不会加载它——类字面量是逐个解析的。
 */
public final class WinefoxNonLethalGuard {

    private WinefoxNonLethalGuard() {
    }

    /**
     * 坐姿只限制玩家的短剑邀战，不再让其他生物把她当作不可选中的目标。
     * 对方一旦锁定她，就切入普通生物战斗；该入口会自行排除玩家和正式挑战。
     *
     * <p><b>玩家女仆生态是例外：坐姿待机时她们连目标都不该选上。</b>
     * 女仆的索敌（{@code StartAttacking}）会为 {@code LivingChangeTargetEvent} 的取消让路 ——
     * 取消之后 {@code ATTACK_TARGET} 记忆根本不会落下去，于是女仆既不会主动开战，
     * 也不会触发她起身。战斗只能由玩家递星芒短剑开始；打起来之后女仆才作为
     * 挑战参与者下场，拿得到下面这条 1 点血的保护。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void activateNormalMobCombat(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof MagicalWinefoxBossEntity boss)) {
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

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Player) && !(victim instanceof EntityMaid)) {
            return;
        }
        if (victim.level().isClientSide) {
            return;
        }
        if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        MagicalWinefoxBossEntity boss = findBoss(event.getSource());
        if (boss == null && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            boss = victim.level().getEntitiesOfClass(MagicalWinefoxBossEntity.class,
                victim.getBoundingBox().inflate(64.0D), candidate -> candidate.isBattleActive()
                    && candidate.isChallengeParticipant(victim)).stream().findFirst().orElse(null);
        }
        // The non-lethal duel rule belongs only to the player who opened a
        // formal challenge. A boss fighting normally must be able to damage
        // and kill players just like any other hostile mob.
        if (boss == null || !boss.isChallengeParticipant(victim)) {
            return;
        }
        if (!boss.isBattleActive()) {
            event.setAmount(0.0F);
            return;
        }
        float survivable = Math.max(0.0F,
            victim.getHealth() - MagicalWinefoxBossEntity.SURVIVAL_HEALTH_FLOOR);
        if (event.getAmount() >= survivable) {
            event.setAmount(survivable);
            if (victim instanceof Player) {
                boss.endChallengeLost();
            } else {
                boss.retireMaidFromChallenge((EntityMaid) victim);
            }
        }
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
}
