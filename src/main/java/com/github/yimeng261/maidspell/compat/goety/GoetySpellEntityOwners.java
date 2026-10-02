package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.common.entities.projectiles.AbstractBeam;
import com.Polarice3.Goety.common.entities.projectiles.Fangs;
import com.Polarice3.Goety.common.entities.projectiles.GroundProjectile;
import com.Polarice3.Goety.common.entities.projectiles.ScatterMine;
import com.Polarice3.Goety.common.entities.projectiles.SpellLightningBolt;
import com.Polarice3.Goety.common.entities.projectiles.TangleEntity;
import com.Polarice3.Goety.common.entities.projectiles.ViciousPike;
import com.Polarice3.Goety.common.entities.projectiles.ViciousTooth;
import com.Polarice3.Goety.common.entities.util.AbstractTrap;
import com.Polarice3.Goety.common.entities.util.BrewEffectCloud;
import com.Polarice3.Goety.common.entities.util.BrewGas;
import com.Polarice3.Goety.common.entities.util.EffectBlastTrap;
import com.Polarice3.Goety.common.entities.util.FireBlastTrap;
import com.Polarice3.Goety.common.entities.util.UpdraftBlast;
import com.github.yimeng261.maidspell.compat.EntityOwnerGetter;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Goety 既不是弹射物、也不是仆从的法术实体（尖牙、光束、陷阱、地刺、药雾等）的施法者；仆从见 {@link GoetyMinionOwners}。
 * <p>
 * 只在 Goety 已加载时访问。
 */
public final class GoetySpellEntityOwners {
    private static final List<EntityOwnerGetter<?>> OWNERS = List.of(
            new EntityOwnerGetter<>(Fangs.class, Fangs::getOwner),
            new EntityOwnerGetter<>(GroundProjectile.class, GroundProjectile::getOwner),
            new EntityOwnerGetter<>(AbstractBeam.class, AbstractBeam::getOwner),
            new EntityOwnerGetter<>(ScatterMine.class, ScatterMine::getOwner),
            new EntityOwnerGetter<>(ViciousPike.class, ViciousPike::getOwner),
            new EntityOwnerGetter<>(ViciousTooth.class, ViciousTooth::getOwner),
            new EntityOwnerGetter<>(TangleEntity.class, TangleEntity::getOwner),
            new EntityOwnerGetter<>(SpellLightningBolt.class, SpellLightningBolt::getOwner),
            new EntityOwnerGetter<>(AbstractTrap.class, AbstractTrap::getOwner),
            new EntityOwnerGetter<>(FireBlastTrap.class, FireBlastTrap::getOwner),
            new EntityOwnerGetter<>(EffectBlastTrap.class, EffectBlastTrap::getOwner),
            new EntityOwnerGetter<>(UpdraftBlast.class, UpdraftBlast::getOwner),
            new EntityOwnerGetter<>(BrewGas.class, BrewGas::getOwner),
            new EntityOwnerGetter<>(BrewEffectCloud.class, BrewEffectCloud::getOwner)
    );

    private GoetySpellEntityOwners() {
    }

    /** 表里第一项认得 {@code type} 的表项；都不认得时为 {@code null}。 */
    @Nullable
    public static EntityOwnerGetter<?> getterFor(Class<?> type) {
        return EntityOwnerGetter.find(OWNERS, type);
    }
}
