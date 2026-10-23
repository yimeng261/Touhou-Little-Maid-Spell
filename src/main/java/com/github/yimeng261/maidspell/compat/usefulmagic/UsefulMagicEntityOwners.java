package com.github.yimeng261.maidspell.compat.usefulmagic;

import cn.coostack.usefulmagic.entity.custom.dragon.eye.MagicHeartEntity;
import cn.coostack.usefulmagic.entity.custom.dragon.eye.MagicSubEyeEntity;
import com.github.yimeng261.maidspell.compat.EntityOwnerGetter;

import javax.annotation.Nullable;
import java.util.List;

/** UsefulMagic 核心与子眼实体的主人，只在该模组加载时访问。 */
public final class UsefulMagicEntityOwners {
    private static final List<EntityOwnerGetter<?>> OWNERS = List.of(
            new EntityOwnerGetter<>(MagicHeartEntity.class, MagicHeartEntity::getOwner),
            new EntityOwnerGetter<>(MagicSubEyeEntity.class, MagicSubEyeEntity::getOwner));

    private UsefulMagicEntityOwners() { }

    @Nullable
    public static EntityOwnerGetter<?> getterFor(Class<?> type) {
        return EntityOwnerGetter.find(OWNERS, type);
    }
}
