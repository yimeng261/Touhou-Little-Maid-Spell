package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;

/** Compatibility facade for the split common configuration specs. */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    public static final ForgeConfigSpec COMBAT_SPEC = ConfigCombat.SPEC;
    public static final ForgeConfigSpec BAUBLES_SPEC = ConfigBaubles.SPEC;
    public static final ForgeConfigSpec RETREAT_SPEC = ConfigRetreat.SPEC;
    public static final ForgeConfigSpec COMPAT_SPEC = ConfigCompat.SPEC;
    public static final ForgeConfigSpec WINEFOX_SPEC = ConfigWinefox.SPEC;

    public static double maxSpellRange;
    public static double meleeRange;
    public static double farRange;
    public static double spellDamageMultiplier;
    public static double coolDownMultiplier;
    public static int meleeAttackInterval;
    public static int farAttackInterval;
    public static List<String> ironsSpellBlacklist;

    public static double silverCercisTrueDamageMultiplier;
    public static double springRingMaxDamageBonus;
    public static double flowCoreHealthRegenRate;
    public static double bleedingHeartHealRatio;
    public static double flowCoreDamageReduction;
    public static double doubleHeartChainShareRatio;
    public static double doubleHeartChainMaxDistance;
    public static double quickChantRingCooldownReduction;
    public static int rockCrystalKnockbackResistance;
    public static int flowCoreTickInterval;
    public static int silverCercisTriggerCount;
    public static int silverCercisCooldownTicks;
    public static int woundRimeBladeRecordTimes;
    public static double hairpinBeneficialEffectExtension;
    public static int hairpinMinExtensionTicks;
    public static int fragrantIngenuityFavorabilityGain;
    public static int fragrantIngenuityBuffDuration;
    public static List<String> fragrantIngenuityEffectBlacklist;
    public static int springBloomReturnMaxStacks;
    public static int springBloomReturnStackDurationTicks;
    public static int springBloomReturnGainCooldownTicks;
    public static int springBloomReturnTriggerCooldownTicks;
    public static double springBloomReturnDamageThreshold;
    public static double springBloomReturnDamageThresholdRatio;
    public static double springBloomReturnHealRatio;
    public static double springBloomReturnCooldownRefundRatio;
    public static double chaosBookTrueDamageMin;
    public static double chaosBookTrueDamagePercent;
    public static int chaosBookDamageSplitCount;
    public static double chaosBookMinSplitDamage;
    public static double soulBookDamageThresholdPercent;
    public static int soulBookDamageIntervalThreshold;
    public static double floatingFoxLeafOwnerRange;
    public static double moltenFoxLeafOwnerRange;
    public static List<String> dreamCrystalEffectBlacklist;
    public static boolean dreamCrystalUseEffectWhitelist;
    public static List<String> dreamCrystalEffectWhitelist;
    public static boolean dreamCrystalExtraTrueDamageEnabled;

    public static boolean enablePrivateDimensions;
    public static boolean enableSharedQuotaLimit;
    public static boolean allowMobSpawnsInRetreat;
    public static boolean allowHostileMobSpawnsInRetreat;
    public static int retreatRecordRetentionDays;
    public static boolean autoInstallTlmModelPack;

    public static double winefoxNonPlayerDamageShareLimit;
    public static boolean winefoxChallengeConfigEnabled;
    public static boolean winefoxTrueDamageRestrictsReward;
    public static double winefoxMaxHealth;
    public static double winefoxArmor = 12.0D;
    public static double winefoxDamageMultiplier;
    public static double winefoxSpellPowerMultiplier;
    public static double winefoxHitDamageCapRatio;
    public static int winefoxHitIntervalTicks;
    public static double winefoxMaidDamageMultiplier;
    public static double winefoxDamageToMaidMultiplier;
    public static double winefoxPhaseTwoDamageMultiplier;
    public static boolean winefoxAdaptiveDamageEnabled;
    public static boolean winefoxDamageDebugLogging;
    public static double winefoxAdaptiveTargetDpsRatio;
    public static int winefoxAdaptiveWindowTicks;
    public static double winefoxAdaptiveActivationRatio;
    public static double winefoxAdaptiveStartMultiplier;
    public static double winefoxAdaptiveMinMultiplier;
    public static double winefoxAdaptiveSaturationRatio;
    public static double winefoxAdaptiveFinalBudgetMultiplier;
    public static double winefoxDuelSurvivalFloor;
    public static int winefoxInvisibilitySearchTicks = 200;

    private static boolean combatLoaded;
    private static boolean baublesLoaded;
    private static boolean retreatLoaded;
    private static boolean compatLoaded;
    private static boolean winefoxLoaded;

    private Config() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        Object spec = event.getConfig().getSpec();
        if (spec == COMBAT_SPEC) {
            ConfigCombat.load();
            combatLoaded = true;
        } else if (spec == BAUBLES_SPEC) {
            ConfigBaubles.load(event);
            baublesLoaded = true;
        } else if (spec == RETREAT_SPEC) {
            ConfigRetreat.load();
            retreatLoaded = true;
        } else if (spec == COMPAT_SPEC) {
            ConfigCompat.load();
            compatLoaded = true;
        } else if (spec == WINEFOX_SPEC) {
            ConfigWinefox.load();
            winefoxLoaded = true;
        } else {
            return;
        }

        if (combatLoaded && baublesLoaded && retreatLoaded && compatLoaded && winefoxLoaded) {
            applyRuntimeValues();
        }
    }

    private static void applyRuntimeValues() {
        SpellCombatMeleeTask.setSpellRange((float) maxSpellRange);
        SpellCombatFarTask.setSpellRange((float) maxSpellRange);
        SimplifiedSpellCaster.MELEE_RANGE = (float) meleeRange;
        SimplifiedSpellCaster.FAR_RANGE = (float) farRange;
        Global.resetCommonDamageCalc();
        Global.resetCommonCoolDownCalc();
        DreamCatCrystalBauble.registerCommonCallbacks();
    }

    static void migrateDreamCrystalEffectBlacklist(
            ModConfigEvent event,
            ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist,
            ForgeConfigSpec.IntValue migrationVersion,
            List<String> defaults) {
        if (migrationVersion.get() >= 1) {
            return;
        }
        List<? extends String> configured = blacklist.get();
        List<String> previousDefaults = defaults.subList(0, defaults.size() - 1);
        if (configured.equals(previousDefaults)) {
            List<String> migrated = new ArrayList<>(configured);
            migrated.add(defaults.get(defaults.size() - 1));
            blacklist.set(migrated);
        }
        migrationVersion.set(1);
        event.getConfig().save();
    }
}
