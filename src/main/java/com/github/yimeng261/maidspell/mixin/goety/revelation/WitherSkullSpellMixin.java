package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.enchantments.ModEnchantments;
import com.Polarice3.Goety.common.entities.projectiles.NetherMeteor;
import com.Polarice3.Goety.common.magic.Spell;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.spells.nether.WitherSkullSpell;
import com.Polarice3.Goety.utils.WandUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mega.revelationfix.common.entity.projectile.StarArrow;
import com.mega.revelationfix.common.init.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：凋灵聚晶转化
 * - 晋升之环：凋灵聚晶 → 下界流星（NetherMeteor）
 * - 终末之环：凋灵聚晶 → 星辰箭矢（StarArrow）
 * 参考 revelationfix 的 WitherSkullSpellMixin
 */
@Mixin(WitherSkullSpell.class)
public abstract class WitherSkullSpellMixin extends Spell {

    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createProjectile(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (!(caster instanceof EntityMaid maid)) {
            return;
        }

        LivingEntity maidOwner = maid.getOwner();
        if (maidOwner == null) {
            return;
        }

        // 晋升之环：发射下界流星
        if (BaubleStateManager.hasMaidWithAscensionHalo(caster)) {
            ci.cancel();

            Vec3 vector3d = caster.getViewVector(1.0F);
            float extraBlast = (float) WandUtil.getLevels(ModEnchantments.RADIUS.get(), caster) / 2.5F;

            NetherMeteor netherMeteor = new NetherMeteor(
                caster.level(),
                caster.getX() + vector3d.x / 2.0,
                caster.getEyeY() - 0.2,
                caster.getZ() + vector3d.z / 2.0,
                vector3d.x,
                vector3d.y,
                vector3d.z
            );
            netherMeteor.setOwner(maid);

            if (this.isShifting(caster)) {
                netherMeteor.setDangerous(true);
            }

            netherMeteor.setExtraDamage((float) WandUtil.getLevels(ModEnchantments.POTENCY.get(), caster));
            netherMeteor.setFiery(WandUtil.getLevels(ModEnchantments.BURNING.get(), caster));
            netherMeteor.setExplosionPower(netherMeteor.getExplosionPower() + extraBlast);

            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BLAZE_SHOOT, this.getSoundSource(), 1.0F, 1.0F);

            worldIn.addFreshEntity(netherMeteor);
        }
        // 终末之环：发射星辰箭矢
        else if (BaubleStateManager.hasMaidWithHaloOfTheEnd(caster)) {
            ci.cancel();

            StarArrow starArrow = new StarArrow(worldIn, caster,
                caster.getLookAngle().x * 4,
                caster.getLookAngle().y * 4,
                caster.getLookAngle().z * 4);
            // 设置所有者为女仆的主人，这样碰撞检测和伤害计算才能正确工作
            starArrow.setOwner(maidOwner);

            // 设置发射位置
            Vec3 shootPos = caster.getEyePosition().add(caster.getHandHoldingItemAngle(staff.getItem()));
            starArrow.setPosRaw(shootPos.x, shootPos.y, shootPos.z);
            starArrow.setAlpha(1F);

            // 如果潜行，设置为危险模式
            if (this.isShifting(caster)) {
                starArrow.setDangerous(true);
            }

            // 设置目标追踪
            if (rayTrace(worldIn, caster, 64, 3) instanceof EntityHitResult entityHitResult) {
                starArrow.setTarget(entityHitResult.getEntity());
            }

            // 设置伤害倍率
            starArrow.setDamageMultiplier((float) WandUtil.getLevels(ModEnchantments.POTENCY.get(), caster) / 10F + 1F);

            // 播放音效
            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                ModSounds.STAR_CAST.get(), this.getSoundSource(), 1.0F, 1.0F);

            worldIn.addFreshEntity(starArrow);
        }
    }
}
