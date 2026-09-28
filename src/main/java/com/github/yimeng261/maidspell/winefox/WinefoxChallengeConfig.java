package com.github.yimeng261.maidspell.winefox;

import com.github.yimeng261.maidspell.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-invitation settings for the next Stellar Witch challenge.
 *
 * <p>The values are deliberately kept on the Starglint Dagger. This makes the
 * setting portable and, more importantly, gives it a clear one-challenge
 * lifetime when the boss consumes the invitation data.</p>
 */
public record WinefoxChallengeConfig(
        double maxHealth,
        double damageMultiplier,
        double spellPowerMultiplier,
        double hitDamageCapRatio,
        int hitIntervalTicks,
        double maidDamageMultiplier,
        double damageToMaidMultiplier,
        double phaseTwoDamageMultiplier,
        boolean imitatePlayerSpells,
        boolean imitateMaidSpells,
        int imitateChancePercent,
        List<WinefoxSpellChoice> phaseOneSpells,
        List<WinefoxSpellChoice> phaseTwoSpells,
        boolean spellPoolsConfigured) {

    public static final String TAG = "MaidSpellWinefoxNextChallenge";
    private static final int MAX_INTERVAL = 200;

    public static double minimumBasicValue(int index) {
        return minimumBasicValue(index, defaults());
    }

    public static double minimumBasicValue(int index, WinefoxChallengeConfig defaults) {
        return switch (index) {
            case 0 -> Math.max(1.0D, defaults.maxHealth / 2.0D);
            case 1 -> defaults.damageMultiplier / 2.0D;
            case 2 -> defaults.spellPowerMultiplier / 2.0D;
            case 3 -> defaults.hitDamageCapRatio / 2.0D;
            case 4 -> Math.ceil(defaults.hitIntervalTicks / 2.0D);
            case 5 -> defaults.maidDamageMultiplier / 2.0D;
            case 6 -> defaults.damageToMaidMultiplier / 2.0D;
            case 7 -> defaults.phaseTwoDamageMultiplier / 2.0D;
            default -> throw new IllegalArgumentException("Unknown Winefox setting: " + index);
        };
    }

    public static int minimumImitateChancePercent() {
        return minimumImitateChancePercent(defaults());
    }

    public static int minimumImitateChancePercent(WinefoxChallengeConfig defaults) {
        return (int) Math.ceil(defaults.imitateChancePercent / 2.0D);
    }

    public static WinefoxChallengeConfig defaults() {
        return new WinefoxChallengeConfig(
                Config.winefoxMaxHealth,
                Config.winefoxDamageMultiplier,
                Config.winefoxSpellPowerMultiplier,
                Config.winefoxHitDamageCapRatio,
                Config.winefoxHitIntervalTicks,
                Config.winefoxMaidDamageMultiplier,
                Config.winefoxDamageToMaidMultiplier,
                Config.winefoxPhaseTwoDamageMultiplier,
                false,
                false,
                25,
                List.of(),
                List.of(),
                false);
    }

    public WinefoxChallengeConfig sanitized() {
        return sanitized(defaults());
    }

    public WinefoxChallengeConfig sanitized(WinefoxChallengeConfig defaults) {
        return new WinefoxChallengeConfig(
                clamp(maxHealth, minimumBasicValue(0, defaults), 100_000.0D),
                clamp(damageMultiplier, minimumBasicValue(1, defaults), 100.0D),
                clamp(spellPowerMultiplier, minimumBasicValue(2, defaults), 1_000.0D),
                clamp(hitDamageCapRatio, minimumBasicValue(3, defaults), 1.0D),
                Math.max((int) minimumBasicValue(4, defaults), Math.min(MAX_INTERVAL, hitIntervalTicks)),
                clamp(maidDamageMultiplier, minimumBasicValue(5, defaults), 1.0D),
                clamp(damageToMaidMultiplier, minimumBasicValue(6, defaults), 100.0D),
                clamp(phaseTwoDamageMultiplier, minimumBasicValue(7, defaults), 1.0D),
                imitatePlayerSpells,
                imitateMaidSpells,
                Math.max(minimumImitateChancePercent(defaults), Math.min(100, imitateChancePercent)),
                sanitizeSpells(phaseOneSpells),
                sanitizeSpells(phaseTwoSpells),
                spellPoolsConfigured);
    }

    public static WinefoxChallengeConfig fromItem(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG, CompoundTag.TAG_COMPOUND)) {
            return defaults();
        }
        return fromTag(root.getCompound(TAG));
    }

    public static boolean hasPendingConfig(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(TAG, Tag.TAG_COMPOUND);
    }

    public static WinefoxChallengeConfig takeFromItem(ItemStack stack) {
        WinefoxChallengeConfig result = fromItem(stack);
        clearFromItem(stack);
        return result;
    }

    public static void writeToItem(ItemStack stack, WinefoxChallengeConfig config) {
        stack.getOrCreateTag().put(TAG, config.sanitized().toTag());
    }

    public static void clearFromItem(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) {
            return;
        }
        root.remove(TAG);
        if (root.isEmpty()) {
            stack.setTag(null);
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("MaxHealth", maxHealth);
        tag.putDouble("DamageMultiplier", damageMultiplier);
        tag.putDouble("SpellPowerMultiplier", spellPowerMultiplier);
        tag.putDouble("HitDamageCapRatio", hitDamageCapRatio);
        tag.putInt("HitIntervalTicks", hitIntervalTicks);
        tag.putDouble("MaidDamageMultiplier", maidDamageMultiplier);
        tag.putDouble("DamageToMaidMultiplier", damageToMaidMultiplier);
        tag.putDouble("PhaseTwoDamageMultiplier", phaseTwoDamageMultiplier);
        tag.putBoolean("ImitatePlayerSpells", imitatePlayerSpells);
        tag.putBoolean("ImitateMaidSpells", imitateMaidSpells);
        tag.putInt("ImitateChancePercent", imitateChancePercent);
        tag.put("PhaseOneSpells", writeSpells(phaseOneSpells));
        tag.put("PhaseTwoSpells", writeSpells(phaseTwoSpells));
        tag.putBoolean("SpellPoolsConfigured", spellPoolsConfigured);
        return tag;
    }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeDouble(maxHealth);
        buffer.writeDouble(damageMultiplier);
        buffer.writeDouble(spellPowerMultiplier);
        buffer.writeDouble(hitDamageCapRatio);
        buffer.writeInt(hitIntervalTicks);
        buffer.writeDouble(maidDamageMultiplier);
        buffer.writeDouble(damageToMaidMultiplier);
        buffer.writeDouble(phaseTwoDamageMultiplier);
        buffer.writeBoolean(imitatePlayerSpells);
        buffer.writeBoolean(imitateMaidSpells);
        buffer.writeInt(imitateChancePercent);
        writeSpells(buffer, phaseOneSpells);
        writeSpells(buffer, phaseTwoSpells);
        buffer.writeBoolean(spellPoolsConfigured);
    }

    public static WinefoxChallengeConfig fromBuffer(FriendlyByteBuf buffer) {
        return readRawFromBuffer(buffer).sanitized();
    }

    public static WinefoxChallengeConfig readRawFromBuffer(FriendlyByteBuf buffer) {
        return new WinefoxChallengeConfig(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readInt(),
                readSpells(buffer),
                readSpells(buffer),
                buffer.readBoolean());
    }

    public static WinefoxChallengeConfig fromTag(CompoundTag tag) {
        WinefoxChallengeConfig fallback = defaults();
        return new WinefoxChallengeConfig(
                tag.contains("MaxHealth", CompoundTag.TAG_DOUBLE) ? tag.getDouble("MaxHealth") : fallback.maxHealth,
                tag.contains("DamageMultiplier", CompoundTag.TAG_DOUBLE) ? tag.getDouble("DamageMultiplier") : fallback.damageMultiplier,
                tag.contains("SpellPowerMultiplier", CompoundTag.TAG_DOUBLE) ? tag.getDouble("SpellPowerMultiplier") : fallback.spellPowerMultiplier,
                tag.contains("HitDamageCapRatio", CompoundTag.TAG_DOUBLE) ? tag.getDouble("HitDamageCapRatio") : fallback.hitDamageCapRatio,
                tag.contains("HitIntervalTicks", CompoundTag.TAG_INT) ? tag.getInt("HitIntervalTicks") : fallback.hitIntervalTicks,
                tag.contains("MaidDamageMultiplier", CompoundTag.TAG_DOUBLE) ? tag.getDouble("MaidDamageMultiplier") : fallback.maidDamageMultiplier,
                tag.contains("DamageToMaidMultiplier", CompoundTag.TAG_DOUBLE) ? tag.getDouble("DamageToMaidMultiplier") : fallback.damageToMaidMultiplier,
                tag.contains("PhaseTwoDamageMultiplier", CompoundTag.TAG_DOUBLE) ? tag.getDouble("PhaseTwoDamageMultiplier") : fallback.phaseTwoDamageMultiplier,
                tag.contains("ImitatePlayerSpells", CompoundTag.TAG_BYTE) && tag.getBoolean("ImitatePlayerSpells"),
                tag.contains("ImitateMaidSpells", CompoundTag.TAG_BYTE) && tag.getBoolean("ImitateMaidSpells"),
                tag.contains("ImitateChancePercent", CompoundTag.TAG_INT) ? tag.getInt("ImitateChancePercent") : fallback.imitateChancePercent,
                readSpells(tag.getList("PhaseOneSpells", Tag.TAG_COMPOUND)),
                readSpells(tag.getList("PhaseTwoSpells", Tag.TAG_COMPOUND)),
                tag.getBoolean("SpellPoolsConfigured") || tag.getList("PhaseOneSpells", Tag.TAG_COMPOUND).size() > 0
                        || tag.getList("PhaseTwoSpells", Tag.TAG_COMPOUND).size() > 0).sanitized();
    }

    private static List<WinefoxSpellChoice> sanitizeSpells(List<WinefoxSpellChoice> spells) {
        List<WinefoxSpellChoice> result = new ArrayList<>();
        if (spells != null) {
            for (WinefoxSpellChoice spell : spells) {
                if (result.size() == WinefoxSpellChoice.MAX_SLOTS_PER_PHASE) {
                    break;
                }
                if (spell == null) continue;
                WinefoxSpellChoice valid = WinefoxSpellChoice.valid(spell.id(), spell.level());
                if (valid != null) {
                    result.add(valid);
                }
            }
        }
        return List.copyOf(result);
    }

    private static ListTag writeSpells(List<WinefoxSpellChoice> spells) {
        ListTag list = new ListTag();
        sanitizeSpells(spells).forEach(spell -> list.add(spell.toTag()));
        return list;
    }

    private static List<WinefoxSpellChoice> readSpells(ListTag tags) {
        List<WinefoxSpellChoice> result = new ArrayList<>();
        for (int i = 0; i < Math.min(tags.size(), WinefoxSpellChoice.MAX_SLOTS_PER_PHASE); i++) {
            WinefoxSpellChoice choice = WinefoxSpellChoice.fromTag(tags.getCompound(i));
            if (choice != null) {
                result.add(choice);
            }
        }
        return List.copyOf(result);
    }

    private static void writeSpells(FriendlyByteBuf buffer, List<WinefoxSpellChoice> spells) {
        List<WinefoxSpellChoice> sanitized = sanitizeSpells(spells);
        buffer.writeVarInt(sanitized.size());
        sanitized.forEach(spell -> {
            buffer.writeUtf(spell.id(), 128);
            buffer.writeVarInt(spell.level());
        });
    }

    private static List<WinefoxSpellChoice> readSpells(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > WinefoxSpellChoice.MAX_SLOTS_PER_PHASE) {
            throw new IllegalArgumentException("Invalid Winefox spell count: " + count);
        }
        List<WinefoxSpellChoice> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WinefoxSpellChoice choice = WinefoxSpellChoice.valid(buffer.readUtf(128), buffer.readVarInt());
            if (choice != null) {
                result.add(choice);
            }
        }
        return List.copyOf(result);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
