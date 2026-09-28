package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;

/** 本模组内容产出的女仆的来源标记，存在女仆的持久数据里。 */
public final class MaidOriginData {
    public static final String STAR_WITCH_VICTORY_TAMED = "MaidSpellStarWitchVictoryTamed";
    public static final String HIDDEN_RETREAT_MAID = "MaidSpellHiddenRetreatMaid";
    private static final ResourceLocation SPELL_CONTAINER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_container");
    private static final String PERSISTENT_DATA = "NeoForgeData";
    private static final String MAX_SPELLS = "maxSpells";
    private static final int STAR_WITCH_SPELL_SLOTS = 20;

    private MaidOriginData() {
    }

    public static void markStarWitchVictoryTamed(EntityMaid maid) {
        maid.getPersistentData().putBoolean(STAR_WITCH_VICTORY_TAMED, true);
    }

    public static void markHiddenRetreatMaid(EntityMaid maid) {
        maid.getPersistentData().putBoolean(HIDDEN_RETREAT_MAID, true);
    }

    /** 只升级星之魔女女仆身上的铁魔法法术书。 */
    public static void upgradeStarWitchSpellBooks(EntityMaid maid) {
        if (!isStarWitchVictoryTamed(maid)) {
            return;
        }
        for (int slot = 0; slot < maid.getMaidInv().getSlots(); slot++) {
            upgradeSpellBook(maid.getMaidInv().getStackInSlot(slot));
        }
    }

    /** 奖励魂符放出之前，升级其中序列化背包里的法术书。 */
    public static void upgradeStarWitchSpellBookData(CompoundTag maidData) {
        CompoundTag inventory = maidData.getCompound(EntityMaid.MAID_INVENTORY_TAG);
        if (!inventory.contains("Items", Tag.TAG_LIST)) {
            return;
        }
        ListTag items = inventory.getList("Items", Tag.TAG_COMPOUND);
        for (Tag element : items) {
            CompoundTag item = (CompoundTag) element;
            String id = item.getString("id");
            if (!id.startsWith("irons_spellbooks:") || !id.contains("spell_book")) {
                continue;
            }
            CompoundTag components = item.getCompound("components");
            String key = SPELL_CONTAINER.toString();
            if (components.contains(key, Tag.TAG_COMPOUND)) {
                components.getCompound(key).putInt(MAX_SPELLS, STAR_WITCH_SPELL_SLOTS);
            }
        }
    }

    public static boolean isStarWitchVictoryTamed(EntityMaid maid) {
        return maid != null && maid.getPersistentData().getBoolean(STAR_WITCH_VICTORY_TAMED);
    }

    /** 女仆变成物品前，把标记写进 TLM 的转运数据。 */
    public static void writeTransportData(EntityMaid maid, CompoundTag data) {
        CompoundTag persistent = maid.getPersistentData();
        copyBoolean(persistent, data, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistent, data, HIDDEN_RETREAT_MAID);
        CompoundTag persistentData = data.getCompound(PERSISTENT_DATA);
        copyBoolean(persistent, persistentData, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistent, persistentData, HIDDEN_RETREAT_MAID);
        if (!persistentData.isEmpty()) {
            data.put(PERSISTENT_DATA, persistentData);
        }
    }

    /** TLM 从胶片或魂符放出女仆后恢复标记。 */
    public static void readTransportData(EntityMaid maid, CompoundTag data) {
        copyBoolean(data, maid.getPersistentData(), STAR_WITCH_VICTORY_TAMED);
        copyBoolean(data, maid.getPersistentData(), HIDDEN_RETREAT_MAID);
        CompoundTag persistentData = data.getCompound(PERSISTENT_DATA);
        copyBoolean(persistentData, maid.getPersistentData(), STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistentData, maid.getPersistentData(), HIDDEN_RETREAT_MAID);
    }

    private static void copyBoolean(CompoundTag source, CompoundTag target, String key) {
        if (source.getBoolean(key)) {
            target.putBoolean(key, true);
        }
    }

    private static void upgradeSpellBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"irons_spellbooks".equals(id.getNamespace()) || !id.getPath().contains("spell_book")) {
            return;
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SPELL_CONTAINER);
        if (type != null) {
            upgradeContainer(stack, type);
        }
    }

    /** 经组件编解码器改写 maxSpells，不直接引用铁魔法的类。 */
    private static <T> void upgradeContainer(ItemStack stack, DataComponentType<T> type) {
        T container = stack.get(type);
        Codec<T> codec = type.codec();
        if (container == null || codec == null) {
            return;
        }
        codec.encodeStart(NbtOps.INSTANCE, container).result()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .filter(tag -> tag.getInt(MAX_SPELLS) != STAR_WITCH_SPELL_SLOTS)
                .ifPresent(tag -> {
                    tag.putInt(MAX_SPELLS, STAR_WITCH_SPELL_SLOTS);
                    codec.parse(NbtOps.INSTANCE, tag).result().ifPresent(upgraded -> stack.set(type, upgraded));
                });
    }
}
