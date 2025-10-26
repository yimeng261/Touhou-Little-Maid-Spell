package com.github.yimeng261.maidspell.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 物品 NBT 组件注册
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2025-10-23 01:35
 */
public class MaidSpellDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MaidSpellMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> BLUE_NOTE_SCROLLS_TAG = DATA_COMPONENTS.register("stored_scrolls", key -> DataComponentType.<List<ItemStack>>builder()
        .persistent(ItemStack.OPTIONAL_CODEC.listOf())
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> BLUE_NOTE_SPELL_IDS_TAG = DATA_COMPONENTS.register("spell_ids", key -> DataComponentType.<List<String>>builder()
        .persistent(Codec.STRING.listOf())
        .build());
}
