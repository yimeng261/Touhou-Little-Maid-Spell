package com.github.yimeng261.maidspell.mixin.usefulmagic;

import cn.coostack.usefulmagic.items.weapon.magic.useful.DiggingMagic;
import com.github.yimeng261.maidspell.compat.usefulmagic.UsefulMagicTerrainProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 挖掘魔法的射线仍能伤害实体，受保护时不推进方块挖掘。 */
@Mixin(value = DiggingMagic.class, remap = false)
public abstract class DiggingMagicMixin {
    @Shadow
    private void clearDiggingState(LivingEntity shooter, Level world) {
    }

    @Inject(method = "tickDiggingState", at = @At("HEAD"), cancellable = true)
    private void maidspell$protectTerrain(LivingEntity shooter, ServerLevel world, ItemStack wand,
                                          ItemStack ball, BlockHitResult clip, int time, CallbackInfo ci) {
        if (UsefulMagicTerrainProtection.blocksTerrainDamage(shooter)) {
            this.clearDiggingState(shooter, world);
            ci.cancel();
        }
    }
}
