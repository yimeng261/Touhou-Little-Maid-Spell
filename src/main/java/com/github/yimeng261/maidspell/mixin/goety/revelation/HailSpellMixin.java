package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.projectiles.HellCloud;
import com.Polarice3.Goety.common.magic.Spell;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.spells.frost.HailSpell;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 晋升之环女仆：冰雹云 -> 狱云
 * - 范围扩大为原始冰雹云的 4 倍
 * - 生命周期翻倍
 * - 狱云会在后续 mixin 中追踪敌人
 */
@Mixin(HailSpell.class)
public abstract class HailSpellMixin extends Spell {

    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createHellCloud(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (!BaubleStateManager.hasMaidWithAscensionHalo(caster)) {
            return;
        }

        ci.cancel();

        int range = spellStat.getRange();
        int duration = spellStat.getDuration();
        float extraDamage = (float) spellStat.getPotency();
        float radius = (float) spellStat.getRadius();

        HitResult hitResult = this.rayTrace(worldIn, caster, range, spellStat.getRadius());
        LivingEntity target = this.getTarget(caster, range);

        if (target != null) {
            HellCloud hellCloud = this.maidSpell$createHellCloud(worldIn, caster, target, extraDamage, radius, duration);
            worldIn.addFreshEntity(hellCloud);
            this.playSound(worldIn, caster, SoundEvents.PLAYER_HURT_FREEZE);
            return;
        }

        if (hitResult instanceof BlockHitResult blockHitResult) {
            HellCloud hellCloud = this.maidSpell$createHellCloud(worldIn, caster, null, extraDamage, radius, duration);
            BlockPos blockPos = blockHitResult.getBlockPos();
            hellCloud.setPos(blockPos.getX() + 0.5D, blockPos.getY() + 4.0D, blockPos.getZ() + 0.5D);
            worldIn.addFreshEntity(hellCloud);
            this.playSound(worldIn, caster, SoundEvents.PLAYER_HURT_FREEZE);
        }
    }

    @Unique
    private HellCloud maidSpell$createHellCloud(ServerLevel worldIn, LivingEntity caster, LivingEntity target, float extraDamage, float radius, int duration) {
        HellCloud hellCloud = new HellCloud(worldIn, caster, target);
        hellCloud.setExtraDamage(extraDamage);
        hellCloud.setRadius(radius * 4.0F);
        hellCloud.setLifeSpan(duration * 2);
        // 使用 staff 标记这类由冰雹云转化而来的狱云，供追踪和伤害逻辑识别。
        hellCloud.setStaff(true);
        return hellCloud;
    }
}
