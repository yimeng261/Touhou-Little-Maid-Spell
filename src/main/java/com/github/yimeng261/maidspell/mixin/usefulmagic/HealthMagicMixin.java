package com.github.yimeng261.maidspell.mixin.usefulmagic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.utils.UsefulMagicHealSupport;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.coostack.usefulmagic.items.weapon.magic.HealthMagic;
import cn.coostack.usefulmagic.sounds.UsefulMagicSoundEvents;
import cn.coostack.usefulmagic.utils.MagicHelper;

/**
 * 让女仆施放的生命法术改为治疗「自己 + 主人」，而非走原版的友军 AOE。
 *
 * <p>原 {@code HealthMagic.onRelease} 用 {@code filterFriend(shooter, it)} 选治疗对象，
 * 而 UsefulMagic 的友军判定对非玩家恒 false（见 {@code FriendFilterHelperMixin}），女仆放它谁都治不到、纯空放。
 * 这里在 shooter 为女仆时接管：用同一套伤害数值({@code MagicHelper.getMagicDamage})与音效，
 * 治疗女仆自己以及范围内(≤32 格)存活的主人，并加原版同款 REGENERATION/DAMAGE_RESISTANCE。
 * 非女仆 shooter 不拦截，保持原逻辑。
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-07-17
 */
@Mixin(value = HealthMagic.class, remap = false)
public class HealthMagicMixin {

    @Inject(
            method = "onRelease(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void maidspell$healSelfAndOwner(LivingEntity shooter, Level world, ItemStack wandStack, ItemStack ballStack, int time, CallbackInfo ci) {
        if (!(shooter instanceof EntityMaid maid)) {
            return;
        }
        ci.cancel();
        if (world.isClientSide) {
            return;
        }
        float healAmount = (float) Math.abs(MagicHelper.INSTANCE.getMagicDamage(wandStack));

        maidspell$applyHeal(maid, healAmount);

        LivingEntity owner = UsefulMagicHealSupport.ownerInHealRange(maid);
        if (owner != null) {
            maidspell$applyHeal(owner, healAmount);
        }

        world.playSound(null, maid.blockPosition(),
                UsefulMagicSoundEvents.getMAGIC_EXTEND().get(), maid.getSoundSource(), 10.0f, 0.7f);
    }

    private static void maidspell$applyHeal(LivingEntity entity, float healAmount) {
        if (healAmount > 0.0f) {
            entity.heal(healAmount);
        }
        // 与原版一致的持续恢复 + 减伤增益。
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1000, 2));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1000, 1));
    }
}
