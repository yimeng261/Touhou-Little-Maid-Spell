package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipeSerializer;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AltarRecipeSerializer.class, remap = false)
public class AltarRecipeSerializerMixin {
    @Inject(method = "fromJson", at = @At("RETURN"), cancellable = true, remap = false)
    private void maidspell$filterEmptyIngredients(ResourceLocation recipeId, JsonObject json,
                                                  CallbackInfoReturnable<AltarRecipe> cir) {
        AltarRecipe recipe = cir.getReturnValue();
        if (recipe == null) {
            return;
        }

        Ingredient[] filtered = recipe.getIngredients().stream()
                .filter(ingredient -> ingredient != null && !ingredient.isEmpty())
                .toArray(Ingredient[]::new);

        if (filtered.length == 0 || filtered.length == recipe.getIngredients().size()) {
            return;
        }

        cir.setReturnValue(new AltarRecipe(
                recipeId,
                recipe.getEntityType(),
                recipe.getExtraData(),
                recipe.getPowerCost(),
                recipe.getCopyInput(),
                recipe.getCopyTag(),
                filtered
        ));
    }
}
