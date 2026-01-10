package com.github.yimeng261.maidspell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.entity.AnchoredEntityMaid;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 取消追踪实体时判断是否有锚定核心
 * <br />
 * 暂未实装，有 bug
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-11-15 01:52
 */
@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerMixin<T extends EntityAccess> {
    @Inject(method = "stopTracking", at = @At("HEAD"), cancellable = true)
    public void beforeStopTracking(T entity, CallbackInfo ci) {
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide()) {
            return;
        }
        AnchoredEntityMaid anchoredEntityMaid = (AnchoredEntityMaid) maid;
        if (anchoredEntityMaid.maidSpell$isAnchored()) {
            ci.cancel();
        }
    }
}
