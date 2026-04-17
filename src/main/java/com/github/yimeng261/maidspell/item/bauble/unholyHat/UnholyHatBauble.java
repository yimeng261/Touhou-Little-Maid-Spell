package com.github.yimeng261.maidspell.item.bauble.unholyHat;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import za.co.infernos.goety.common.effects.GoetyEffects;
import za.co.infernos.goety.utils.ModDamageSource;

/**
 * 不洁圣冠饰品逻辑
 * <p>
 * 女仆装备效果：
 * - 防火能力
 * - 狱火伤害额外 85% 抗性
 * - 下界维度伤害减半
 * - 限伤：单次伤害不超过 20 点
 * - 免疫灼烧诅咒（Goety 的 BURN_HEX 效果）
 * - 渲染头顶光环（使徒同款）
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class UnholyHatBauble implements IMaidBauble {

    private static boolean initialized = false;

    /**
     * 初始化不洁圣冠的所有效果
     * 在 MaidBaubleRegistry 中调用
     */
    public static void init() {
        if (initialized) return;

        Item unholyHat = MaidSpellItems.getUnholyHat();

        if (unholyHat == null) {
            Global.LOGGER.warn("Goety 未加载，无法初始化不洁圣冠女仆效果");
            return;
        }

        initialized = true;
        Global.LOGGER.info("正在初始化不洁圣冠女仆效果...");

        // 伤害处理器
        Global.baubleHurtHandlers.put(unholyHat, (event, maid) -> {
            DamageSource source = event.getSource();
            float amount = event.getAmount();

            // 狱火伤害抗性：额外 85% 抗性
            if (ModDamageSource.hellfireAttacks(source)) {
                amount *= 0.15F;
            }

            // 下界维度伤害减半
            if (maid.level().dimension() == net.minecraft.world.level.Level.NETHER) {
                amount *= 0.5F;
            }

            // 限伤：单次伤害不超过 20 点
            if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                amount = Math.min(amount, 20.0F);
            }

            event.setAmount(amount);
            return null;
        });

        Global.LOGGER.info("不洁圣冠女仆效果初始化完成");
    }

    private static boolean hasUnholyHat(EntityMaid maid) {
        var unholyHat = MaidSpellItems.getUnholyHat();
        return unholyHat != null && BaubleStateManager.hasBauble(maid, unholyHat);
    }

    /**
     * 免疫灼烧诅咒（Goety 的 BURN_HEX 效果）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        if (!hasUnholyHat(maid)) {
            return;
        }

        // 直接引用 Goety 3 的 BURN_HEX 效果进行比较
        if (event.getEffectInstance().getEffect() == GoetyEffects.BURN_HEX) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @Override
    public boolean syncClient(EntityMaid maid, ItemStack baubleItem) {
        return true;
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide) {
            return;
        }

        // 每 tick 移除火焰效果（额外防火）
        if (maid.isOnFire()) {
            maid.clearFire();
        }
    }

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
    }
}
