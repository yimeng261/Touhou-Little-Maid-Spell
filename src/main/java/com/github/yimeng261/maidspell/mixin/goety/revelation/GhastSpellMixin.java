package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.ModEntityType;
import com.Polarice3.Goety.common.entities.hostile.servants.Malghast;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.SummonSpell;
import com.Polarice3.Goety.common.magic.spells.nether.GhastSpell;
import com.Polarice3.Goety.config.SpellConfig;
import com.Polarice3.Goety.utils.EffectsUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：恶魂聚晶 → 凶魂（Malghast）
 * 支持终末之环和晋升之环
 * 使用@Inject避免与revelationfix的@Redirect冲突
 */
@Mixin(value = GhastSpell.class, priority = 1100)
public abstract class GhastSpellMixin extends SummonSpell {

    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createMalghast(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (BaubleStateManager.hasMaidWithHalo(caster)) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            // 在女仆周围随机位置生成
            BlockPos blockpos = caster.blockPosition().offset(
                -2 + caster.getRandom().nextInt(5),
                1,
                -2 + caster.getRandom().nextInt(5)
            );

            // 创建凶魂（Malghast）
            Malghast malghast = new Malghast(ModEntityType.MALGHAST.get(), worldIn);
            malghast.setTrueOwner(maid);
            Vec3 spawnPos = Vec3.atBottomCenterOf(blockpos);
            malghast.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, caster.getYRot(), 0.0F);
            malghast.finalizeSpawn(worldIn, caster.level().getCurrentDifficultyAt(blockpos),
                MobSpawnType.MOB_SUMMONED, null, null);

            // 应用召唤消耗
            this.SummonSap(caster, malghast);

            // 设置火球伤害
            int boost = Mth.clamp(spellStat.potency, 0, 10);
            malghast.setFireBallDamage((float) (boost - EffectsUtil.getAmplifierPlus(caster, MobEffects.WEAKNESS)));

            // 设置爆炸威力
            float extraBlast = (float) Mth.clamp(spellStat.potency, 0, SpellConfig.MaxRadiusLevel.get()) / 2.5F;
            malghast.setExplosionPower(malghast.getExplosionPower() + extraBlast);

            // 设置生命周期（使用默认值）
            malghast.setLimitedLife(6000);

            // 设置目标
            this.setTarget(caster, malghast);

            worldIn.addFreshEntity(malghast);
            this.summonAdvancement(caster, malghast);
        }
    }
}
