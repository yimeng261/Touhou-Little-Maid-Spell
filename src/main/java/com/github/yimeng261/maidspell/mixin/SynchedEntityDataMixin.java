package com.github.yimeng261.maidspell.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataMixin {
    @Accessor("itemsById")
    Int2ObjectMap<SynchedEntityData.DataItem<?>> getItemsById();
}
