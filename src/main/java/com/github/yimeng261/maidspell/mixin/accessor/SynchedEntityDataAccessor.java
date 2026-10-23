package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取实体的全部同步数据项。
 *
 * <p>{@code getNonDefaultValues} 不含已回到默认值的项，推给末影腰包代理时会漏掉被关掉的开关。
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {
    @Accessor("itemsById")
    SynchedEntityData.DataItem<?>[] maidspell$getItemsById();
}
