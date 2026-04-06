package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.effects.GoetyEffects;
import com.Polarice3.Goety.common.entities.ModEntityType;
import com.Polarice3.Goety.common.entities.hostile.servants.Damned;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.SummonSpell;
import com.Polarice3.Goety.common.magic.spells.necromancy.HauntedSkullSpell;
import com.Polarice3.Goety.utils.EffectsUtil;
import com.Polarice3.Goety.utils.MathHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：飞颅聚晶 → 狱魂（Damned）
 * 支持终末之环和晋升之环
 * 参考 AllTitlesApostle 的 HauntedSkullSpellMixin
 */
@Mixin(HauntedSkullSpell.class)
public abstract class HauntedSkullSpellMixin extends SummonSpell {

    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createDamned(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (BaubleStateManager.hasMaidWithHalo(caster)) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            int potency = spellStat.getPotency();
            int duration = spellStat.getDuration();

            // 召唤1个狱魂
            int count = 2;

            for (int i = 0; i < count; i++) {
                // 在女仆周围随机位置生成
                BlockPos blockpos = caster.blockPosition().offset(
                    -2 + caster.getRandom().nextInt(5),
                    1,
                    -2 + caster.getRandom().nextInt(5)
                );

                // 创建狱魂（Damned）
                Damned damned = new Damned(ModEntityType.DAMNED.get(), worldIn);
                damned.setTrueOwner(maid);
                Vec3 spawnPos = Vec3.atBottomCenterOf(blockpos);
                damned.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
                damned.finalizeSpawn(worldIn, caster.level().getCurrentDifficultyAt(blockpos),
                    MobSpawnType.MOB_SUMMONED, (SpawnGroupData) null, (CompoundTag) null);

                // 设置生命周期
                damned.setLimitedLife(MathHelper.minutesToTicks(1) * duration);

                // 应用增益效果
                if (potency > 0) {
                    int boost = Mth.clamp(potency - 1, 0, 10);
                    damned.addEffect(new MobEffectInstance(GoetyEffects.BUFF.get(),
                        EffectsUtil.infiniteEffect(), boost));
                }

                // 设置目标
                this.setTarget(caster, damned);

                worldIn.addFreshEntity(damned);
                this.summonAdvancement(caster, damned);
            }

            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, this.getSoundSource(), 1.0F, 1.0F);
        }
    }
}
