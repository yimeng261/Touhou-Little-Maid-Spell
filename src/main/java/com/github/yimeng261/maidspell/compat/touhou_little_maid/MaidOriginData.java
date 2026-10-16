package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/** 本模组内容产出的女仆的来源标记，存在女仆的持久数据里。 */
public final class MaidOriginData {
    public static final String ORIGIN = "MaidSpellOrigin";
    public static final int ORIGIN_VERSION = 1;
    public static final String HIDDEN_RETREAT_SOURCE = "touhou_little_maid_spell:hidden_retreat";
    public static final String STAR_WITCH_VICTORY_TAMED = "MaidSpellStarWitchVictoryTamed";
    public static final String HIDDEN_RETREAT_MAID = "MaidSpellHiddenRetreatMaid";
    private static final ResourceLocation SPELL_CONTAINER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_container");
    private static final String PERSISTENT_DATA = "NeoForgeData";
    private static final String ACTIVE_EFFECTS = "active_effects";
    private static final String EFFECT_DURATION = "duration";
    private static final String MAX_SPELLS = "maxSpells";
    private static final int STAR_WITCH_SPELL_SLOTS = 20;

    private MaidOriginData() {
    }

    public static void markStarWitchVictoryTamed(EntityMaid maid) {
        maid.getPersistentData().putBoolean(STAR_WITCH_VICTORY_TAMED, true);
    }

    public static void markHiddenRetreatMaid(EntityMaid maid) {
        maid.getPersistentData().putBoolean(HIDDEN_RETREAT_MAID, true);
        if (!maid.getPersistentData().contains(ORIGIN, Tag.TAG_COMPOUND)) {
            maid.getPersistentData().put(ORIGIN, createOrigin(HIDDEN_RETREAT_SOURCE, false));
        }
    }

    /** 来源标记：Version 为格式版本，Source 为产出内容的注册名，PreservePermanentEffects 表示是否在转运时保留永久效果。 */
    public static CompoundTag createOrigin(String source, boolean preservePermanentEffects) {
        CompoundTag origin = new CompoundTag();
        origin.putInt("Version", ORIGIN_VERSION);
        origin.putString("Source", source);
        origin.putBoolean("PreservePermanentEffects", preservePermanentEffects);
        return origin;
    }

    /** 参数可以是持久数据本身、完整实体 NBT 或 TLM 的 MaidInfo，返回来源标记的副本。 */
    public static CompoundTag getOrigin(CompoundTag data) {
        CompoundTag persistent = data.getCompound(PERSISTENT_DATA);
        return (persistent.contains(ORIGIN, Tag.TAG_COMPOUND)
                ? persistent.getCompound(ORIGIN) : data.getCompound(ORIGIN)).copy();
    }

    public static boolean preservesPermanentEffects(CompoundTag data) {
        CompoundTag origin = getOrigin(data);
        return origin.getInt("Version") >= ORIGIN_VERSION
                && ResourceLocation.tryParse(origin.getString("Source")) != null
                && origin.getBoolean("PreservePermanentEffects");
    }

    public static boolean isHiddenRetreatMaid(CompoundTag data) {
        return data.getBoolean(HIDDEN_RETREAT_MAID)
                || data.getCompound(PERSISTENT_DATA).getBoolean(HIDDEN_RETREAT_MAID)
                || HIDDEN_RETREAT_SOURCE.equals(getOrigin(data).getString("Source"));
    }

    public static boolean isHiddenRetreatMaid(EntityMaid maid) {
        return isHiddenRetreatMaid(maid.getPersistentData());
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
        copyToTransport(persistent, data);
        // 死亡胶片会在发出 ToItem 事件前清掉状态效果；只给声明保留永久效果的女仆补回无限时长的效果。
        if (preservesPermanentEffects(persistent) && !data.contains(ACTIVE_EFFECTS, Tag.TAG_LIST)) {
            ListTag effects = new ListTag();
            maid.getActiveEffects().forEach(effect -> effects.add(effect.save()));
            restorePermanentEffects(persistent, effects, data);
        }
    }

    static void restorePermanentEffects(CompoundTag persistent, ListTag activeEffects, CompoundTag data) {
        if (!preservesPermanentEffects(persistent) || data.contains(ACTIVE_EFFECTS, Tag.TAG_LIST)) {
            return;
        }
        ListTag permanent = new ListTag();
        for (Tag element : activeEffects) {
            if (element instanceof CompoundTag effect
                    && effect.getInt(EFFECT_DURATION) == MobEffectInstance.INFINITE_DURATION) {
                permanent.add(effect.copy());
            }
        }
        if (!permanent.isEmpty()) {
            data.put(ACTIVE_EFFECTS, permanent);
        }
    }

    static void copyToTransport(CompoundTag persistent, CompoundTag data) {
        copyBoolean(persistent, data, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistent, data, HIDDEN_RETREAT_MAID);
        copyOrigin(persistent, data);
        CompoundTag persistentData = data.getCompound(PERSISTENT_DATA);
        copyBoolean(persistent, persistentData, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistent, persistentData, HIDDEN_RETREAT_MAID);
        copyOrigin(persistent, persistentData);
        if (!persistentData.isEmpty()) {
            data.put(PERSISTENT_DATA, persistentData);
        }
    }

    /** TLM 从胶片或魂符放出女仆后恢复标记。 */
    public static void readTransportData(EntityMaid maid, CompoundTag data) {
        restoreTransportData(data, maid.getPersistentData());
    }

    static void restoreTransportData(CompoundTag data, CompoundTag persistent) {
        copyBoolean(data, persistent, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(data, persistent, HIDDEN_RETREAT_MAID);
        copyOrigin(data, persistent);
        CompoundTag persistentData = data.getCompound(PERSISTENT_DATA);
        copyBoolean(persistentData, persistent, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistentData, persistent, HIDDEN_RETREAT_MAID);
        copyOrigin(persistentData, persistent);
        if (isHiddenRetreatMaid(persistent)) {
            persistent.putBoolean(HIDDEN_RETREAT_MAID, true);
            if (!persistent.contains(ORIGIN, Tag.TAG_COMPOUND)) {
                persistent.put(ORIGIN, createOrigin(HIDDEN_RETREAT_SOURCE, false));
            }
        }
        // ToMaid 先于 maid.load() 执行，实体加载会整体替换持久数据，
        // 所以把恢复出来的字段也并回传入的数据里，免得加载时被抹掉。
        copyToTransport(persistent, data);
    }

    private static void copyOrigin(CompoundTag source, CompoundTag target) {
        if (source.contains(ORIGIN, Tag.TAG_COMPOUND)) {
            target.put(ORIGIN, source.getCompound(ORIGIN).copy());
        }
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
