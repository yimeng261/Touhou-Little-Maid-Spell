package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露三叉戟的忠诚与附魔光效同步字段，供自定义实体类型的投枪写入。 */
@Mixin(ThrownTrident.class)
public interface ThrownTridentAccessor {

    @Accessor("ID_LOYALTY")
    static EntityDataAccessor<Byte> maidspell$getLoyaltyData() {
        throw new AssertionError();
    }

    @Accessor("ID_FOIL")
    static EntityDataAccessor<Boolean> maidspell$getFoilData() {
        throw new AssertionError();
    }
}
