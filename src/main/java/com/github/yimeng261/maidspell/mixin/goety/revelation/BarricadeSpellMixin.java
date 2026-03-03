package com.github.yimeng261.maidspell.mixin.goety.revelation;

import com.Polarice3.Goety.common.entities.ModEntityType;
import com.Polarice3.Goety.common.entities.hostile.servants.ObsidianMonolith;
import com.Polarice3.Goety.common.magic.Spell;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.common.magic.spells.geomancy.BarricadeSpell;
import com.Polarice3.Goety.config.AttributesConfig;
import com.Polarice3.Goety.utils.MobUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.spell.manager.MaidMonolithManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * 女仆光环：障碍聚晶 → 黑曜石巨柱
 * 支持终末之环和晋升之环
 * 参考 revelationfix 的 BarricadeSpellMixin
 *
 * 女仆召唤的巨柱主人为女仆自己，并提供女仆增益效果
 * - 晋升之环：每个女仆只能拥有 1 个巨柱
 * - 终末之环：每个女仆最多拥有 5 个巨柱
 */
@Mixin(BarricadeSpell.class)
public abstract class BarricadeSpellMixin extends Spell {



    @Inject(at = @At("HEAD"), method = "SpellResult", cancellable = true, remap = false)
    private void maidSpell$createObsidianMonolith(ServerLevel worldIn, LivingEntity caster, ItemStack staff, SpellStat spellStat, CallbackInfo ci) {

        boolean hasAscensionHalo = BaubleStateManager.hasMaidWithAscensionHalo(caster);
        boolean hasHaloOfTheEnd = BaubleStateManager.hasMaidWithHaloOfTheEnd(caster);

        if (hasAscensionHalo || hasHaloOfTheEnd) {
            EntityMaid maid = (EntityMaid) caster;

            ci.cancel();

            // 射线检测目标位置
            HitResult rayTraceResult = this.rayTrace(worldIn, caster, 24, 3.0);

            BlockPos blockPos = null;

            // 处理方块检测结果
            if (rayTraceResult instanceof BlockHitResult blockHitResult) {
                blockPos = blockHitResult.getBlockPos();
            }
            // 处理实体检测结果 - 在实体脚下生成（但不在黑曜石巨柱脚下生成）
            else if (rayTraceResult instanceof EntityHitResult entityHitResult) {
                var hitEntity = entityHitResult.getEntity();
                // 只有当击中的实体不是黑曜石巨柱时，才在其脚下生成
                if (!(hitEntity instanceof ObsidianMonolith)) {
                    Vec3 hitPos = rayTraceResult.getLocation();
                    blockPos = new BlockPos((int)Math.floor(hitPos.x), (int)Math.floor(hitPos.y) - 1, (int)Math.floor(hitPos.z));
                }
            }

            if (blockPos != null) {
                blockPos = blockPos.offset(0, 1, 0);
                Vec3 vec3 = Vec3.atBottomCenterOf(blockPos);

                // 创建黑曜石巨柱
                ObsidianMonolith monolith = ModEntityType.OBSIDIAN_MONOLITH.get().create(worldIn);
                if (monolith != null) {
                    // 设置所有者为女仆自己
                    monolith.setTrueOwner(maid);
                    monolith.setPos(vec3.x(), vec3.y(), vec3.z());

                    // 初始化生成
                    monolith.finalizeSpawn(worldIn, worldIn.getCurrentDifficultyAt(blockPos),
                        MobSpawnType.MOB_SUMMONED, null, null);

                    // 设置血量为200
                    MobUtil.setBaseAttributes(monolith.getAttribute(Attributes.MAX_HEALTH),
                        Math.max(200.0F, AttributesConfig.ObsidianMonolithHealth.get()));
                    monolith.setHealth(monolith.getMaxHealth());

                    worldIn.addFreshEntity(monolith);

                    // 添加到女仆的巨柱管理器
                    // 晋升之环：最多1个巨柱
                    // 终末之环：最多5个巨柱
                    if (hasAscensionHalo) {
                        MaidMonolithManager.addMonolith(maid, monolith, 1);
                    } else {
                        MaidMonolithManager.addMonolith(maid, monolith, 5);
                    }
                }
            }
        }
    }
}
