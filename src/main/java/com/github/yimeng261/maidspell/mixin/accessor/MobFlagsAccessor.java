package com.github.yimeng261.maidspell.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取 {@code Mob.DATA_MOB_FLAGS_ID}（private static）。
 *
 * <p>{@code setNoAi} 写的正是它其中那一位，而星之魔女要同时挡住方法层与同步字段层，
 * 所以需要直接拿到这个 accessor。
 */
@Mixin(Mob.class)
public interface MobFlagsAccessor {
    @Accessor("DATA_MOB_FLAGS_ID")
    static EntityDataAccessor<Byte> maidspell$getMobFlagsAccessor() {
        throw new AssertionError();
    }
}
