package com.github.yimeng261.maidspell.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取 SynchedEntityData 的辅助器
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-10-25 00:45
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_HEALTH_ID")
    EntityDataAccessor<Float> getDataHealthIdAccessor();
}
