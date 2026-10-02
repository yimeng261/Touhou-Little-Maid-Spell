package com.github.yimeng261.maidspell.compat.irons_spellbooks;

import com.github.yimeng261.maidspell.compat.EntityOwnerGetter;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.frozen_humanoid.FrozenHumanoid;
import io.redspace.ironsspellbooks.entity.spells.firefly_swarm.FireflySwarmProjectile;
import io.redspace.ironsspellbooks.entity.spells.ice_tomb.IceTombEntity;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import io.redspace.ironsspellbooks.entity.spells.void_tentacle.VoidTentacle;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 铁魔法召唤物，以及既不是弹射物、也不是召唤物的法术实体（触手、根须、火墙、冰墓等）的施法者。
 * <p>
 * 只在铁魔法已加载时访问。
 */
public final class IronsSpellEntityOwners {
    private static final List<EntityOwnerGetter<?>> OWNERS = List.of(
            new EntityOwnerGetter<>(IMagicSummon.class, IMagicSummon::getSummoner),
            new EntityOwnerGetter<>(WallOfFireEntity.class, WallOfFireEntity::getOwner),
            new EntityOwnerGetter<>(VoidTentacle.class, VoidTentacle::getOwner),
            new EntityOwnerGetter<>(RootEntity.class, RootEntity::getOwner),
            new EntityOwnerGetter<>(IceTombEntity.class, IceTombEntity::getOwner),
            new EntityOwnerGetter<>(FireflySwarmProjectile.class, FireflySwarmProjectile::getOwner),
            new EntityOwnerGetter<>(FrozenHumanoid.class, FrozenHumanoid::getSummoner)
    );

    private IronsSpellEntityOwners() {
    }

    /** 表里第一项认得 {@code type} 的表项；都不认得时为 {@code null}。 */
    @Nullable
    public static EntityOwnerGetter<?> getterFor(Class<?> type) {
        return EntityOwnerGetter.find(OWNERS, type);
    }
}
