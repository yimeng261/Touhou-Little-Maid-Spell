package com.github.yimeng261.maidspell.item.bauble.arcCross;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * 为女仆维持铁魔法的雷暴与神圣守护效果。
 */
public class ArcCrossBauble implements IMaidBauble {
    private static final int REFRESH_INTERVAL = 40;
    private static final int EFFECT_DURATION = 100;
    private static final int EFFECT_AMPLIFIER = 9;

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide || !maid.isAlive() || maid.tickCount % REFRESH_INTERVAL != 0) {
            return;
        }

        refreshEffect(maid, MobEffectRegistry.THUNDERSTORM);
        refreshEffect(maid, MobEffectRegistry.FORTIFY);
    }

    private static void refreshEffect(EntityMaid maid, Holder<MobEffect> effect) {
        maid.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, EFFECT_AMPLIFIER, false, true, true));
    }
}
