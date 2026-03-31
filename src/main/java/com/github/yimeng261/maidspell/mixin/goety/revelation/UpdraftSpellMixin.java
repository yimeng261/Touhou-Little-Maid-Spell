package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.util.FireBlastTrap;
import com.Polarice3.Goety.common.magic.Spell;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.spells.wind.UpdraftSpell;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：上升气流聚晶 → 炎爆陷阱（FireBlastTrap）
 * 支持终末之环和晋升之环
 * 伤害设置为40点
 * 使用射线检测确定生成位置
 */
@Mixin(UpdraftSpell.class)
public abstract class UpdraftSpellMixin extends Spell {


    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createFireBlastTrap(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (BaubleStateManager.hasMaidWithHalo(caster)) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            // 射线检测目标位置
            HitResult rayTraceResult = this.rayTrace(worldIn, caster, 24, 3.0);

            BlockPos blockPos = null;

            // 处理方块检测结果
            if (rayTraceResult instanceof BlockHitResult blockHitResult) {
                blockPos = blockHitResult.getBlockPos();
            }else if (rayTraceResult instanceof EntityHitResult) {
                Vec3 hitPos = rayTraceResult.getLocation();
                blockPos = new BlockPos((int)Math.floor(hitPos.x), (int)Math.floor(hitPos.y) - 1, (int)Math.floor(hitPos.z));
            }

            // 如果没有有效的位置，使用女仆脚下位置
            if (blockPos == null) {
                blockPos = caster.blockPosition();
            }

            // 创建炎爆陷阱（FireBlastTrap）
            FireBlastTrap trap = new FireBlastTrap(worldIn, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);

            trap.setOwner(maid);
            trap.setExtraDamage(40.0F);
            trap.setAreaOfEffect(7.5F);

            worldIn.addFreshEntity(trap);

            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BLAZE_SHOOT, this.getSoundSource(), 1.0F, 1.0F);
        }
    }
}
