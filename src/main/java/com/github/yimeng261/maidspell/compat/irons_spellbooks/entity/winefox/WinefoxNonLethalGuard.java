package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 万法酒狐的攻击不会真的打死人：把目标留在 1 点血。
 *
 * <p>盖住所有出伤口径——近战、法术、弹体，只要伤害源头能追溯到她。
 * 法术伤害由铁魔法自己发，我们插不进它的计算，所以拦在<b>承伤方</b>这一侧。
 *
 * <p>挂 {@code LivingDamageEvent} 而不是 {@code LivingHurtEvent}：前者拿到的是护甲、
 * 抗性、吸收全部结算完、马上就要扣到血条上的那个数，后者是结算<b>之前</b>的原始伤害。
 * 按原始伤害去削，护甲会再砍一刀，玩家的血只会渐近 1 而永远碰不到 1——那样
 * {@code isViableTarget} 就一直认为他还能打，她会追着一个永远打不服的人不放。
 *
 * <p>只保护玩家。小怪该死还是得死，否则召唤物永远清不掉。
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

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!isWinefoxDamage(event.getSource())) {
            return;
        }
        float survivable = Math.max(0.0F,
            player.getHealth() - MagicalWinefoxBossEntity.SURVIVAL_HEALTH_FLOOR);
        if (event.getAmount() >= survivable) {
            event.setAmount(survivable);
        }
    }

    /** 伤害是否出自酒狐：直接打的、她的弹体、或她召出来的东西。 */
    private static boolean isWinefoxDamage(DamageSource source) {
        return MagicalWinefoxBossEntity.damageFrom(source, MagicalWinefoxBossEntity.class);
    }
}
