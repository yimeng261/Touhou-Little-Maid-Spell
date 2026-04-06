package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.ModEntityType;
import com.Polarice3.Goety.common.entities.neutral.ZPiglinServant;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.SummonSpell;
import com.Polarice3.Goety.common.magic.spells.necromancy.ZombieSpell;
import com.Polarice3.Goety.utils.MathHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆光环：腐烂聚晶 → 僵尸猪灵蛮兵仆从（ZPiglinServant）
 * 支持终末之环和晋升之环
 * 召唤2~4只僵尸猪灵蛮兵，50%概率召唤合金全装+迅捷II精锐版本
 */
@Mixin(ZombieSpell.class)
public abstract class ZombieSpellMixin extends SummonSpell {

    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createZPiglinServant(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {
        if (BaubleStateManager.hasMaidWithHalo(caster)) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            int duration = spellStat.getDuration();

            // 召唤数量：2~4只
            int count = 2 + caster.getRandom().nextInt(3);

            for (int i = 0; i < count; i++) {
                // 在女仆周围随机位置生成
                BlockPos blockpos = caster.blockPosition().offset(
                    -2 + caster.getRandom().nextInt(5),
                    1,
                    -2 + caster.getRandom().nextInt(5)
                );

                // 创建僵尸猪灵蛮兵仆从（ZPiglinServant）
                ZPiglinServant zpiglin = new ZPiglinServant(ModEntityType.ZPIGLIN_SERVANT.get(), worldIn);
                zpiglin.setTrueOwner(maid);
                Vec3 spawnPos = Vec3.atBottomCenterOf(blockpos);
                zpiglin.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
                zpiglin.finalizeSpawn(worldIn, caster.level().getCurrentDifficultyAt(blockpos),
                    MobSpawnType.MOB_SUMMONED, null, null);

                // 设置生命周期
                zpiglin.setLimitedLife(MathHelper.minutesToTicks(1) * duration);

                // 50%概率装备合金全装并添加迅捷II效果
                if (caster.getRandom().nextFloat() < 0.5F) {
                    // 装备下界合金装备
                    zpiglin.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                    zpiglin.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    zpiglin.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
                    zpiglin.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
                    zpiglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));

                    // 添加无限时长迅捷II效果
                    zpiglin.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 1));
                }

                // 设置目标
                this.setTarget(caster, zpiglin);

                worldIn.addFreshEntity(zpiglin);
                this.summonAdvancement(caster, zpiglin);
            }

            worldIn.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT, this.getSoundSource(), 1.0F, 1.0F);
        }
    }
}
