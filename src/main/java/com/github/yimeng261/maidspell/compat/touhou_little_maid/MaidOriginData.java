package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/** Persistent origin markers for maids created by MaidSpell content. */
public final class MaidOriginData {
    public static final String ORIGIN = "MaidSpellOrigin";
    public static final int ORIGIN_VERSION = 1;
    public static final String PRESET_EFFECTS = "PresetEffects";
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
        return createOrigin(source, preservePermanentEffects, new ListTag());
    }

    /** The effect list must come from initial structure data, never a running maid. */
    public static CompoundTag createOrigin(String source, boolean preservePermanentEffects, ListTag initialEffects) {
        CompoundTag origin = new CompoundTag();
        origin.putInt("Version", ORIGIN_VERSION);
        origin.putString("Source", source);
        origin.putBoolean("PreservePermanentEffects", preservePermanentEffects);
        origin.put(PRESET_EFFECTS, collectPresetEffects(initialEffects));
        return origin;
    }

    /** Accept either persistent data itself, full entity NBT, or TLM's MaidInfo. */
    public static CompoundTag getOrigin(CompoundTag data) {
        return originIn(data).copy();
    }

    private static CompoundTag originIn(CompoundTag data) {
        CompoundTag persistent = data.getCompound("ForgeData");
        return persistent.contains(ORIGIN, Tag.TAG_COMPOUND)
                ? persistent.getCompound(ORIGIN) : data.getCompound(ORIGIN);
    }

    public static boolean preservesPermanentEffects(CompoundTag data) {
        CompoundTag origin = originIn(data);
        return validOrigin(origin) && origin.getBoolean("PreservePermanentEffects");
    }

    private static boolean validOrigin(CompoundTag origin) {
        return origin.getInt("Version") >= ORIGIN_VERSION
                && !origin.getString("Source").isEmpty()
                && ResourceLocation.tryParse(origin.getString("Source")) != null;
    }

    /** Capture only while constructing the structure-template entity, never during ordinary loading. */
    public static void captureTemplatePresetEffects(CompoundTag entityData) {
        if (!"touhou_little_maid:maid".equals(entityData.getString("id"))) {
            return;
        }
        CompoundTag origin = getOrigin(entityData);
        if (!validOrigin(origin)) {
            return;
        }
        // Always use this placement's template data, including data-pack overrides.
        origin.put(PRESET_EFFECTS, collectPresetEffects(entityData.getList("ActiveEffects", Tag.TAG_COMPOUND)));
        writeOrigin(entityData, origin);
    }

    static ListTag collectPresetEffects(ListTag initialEffects) {
        ListTag preset = new ListTag();
        Set<String> seen = new HashSet<>();
        for (Tag element : initialEffects) {
            CompoundTag effect = (CompoundTag) element;
            // Keep names even when an optional effect mod is currently absent.
            String id = effect.getString("forge:id");
            if (effect.getInt("Duration") == -1 && !id.isEmpty()
                    && ResourceLocation.tryParse(id) != null && seen.add(id)) {
                CompoundTag copy = effect.copy();
                copy.remove("Id");
                copy.remove("HiddenEffect");
                preset.add(copy);
            }
        }
        return preset;
    }

    /** Upgrade a legacy marker using template presets, without sampling active effects. */
    static void initializeLegacyPresets(CompoundTag data, @Nullable ListTag templatePresets) {
        CompoundTag origin = originIn(data);
        if (validOrigin(origin) && !origin.contains(PRESET_EFFECTS) && templatePresets != null) {
            origin.put(PRESET_EFFECTS, templatePresets.copy());
        }
    }

    /** Restore marked structure maids' original infinite effects after removal or replacement. */
    public static void maintainPresetEffects(EntityMaid maid) {
        if (maid.level().isClientSide() || !maid.isAlive() || maid.isRemoved()) {
            return;
        }
        CompoundTag persistent = maid.getPersistentData();
        CompoundTag origin = originIn(persistent);
        if (!validOrigin(origin)) {
            return;
        }
        initializeLegacyPresets(persistent, MaidOriginPresetReloadListener.find(origin.getString("Source")));
        ListTag preset = origin.getList(PRESET_EFFECTS, Tag.TAG_COMPOUND);
        for (Tag element : preset) {
            MobEffectInstance expected = loadPresetEffect((CompoundTag) element);
            if (expected == null || expected.getDuration() != -1) {
                continue;
            }
            MobEffectInstance current = maid.getEffect(expected.getEffect());
            if (needsPresetEffect(current, expected)) {
                // Vanilla merges the initial infinite effect underneath stronger finite
                // effects. Do not remove effects or bypass other mods' applicability hooks.
                maid.addEffect(expected);
            }
        }
    }

    static boolean needsPresetEffect(@Nullable MobEffectInstance current, MobEffectInstance expected) {
        return current == null || current.getAmplifier() < expected.getAmplifier()
                || current.getDuration() != -1;
    }

    @Nullable
    static MobEffectInstance loadPresetEffect(CompoundTag effect) {
        ResourceLocation id = ResourceLocation.tryParse(effect.getString("forge:id"));
        if (effect.getInt("Duration") != -1 || effect.getString("forge:id").isEmpty()
                || id == null || !ForgeRegistries.MOB_EFFECTS.containsKey(id)) {
            return null;
        }
        // Guard the named registry lookup: load() may otherwise fall back to a stale
        // numeric Id when the original effect mod is absent in the target pack.
        return MobEffectInstance.load(effect.copy());
    }

    private static ResourceLocation effectId(CompoundTag effect) {
        if (effect.contains("forge:id", Tag.TAG_STRING)) {
            return ResourceLocation.tryParse(effect.getString("forge:id"));
        }
        return null;
    }

    private static void writeOrigin(CompoundTag data, CompoundTag origin) {
        if (data.contains("ForgeData", Tag.TAG_COMPOUND) || data.contains("id", Tag.TAG_STRING)) {
            if (!data.contains("ForgeData", Tag.TAG_COMPOUND)) {
                data.put("ForgeData", new CompoundTag());
            }
            data.getCompound("ForgeData").put(ORIGIN, origin);
            if (data.contains(ORIGIN)) {
                data.put(ORIGIN, origin.copy());
            }
        } else {
            data.put(ORIGIN, origin);
        }
    }

    public static boolean isHiddenRetreatMaid(CompoundTag data) {
        return data.getBoolean(HIDDEN_RETREAT_MAID)
                || data.getCompound("ForgeData").getBoolean(HIDDEN_RETREAT_MAID)
                || HIDDEN_RETREAT_SOURCE.equals(getOrigin(data).getString("Source"));
    }

    public static boolean isHiddenRetreatMaid(EntityMaid maid) {
        return isHiddenRetreatMaid(maid.getPersistentData());
    }

    /**
     * 把出厂魂符序列化背包里的铁魔法法术书容量抬到 {@link #STAR_WITCH_SPELL_SLOTS}。
     *
     * <p><b>只在出厂时调用一次</b>（{@link StellarWitchStarterMaid#createSoulCharm}），
     * 不参与女仆入世流程：出厂之后玩家再往她背包里塞的法术书保持物品原本的容量，
     * 不会被这条逻辑顺手升级。书里的法术列表、等级与其它容器字段一概不动，只写 {@code maxSpells}；
     * 现代键 {@code irons_spellbooks:spell_container} 与旧版 {@code ISB_Spells} 是两个键都写，
     * 因为上游解析时槽位数组长度完全取自这个值（旧版解码还会静默丢弃越界条目）。
     *
     * <p>模板本身已经写着 20；这条逻辑是给被数据包覆盖的模板、以及未来改版兜底。
     */
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
        initializeLegacyPresets(persistent, MaidOriginPresetReloadListener.find(originIn(persistent).getString("Source")));
        copyToTransport(persistent, data);
        // Retain the original transport contract for current infinite effects.
        if (preservesPermanentEffects(persistent) && !data.contains("ActiveEffects", Tag.TAG_LIST)) {
            ListTag effects = new ListTag();
            maid.getActiveEffects().forEach(effect -> effects.add(effect.save(new CompoundTag())));
            restorePermanentEffects(persistent, effects, data);
        }
        mergePresetEffects(persistent, data);
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

    static void mergePresetEffects(CompoundTag persistent, CompoundTag data) {
        CompoundTag origin = originIn(persistent);
        if (!validOrigin(origin)) {
            return;
        }
        ListTag current = data.contains("ActiveEffects", Tag.TAG_LIST)
                ? data.getList("ActiveEffects", Tag.TAG_COMPOUND).copy() : new ListTag();
        ListTag preset = origin.getList(PRESET_EFFECTS, Tag.TAG_COMPOUND);
        for (Tag element : preset) {
            CompoundTag expected = (CompoundTag) element;
            ResourceLocation expectedId = effectId(expected);
            if (expectedId == null || expected.getInt("Duration") != -1) {
                continue;
            }
            boolean present = false;
            for (Tag existing : current) {
                if (existing instanceof CompoundTag existingEffect && expectedId.equals(effectId(existingEffect))) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                CompoundTag copy = expected.copy();
                copy.remove("Id");
                copy.remove("HiddenEffect");
                current.add(copy);
            }
        }
        if (!current.isEmpty()) {
            data.put("ActiveEffects", current);
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
        mergePresetEffects(persistent, data);
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

    private static void upgradeContainer(CompoundTag itemTag, String key) {
        if (itemTag.contains(key, CompoundTag.TAG_COMPOUND)) {
            itemTag.getCompound(key).putInt(MAX_SPELLS, STAR_WITCH_SPELL_SLOTS);
        }
    }
}
