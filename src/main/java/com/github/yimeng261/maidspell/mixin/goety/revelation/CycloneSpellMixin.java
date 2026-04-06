package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.enchantments.ModEnchantments;
import com.Polarice3.Goety.common.entities.projectiles.FireTornado;
import com.Polarice3.Goety.common.magic.Spell;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.spells.wind.CycloneSpell;
import com.Polarice3.Goety.init.ModSounds;
import com.Polarice3.Goety.utils.WandUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：飓风聚晶 → 燃烧龙卷风（FireTornado）
 * 支持终末之环和晋升之环
 * 参考 revelationfix 的 CycloneSpellMixin
 */
@Mixin(CycloneSpell.class)
public abstract class CycloneSpellMixin extends Spell {


    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createFireTornado(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (BaubleStateManager.hasMaidWithHalo(caster)) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            FireTornado cyclone;
            float size = 2.0F;
            float damage = 7.0F;

            if (this.rightStaff(staff)) {
                ++size;
                ++damage;
            }

            // 使用女仆自己的目标系统
            LivingEntity maidTarget = maid.getTarget();
            if (maidTarget != null) {
                Vec3 vector3d = caster.getViewVector(1.0F);
                cyclone = new FireTornado(
                    worldIn,
                    caster.getX() + vector3d.x / 2.0,
                    caster.getEyeY() - 0.2,
                    caster.getZ() + vector3d.z / 2.0,
                    vector3d.x,
                    vector3d.y,
                    vector3d.z
                );
                cyclone.setTarget(maidTarget);
            } else {
                // 否则在女仆位置生成
                cyclone = new FireTornado(worldIn, maid, maid.getX(), maid.getY(), maid.getZ());
            }

            // 设置所有者为女仆本身
            cyclone.setOwnerId(maid.getUUID());
            cyclone.setOwner(maid);

            // 设置属性
            cyclone.setDamage(damage * (float) (WandUtil.getLevels(ModEnchantments.POTENCY.get(), caster) + 1));
            cyclone.setTotalLife(2400 * (WandUtil.getLevels(ModEnchantments.DURATION.get(), caster) + 1));
            cyclone.setBoltSpeed(WandUtil.getLevels(ModEnchantments.VELOCITY.get(), caster));
            cyclone.setSize(size + (float) WandUtil.getLevels(ModEnchantments.RADIUS.get(), caster) / 10.0F);

            worldIn.addFreshEntity(cyclone);
            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                ModSounds.APOSTLE_PREPARE_SPELL.get(), this.getSoundSource(), 1.0F, 1.0F);
        }
    }
}
