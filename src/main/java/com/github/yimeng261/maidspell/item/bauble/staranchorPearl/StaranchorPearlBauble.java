package com.github.yimeng261.maidspell.item.bauble.staranchorPearl;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableFloat;

/**
 * 星锚珍珠的女仆侧：戴着就替她挡虚空伤害，每挡一发扣 1 点耐久。
 *
 * <p>写法整体照车万女仆原版的保护类饰品（{@code FallProtectBauble} 那六个）：
 * 在 {@code onInjured} 里判伤害类型 → {@code hurtAndBreak} → 返回 {@code true} 取消伤害。
 * 「参考原版饰品消耗耐久」指的就是这一套。
 *
 * <p>扣耐久的频率不用自己压：女仆的饰品回调挂在 {@code LivingDamageEvent} 上
 * （见 TLM 的 {@code MaidLivingEntityEvent}），那一条在原版的
 * {@code invulnerableTime} 冷却之后才发。所以掉出世界时不是每 tick 扣一点，
 * 而是跟着伤害结算的频率走，64 点耐久能撑住一段像样的救援时间。
 *
 * <p>玩家那一路不吃这个类：玩家是自己右键喝下状态，走
 * {@code event.StaranchorPearlEvents} 的全局免疫，按 {@link StaranchorPearl#PLAYER_USE_COST}
 * 扣耐久；这里按 {@link StaranchorPearl#MAID_BLOCK_COST} 扣。
 */
public class StaranchorPearlBauble implements IMaidBauble {

    @Override
    public boolean onInjured(EntityMaid maid, ItemStack baubleItem, DamageSource source, MutableFloat damage) {
        if (!VoidWalkEffect.isVoidDamage(source)) {
            return false;
        }

        // 原版保护类饰品的固定写法：坏了就发一条物品损坏提示。
        // 回调参数是「持有者」，sendItemBreakMessage 收的却是那个 ItemStack，别传错。
        baubleItem.hurtAndBreak(StaranchorPearl.MAID_BLOCK_COST, maid,
                holder -> maid.sendItemBreakMessage(baubleItem));
        if (maid.level() instanceof ServerLevel serverLevel) {
            // 挡下的那一下也扫一圈虚空粒子，玩家才看得出是珍珠在起作用。
            VoidWalkEffect.spawnRing(serverLevel, maid);
        }
        return true;
    }
}
