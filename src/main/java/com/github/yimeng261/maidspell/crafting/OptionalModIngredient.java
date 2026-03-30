package com.github.yimeng261.maidspell.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/**
 * 可选模组材料：当指定模组未安装时返回空（不匹配任何物品），
 * 配合 AltarRecipeSerializerMixin 过滤空材料，实现跨模组可选合成。
 */
public class OptionalModIngredient implements ICustomIngredient {

    public static final MapCodec<OptionalModIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.mojang.serialization.Codec.STRING.fieldOf("modid").forGetter(i -> i.modId),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient)
    ).apply(instance, OptionalModIngredient::new));

    private final String modId;
    private final Ingredient ingredient;
    private final boolean modLoaded;

    public OptionalModIngredient(String modId, Ingredient ingredient) {
        this.modId = modId;
        this.ingredient = ingredient;
        this.modLoaded = ModList.get().isLoaded(modId);
    }

    @Override
    public boolean test(ItemStack stack) {
        if (!modLoaded) {
            return false;
        }
        return ingredient.test(stack);
    }

    @Override
    public Stream<ItemStack> getItems() {
        if (!modLoaded) {
            return Stream.empty();
        }
        return Stream.of(ingredient.getItems());
    }

    @Override
    public boolean isSimple() {
        return !modLoaded || ingredient.isSimple();
    }

    @Override
    public IngredientType<?> getType() {
        return MaidSpellIngredientTypes.OPTIONAL_MOD_ITEM.get();
    }
}
