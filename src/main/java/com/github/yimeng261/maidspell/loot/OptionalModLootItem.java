package com.github.yimeng261.maidspell.loot;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/** Resolves optional items at use time so absent integrations do not invalidate a loot table. */
public final class OptionalModLootItem extends LootPoolSingletonContainer {
    public static final DeferredRegister<LootPoolEntryType> TYPES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, MaidSpellMod.MOD_ID);
    public static final RegistryObject<LootPoolEntryType> TYPE =
            TYPES.register("optional_mod_item", () -> new LootPoolEntryType(new Serializer()));

    private final ResourceLocation itemId;

    private OptionalModLootItem(ResourceLocation itemId, int weight, int quality,
                                LootItemCondition[] conditions, LootItemFunction[] functions) {
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

    public static final class Serializer extends LootPoolSingletonContainer.Serializer<OptionalModLootItem> {
        @Override
        public void serializeCustom(JsonObject json, OptionalModLootItem entry,
                                    JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            json.addProperty("name", entry.itemId.toString());
        }

        @Override
        protected OptionalModLootItem deserialize(JsonObject json, JsonDeserializationContext context,
                                                   int weight, int quality, LootItemCondition[] conditions,
                                                   LootItemFunction[] functions) {
            return new OptionalModLootItem(new ResourceLocation(GsonHelper.getAsString(json, "name")),
                    weight, quality, conditions, functions);
        }
    }
}
