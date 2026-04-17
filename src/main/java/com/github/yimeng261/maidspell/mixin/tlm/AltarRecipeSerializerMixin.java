package com.github.yimeng261.maidspell.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipeSerializer;
import com.mojang.serialization.DataResult;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 过滤祭坛配方中的空材料（OptionalModIngredient 在模组未安装时产生的空材料），
 * 使跨模组可选合成配方正常工作。
 */
@Mixin(value = AltarRecipeSerializer.class, remap = false)
public class AltarRecipeSerializerMixin {

    @Inject(method = "checkIngredients", at = @At("HEAD"), cancellable = true)
    private static void maidspell$filterEmptyIngredients(List<Ingredient> ingredientList,
                                                         CallbackInfoReturnable<DataResult<NonNullList<Ingredient>>> cir) {
        Ingredient[] filtered = ingredientList.stream()
                .filter(ingredient -> ingredient != null && !ingredient.isEmpty())
                .toArray(Ingredient[]::new);

        if (filtered.length == ingredientList.size()) {
            return;
        }

        if (filtered.length == 0) {
            cir.setReturnValue(DataResult.error(() -> "No ingredients for shapeless recipe"));
            return;
        }
        if (filtered.length > 6) {
            cir.setReturnValue(DataResult.error(() -> "Too many ingredients for shapeless recipe. The maximum is: 6"));
            return;
        }

        cir.setReturnValue(DataResult.success(NonNullList.of(Ingredient.EMPTY, filtered)));
    }
}
