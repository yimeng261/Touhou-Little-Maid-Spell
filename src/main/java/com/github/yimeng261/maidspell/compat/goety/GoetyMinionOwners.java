package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.api.entities.IOwned;
import com.github.yimeng261.maidspell.compat.EntityOwnerGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Goety 仆从的主人。
 * <p>
 * 只依赖 Goety 的 API 接口 {@link IOwned}，与 {@link GoetySpellEntityOwners} 分开加载，
 * 那边的法术实体类随 Goety 版本改名或删除时不影响仆从。只在 Goety 已加载时访问。
 */
public final class GoetyMinionOwners {
    private static final EntityOwnerGetter<IOwned> OWNER = new EntityOwnerGetter<>(IOwned.class, GoetyMinionOwners::getMinionOwner);

    private GoetyMinionOwners() {
    }

    /** {@code type} 是仆从时给出取主人的表项，否则为 {@code null}。 */
    @Nullable
    public static EntityOwnerGetter<?> getterFor(Class<?> type) {
        return OWNER.type().isAssignableFrom(type) ? OWNER : null;
    }

    /** 仆从记下的主人 UUID，主人不在场时也有。 */
    @Nullable
    public static UUID getOwnerId(Entity entity) {
        return entity instanceof IOwned owned ? owned.getOwnerId() : null;
    }

    @Nullable
    private static LivingEntity getMinionOwner(IOwned owned) {
        LivingEntity owner = owned.getTrueOwner();
        return owner != null ? owner : owned.getMasterOwner();
    }
}
