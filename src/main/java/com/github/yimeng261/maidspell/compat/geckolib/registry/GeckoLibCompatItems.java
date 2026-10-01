package com.github.yimeng261.maidspell.compat.geckolib.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.geckolib.item.StarShadowSpearItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 需要 GeckoLib 的物品注册表。
 *
 * <p>只有 {@code GeckoLibCompat.isLoaded()} 成立时才会挂到事件总线上，见 {@code GeckoLibCompat#init}。
 */
public final class GeckoLibCompatItems {

    private GeckoLibCompatItems() {
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidSpellMod.MOD_ID);

    /** 万法酒狐掉落、投掷时用几何体模型的星影投枪。 */
    public static final RegistryObject<Item> STAR_SHADOW_SPEAR =
            ITEMS.register("star_shadow_spear", StarShadowSpearItem::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
