package com.github.yimeng261.maidspell.crafting;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
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
 *
 * <p>内层材料只在模组已安装时才解析：未安装时其中的物品 id、材料类型都不存在，提前解析会让整条配方加载失败。
 */
public class OptionalModIngredient implements ICustomIngredient {

    public static final MapCodec<OptionalModIngredient> CODEC = RecordCodecBuilder.<Pair<String, Dynamic<?>>>mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("modid").forGetter(Pair::getFirst),
            Codec.PASSTHROUGH.fieldOf("ingredient").forGetter(Pair::getSecond)
    ).apply(instance, Pair::of)).flatXmap(OptionalModIngredient::decode,
            i -> DataResult.success(Pair.of(i.modId, i.rawIngredient)));

    private final String modId;
    private final Dynamic<?> rawIngredient;
    private final Ingredient ingredient;
    private final boolean modLoaded;

    private OptionalModIngredient(String modId, Dynamic<?> rawIngredient, Ingredient ingredient, boolean modLoaded) {
        this.modId = modId;
        this.rawIngredient = rawIngredient;
        this.ingredient = ingredient;
        this.modLoaded = modLoaded;
    }

    private static DataResult<OptionalModIngredient> decode(Pair<String, Dynamic<?>> fields) {
        String modId = fields.getFirst();
        Dynamic<?> raw = fields.getSecond();
        if (!ModList.get().isLoaded(modId)) {
            return DataResult.success(new OptionalModIngredient(modId, raw, Ingredient.EMPTY, false));
        }
        return Ingredient.CODEC.parse(raw).map(parsed -> new OptionalModIngredient(modId, raw, parsed, true));
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
