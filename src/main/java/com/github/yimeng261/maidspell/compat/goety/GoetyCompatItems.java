package com.github.yimeng261.maidspell.compat.goety;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Goety-only item registrations. This class is loaded only when Goety is
 * present, so its references to the Goety API cannot break other modpacks.
 */
public final class GoetyCompatItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidSpellMod.MOD_ID);

    /**
     * A hidden item used only as an ItemStack identity for a virtual wand.
     * It is deliberately not added to the creative tab or any loot/recipe.
     */
    public static final RegistryObject<Item> VIRTUAL_GOETY_WAND =
            ITEMS.register("virtual_goety_wand", () -> new VirtualGoetyWandItem());

    private GoetyCompatItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
