package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;

/** Bauble configuration stored in baubles.toml. */
final class ConfigBaubles {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final List<String> DEFAULT_WHITELIST = List.of(
            "regex:minecraft:.*", "regex:goety:.*", "regex:irons_spellbooks:.*",
            "regex:ars_nouveau:.*", "regex:youkaishomecoming:.*", "regex:farmersdelight:.*",
            "regex:kaleidoscope_cookery:.*", "regex:alexsmobs:.*", "regex:alexscaves:.*",
            "regex:cataclysm:.*");
    private static final List<String> DEFAULT_BLACKLIST = List.of(
            "irons_spellbooks:ascension", "irons_spellbooks:burning_dash",
            "irons_spellbooks:antigravity", "irons_spellbooks:volt_strike",
            "traveloptics:aqua_missiles_hover", "traveloptics:meteor_storm",
            "traveloptics:aerial_collapse", "traveloptics:aerial_collapse_helper",
            "soulsweapons:chungus_tonic_effect", "goety:fire_trail", "goety:charged",
            "goety:rampage", "goety:shadow_walk", "goety:fiery_aura", "goety:frosty_aura",
            "minecraft:invisibility", "irons_spellbooks:true_invisibility", "goety:explosive");

    private static final ForgeConfigSpec.DoubleValue SILVER_CERCIS_TRUE_DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue SPRING_RING_MAX_DAMAGE_BONUS;
    private static final ForgeConfigSpec.DoubleValue FLOW_CORE_HEALTH_REGEN_RATE;
    private static final ForgeConfigSpec.DoubleValue BLEEDING_HEART_HEAL_RATIO;
    private static final ForgeConfigSpec.DoubleValue FLOW_CORE_DAMAGE_REDUCTION;
    private static final ForgeConfigSpec.DoubleValue DOUBLE_HEART_CHAIN_SHARE_RATIO;
    private static final ForgeConfigSpec.DoubleValue DOUBLE_HEART_CHAIN_MAX_DISTANCE;
    private static final ForgeConfigSpec.DoubleValue QUICK_CHANT_RING_COOLDOWN_REDUCTION;
    private static final ForgeConfigSpec.IntValue ROCK_CRYSTAL_KNOCKBACK_RESISTANCE;
    private static final ForgeConfigSpec.IntValue FLOW_CORE_TICK_INTERVAL;
    private static final ForgeConfigSpec.DoubleValue HAIRPIN_BENEFICIAL_EFFECT_EXTENSION;
    private static final ForgeConfigSpec.IntValue HAIRPIN_MIN_EXTENSION_TICKS;
    private static final ForgeConfigSpec.IntValue FRAGRANT_INGENUITY_FAVORABILITY_GAIN;
    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_MAX_STACKS;
    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_STACK_DURATION_TICKS;
    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_GAIN_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD_RATIO;
    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_HEAL_RATIO;
    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_COOLDOWN_REFUND_RATIO;
    private static final ForgeConfigSpec.IntValue FRAGRANT_INGENUITY_BUFF_DURATION;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> FRAGRANT_INGENUITY_EFFECT_BLACKLIST;
    private static final ForgeConfigSpec.IntValue SILVER_CERCIS_TRIGGER_COUNT;
    private static final ForgeConfigSpec.IntValue SILVER_CERCIS_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.IntValue WOUND_RIME_BLADE_RECORD_DURATION;
    private static final ForgeConfigSpec.DoubleValue CHAOS_BOOK_TRUE_DAMAGE_MIN;
    private static final ForgeConfigSpec.DoubleValue CHAOS_BOOK_TRUE_DAMAGE_PERCENT;
    private static final ForgeConfigSpec.IntValue CHAOS_BOOK_DAMAGE_SPLIT_COUNT;
    private static final ForgeConfigSpec.DoubleValue CHAOS_BOOK_MIN_SPLIT_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue SOUL_BOOK_DAMAGE_THRESHOLD_PERCENT;
    private static final ForgeConfigSpec.IntValue SOUL_BOOK_DAMAGE_INTERVAL_THRESHOLD;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DREAM_CRYSTAL_EFFECT_BLACKLIST;
    private static final ForgeConfigSpec.IntValue DREAM_CRYSTAL_EFFECT_BLACKLIST_MIGRATION_VERSION;
    private static final ForgeConfigSpec.BooleanValue DREAM_CRYSTAL_USE_EFFECT_WHITELIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DREAM_CRYSTAL_EFFECT_WHITELIST;
    private static final ForgeConfigSpec.BooleanValue DREAM_CRYSTAL_EXTRA_TRUE_DAMAGE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue FLOATING_FOX_LEAF_OWNER_RANGE;
    private static final ForgeConfigSpec.DoubleValue MOLTEN_FOX_LEAF_OWNER_RANGE;

    static {
        BUILDER.comment("饰品系统相关配置",
                        "Bauble system configurations").push("baubles");
        BUILDER.comment("伤害相关饰品配置",
                        "Damage-related bauble configurations").push("damage");
        SILVER_CERCIS_TRUE_DAMAGE_MULTIPLIER = BUILDER
                .comment("紫荆银冠攒够次数后反出去的真伤，按这一击伤害的多少倍算 (默认: 0.8)",
                        "Silver Cercis deals the recorded hit back as true damage, scaled by this.")
                .defineInRange("silverCercisTrueDamageMultiplier", 0.8, 0.1, 2.0);
        SPRING_RING_MAX_DAMAGE_BONUS = BUILDER
                .comment("烬血之戒看女仆自己掉了多少血来决定加成，越残血打得越痛，最多额外加这么多 (默认: 0.5，即残血时 +50%)",
                        "Spring Ring adds a bonus based on how hurt the maid is, capped at this ratio.")
                .defineInRange("springRingMaxDamageBonus", 0.5, 0.1, 1.0);
        BUILDER.pop();

        BUILDER.comment("治疗相关饰品配置",
                        "Healing-related bauble configurations").push("healing");
        FLOW_CORE_HEALTH_REGEN_RATE = BUILDER
                .comment("流转核心每级好感、每次触发回复的最大生命比例",
                        "Flow Core healing per favour level and trigger, as a fraction of max health.")
                .defineInRange("flowCoreHealthRegenRate", 0.025, 0.001, 0.3);
        BLEEDING_HEART_HEAL_RATIO = BUILDER
                .comment("女仆打出的伤害会按这个比例同时回给她自己和主人，是吸血不是反伤 (默认: 0.1)",
                        "Bleeding Heart heals the maid and her owner for this fraction of the damage she deals.")
                .defineInRange("bleedingHeartHealRatio", 0.1, 0.01, 1.0);
        BUILDER.pop();

        BUILDER.comment("防御相关饰品配置",
                        "Defense-related bauble configurations").push("defense");
        FLOW_CORE_DAMAGE_REDUCTION = BUILDER
                .comment("流转核心按好感等级减伤，每级减掉这么多受击伤害，好感越高越硬 (默认: 0.15)",
                        "Flow Core reduces incoming damage by this fraction per favour level.")
                .defineInRange("flowCoreDamageReduction", 0.15, 0.05, 0.5);
        DOUBLE_HEART_CHAIN_SHARE_RATIO = BUILDER
                .comment("双心链：女仆和主人各承受原伤害乘此比例",
                        "Double Heart Chain: maid and owner each take this fraction of the original hit.")
                .defineInRange("doubleHeartChainShareRatio", 0.5, 0.1, 0.9);
        DOUBLE_HEART_CHAIN_MAX_DISTANCE = BUILDER
                .comment("主人离女仆超过这个距离就不再分摊，那一击由女仆自己吃全额 (默认: 32.0 格)",
                        "Above this distance the chain stops sharing and the maid takes the hit alone.")
                .defineInRange("doubleHeartChainMaxDistance", 32.0, 4.0, 128.0);
        BUILDER.pop();

        BUILDER.comment("功能相关饰品配置",
                        "Utility-related bauble configurations").push("utility");
        QUICK_CHANT_RING_COOLDOWN_REDUCTION = BUILDER
                .comment("时痕之戒按好感等级缩短法术冷却，每级少这么多 (默认: 0.25，即每级 -25%)",
                        "Quick Chant Ring cuts spell cooldowns by this fraction per favour level.")
                .defineInRange("quickChantRingCooldownReduction", 0.25, 0.1, 0.8);
        ROCK_CRYSTAL_KNOCKBACK_RESISTANCE = BUILDER
                .comment("磐石魔晶提供的击退抗性；原版属性上限为 1.0",
                        "Knockback resistance from Rock Crystal; the vanilla attribute caps at 1.0.")
                .defineInRange("rockCrystalKnockbackResistance", 8, 1, 20);
        FLOW_CORE_TICK_INTERVAL = BUILDER
                .comment("流转核心每隔这么多 tick 回一次血，调小回得更碎更勤 (默认: 10 tick，即 0.5 秒)",
                        "How often Flow Core heals, in ticks (default 10 ticks = 0.5 seconds).")
                .defineInRange("flowCoreTickInterval", 10, 1, 100);
        HAIRPIN_BENEFICIAL_EFFECT_EXTENSION = BUILDER
                .comment("发簪有益效果时长倍率；与最小延长量取较大值",
                        "Hairpin beneficial-effect duration multiplier; uses the larger of this and the minimum extension.")
                .defineInRange("hairpinBeneficialEffectExtension", 1.15, 1.0, 10.0);
        HAIRPIN_MIN_EXTENSION_TICKS = BUILDER
                .comment("发簪有益效果的最小延长量，单位 tick",
                        "Minimum ticks added to a beneficial effect by Hairpin.")
                .defineInRange("hairpinMinExtensionTicks", 300, 0, 10000);
        FRAGRANT_INGENUITY_FAVORABILITY_GAIN = BUILDER
                .comment("女仆喂主人吃点东西时，女仆自己涨多少好感 (默认: 2)",
                        "Fragrant Ingenuity favour the maid gains when she feeds her owner.")
                .defineInRange("fragrantIngenuityFavorabilityGain", 2, 1, 10);
        SPRING_BLOOM_RETURN_MAX_STACKS = BUILDER
                .comment("春花-返最多能存几层，层数就是她替主人挨刀的次数 (默认: 3)",
                        "Spring Bloom Return: maximum number of stored stacks.")
                .defineInRange("springBloomReturnMaxStacks", 3, 1, 8);
        SPRING_BLOOM_RETURN_STACK_DURATION_TICKS = BUILDER
                .comment("每层能存多久，存太久会自己过期掉 (默认: 400 tick，即 20 秒)",
                        "How long one Spring Bloom Return stack lives before expiring (default 400 ticks = 20s).")
                .defineInRange("springBloomReturnStackDurationTicks", 400, 20, 72000);
        SPRING_BLOOM_RETURN_GAIN_COOLDOWN_TICKS = BUILDER
                .comment("两层之间至少要隔这么久，免得一秒钟叠满 (默认: 20 tick，即 1 秒)",
                        "Minimum delay between gaining two stacks (default 20 ticks = 1 second).")
                .defineInRange("springBloomReturnGainCooldownTicks", 20, 0, 1200);
        SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_TICKS = BUILDER
                .comment("替主人挡一次之后要等这么久才能再挡 (默认: 200 tick，即 10 秒)",
                        "Cooldown after one Spring Bloom Return trigger (default 200 ticks = 10 seconds).")
                .defineInRange("springBloomReturnTriggerCooldownTicks", 200, 0, 72000);
        SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD = BUILDER
                .comment("单次伤害到这个固定值就算「够重」，值得替主人挡一下 (默认: 4.0)",
                        "A single hit at or above this flat amount counts as heavy enough to trigger it.")
                .defineInRange("springBloomReturnDamageThreshold", 4.0, 0.0, 100.0);
        SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD_RATIO = BUILDER
                .comment("按受击者最大生命计算的重击阈值；与固定伤害阈值满足其一即可",
                        "Heavy-hit threshold as a fraction of max health; either threshold can trigger.")
                .defineInRange("springBloomReturnDamageThresholdRatio", 0.10, 0.0, 1.0);
        SPRING_BLOOM_RETURN_HEAL_RATIO = BUILDER
                .comment("触发时直接把这一击削掉「保护对象最大生命 × 本值」那么多血，等于替他回了一口 (默认: 0.05，即 5%)",
                        "On trigger the hit is reduced by this fraction of the protected target's max health.")
                .defineInRange("springBloomReturnHealRatio", 0.05, 0.0, 1.0);
        SPRING_BLOOM_RETURN_COOLDOWN_REFUND_RATIO = BUILDER
                .comment("春花-返触发时返还的剩余法术冷却比例；好感 2 级起生效",
                        "Spring Bloom Return refunds this fraction of remaining spell cooldowns from favour level 2.")
                .defineInRange("springBloomReturnCooldownRefundRatio", 0.20, 0.0, 1.0);
        FRAGRANT_INGENUITY_BUFF_DURATION = BUILDER
                .comment("馥郁巧思喂食时给主人施加的随机增益时长，单位 tick",
                        "Duration in ticks of the random beneficial effect granted when the maid feeds her owner.")
                .defineInRange("fragrantIngenuityBuffDuration", 2400, 200, 120000);
        FRAGRANT_INGENUITY_EFFECT_BLACKLIST = BUILDER
                .comment("馥郁巧思随机效果黑名单；支持完整 id 和 regex: 正则，默认与梦云水晶相同",
                        "Fragrant Ingenuity effect blacklist; supports ids and regex: patterns, defaults to Dream Crystal's list.")
                .defineListAllowEmpty(
                List.of("fragrantIngenuityEffectBlacklist"), () -> DEFAULT_BLACKLIST, obj -> obj instanceof String);
        BUILDER.pop();

        BUILDER.comment("触发机制相关饰品配置",
                        "Trigger mechanism bauble configurations").push("triggers");
        SILVER_CERCIS_TRIGGER_COUNT = BUILDER
                .comment("攒够几次命中才反一次真伤，反完清零重新数 (默认: 3)",
                        "Hits the maid must land before Silver Cercis fires once.")
                .defineInRange("silverCercisTriggerCount", 3, 1, 10);
        SILVER_CERCIS_COOLDOWN_TICKS = BUILDER
                .comment("两次命中间隔超过此 tick 数时，紫荆银冠立即触发，无需累计命中",
                        "If hits are farther apart than this many ticks, Silver Cercis triggers immediately.")
                .defineInRange("silverCercisCooldownTicks", 5, 1, 100);
        WOUND_RIME_BLADE_RECORD_DURATION = BUILDER
                .comment("破愈咒锋每次命中施加的禁疗层数；每次回血消耗一层并被取消",
                        "Heal-block charges per Wound Rime Blade hit; each heal consumes one charge and is cancelled.")
                .defineInRange("woundRimeBladeRecordDuration", 15, 5, 100);
        BUILDER.pop();

        BUILDER.comment("特殊饰品配置",
                        "Special bauble configurations").push("special");
        CHAOS_BOOK_TRUE_DAMAGE_MIN = BUILDER
                .comment("混沌之书真伤下限；与目标最大生命百分比取较大值",
                        "Chaos Book true damage floor; uses the larger of this and the max-health percentage.")
                .defineInRange("chaosBookTrueDamageMin", 5.0, 1.0, 50.0);
        CHAOS_BOOK_TRUE_DAMAGE_PERCENT = BUILDER
                .comment("真伤按目标最大生命算的比例，只有高血量目标才用得着这一条 (默认: 0.01，即 1%)",
                        "Max-health percentage used for the true damage above.")
                .defineInRange("chaosBookTrueDamagePercent", 0.01, 0.001, 0.1);
        CHAOS_BOOK_DAMAGE_SPLIT_COUNT = BUILDER
                .comment("混沌之书伤害分段数；单段下限可能使总伤害随段数增加",
                        "Chaos Book hit segments; the per-segment floor may increase total damage as this grows.")
                .defineInRange("chaosBookDamageSplitCount", 5, 1, 20);
        CHAOS_BOOK_MIN_SPLIT_DAMAGE = BUILDER
                .comment("每一段的下限，均分结果比它还小的时候就按它算 (默认: 3.0)",
                        "Damage floor for a single chunk.")
                .defineInRange("chaosBookMinSplitDamage", 3.0, 0.1, 100.0);
        SOUL_BOOK_DAMAGE_THRESHOLD_PERCENT = BUILDER
                .comment("魂之书把单次受到的伤害拦在「最大生命 × 本值」以内，超出的部分直接不算 (默认: 0.2，即 20%)",
                        "Soul Book clamps a single incoming hit to this fraction of the maid's max health.")
                .defineInRange("soulBookDamageThresholdPercent", 0.2, 0.05, 1.0);
        SOUL_BOOK_DAMAGE_INTERVAL_THRESHOLD = BUILDER
                .comment("距上一次受伤不到这么多 tick 的话，这一击整个不生效；魂之书真正值钱的是这条 (默认: 10 tick，即 0.5 秒)",
                        "A hit arriving sooner than this after the previous one is cancelled outright.")
                .defineInRange("soulBookDamageIntervalThreshold", 10, 1, 100);
        DREAM_CRYSTAL_EFFECT_BLACKLIST = BUILDER
                .comment("梦云水晶随机效果黑名单；支持完整 id 和 regex: 正则",
                        "Dream Crystal effect blacklist; supports full ids and regex: patterns.")
                .defineListAllowEmpty(
                List.of("dreamCrystalEffectBlacklist"), () -> DEFAULT_BLACKLIST, obj -> obj instanceof String);
        DREAM_CRYSTAL_EFFECT_BLACKLIST_MIGRATION_VERSION = BUILDER
                .comment("梦云水晶黑名单默认值迁移版本；请勿手动修改",
                        "Dream Crystal blacklist default migration version; do not edit manually.")
                .defineInRange(
                "dreamCrystalEffectBlacklistMigrationVersion", 0, 0, 1);
        DREAM_CRYSTAL_USE_EFFECT_WHITELIST = BUILDER
                .comment("true: 只抽白名单效果；false: 按黑名单排除",
                        "true: roll only whitelisted effects; false: exclude blacklisted effects.")
                .define("dreamCrystalUseEffectWhitelist", true);
        DREAM_CRYSTAL_EFFECT_WHITELIST = BUILDER
                .comment("梦云水晶效果白名单；启用白名单模式时生效，支持完整 id 和 regex: 正则",
                        "Dream Crystal effect whitelist; active in whitelist mode, supports ids and regex: patterns.")
                .defineListAllowEmpty(
                List.of("dreamCrystalEffectWhitelist"), () -> DEFAULT_WHITELIST, obj -> obj instanceof String);
        DREAM_CRYSTAL_EXTRA_TRUE_DAMAGE_ENABLED = BUILDER
                .comment("梦云水晶是否在女仆命中时追加等量真伤",
                        "Whether Dream Crystal adds true damage equal to a maid's hit.")
                .define("dreamCrystalExtraTrueDamageEnabled", true);
        BUILDER.pop();

        BUILDER.comment("移动相关饰品配置",
                        "Movement-related bauble configurations").push("movement");
        FLOATING_FOX_LEAF_OWNER_RANGE = BUILDER
                .comment("浮波狐叶赋予主人水面行走的最大距离，单位格",
                        "Maximum maid-owner distance in blocks for Floating Fox Leaf water walking.")
                .defineInRange("floatingFoxLeafOwnerRange", 16.0, 4.0, 64.0);
        MOLTEN_FOX_LEAF_OWNER_RANGE = BUILDER
                .comment("熔岩狐叶同理：主人在这个距离内才能踩着岩浆走 (默认: 16.0 格)",
                        "Same for Molten Fox Leaf, with lava instead of water.")
                .defineInRange("moltenFoxLeafOwnerRange", 16.0, 4.0, 64.0);
        BUILDER.pop();
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ConfigBaubles() {
    }

    static void load(ModConfigEvent event) {
        Config.silverCercisTrueDamageMultiplier = SILVER_CERCIS_TRUE_DAMAGE_MULTIPLIER.get();
        Config.springRingMaxDamageBonus = SPRING_RING_MAX_DAMAGE_BONUS.get();
        Config.flowCoreHealthRegenRate = FLOW_CORE_HEALTH_REGEN_RATE.get();
        Config.bleedingHeartHealRatio = BLEEDING_HEART_HEAL_RATIO.get();
        Config.flowCoreDamageReduction = FLOW_CORE_DAMAGE_REDUCTION.get();
        Config.doubleHeartChainShareRatio = DOUBLE_HEART_CHAIN_SHARE_RATIO.get();
        Config.doubleHeartChainMaxDistance = DOUBLE_HEART_CHAIN_MAX_DISTANCE.get();
        Config.quickChantRingCooldownReduction = QUICK_CHANT_RING_COOLDOWN_REDUCTION.get();
        Config.rockCrystalKnockbackResistance = ROCK_CRYSTAL_KNOCKBACK_RESISTANCE.get();
        Config.flowCoreTickInterval = FLOW_CORE_TICK_INTERVAL.get();
        Config.silverCercisTriggerCount = SILVER_CERCIS_TRIGGER_COUNT.get();
        Config.silverCercisCooldownTicks = SILVER_CERCIS_COOLDOWN_TICKS.get();
        Config.woundRimeBladeRecordTimes = WOUND_RIME_BLADE_RECORD_DURATION.get();
        Config.hairpinBeneficialEffectExtension = HAIRPIN_BENEFICIAL_EFFECT_EXTENSION.get();
        Config.hairpinMinExtensionTicks = HAIRPIN_MIN_EXTENSION_TICKS.get();
        Config.fragrantIngenuityFavorabilityGain = FRAGRANT_INGENUITY_FAVORABILITY_GAIN.get();
        Config.fragrantIngenuityBuffDuration = FRAGRANT_INGENUITY_BUFF_DURATION.get();
        Config.fragrantIngenuityEffectBlacklist = new ArrayList<>(FRAGRANT_INGENUITY_EFFECT_BLACKLIST.get());
        Config.springBloomReturnMaxStacks = SPRING_BLOOM_RETURN_MAX_STACKS.get();
        Config.springBloomReturnStackDurationTicks = SPRING_BLOOM_RETURN_STACK_DURATION_TICKS.get();
        Config.springBloomReturnGainCooldownTicks = SPRING_BLOOM_RETURN_GAIN_COOLDOWN_TICKS.get();
        Config.springBloomReturnTriggerCooldownTicks = SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_TICKS.get();
        Config.springBloomReturnDamageThreshold = SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD.get();
        Config.springBloomReturnDamageThresholdRatio = SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD_RATIO.get();
        Config.springBloomReturnHealRatio = SPRING_BLOOM_RETURN_HEAL_RATIO.get();
        Config.springBloomReturnCooldownRefundRatio = SPRING_BLOOM_RETURN_COOLDOWN_REFUND_RATIO.get();
        Config.chaosBookTrueDamageMin = CHAOS_BOOK_TRUE_DAMAGE_MIN.get();
        Config.chaosBookTrueDamagePercent = CHAOS_BOOK_TRUE_DAMAGE_PERCENT.get();
        Config.chaosBookDamageSplitCount = CHAOS_BOOK_DAMAGE_SPLIT_COUNT.get();
        Config.chaosBookMinSplitDamage = CHAOS_BOOK_MIN_SPLIT_DAMAGE.get();
        Config.soulBookDamageThresholdPercent = SOUL_BOOK_DAMAGE_THRESHOLD_PERCENT.get();
        Config.soulBookDamageIntervalThreshold = SOUL_BOOK_DAMAGE_INTERVAL_THRESHOLD.get();
        Config.floatingFoxLeafOwnerRange = FLOATING_FOX_LEAF_OWNER_RANGE.get();
        Config.moltenFoxLeafOwnerRange = MOLTEN_FOX_LEAF_OWNER_RANGE.get();
        Config.migrateDreamCrystalEffectBlacklist(event, DREAM_CRYSTAL_EFFECT_BLACKLIST,
                DREAM_CRYSTAL_EFFECT_BLACKLIST_MIGRATION_VERSION, DEFAULT_BLACKLIST);
        Config.dreamCrystalEffectBlacklist = new ArrayList<>(DREAM_CRYSTAL_EFFECT_BLACKLIST.get());
        Config.dreamCrystalUseEffectWhitelist = DREAM_CRYSTAL_USE_EFFECT_WHITELIST.get();
        Config.dreamCrystalEffectWhitelist = new ArrayList<>(DREAM_CRYSTAL_EFFECT_WHITELIST.get());
        Config.dreamCrystalExtraTrueDamageEnabled = DREAM_CRYSTAL_EXTRA_TRUE_DAMAGE_ENABLED.get();
        DreamCatCrystalBauble.invalidateBeneficialEffectsCache();
    }
}
