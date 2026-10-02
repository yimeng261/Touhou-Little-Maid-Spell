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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> SPELL_WHITE_LIST_SCROLLS_TAG = DATA_COMPONENTS.register("stored_scrolls", key -> DataComponentType.<List<ItemStack>>builder()
        .persistent(ItemStack.OPTIONAL_CODEC.listOf())
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> SPELL_WHITE_LIST_SPELL_IDS_TAG = DATA_COMPONENTS.register("spell_ids", key -> DataComponentType.<List<String>>builder()
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SPRING_BLOOM_RETURN_GAIN_COOLDOWN_UNTIL = DATA_COMPONENTS.register("spring_bloom_return_gain_cooldown_until", key -> DataComponentType.<Long>builder()
        .persistent(Codec.LONG)
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPRING_BLOOM_RETURN_CLOCK_VERSION = DATA_COMPONENTS.register("spring_bloom_return_clock_version", key -> DataComponentType.<Integer>builder()
        .persistent(Codec.INT)
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_UNTIL = DATA_COMPONENTS.register("spring_bloom_return_trigger_cooldown_until", key -> DataComponentType.<Long>builder()
        .persistent(Codec.LONG)
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Long>>> DREAM_CRYSTAL_REVIVE_TIMESTAMPS = DATA_COMPONENTS.register("dream_crystal_revive_timestamps", key -> DataComponentType.<List<Long>>builder()
        .persistent(Codec.LONG.listOf())
        .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DREAM_CRYSTAL_REVIVE_CLOCK_VERSION = DATA_COMPONENTS.register("dream_crystal_revive_clock_version", key -> DataComponentType.<Integer>builder()
        .persistent(Codec.INT)
        .build());

    /** 旧版本按 tick 倒数的无敌时间，只在读到旧物品时换算成 {@link #DREAM_CRYSTAL_INVULNERABLE_UNTIL} */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DREAM_CRYSTAL_INVULNERABLE_TICKS = DATA_COMPONENTS.register("dream_crystal_invulnerable_ticks", key -> DataComponentType.<Integer>builder()
        .persistent(Codec.INT)
        .build());

    /** 复活后无敌的截止时间（主世界游戏时间），只在复活和到期时各写一次 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> DREAM_CRYSTAL_INVULNERABLE_UNTIL = DATA_COMPONENTS.register("dream_crystal_invulnerable_until", key -> DataComponentType.<Long>builder()
        .persistent(Codec.LONG)
        .build());
}
