package com.github.yimeng261.maidspell.loot;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Consumer;

/** 在使用时才解析可选物品，联动模组缺席时不会让整张战利品表失效。 */
public final class OptionalModLootItem extends LootPoolSingletonContainer {
    public static final MapCodec<OptionalModLootItem> CODEC = RecordCodecBuilder.mapCodec(instance ->
            singletonFields(instance)
                    .and(ResourceLocation.CODEC.fieldOf("name").forGetter(entry -> entry.itemId))
                    .apply(instance, OptionalModLootItem::new));

    public static final DeferredRegister<LootPoolEntryType> TYPES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, MaidSpellMod.MOD_ID);
    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> TYPE =
            TYPES.register("optional_mod_item", () -> new LootPoolEntryType(CODEC));

    private final ResourceLocation itemId;

    private OptionalModLootItem(int weight, int quality, List<LootItemCondition> conditions,
                                List<LootItemFunction> functions, ResourceLocation itemId) {
        super(weight, quality, conditions, functions);
        this.itemId = itemId;
    }

    @Override
    public LootPoolEntryType getType() {
        return TYPE.get();
    }

    @Override
    public boolean expand(LootContext context, Consumer<LootPoolEntry> output) {
        return BuiltInRegistries.ITEM.containsKey(itemId) && super.expand(context, output);
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> output.accept(new ItemStack(item)));
    }
}
