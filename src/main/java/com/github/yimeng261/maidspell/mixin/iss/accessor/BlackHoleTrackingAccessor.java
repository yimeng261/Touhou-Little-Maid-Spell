package com.github.yimeng261.maidspell.mixin.iss.accessor;

import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 读写 BlackHole 的包级私有字段 trackingEntities
 */
@Mixin(value = BlackHole.class, remap = false)
public interface BlackHoleTrackingAccessor {

    @Accessor("trackingEntities")
    List<Entity> maidspell$getTrackingEntities();

    @Accessor("trackingEntities")
    void maidspell$setTrackingEntities(List<Entity> entities);
}
