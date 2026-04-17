package com.github.yimeng261.maidspell.crafting;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class MaidSpellIngredientTypes {

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, MaidSpellMod.MOD_ID);

    public static final Supplier<IngredientType<OptionalModIngredient>> OPTIONAL_MOD_ITEM =
            INGREDIENT_TYPES.register("optional_mod_item",
                    () -> new IngredientType<>(OptionalModIngredient.CODEC));
}
