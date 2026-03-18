package com.github.yimeng261.maidspell.item.bauble.unholyHat;

import java.util.function.BiFunction;

import com.Polarice3.Goety.utils.ModDamageSource;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 不洁圣冠饰品逻辑
 *
 * 女仆装备效果：
 * - 防火能力
 * - 狱火伤害额外 85% 抗性
 * - 下界维度伤害减半
 * - 限伤：单次伤害不超过 20 点
 * - 免疫灼烧诅咒（Goety 的 BURN_HEX 效果）
 * - 渲染头顶光环（使徒同款）
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID)
public class UnholyHatBauble implements IMaidBauble {

    private static boolean initialized = false;

    /**
     * 初始化不洁圣冠的所有效果
     * 在 MaidBaubleRegistry 中调用
     */
    public static void init() {
        if (initialized) return;

        Item unholyHat = MaidSpellItems.getUnholyHat();
        Item unholyHatHalo = MaidSpellItems.getUnholyHatHalo();

        if (unholyHat == null && unholyHatHalo == null) {
            Global.LOGGER.warn("Goety 未加载，无法初始化不洁圣冠女仆效果");
            return;
        }

        initialized = true;
        Global.LOGGER.info("正在初始化不洁圣冠女仆效果...");

        // 创建伤害处理器（两个物品共用同一个处理器）
        BiFunction<LivingHurtEvent, EntityMaid, Void> hurtHandler = (event, maid) -> {
            DamageSource source = event.getSource();
            float amount = event.getAmount();

            // 狱火伤害抗性：额外 85% 抗性
            if (ModDamageSource.hellfireAttacks(source)) {
                amount *= 0.15F; // 15% 伤害 = 85% 抗性
            }

            // 下界维度伤害减半
            if (maid.level().dimension() == net.minecraft.world.level.Level.NETHER) {
                amount *= 0.5F;
            }

            // 限伤：单次伤害不超过 20 点
            // 注意：这里优先级高于其他减伤，与 Goety 原版逻辑一致
            if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                amount = Math.min(amount, 20.0F);
            }

            event.setAmount(amount);
            return null;
        };

        // 注册两个物品的伤害处理器
        if (unholyHat != null) {
            Global.baubleHurtHandlers.put(unholyHat, hurtHandler);
        }
        if (unholyHatHalo != null) {
            Global.baubleHurtHandlers.put(unholyHatHalo, hurtHandler);
        }

        Global.LOGGER.info("不洁圣冠女仆效果初始化完成");
    }

    /**
     * 检查女仆是否装备了不洁圣冠（任一版本）
     */
    private static boolean hasUnholyHat(EntityMaid maid) {
        var unholyHat = MaidSpellItems.getUnholyHat();
        var unholyHatHalo = MaidSpellItems.getUnholyHatHalo();

        boolean hasHat = unholyHat != null && BaubleStateManager.hasBauble(maid, unholyHat);
        boolean hasHalo = unholyHatHalo != null && BaubleStateManager.hasBauble(maid, unholyHatHalo);

        return hasHat || hasHalo;
    }

    /**
     * 免疫灼烧诅咒
     * 使用 Applicable 事件，在效果应用前进行拦截
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        if (!hasUnholyHat(maid)) {
            return;
        }

        MobEffect effect = event.getEffectInstance().getEffect();

        // 检查是否为灼烧诅咒
        // Goety 的灼烧诅咒效果 ID 为 goety:burn_hex
        String effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect).toString();

        if ("goety:burn_hex".equals(effectId)) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /**
     * 防火能力
     * 免疫所有火焰伤害（最高优先级）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        if (!hasUnholyHat(maid)) {
            return;
        }

        // 检查是否为火焰伤害
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            // 不免疫无法豁免的伤害（如虚空、创造模式等）
            if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
            }
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
        // 装备时触发
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        // 卸下时触发
    }
}
