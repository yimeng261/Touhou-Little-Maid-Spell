package com.github.yimeng261.maidspell.mixin.iss.accessor;

import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 {@link PlayerRecasts#recastLookup}：{@code removeRecast(String)} 是 {@code @OnlyIn(Dist.CLIENT)}，
 * 服务端清理女仆 recast 时需要绕过它。
 */
@Mixin(value = PlayerRecasts.class, remap = false)
public interface PlayerRecastsAccessor {
    @Accessor("recastLookup")
    Map<String, RecastInstance> maidspell$getRecastLookup();
}
