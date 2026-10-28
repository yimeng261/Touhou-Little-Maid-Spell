package com.github.yimeng261.maidspell.mixin.usefulmagic;

import cn.coostack.usefulmagic.barrages.entity.skill.GiantSwordLightBarrageMagic;
import cn.coostack.usefulmagic.utils.FallingBlockHelper;
import com.github.yimeng261.maidspell.compat.usefulmagic.UsefulMagicTerrainProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** 巨剑命中时保留伤害与音效，不把受保护的地面转为掉落方块。 */
@Mixin(value = GiantSwordLightBarrageMagic.class, remap = false)
public abstract class GiantSwordLightBarrageMagicMixin {
    @WrapOperation(method = "onHitDamaged", at = @At(value = "INVOKE",
            target = "Lcn/coostack/usefulmagic/utils/FallingBlockHelper;conversionBlockToFallingBlocks(Ljava/util/List;ZLnet/minecraft/world/level/Level;)Ljava/util/List;"))
    private List<FallingBlockEntity> maidspell$protectTerrain(FallingBlockHelper helper, List<BlockPos> blocks,
                                                             boolean breakOrigin, Level world,
                                                             Operation<List<FallingBlockEntity>> original) {
        if (UsefulMagicTerrainProtection.blocksTerrainDamage(
                ((GiantSwordLightBarrageMagic) (Object) this).getShooter())) {
            return List.of();
        }
        return original.call(helper, blocks, breakOrigin, world);
    }
}
