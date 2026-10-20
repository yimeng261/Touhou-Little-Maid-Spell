package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;

/** 按功能拆分后的通用配置门面：各配置文件加载后把值写入这里的静态字段，供其余代码读取。 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class Config {
    public static final ModConfigSpec COMBAT_SPEC = ConfigCombat.SPEC;
    public static final ModConfigSpec BAUBLES_SPEC = ConfigBaubles.SPEC;
    public static final ModConfigSpec RETREAT_SPEC = ConfigRetreat.SPEC;
    public static final ModConfigSpec COMPAT_SPEC = ConfigCompat.SPEC;
    public static final ModConfigSpec WINEFOX_SPEC = ConfigWinefox.SPEC;
    public static final ModConfigSpec WORLDGEN_SPEC = ConfigWorldgen.SPEC;

    public static double maxSpellRange;
    public static double meleeRange;
    public static double farRange;
    public static double spellDamageMultiplier;
    public static double coolDownMultiplier;
    public static int meleeAttackInterval;
    public static int farAttackInterval;
    public static List<String> ironsSpellBlacklist;
    public static volatile boolean powerfulFriendlyFireProtection = false;

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
    public static boolean disableHostileMobSpawning;
    public static boolean allowAllStructures;
    public static Set<ResourceLocation> allowedStructures;
    public static boolean allowMobSpawnsInRetreat;
    public static boolean allowHostileMobSpawnsInRetreat;
    public static int retreatRecordRetentionDays;
    public static boolean autoInstallTlmModelPack;
    public static volatile boolean enableStarfallGarden = true;

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
    private static boolean worldgenLoaded;

    private Config() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        onConfigEvent(event);
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        onConfigEvent(event);
    }

    private static void onConfigEvent(ModConfigEvent event) {
        Object spec = event.getConfig().getSpec();
        if (spec == COMBAT_SPEC) {
            ConfigCombat.load();
            combatLoaded = true;
        } else if (spec == BAUBLES_SPEC) {
            ConfigBaubles.load();
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
        } else if (spec == WORLDGEN_SPEC) {
            ConfigWorldgen.load();
            worldgenLoaded = true;
        } else {
            return;
        }

        if (combatLoaded && baublesLoaded && retreatLoaded && compatLoaded && winefoxLoaded && worldgenLoaded) {
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
}
