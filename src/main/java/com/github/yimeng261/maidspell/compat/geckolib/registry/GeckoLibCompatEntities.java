package com.github.yimeng261.maidspell.compat.geckolib.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.geckolib.entity.StarShadowSpearEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 需要 GeckoLib 的实体类型注册表。
 *
 * <p>与 {@link GeckoLibCompatItems} 一起，只在 GeckoLib 存在时才会挂到事件总线上。
 */
public final class GeckoLibCompatEntities {

    private GeckoLibCompatEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MaidSpellMod.MOD_ID);

    /** 飞行中的星影投枪。和物品同生共死：有物品才有它。 */
    public static final RegistryObject<EntityType<StarShadowSpearEntity>> STAR_SHADOW_SPEAR =
            ENTITY_TYPES.register("star_shadow_spear",
                    () -> EntityType.Builder.<StarShadowSpearEntity>of(StarShadowSpearEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(5)
                            .build("star_shadow_spear"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
