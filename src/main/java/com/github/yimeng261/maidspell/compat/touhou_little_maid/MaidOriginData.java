package com.github.yimeng261.maidspell.compat.touhou_little_maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** 本模组内容产出的女仆的来源标记，存在女仆的持久数据里。 */
public final class MaidOriginData {
    public static final String ORIGIN = "MaidSpellOrigin";
    public static final int ORIGIN_VERSION = 1;
    public static final String PRESET_EFFECTS = "PresetEffects";
    public static final String HIDDEN_RETREAT_SOURCE = "touhou_little_maid_spell:hidden_retreat";
    public static final String STAR_WITCH_VICTORY_TAMED = "MaidSpellStarWitchVictoryTamed";
    public static final String HIDDEN_RETREAT_MAID = "MaidSpellHiddenRetreatMaid";
    private static final String SPELL_CONTAINER = "irons_spellbooks:spell_container";
    private static final String PERSISTENT_DATA = "NeoForgeData";
    private static final String ACTIVE_EFFECTS = "active_effects";
    private static final String EFFECT_DURATION = "duration";
    private static final String EFFECT_ID = "id";
    private static final String HIDDEN_EFFECT = "hidden_effect";
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
        return createOrigin(source, preservePermanentEffects, new ListTag());
    }

    /** 初始效果列表只能来自结构里的初始数据，不能取自正在运行的女仆；PresetEffects 只保留其中无限时长的效果。 */
    public static CompoundTag createOrigin(String source, boolean preservePermanentEffects, ListTag initialEffects) {
        CompoundTag origin = new CompoundTag();
        origin.putInt("Version", ORIGIN_VERSION);
        origin.putString("Source", source);
        origin.putBoolean("PreservePermanentEffects", preservePermanentEffects);
        origin.put(PRESET_EFFECTS, collectPresetEffects(initialEffects));
        return origin;
    }

    /** 参数可以是持久数据本身、完整实体 NBT 或 TLM 的 MaidInfo，返回来源标记的副本。 */
    public static CompoundTag getOrigin(CompoundTag data) {
        return originIn(data).copy();
    }

    /** 与 {@link #getOrigin} 取同一处来源标记，但返回原对象而不是副本。 */
    private static CompoundTag originIn(CompoundTag data) {
        CompoundTag persistent = data.getCompound(PERSISTENT_DATA);
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

    /** 只在构造结构模板实体时捕获，普通的实体加载不走这里。 */
    public static void captureTemplatePresetEffects(CompoundTag entityData) {
        if (!"touhou_little_maid:maid".equals(entityData.getString("id"))) {
            return;
        }
        CompoundTag origin = getOrigin(entityData);
        if (!validOrigin(origin)) {
            return;
        }
        // 以本次放置的模板数据为准，数据包覆盖过的模板也一样。
        origin.put(PRESET_EFFECTS, collectPresetEffects(entityData.getList(ACTIVE_EFFECTS, Tag.TAG_COMPOUND)));
        writeOrigin(entityData, origin);
    }

    /** 取出无限时长且 id 合法的效果，按 id 去重；对应的效果模组暂时缺失时也照样保留名字。 */
    static ListTag collectPresetEffects(ListTag initialEffects) {
        ListTag preset = new ListTag();
        Set<String> seen = new HashSet<>();
        for (Tag element : initialEffects) {
            CompoundTag effect = (CompoundTag) element;
            String id = effect.getString(EFFECT_ID);
            if (effect.getInt(EFFECT_DURATION) == MobEffectInstance.INFINITE_DURATION && !id.isEmpty()
                    && ResourceLocation.tryParse(id) != null && seen.add(id)) {
                CompoundTag copy = effect.copy();
                copy.remove(HIDDEN_EFFECT);
                preset.add(copy);
            }
        }
        return preset;
    }

    /** 没有 PresetEffects 的旧标记按模板预设升级，不去采样女仆当前的效果。 */
    static void initializeLegacyPresets(CompoundTag data, @Nullable ListTag templatePresets) {
        CompoundTag origin = originIn(data);
        if (validOrigin(origin) && !origin.contains(PRESET_EFFECTS) && templatePresets != null) {
            origin.put(PRESET_EFFECTS, templatePresets.copy());
        }
    }

    /** 带标记的结构女仆的初始无限效果被移除或覆盖后，把它们补回来。 */
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
            if (expected == null || !expected.isInfiniteDuration()) {
                continue;
            }
            MobEffectInstance current = maid.getEffect(expected.getEffect());
            if (needsPresetEffect(current, expected)) {
                // 原版会把初始的无限效果垫在更强的有限效果下面合并；这里不移除任何效果，
                // 也不绕过其他模组对效果适用性的判断。
                maid.addEffect(expected);
            }
        }
    }

    static boolean needsPresetEffect(@Nullable MobEffectInstance current, MobEffectInstance expected) {
        return current == null || current.getAmplifier() < expected.getAmplifier()
                || !current.isInfiniteDuration();
    }

    /** 效果模组缺失或数据损坏时返回 null。 */
    @Nullable
    static MobEffectInstance loadPresetEffect(CompoundTag effect) {
        ResourceLocation id = effectId(effect);
        if (effect.getInt(EFFECT_DURATION) != MobEffectInstance.INFINITE_DURATION
                || id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            return null;
        }
        return MobEffectInstance.load(effect.copy());
    }

    @Nullable
    private static ResourceLocation effectId(CompoundTag effect) {
        if (effect.contains(EFFECT_ID, Tag.TAG_STRING)) {
            return ResourceLocation.tryParse(effect.getString(EFFECT_ID));
        }
        return null;
    }

    private static void writeOrigin(CompoundTag data, CompoundTag origin) {
        if (data.contains(PERSISTENT_DATA, Tag.TAG_COMPOUND) || data.contains("id", Tag.TAG_STRING)) {
            if (!data.contains(PERSISTENT_DATA, Tag.TAG_COMPOUND)) {
                data.put(PERSISTENT_DATA, new CompoundTag());
            }
            data.getCompound(PERSISTENT_DATA).put(ORIGIN, origin);
            if (data.contains(ORIGIN)) {
                data.put(ORIGIN, origin.copy());
            }
        } else {
            data.put(ORIGIN, origin);
        }
    }

    public static boolean isHiddenRetreatMaid(CompoundTag data) {
        return data.getBoolean(HIDDEN_RETREAT_MAID)
                || data.getCompound(PERSISTENT_DATA).getBoolean(HIDDEN_RETREAT_MAID)
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
     * 不会被这条逻辑顺手升级。书里的法术列表、等级与其它容器字段一概不动，
     * 只改 {@code irons_spellbooks:spell_container} 组件里的 {@code maxSpells}，
     * 因为铁魔法解码时槽位数组长度完全取自这个值。
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
            CompoundTag components = item.getCompound("components");
            if (components.contains(SPELL_CONTAINER, Tag.TAG_COMPOUND)) {
                components.getCompound(SPELL_CONTAINER).putInt(MAX_SPELLS, STAR_WITCH_SPELL_SLOTS);
            }
        }
    }

    public static boolean isStarWitchVictoryTamed(EntityMaid maid) {
        return maid != null && maid.getPersistentData().getBoolean(STAR_WITCH_VICTORY_TAMED);
    }

    /** 女仆变成物品前，把标记写进 TLM 的转运数据。 */
    public static void writeTransportData(EntityMaid maid, CompoundTag data) {
        CompoundTag persistent = maid.getPersistentData();
        initializeLegacyPresets(persistent, MaidOriginPresetReloadListener.find(originIn(persistent).getString("Source")));
        copyToTransport(persistent, data);
        // 死亡胶片会在发出 ToItem 事件前清掉状态效果；只给声明保留永久效果的女仆补回无限时长的效果。
        if (preservesPermanentEffects(persistent) && !data.contains(ACTIVE_EFFECTS, Tag.TAG_LIST)) {
            ListTag effects = new ListTag();
            maid.getActiveEffects().forEach(effect -> effects.add(effect.save()));
            restorePermanentEffects(persistent, effects, data);
        }
        mergePresetEffects(persistent, data);
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

    /** 把预设里转运数据缺少的无限效果补进 active_effects，已有的效果原样保留。 */
    static void mergePresetEffects(CompoundTag persistent, CompoundTag data) {
        CompoundTag origin = originIn(persistent);
        if (!validOrigin(origin)) {
            return;
        }
        ListTag current = data.contains(ACTIVE_EFFECTS, Tag.TAG_LIST)
                ? data.getList(ACTIVE_EFFECTS, Tag.TAG_COMPOUND).copy() : new ListTag();
        ListTag preset = origin.getList(PRESET_EFFECTS, Tag.TAG_COMPOUND);
        for (Tag element : preset) {
            CompoundTag expected = (CompoundTag) element;
            ResourceLocation expectedId = effectId(expected);
            if (expectedId == null || expected.getInt(EFFECT_DURATION) != MobEffectInstance.INFINITE_DURATION) {
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
                copy.remove(HIDDEN_EFFECT);
                current.add(copy);
            }
        }
        if (!current.isEmpty()) {
            data.put(ACTIVE_EFFECTS, current);
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
}
