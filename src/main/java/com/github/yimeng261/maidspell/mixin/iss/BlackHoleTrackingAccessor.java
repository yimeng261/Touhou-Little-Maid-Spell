package com.github.yimeng261.maidspell.mixin.iss;

import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 只为拿 {@code BlackHole.trackingEntities} 这一个包级私有字段的读写口。
 *
 * <p><b>为什么需要它</b>：那个字段（{@code BlackHole.java:36} 写作
 * {@code List<Entity> trackingEntities}，没有修饰符）对别的包不可见，
 * 所以 {@code CompanionBlackHoleFilterMixin} 想在那份名单上做减法就必须借一个访问器。
 * 接口式 {@code @Accessor} 是最轻的一层 —— 不写方法体、不碰行为，
 * 只把字段的 get/set 暴露成接口方法。
 */
@Mixin(value = BlackHole.class, remap = false)
public interface BlackHoleTrackingAccessor {

    @Accessor("trackingEntities")
    List<Entity> maidspell$getTrackingEntities();

    @Accessor("trackingEntities")
    void maidspell$setTrackingEntities(List<Entity> entities);
}
