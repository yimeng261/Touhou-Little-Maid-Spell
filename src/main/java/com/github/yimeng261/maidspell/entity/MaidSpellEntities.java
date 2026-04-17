package com.github.yimeng261.maidspell.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.entity.mob.HolyConstructEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 女仆法术实体注册
 */
public class MaidSpellEntities {
    
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = 
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MaidSpellMod.MOD_ID);
    
    public static final RegistryObject<EntityType<WindSeekingBellEntity>> WIND_SEEKING_BELL = 
        ENTITY_TYPES.register("wind_seeking_bell", 
            () -> EntityType.Builder.<WindSeekingBellEntity>of(WindSeekingBellEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("wind_seeking_bell"));

    public static final RegistryObject<EntityType<HolyConstructEntity>> HOLY_CONSTRUCT =
        ENTITY_TYPES.register("holy_construct",
            () -> EntityType.Builder.<HolyConstructEntity>of(HolyConstructEntity::new, MobCategory.MONSTER)
                .sized(0.8F, 2.9F)
                .clientTrackingRange(10)
                .build("holy_construct"));
    
    /**
     * 注册实体类型
     */
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
