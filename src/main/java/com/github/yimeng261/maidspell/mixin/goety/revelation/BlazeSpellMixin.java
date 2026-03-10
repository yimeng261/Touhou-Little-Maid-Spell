package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.ModEntityType;
import com.Polarice3.Goety.common.entities.hostile.servants.Inferno;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.SummonSpell;
import com.Polarice3.Goety.common.magic.spells.nether.BlazeSpell;
import com.Polarice3.Goety.utils.MathHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：燃烧聚晶 → 狱焰（Inferno）
 * 支持终末之环和晋升之环
 */
@Mixin(BlazeSpell.class)
public abstract class BlazeSpellMixin extends SummonSpell {

    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createInferno(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (BaubleStateManager.hasMaidWithHalo(caster)) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            int duration = spellStat.getDuration();

            // 在女仆周围随机位置生成
            BlockPos blockpos = caster.blockPosition().offset(
                -2 + caster.getRandom().nextInt(5),
                1,
                -2 + caster.getRandom().nextInt(5)
            );

            // 创建狱焰（Inferno）
            Inferno inferno = new Inferno(ModEntityType.INFERNO.get(), worldIn);
            inferno.setTrueOwner(maid);
            Vec3 spawnPos = Vec3.atBottomCenterOf(blockpos);
            inferno.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
            inferno.finalizeSpawn(worldIn, caster.level().getCurrentDifficultyAt(blockpos),
                MobSpawnType.MOB_SUMMONED, null, null);

            // 设置生命周期
            inferno.setLimitedLife(MathHelper.minutesToTicks(1) * duration);

            // 设置目标
            this.setTarget(caster, inferno);

            worldIn.addFreshEntity(inferno);
            this.summonAdvancement(caster, inferno);

            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BLAZE_AMBIENT, this.getSoundSource(), 1.0F, 1.0F);
        }
    }
}
