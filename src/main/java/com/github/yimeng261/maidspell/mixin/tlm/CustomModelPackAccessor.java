package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CustomModelPack.class, remap = false)
public interface CustomModelPackAccessor {
    @Accessor("icon")
    ResourceLocation maidspell$getIcon();

    @Accessor("icon")
    void maidspell$setIcon(ResourceLocation icon);
}
