package com.github.yimeng261.maidspell.mixin.goety;

import com.Polarice3.Goety.common.entities.hostile.servants.ObsidianMonolith;
import com.Polarice3.Goety.common.entities.neutral.AbstractMonolith;
import com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith;
import com.Polarice3.Goety.common.entities.neutral.Owned;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆黑曜石巨柱增益效果
 * 参考 revelationfix 的 AbstractObsidianMonolithMixin
 * 支持终末之环和晋升之环
 *
 * 晋升之环：为女仆提供无敌和生命恢复1效果，45秒生命周期
 * 终末之环：为女仆提供增益效果
 *
 * 注意：Mixin 到 AbstractObsidianMonolith 类（aiStep() 方法在此类中定义）
 * 使用实例检查确保只处理 ObsidianMonolith 类型
 */
@Mixin(value = AbstractObsidianMonolith.class, priority = 1100)
public abstract class ObsidianMonolithMaidMixin extends AbstractMonolith {

    @Unique
    private int maidSpell$maidOwnTick = 20 * 45; // 45 秒生命周期

    ObsidianMonolithMaidMixin(EntityType<? extends Owned> type, Level worldIn) {
        super(type, worldIn);
    }

    /**
     * 检查女仆是否装备晋升之环
     */
    @Unique
    private boolean maidSpell$hasMaidWithAscensionHalo(LivingEntity owner) {
        if (!(owner instanceof EntityMaid maid)) {
            return false;
        }
        var ascensionHalo = MaidSpellItems.getAscensionHalo();
        return ascensionHalo != null && BaubleStateManager.hasBauble(maid, ascensionHalo);
    }


    @Inject(method = "aiStep", at = @At("RETURN"))
    private void maidSpell$provideMaidBenefits(CallbackInfo ci) {
        // 只处理 ObsidianMonolith 实例（不处理其他可能的 AbstractObsidianMonolith 子类）
        if (!((Object) this instanceof ObsidianMonolith)) {
            return;
        }

        if (!this.isEmerging() && !this.level().isClientSide) {
            LivingEntity owner = this.getTrueOwner();

            // 只处理女仆拥有的巨柱
            if (owner instanceof EntityMaid maid) {
                boolean hasAscensionHalo = maidSpell$hasMaidWithAscensionHalo(maid);

                if (hasAscensionHalo) {
                    // 晋升之环：45秒生命周期限制
                    if (--this.maidSpell$maidOwnTick <= 0) {
                        this.discard();
                        return;
                    }

                    // 为女仆提供无敌效果（每 tick 设置 10 tick 无敌）
                    maid.invulnerableTime = 10;

                    // 为女仆提供持续回血
                    // 晋升之环：生命恢复1效果（每 50 tick 回复 1 HP）
                    if (hasAscensionHalo) {
                        if (maid.tickCount % 50 == 0) {
                            maid.heal(1.0F);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        // 女仆拥有的巨柱在和平模式下不消失
        if (this.getTrueOwner() instanceof EntityMaid) {
            return false;
        }
        return super.shouldDespawnInPeaceful();
    }
}
