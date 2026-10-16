package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Persistent origin markers for maids created by MaidSpell content. */
public final class MaidOriginData {
    public static final String ORIGIN = "MaidSpellOrigin";
    public static final int ORIGIN_VERSION = 1;
    public static final String HIDDEN_RETREAT_SOURCE = "touhou_little_maid_spell:hidden_retreat";
    public static final String STAR_WITCH_VICTORY_TAMED = "MaidSpellStarWitchVictoryTamed";
    public static final String HIDDEN_RETREAT_MAID = "MaidSpellHiddenRetreatMaid";
    private static final String MODERN_SPELL_CONTAINER = "irons_spellbooks:spell_container";
    private static final String LEGACY_SPELL_CONTAINER = "ISB_Spells";
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

    public static CompoundTag createOrigin(String source, boolean preservePermanentEffects) {
        CompoundTag origin = new CompoundTag();
        origin.putInt("Version", ORIGIN_VERSION);
        origin.putString("Source", source);
        origin.putBoolean("PreservePermanentEffects", preservePermanentEffects);
        return origin;
    }

    /** Accept either persistent data itself, full entity NBT, or TLM's MaidInfo. */
    public static CompoundTag getOrigin(CompoundTag data) {
        CompoundTag persistent = data.getCompound("ForgeData");
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
                || data.getCompound("ForgeData").getBoolean(HIDDEN_RETREAT_MAID)
                || HIDDEN_RETREAT_SOURCE.equals(getOrigin(data).getString("Source"));
    }

    public static boolean isHiddenRetreatMaid(EntityMaid maid) {
        return isHiddenRetreatMaid(maid.getPersistentData());
    }

    /** Upgrade only Iron's Spellbooks spell-book items carried by a Star Witch maid. */
    public static void upgradeStarWitchSpellBooks(EntityMaid maid) {
        if (!isStarWitchVictoryTamed(maid)) {
            return;
        }
        for (int slot = 0; slot < maid.getMaidInv().getSlots(); slot++) {
            upgradeSpellBook(maid.getMaidInv().getStackInSlot(slot));
        }
    }

    /** Upgrade the serialized inventory in a reward soul charm before it is spawned. */
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
            upgradeContainer(item.getCompound("tag"), MODERN_SPELL_CONTAINER);
            upgradeContainer(item.getCompound("tag"), LEGACY_SPELL_CONTAINER);
        }
    }

    public static boolean isStarWitchVictoryTamed(EntityMaid maid) {
        return maid != null && maid.getPersistentData().getBoolean(STAR_WITCH_VICTORY_TAMED);
    }

    /** Copy markers into TLM's transport data before a maid becomes an item. */
    public static void writeTransportData(EntityMaid maid, CompoundTag data) {
        CompoundTag persistent = maid.getPersistentData();
        copyToTransport(persistent, data);
        // Death film deliberately strips ActiveEffects before posting ToItem.
        // Restore only infinite effects for maids explicitly opting into this protocol.
        if (preservesPermanentEffects(persistent) && !data.contains("ActiveEffects", Tag.TAG_LIST)) {
            ListTag effects = new ListTag();
            maid.getActiveEffects().forEach(effect -> effects.add(effect.save(new CompoundTag())));
            restorePermanentEffects(persistent, effects, data);
        }
    }

    static void restorePermanentEffects(CompoundTag persistent, ListTag activeEffects, CompoundTag data) {
        if (!preservesPermanentEffects(persistent) || data.contains("ActiveEffects", Tag.TAG_LIST)) {
            return;
        }
        ListTag permanent = new ListTag();
        for (Tag element : activeEffects) {
            if (element instanceof CompoundTag effect && effect.getInt("Duration") == -1) {
                permanent.add(effect.copy());
            }
        }
        if (!permanent.isEmpty()) {
            data.put("ActiveEffects", permanent);
        }
    }

    static void copyToTransport(CompoundTag persistent, CompoundTag data) {
        copyBoolean(persistent, data, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistent, data, HIDDEN_RETREAT_MAID);
        copyOrigin(persistent, data);
        CompoundTag forgeData = data.getCompound("ForgeData");
        copyBoolean(persistent, forgeData, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(persistent, forgeData, HIDDEN_RETREAT_MAID);
        copyOrigin(persistent, forgeData);
        if (!forgeData.isEmpty()) {
            data.put("ForgeData", forgeData);
        }
    }

    /** Restore markers after TLM creates a maid from a film or smart slab. */
    public static void readTransportData(EntityMaid maid, CompoundTag data) {
        restoreTransportData(data, maid.getPersistentData());
    }

    static void restoreTransportData(CompoundTag data, CompoundTag persistent) {
        copyBoolean(data, persistent, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(data, persistent, HIDDEN_RETREAT_MAID);
        copyOrigin(data, persistent);
        CompoundTag forgeData = data.getCompound("ForgeData");
        copyBoolean(forgeData, persistent, STAR_WITCH_VICTORY_TAMED);
        copyBoolean(forgeData, persistent, HIDDEN_RETREAT_MAID);
        copyOrigin(forgeData, persistent);
        if (isHiddenRetreatMaid(persistent)) {
            persistent.putBoolean(HIDDEN_RETREAT_MAID, true);
            if (!persistent.contains(ORIGIN, Tag.TAG_COMPOUND)) {
                persistent.put(ORIGIN, createOrigin(HIDDEN_RETREAT_SOURCE, false));
            }
        }
        // ToMaid runs before maid.load(); merge the restored fields into the input too,
        // otherwise Entity.load replaces persistent data and can erase the restoration.
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
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !"irons_spellbooks".equals(id.getNamespace())
                || !id.getPath().contains("spell_book")) {
            return;
        }
        CompoundTag itemTag = stack.getTag();
        if (itemTag == null) {
            return;
        }
        upgradeContainer(itemTag, MODERN_SPELL_CONTAINER);
        upgradeContainer(itemTag, LEGACY_SPELL_CONTAINER);
    }

    private static void upgradeContainer(CompoundTag itemTag, String key) {
        if (itemTag.contains(key, CompoundTag.TAG_COMPOUND)) {
            itemTag.getCompound(key).putInt(MAX_SPELLS, STAR_WITCH_SPELL_SLOTS);
        }
    }
}
