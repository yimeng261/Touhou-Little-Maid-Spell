package com.github.yimeng261.maidspell.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 女仆法术实体注册
 */
public class MaidSpellEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, MaidSpellMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<WindSeekingBellEntity>> WIND_SEEKING_BELL =
        ENTITY_TYPES.register("wind_seeking_bell",
            () -> EntityType.Builder.<WindSeekingBellEntity>of(WindSeekingBellEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                    .updateInterval(4)
                .build("wind_seeking_bell"));

    /** 扔出去的星影投枪。物品无条件存在，实体也必须无条件注册。 */
    public static final DeferredHolder<EntityType<?>, EntityType<StarShadowSpearEntity>> STAR_SHADOW_SPEAR =
        ENTITY_TYPES.register("star_shadow_spear",
            () -> EntityType.Builder.<StarShadowSpearEntity>of(StarShadowSpearEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(4)
                .updateInterval(5)
                .build("star_shadow_spear"));

    /**
     * 注册实体类型
     */
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
