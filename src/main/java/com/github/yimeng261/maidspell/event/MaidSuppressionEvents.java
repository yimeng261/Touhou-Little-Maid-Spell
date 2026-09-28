package com.github.yimeng261.maidspell.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.utils.MaidSuppressionZone;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** 压制区中的女仆索敌与伤害拦截；先检查区域是否存在，再解析主人链。 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public final class MaidSuppressionEvents {

    private MaidSuppressionEvents() {
    }

    /** 压制区里的女仆不许换目标。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        if (suppressed(attacker, event.getNewTarget())) {
            event.setCanceled(true);
            attacker.setLastHurtByMob(null);
        }
    }

    /**
     * 压制区里女仆这一边打不出伤害。
     *
     * <p>光拦索敌不够：飞在半空的箭、已经抬起的刀、别的模组直接调 {@code hurt} 的路径，
     * 都不经过 {@link LivingChangeTargetEvent}。这一条是兜底。
     *
     * <p>拦的不分对象——擂台是玩家一个人的，圈里的女仆连顺手清个小怪都不该做。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        Entity origin = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (suppressed(origin, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 这一下算不算「压制区里女仆插手」。
     *
     * <p>出手方和挨打方各算一次「在不在圈里」：压制区是以 Boss 为心的球，而女仆的远程手段够得着更远。
     * 只看出手方的位置，站在圈外放法术的女仆照打不误，而她打出的伤害仍会计进伤害归属，玩家反倒被判了代打。
     *
     * <p>「女仆这一边」包含她的召唤物：伤害源上挂的是召唤物本身，主人在 owner 链的上游，
     * 不上溯就等于留着一条绕开整条规则的路。
     */
    private static boolean suppressed(@Nullable Entity origin, @Nullable Entity victim) {
        if (!MaidSuppressionZone.isActive()) {
            return false;
        }
        if (!MaidSuppressionZone.suppresses(origin) && !MaidSuppressionZone.suppresses(victim)) {
            return false;
        }
        return MaidSpellAllyResolver.isOwnedBy(origin, EntityMaid.class);
    }
}
