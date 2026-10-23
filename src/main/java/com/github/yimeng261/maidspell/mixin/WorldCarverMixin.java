package com.github.yimeng261.maidspell.mixin;

import com.github.yimeng261.maidspell.worldgen.CarverGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 雕刻器把受保护结构范围里的格子当作已经挖过，跳过不挖，见 {@link CarverGuard}。
 */
@Mixin(WorldCarver.class)
public abstract class WorldCarverMixin {

    @WrapOperation(method = "carveEllipsoid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/CarvingMask;get(III)Z"))
    private boolean maidspell$skipProtectedStructures(CarvingMask mask, int x, int y, int z, Operation<Boolean> original,
                                                      @Local(argsOnly = true) ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        return CarverGuard.blocked(pos.getBlockX(x), y, pos.getBlockZ(z)) || original.call(mask, x, y, z);
    }
}
