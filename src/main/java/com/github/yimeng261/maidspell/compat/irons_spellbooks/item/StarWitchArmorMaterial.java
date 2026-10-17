package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * 星之魔女法帽的护甲材质：护甲 3、韧性 3，紫水晶碎片修复。
 * 法力、法强、冷却缩减由 {@link StarWitchHatItem} 的属性容器提供。
 */
public final class StarWitchArmorMaterial {
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MaidSpellMod.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STAR_WITCH =
            ARMOR_MATERIALS.register("star_witch", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> map.put(ArmorItem.Type.HELMET, 3)),
                    22,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.AMETHYST_SHARD),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "star_witch"))),
                    3.0F,
                    0.0F));

    private StarWitchArmorMaterial() {
    }

    public static void register(IEventBus eventBus) {
        ARMOR_MATERIALS.register(eventBus);
    }
}
