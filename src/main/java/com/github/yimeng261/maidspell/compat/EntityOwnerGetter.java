package com.github.yimeng261.maidspell.compat;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * 可选模组桥接类里的一条表项：{@code type} 这类实体的主人怎么取。
 */
public record EntityOwnerGetter<T>(Class<T> type, Function<? super T, ? extends Entity> getter) {

    /** 按表序取第一项认得 {@code entityType} 的表项；没有表项认得时返回 {@code null}。 */
    @Nullable
    public static EntityOwnerGetter<?> find(List<EntityOwnerGetter<?>> table, Class<?> entityType) {
        for (EntityOwnerGetter<?> entry : table) {
            if (entry.type.isAssignableFrom(entityType)) {
                return entry;
            }
        }
        return null;
    }

    /** 这一项给出的主人；{@code entity} 须是 {@link #type} 的实例。 */
    @Nullable
    public Entity ownerOf(Entity entity) {
        return getter.apply(type.cast(entity));
    }
}
