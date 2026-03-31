package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.projectiles.HellBolt;
import com.Polarice3.Goety.common.magic.Spell;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.spells.nether.FireballSpell;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：火球/熔岩炸弹聚晶 → 狱火束（HellBolt）
 * 支持终末之环和晋升之环
 * 参考 revelationfix 的 FireballSpellMixin
 */
@Mixin(FireballSpell.class)
public abstract class FireballSpellMixin extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();


    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createHellBolt(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (BaubleStateManager.hasMaidWithHalo(caster)) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            Vec3 vector3d = caster.getViewVector(1.0F);

            // 创建狱火束
            HellBolt hellBolt = new HellBolt(
                caster.getX() + vector3d.x / 2.0,
                caster.getEyeY() - 0.2,
                caster.getZ() + vector3d.z / 2.0,
                vector3d.x,
                vector3d.y,
                vector3d.z,
                worldIn
            );
            // 设置所有者为女仆本身
            hellBolt.setOwner(maid);

            // 播放音效
            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BLAZE_SHOOT, this.getSoundSource(), 1.0F, 1.0F);

            worldIn.addFreshEntity(hellBolt);
        }
    }
}
