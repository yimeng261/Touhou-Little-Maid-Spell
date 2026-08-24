package com.github.yimeng261.maidspell.item.bauble.staranchorPearl;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.effect.VoidWalkEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableFloat;

/** 女仆佩戴星锚珍珠时按次消耗耐久并取消虚空伤害；玩家用法由事件处理。 */
public class StaranchorPearlBauble implements IMaidBauble {

    @Override
    public boolean onInjured(EntityMaid maid, ItemStack baubleItem, DamageSource source, MutableFloat damage) {
        if (!VoidWalkEffect.isVoidDamage(source)) {
            return false;
        }

        maid.hurtAndBreak(baubleItem, StaranchorPearl.MAID_BLOCK_COST);
        if (maid.level() instanceof ServerLevel serverLevel) {
            // 挡下的那一下也扫一圈虚空粒子，玩家才看得出是珍珠在起作用。
            VoidWalkEffect.spawnRing(serverLevel, maid);
        }
        return true;
    }
}
