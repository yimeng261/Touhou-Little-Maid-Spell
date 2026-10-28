package com.github.yimeng261.maidspell.mixin.usefulmagic;

import cn.coostack.usefulmagic.meteorite.MeteoriteImpactHelper;
import com.github.yimeng261.maidspell.compat.usefulmagic.UsefulMagicTerrainProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

/** 陨石、星陨与爆裂魔法的分批破坏按当前配置执行。 */
@Mixin(value = MeteoriteImpactHelper.class, remap = false)
public abstract class MeteoriteImpactHelperMixin {
    @Inject(method = "processPendingExplosion", at = @At("HEAD"), cancellable = true)
    private void maidspell$protectTerrain(ServerLevel world, ArrayDeque<BlockPos> pendingBlocks,
                                          int blocksPerTick, LivingEntity source, CallbackInfo ci) {
        if (UsefulMagicTerrainProtection.blocksTerrainDamage(source)) {
            // 调用方可能循环处理直到队列为空，禁止破坏时也必须结束队列。
            pendingBlocks.clear();
            ci.cancel();
        }
    }
}
