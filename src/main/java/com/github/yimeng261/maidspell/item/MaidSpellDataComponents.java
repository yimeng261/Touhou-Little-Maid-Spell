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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TRANSMOG_HALO_STYLE_TAG = DATA_COMPONENTS.register("transmog_halo_style", key -> DataComponentType.<String>builder()
        .persistent(Codec.STRING)
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Long>>> SPRING_BLOOM_RETURN_EXPIRIES = DATA_COMPONENTS.register("spring_bloom_return_expiries", key -> DataComponentType.<List<Long>>builder()
        .persistent(Codec.LONG.listOf())
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SPRING_BLOOM_RETURN_LAST_GAIN_TICK = DATA_COMPONENTS.register("spring_bloom_return_last_gain_tick", key -> DataComponentType.<Long>builder()
        .persistent(Codec.LONG)
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_UNTIL = DATA_COMPONENTS.register("spring_bloom_return_trigger_cooldown_until", key -> DataComponentType.<Long>builder()
        .persistent(Codec.LONG)
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Long>>> DREAM_CRYSTAL_REVIVE_TIMESTAMPS = DATA_COMPONENTS.register("dream_crystal_revive_timestamps", key -> DataComponentType.<List<Long>>builder()
        .persistent(Codec.LONG.listOf())
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DREAM_CRYSTAL_INVULNERABLE_TICKS = DATA_COMPONENTS.register("dream_crystal_invulnerable_ticks", key -> DataComponentType.<Integer>builder()
        .persistent(Codec.INT)
        .build());
}
