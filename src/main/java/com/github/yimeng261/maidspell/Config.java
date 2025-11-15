package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 女仆法术战斗系统配置类
 * 管理所有可配置的参数和设置
 */
@EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ========== 战斗系统配置 ==========
    static {
        BUILDER.comment("战斗系统相关配置")
               .comment("Combat system configurations")
               .push("combat");
    }

    private static final ModConfigSpec.DoubleValue MAX_SPELL_RANGE = BUILDER
        .comment("最大法术攻击范围 (默认: 24.0)")
        .comment("Maximum spell attack range")
        .defineInRange("maxSpellRange", 24.0, 8.0, 64.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue MELEE_RANGE = BUILDER
        .comment("近战攻击范围 (默认: 2.5)")
        .comment("Melee attack range")
        .defineInRange("meleeRange", 2.5, 1.0, 5.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue FAR_RANGE = BUILDER
            .comment("远程攻击范围 (默认: 8.5)")
            .comment("Far attack range")
            .defineInRange("farRange", 8.5, 1.0, 20.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
        .comment("女仆伤害倍率 (默认: 1.0，仅在法术战斗任务下生效)")
        .comment("Maid damage multiplier(default:1.0,only effective on spellCombatTask)")
        .defineInRange("maidDamageMultiplier", 1.0, 0, 50.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue COOLDOWN_MULITIPLIER = BUILDER
            .comment("女仆法术冷却倍率 (默认: 1.0，仅在法术战斗任务下生效)")
            .comment("Maid cooldown multiplier(default:1.0,only effective on spellCombatTask)")
            .defineInRange("maidCooldownMultiplier", 1.0, 0, 50.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue MELEE_ATTACK_INTERVAL = BUILDER
            .comment("近战法术任务攻击间隔（单位：tick，默认: 8）")
            .comment("Melee spell task attack interval (in ticks, default: 8)")
            .defineInRange("meleeAttackInterval", 8, 1, 100);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue FAR_ATTACK_INTERVAL = BUILDER
            .comment("远程法术任务攻击间隔（单位：tick，默认: 5）")
            .comment("Far spell task attack interval (in ticks, default: 5)")
            .defineInRange("farAttackInterval", 5, 1, 100);

    static {
        BUILDER.pop();
    }


    // ========== 饰品系统配置 ==========
    static {
        BUILDER.comment("饰品系统相关配置")
               .comment("Bauble system configurations")
               .push("baubles");
    }

    // 伤害类饰品
    static {
        BUILDER.comment("伤害相关饰品配置")
               .comment("Damage-related bauble configurations")
               .push("damage");
    }

    private static final ModConfigSpec.DoubleValue SILVER_CERCIS_TRUE_DAMAGE_MULTIPLIER = BUILDER
            .comment("紫荆银冠反伤倍率 (默认: 0.8)")
            .comment("Silver Cercis true damage multiplier")
            .defineInRange("silverCercisTrueDamageMultiplier", 0.8, 0.1, 2.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue SPRING_RING_MAX_DAMAGE_BONUS = BUILDER
            .comment("烬血之戒最大伤害加成比例 (默认: 0.5)")
            .comment("Spring Ring maximum damage bonus ratio")
            .defineInRange("springRingMaxDamageBonus", 0.5, 0.1, 1.0);

    static {
        BUILDER.pop(); // damage
    }

    // 治疗类饰品
    static {
        BUILDER.comment("治疗相关饰品配置")
               .comment("Healing-related bauble configurations")
               .push("healing");
    }

    private static final ModConfigSpec.DoubleValue FLOW_CORE_HEALTH_REGEN_RATE = BUILDER
            .comment("流转核心每级好感生命恢复百分比 (默认: 0.025)")
            .comment("Flow Core health regeneration rate")
            .defineInRange("flowCoreHealthRegenRate", 0.025, 0.001, 0.3);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue BLEEDING_HEART_HEAL_RATIO = BUILDER
            .comment("喋血之心治疗比例 (默认: 0.1)")
            .comment("Bleeding Heart heal ratio")
            .defineInRange("bleedingHeartHealRatio", 0.1, 0.01, 1);

    static {
        BUILDER.pop(); // healing
    }

    // 防御类饰品
    static {
        BUILDER.comment("防御相关饰品配置")
               .comment("Defense-related bauble configurations")
               .push("defense");
    }

    private static final ModConfigSpec.DoubleValue FLOW_CORE_DAMAGE_REDUCTION = BUILDER
            .comment("流转核心每级好感伤害减免比例 (默认: 0.15)")
            .comment("Flow Core damage reduction ratio")
            .defineInRange("flowCoreDamageReduction", 0.15, 0.05, 0.5);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue DOUBLE_HEART_CHAIN_SHARE_RATIO = BUILDER
            .comment("双心链伤害分摊比例 (默认: 0.5)")
            .comment("主人和女仆各受到原伤害*比例的伤害")
            .comment("Double Heart Chain damage share ratio")
            .defineInRange("doubleHeartChainShareRatio", 0.5, 0.1, 0.9);

    static {
        BUILDER.pop(); // defense
    }

    // 功能类饰品
    static {
        BUILDER.comment("功能相关饰品配置")
               .comment("Utility-related bauble configurations")
               .push("utility");
    }

    private static final ModConfigSpec.DoubleValue QUICK_CHANT_RING_COOLDOWN_REDUCTION = BUILDER
            .comment("时痕之戒每级好感冷却减少比例 (默认: 0.25)")
            .comment("Quick Chant Ring cooldown reduction ratio")
            .defineInRange("quickChantRingCooldownReduction", 0.25, 0.1, 0.8);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue ROCK_CRYSTAL_KNOCKBACK_RESISTANCE = BUILDER
            .comment("磐石魔晶击退抗性加成 (默认: 8)")
            .comment("Rock Crystal knockback resistance bonus")
            .defineInRange("rockCrystalKnockbackResistance", 8, 1, 20);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue FLOW_CORE_TICK_INTERVAL = BUILDER
            .comment("流转核心效果触发间隔 (tick) (默认: 10)")
            .comment("Flow Core effect trigger interval in ticks")
            .defineInRange("flowCoreTickInterval", 10, 1, 100);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue HAIRPIN_FAVORABILITY_GAIN = BUILDER
            .comment("发簪进食好感度增长 (默认: 1)")
            .comment("Hairpin favorability gain when eating")
            .defineInRange("hairpinFavorabilityGain", 1, 1, 10);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue HAIRPIN_BENEFICIAL_EFFECT_EXTENSION = BUILDER
            .comment("发簪有益效果延长倍率 (默认: 1.15)")
            .comment("Hairpin beneficial effect extension multiplier")
            .defineInRange("hairpinBeneficialEffectExtension", 1.15, 1.0, 10.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue HAIRPIN_MIN_EXTENSION_TICKS = BUILDER
            .comment("发簪有益效果最小延长时间 (tick) (默认: 300)")
            .comment("Hairpin minimum extension ticks for beneficial effects")
            .defineInRange("hairpinMinExtensionTicks", 300, 0, 10000);

    static {
        BUILDER.pop(); // utility
    }

    // 触发机制类饰品
    static {
        BUILDER.comment("触发机制相关饰品配置")
               .comment("Trigger mechanism bauble configurations")
               .push("triggers");
    }

    private static final ModConfigSpec.IntValue SILVER_CERCIS_TRIGGER_COUNT = BUILDER
            .comment("紫荆银冠触发所需攻击次数 (默认: 3)")
            .comment("Silver Cercis required attack count to trigger")
            .defineInRange("silverCercisTriggerCount", 3, 1, 10);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue SILVER_CERCIS_COOLDOWN_TICKS = BUILDER
            .comment("紫荆银冠触发后冷却时间 (tick) (默认: 5)")
            .comment("Silver Cercis cooldown ticks after trigger")
            .defineInRange("silverCercisCooldownTicks", 5, 1, 100);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue WOUND_RIME_BLADE_RECORD_DURATION = BUILDER
            .comment("破愈咒锋每次攻击增加禁疗次数 (默认: 15)")
            .comment("Wound Rime Blade record duration")
            .defineInRange("woundRimeBladeRecordDuration", 15, 5, 100);

    static {
        BUILDER.pop(); // triggers
    }

    // 特殊饰品
    static {
        BUILDER.comment("特殊饰品配置")
               .comment("Special bauble configurations")
               .push("special");
    }

    private static final ModConfigSpec.DoubleValue CHAOS_BOOK_TRUE_DAMAGE_MIN = BUILDER
            .comment("混沌之书真实伤害最小值 (默认: 5.0)")
            .comment("Chaos Book minimum true damage")
            .defineInRange("chaosBookTrueDamageMin", 5.0, 1.0, 50.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue CHAOS_BOOK_TRUE_DAMAGE_PERCENT = BUILDER
            .comment("混沌之书真实伤害百分比 (默认: 0.01)")
            .comment("Chaos Book true damage percentage of max health")
            .defineInRange("chaosBookTrueDamagePercent", 0.01, 0.001, 0.1);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue CHAOS_BOOK_DAMAGE_SPLIT_COUNT = BUILDER
            .comment("混沌之书伤害分割次数 (默认: 5)")
            .comment("Chaos Book damage split count")
            .defineInRange("chaosBookDamageSplitCount", 5, 1, 20);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue CHAOS_BOOK_MIN_SPLIT_DAMAGE = BUILDER
            .comment("混沌之书每次分割伤害最小值 (默认: 3.0)")
            .comment("Chaos Book minimum damage per split")
            .defineInRange("chaosBookMinSplitDamage", 3.0, 0.1, 100.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.DoubleValue SOUL_BOOK_DAMAGE_THRESHOLD_PERCENT = BUILDER
            .comment("魂之书伤害阈值百分比 (默认: 0.2)")
            .comment("Soul Book damage threshold percentage of max health")
            .defineInRange("soulBookDamageThresholdPercent", 0.2, 0.05, 1.0);

    static {
        BUILDER.comment("");
    }

    private static final ModConfigSpec.IntValue SOUL_BOOK_DAMAGE_INTERVAL_THRESHOLD = BUILDER
            .comment("魂之书伤害间隔阈值 (tick) (默认: 10)")
            .comment("Soul Book damage interval threshold in ticks")
            .defineInRange("soulBookDamageIntervalThreshold", 10, 1, 100);

    static {
        BUILDER.pop(); // special
        BUILDER.pop(); // baubles
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // 缓存的配置值
    public static double maxSpellRange;
    public static double meleeRange;
    public static double farRange;
    public static double spellDamageMultiplier;
    public static double coolDownMultiplier;
    public static int meleeAttackInterval;
    public static int farAttackInterval;

    // 饰品配置缓存值
    // 伤害相关
    public static double silverCercisTrueDamageMultiplier;
    public static double springRingMaxDamageBonus;

    // 治疗相关
    public static double flowCoreHealthRegenRate;
    public static double bleedingHeartHealRatio;

    // 防御相关
    public static double flowCoreDamageReduction;
    public static double doubleHeartChainShareRatio;

    // 冷却相关
    public static double quickChantRingCooldownReduction;

    // 属性相关
    public static int rockCrystalKnockbackResistance;

    // 时间间隔相关
    public static int flowCoreTickInterval;

    // 触发条件相关
    public static int silverCercisTriggerCount;
    public static int silverCercisCooldownTicks;
    public static int woundRimeBladeRecordTimes;

    // 好感度相关
    public static int hairpinFavorabilityGain;
    public static double hairpinBeneficialEffectExtension;
    public static int hairpinMinExtensionTicks;

    // 特殊饰品相关
    public static double chaosBookTrueDamageMin;
    public static double chaosBookTrueDamagePercent;
    public static int chaosBookDamageSplitCount;
    public static double chaosBookMinSplitDamage;
    public static double soulBookDamageThresholdPercent;
    public static int soulBookDamageIntervalThreshold;


    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        // 加载配置值到缓存变量
        maxSpellRange = MAX_SPELL_RANGE.get();
        meleeRange = MELEE_RANGE.get();
        spellDamageMultiplier = SPELL_DAMAGE_MULTIPLIER.get();
        coolDownMultiplier = COOLDOWN_MULITIPLIER.get();
        farRange = FAR_RANGE.get();
        meleeAttackInterval = MELEE_ATTACK_INTERVAL.get();
        farAttackInterval = FAR_ATTACK_INTERVAL.get();

        // 加载饰品配置值
        // 伤害相关
        silverCercisTrueDamageMultiplier = SILVER_CERCIS_TRUE_DAMAGE_MULTIPLIER.get();
        springRingMaxDamageBonus = SPRING_RING_MAX_DAMAGE_BONUS.get();

        // 治疗相关
        flowCoreHealthRegenRate = FLOW_CORE_HEALTH_REGEN_RATE.get();
        bleedingHeartHealRatio = BLEEDING_HEART_HEAL_RATIO.get();

        // 防御相关
        flowCoreDamageReduction = FLOW_CORE_DAMAGE_REDUCTION.get();
        doubleHeartChainShareRatio = DOUBLE_HEART_CHAIN_SHARE_RATIO.get();

        // 冷却相关
        quickChantRingCooldownReduction = QUICK_CHANT_RING_COOLDOWN_REDUCTION.get();

        // 属性相关
        rockCrystalKnockbackResistance = ROCK_CRYSTAL_KNOCKBACK_RESISTANCE.get();

        // 时间间隔相关
        flowCoreTickInterval = FLOW_CORE_TICK_INTERVAL.get();

        // 触发条件相关
        silverCercisTriggerCount = SILVER_CERCIS_TRIGGER_COUNT.get();
        silverCercisCooldownTicks = SILVER_CERCIS_COOLDOWN_TICKS.get();
        woundRimeBladeRecordTimes = WOUND_RIME_BLADE_RECORD_DURATION.get();

        // 好感度相关
        hairpinFavorabilityGain = HAIRPIN_FAVORABILITY_GAIN.get();
        hairpinBeneficialEffectExtension = HAIRPIN_BENEFICIAL_EFFECT_EXTENSION.get();
        hairpinMinExtensionTicks = HAIRPIN_MIN_EXTENSION_TICKS.get();

        // 特殊饰品相关
        chaosBookTrueDamageMin = CHAOS_BOOK_TRUE_DAMAGE_MIN.get();
        chaosBookTrueDamagePercent = CHAOS_BOOK_TRUE_DAMAGE_PERCENT.get();
        chaosBookDamageSplitCount = CHAOS_BOOK_DAMAGE_SPLIT_COUNT.get();
        chaosBookMinSplitDamage = CHAOS_BOOK_MIN_SPLIT_DAMAGE.get();
        soulBookDamageThresholdPercent = SOUL_BOOK_DAMAGE_THRESHOLD_PERCENT.get();
        soulBookDamageIntervalThreshold = SOUL_BOOK_DAMAGE_INTERVAL_THRESHOLD.get();

        SpellCombatMeleeTask.setSpellRange((float) maxSpellRange);
        SpellCombatFarTask.setSpellRange((float) maxSpellRange);
        SimplifiedSpellCaster.MELEE_RANGE= (float) meleeRange;
        SimplifiedSpellCaster.FAR_RANGE= (float) farRange;

        Global.resetCommonDamageCalc();
        Global.resetCommonCoolDownCalc();
    }


}
