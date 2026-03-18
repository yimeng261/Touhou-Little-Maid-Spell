package com.github.yimeng261.maidspell.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.fml.ModList;

public class OptionalModIngredientSerializer implements IIngredientSerializer<Ingredient> {
    public static final OptionalModIngredientSerializer INSTANCE = new OptionalModIngredientSerializer();

    private OptionalModIngredientSerializer() {
    }

    @Override
    public Ingredient parse(JsonObject json) {
        String modId = GsonHelper.getAsString(json, "modid");
        if (!ModList.get().isLoaded(modId)) {
            return Ingredient.EMPTY;
        }

        JsonElement ingredientJson = json.get("ingredient");
        if (ingredientJson == null) {
            throw new IllegalArgumentException("Missing ingredient for optional mod ingredient: " + modId);
        }
        return CraftingHelper.getIngredient(ingredientJson, false);
    }

    @Override
    public Ingredient parse(FriendlyByteBuf buffer) {
        return Ingredient.EMPTY;
    }

    @Override
    public void write(FriendlyByteBuf buffer, Ingredient ingredient) {
    }
}
