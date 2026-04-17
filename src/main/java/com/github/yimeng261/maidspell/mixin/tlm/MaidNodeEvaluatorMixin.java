package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import com.github.yimeng261.maidspell.util.FoxLeafSurfaceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidNodeEvaluator.class, remap = false)
public abstract class MaidNodeEvaluatorMixin extends WalkNodeEvaluator {
    @Inject(
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private void maidspell$treatMoltenFoxLeafSurfaceAsWalkable(BlockGetter level, int x, int y, int z,
                                                               CallbackInfoReturnable<BlockPathTypes> cir) {
        if (!(mob instanceof EntityMaid maid)
            || !BaubleStateManager.hasBauble(maid, MaidSpellItems.MOLTEN_FOX_LEAF)) {
            return;
        }

        if (maidspell$isLavaSurfaceNode(level, new BlockPos(x, y, z))) {
            cir.setReturnValue(BlockPathTypes.WALKABLE);
        }
    }

    private static boolean maidspell$isLavaSurfaceNode(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        return FoxLeafSurfaceHelper.isFluidSurface(level, pos, FluidTags.LAVA);
    }
}
