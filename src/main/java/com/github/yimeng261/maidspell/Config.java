package com.github.yimeng261.maidspell;

import com.github.yimeng261.maidspell.spell.SimplifiedSpellCaster;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;

import java.util.ArrayList;
import java.util.List;

import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Forge common config and its cached values. */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final List<String> DEFAULT_RANDOM_BENEFICIAL_EFFECT_WHITELIST = List.of(
            "regex:minecraft:.*",
            "regex:goety:.*",
            "regex:irons_spellbooks:.*",
            "regex:ars_nouveau:.*",
            "regex:youkaishomecoming:.*",
            "regex:farmersdelight:.*",
            "regex:kaleidoscope_cookery:.*",
            "regex:alexsmobs:.*",
            "regex:alexscaves:.*",
            "regex:cataclysm:.*"
    );
    private static final List<String> DEFAULT_RANDOM_BENEFICIAL_EFFECT_BLACKLIST = List.of(
            "irons_spellbooks:ascension",
            "irons_spellbooks:burning_dash",
            "irons_spellbooks:antigravity",
            "irons_spellbooks:volt_strike",
            "traveloptics:aqua_missiles_hover",
            "traveloptics:meteor_storm",
            "traveloptics:aerial_collapse",
            "traveloptics:aerial_collapse_helper",
            "soulsweapons:chungus_tonic_effect",
            "goety:fire_trail",
            "goety:charged",
            "goety:rampage",
            "goety:shadow_walk",
            "goety:fiery_aura",
            "goety:frosty_aura",
            "minecraft:invisibility",
            "irons_spellbooks:true_invisibility",
            "goety:explosive"
    );
    
    static {
        BUILDER.comment("战斗系统相关配置")
               .comment("Combat system configurations")
               .push("combat");
    }
    
    private static final ForgeConfigSpec.DoubleValue MAX_SPELL_RANGE = BUILDER
        .comment("法术战斗任务里女仆的索敌与施法距离，超出这个距离她就不再出手 (默认: 24.0 格)")
        .comment("How far a maid will pick targets and cast during a spell-combat task, in blocks.")
        .defineInRange("maxSpellRange", 24.0, 8.0, 64.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue MELEE_RANGE = BUILDER
        .comment("近战任务判定「够得着」的距离，比原版手长一点，女仆才追得上边走边打的目标 (默认: 2.5 格)")
        .comment("Melee reach used by the melee task, in blocks.")
        .defineInRange("meleeRange", 2.5, 1.0, 5.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue FAR_RANGE = BUILDER
            .comment("远程任务的开火距离，箭矢/法术从这个距离开始出手 (默认: 8.5 格)")
            .comment("Firing distance used by the far task, in blocks.")
            .defineInRange("farRange", 8.5, 1.0, 20.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
        .comment("女仆在法术战斗任务下的伤害倍率；只有装着那个任务才吃这条，填 0 等于让她一点输出都没有 (默认: 1.0)")
        .comment("Damage multiplier for the maid while running the spell-combat task. 0 turns her damage off.")
        .defineInRange("maidDamageMultiplier", 1.0, 0, 50.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue COOLDOWN_MULITIPLIER = BUILDER
            .comment("法术战斗任务中的女仆法术冷却倍率；越小施法越频繁")
            .comment("Maid spell cooldown multiplier during spell combat; lower values allow more frequent casts.")
            .defineInRange("maidCooldownMultiplier", 1.0, 0, 50.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.IntValue MELEE_ATTACK_INTERVAL = BUILDER
            .comment("近战任务两次出手之间至少隔这么多 tick；调小是贴脸连击，调大输出直接掉一档 (默认: 8 tick，即 0.4 秒)")
            .comment("Minimum ticks between two attacks in a melee task (default 8 ticks = 0.4 seconds).")
            .defineInRange("meleeAttackInterval", 8, 1, 100);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.IntValue FAR_ATTACK_INTERVAL = BUILDER
            .comment("远程任务两次出手之间的间隔，同样按 tick 算 (默认: 5 tick，即 0.25 秒)")
            .comment("Minimum ticks between two attacks in a far task (default 5 ticks = 0.25 seconds).")
            .defineInRange("farAttackInterval", 5, 1, 100);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> IRONS_SPELL_BLACKLIST = BUILDER
            .comment("女仆不施放列表中的法术；可填完整 id 或 regex: 前缀的正则")
            .comment("Maids will not cast listed spells. Use full ids or regex: patterns.")
            .defineListAllowEmpty(
                java.util.List.of("spellBlacklist"), 
                () -> java.util.List.of("irons_spellbooks:spectral_hammer"),
                obj -> obj instanceof String
            );

    static {
        BUILDER.pop();
    }


    static {
        BUILDER.comment("饰品系统相关配置")
               .comment("Bauble system configurations")
               .push("baubles");
    }
    
    static {
        BUILDER.comment("伤害相关饰品配置")
               .comment("Damage-related bauble configurations")
               .push("damage");
    }
    
    private static final ForgeConfigSpec.DoubleValue SILVER_CERCIS_TRUE_DAMAGE_MULTIPLIER = BUILDER
            .comment("紫荆银冠攒够次数后反出去的真伤，按这一击伤害的多少倍算 (默认: 0.8)")
            .comment("Silver Cercis deals the recorded hit back as true damage, scaled by this.")
            .defineInRange("silverCercisTrueDamageMultiplier", 0.8, 0.1, 2.0);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.DoubleValue SPRING_RING_MAX_DAMAGE_BONUS = BUILDER
            .comment("烬血之戒看女仆自己掉了多少血来决定加成，越残血打得越痛，最多额外加这么多 (默认: 0.5，即残血时 +50%)")
            .comment("Spring Ring adds a bonus based on how hurt the maid is, capped at this ratio.")
            .defineInRange("springRingMaxDamageBonus", 0.5, 0.1, 1.0);
    
    static {
        BUILDER.pop(); // damage
    }
    
    static {
        BUILDER.comment("治疗相关饰品配置")
               .comment("Healing-related bauble configurations")
               .push("healing");
    }
    
    private static final ForgeConfigSpec.DoubleValue FLOW_CORE_HEALTH_REGEN_RATE = BUILDER
            .comment("流转核心每级好感、每次触发回复的最大生命比例")
            .comment("Flow Core healing per favour level and trigger, as a fraction of max health.")
            .defineInRange("flowCoreHealthRegenRate", 0.025, 0.001, 0.3);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.DoubleValue BLEEDING_HEART_HEAL_RATIO = BUILDER
            .comment("女仆打出的伤害会按这个比例同时回给她自己和主人，是吸血不是反伤 (默认: 0.1)")
            .comment("Bleeding Heart heals the maid and her owner for this fraction of the damage she deals.")
            .defineInRange("bleedingHeartHealRatio", 0.1, 0.01, 1);
    
    static {
        BUILDER.pop(); // healing
    }
    
    static {
        BUILDER.comment("防御相关饰品配置")
               .comment("Defense-related bauble configurations")
               .push("defense");
    }
    
    private static final ForgeConfigSpec.DoubleValue FLOW_CORE_DAMAGE_REDUCTION = BUILDER
            .comment("流转核心按好感等级减伤，每级减掉这么多受击伤害，好感越高越硬 (默认: 0.15)")
            .comment("Flow Core reduces incoming damage by this fraction per favour level.")
            .defineInRange("flowCoreDamageReduction", 0.15, 0.05, 0.5);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.DoubleValue DOUBLE_HEART_CHAIN_SHARE_RATIO = BUILDER
            .comment("双心链：女仆和主人各承受原伤害乘此比例")
            .comment("Double Heart Chain: maid and owner each take this fraction of the original hit.")
            .defineInRange("doubleHeartChainShareRatio", 0.5, 0.1, 0.9);

    private static final ForgeConfigSpec.DoubleValue DOUBLE_HEART_CHAIN_MAX_DISTANCE = BUILDER
            .comment("主人离女仆超过这个距离就不再分摊，那一击由女仆自己吃全额 (默认: 32.0 格)")
            .comment("Above this distance the chain stops sharing and the maid takes the hit alone.")
            .defineInRange("doubleHeartChainMaxDistance", 32.0, 4.0, 128.0);

    static {
        BUILDER.pop(); // defense
    }
    
    static {
        BUILDER.comment("功能相关饰品配置")
               .comment("Utility-related bauble configurations")
               .push("utility");
    }
    
    private static final ForgeConfigSpec.DoubleValue QUICK_CHANT_RING_COOLDOWN_REDUCTION = BUILDER
            .comment("时痕之戒按好感等级缩短法术冷却，每级少这么多 (默认: 0.25，即每级 -25%)")
            .comment("Quick Chant Ring cuts spell cooldowns by this fraction per favour level.")
            .defineInRange("quickChantRingCooldownReduction", 0.25, 0.1, 0.8);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue ROCK_CRYSTAL_KNOCKBACK_RESISTANCE = BUILDER
            .comment("磐石魔晶提供的击退抗性；原版属性上限为 1.0")
            .comment("Knockback resistance from Rock Crystal; the vanilla attribute caps at 1.0.")
            .defineInRange("rockCrystalKnockbackResistance", 8, 1, 20);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue FLOW_CORE_TICK_INTERVAL = BUILDER
            .comment("流转核心每隔这么多 tick 回一次血，调小回得更碎更勤 (默认: 10 tick，即 0.5 秒)")
            .comment("How often Flow Core heals, in ticks (default 10 ticks = 0.5 seconds).")
            .defineInRange("flowCoreTickInterval", 10, 1, 100);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue HAIRPIN_BENEFICIAL_EFFECT_EXTENSION = BUILDER
            .comment("发簪有益效果时长倍率；与最小延长量取较大值")
            .comment("Hairpin beneficial-effect duration multiplier; uses the larger of this and the minimum extension.")
            .defineInRange("hairpinBeneficialEffectExtension", 1.15, 1.0, 10.0);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue HAIRPIN_MIN_EXTENSION_TICKS = BUILDER
            .comment("发簪有益效果的最小延长量，单位 tick")
            .comment("Minimum ticks added to a beneficial effect by Hairpin.")
            .defineInRange("hairpinMinExtensionTicks", 300, 0, 10000);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue FRAGRANT_INGENUITY_FAVORABILITY_GAIN = BUILDER
            .comment("女仆喂主人吃点东西时，女仆自己涨多少好感 (默认: 2)")
            .comment("Fragrant Ingenuity favour the maid gains when she feeds her owner.")
            .defineInRange("fragrantIngenuityFavorabilityGain", 2, 1, 10);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_MAX_STACKS = BUILDER
            .comment("春花-返最多能存几层，层数就是她替主人挨刀的次数 (默认: 3)")
            .comment("Spring Bloom Return: maximum number of stored stacks.")
            .defineInRange("springBloomReturnMaxStacks", 3, 1, 8);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_STACK_DURATION_TICKS = BUILDER
            .comment("每层能存多久，存太久会自己过期掉 (默认: 400 tick，即 20 秒)")
            .comment("How long one Spring Bloom Return stack lives before expiring (default 400 ticks = 20s).")
            .defineInRange("springBloomReturnStackDurationTicks", 400, 20, 72000);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_GAIN_COOLDOWN_TICKS = BUILDER
            .comment("两层之间至少要隔这么久，免得一秒钟叠满 (默认: 20 tick，即 1 秒)")
            .comment("Minimum delay between gaining two stacks (default 20 ticks = 1 second).")
            .defineInRange("springBloomReturnGainCooldownTicks", 20, 0, 1200);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.IntValue SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_TICKS = BUILDER
            .comment("替主人挡一次之后要等这么久才能再挡 (默认: 200 tick，即 10 秒)")
            .comment("Cooldown after one Spring Bloom Return trigger (default 200 ticks = 10 seconds).")
            .defineInRange("springBloomReturnTriggerCooldownTicks", 200, 0, 72000);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD = BUILDER
            .comment("单次伤害到这个固定值就算「够重」，值得替主人挡一下 (默认: 4.0)")
            .comment("A single hit at or above this flat amount counts as heavy enough to trigger it.")
            .defineInRange("springBloomReturnDamageThreshold", 4.0, 0.0, 100.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD_RATIO = BUILDER
            .comment("按受击者最大生命计算的重击阈值；与固定伤害阈值满足其一即可")
            .comment("Heavy-hit threshold as a fraction of max health; either threshold can trigger.")
            .defineInRange("springBloomReturnDamageThresholdRatio", 0.10, 0.0, 1.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_HEAL_RATIO = BUILDER
            .comment("触发时直接把这一击削掉「保护对象最大生命 × 本值」那么多血，等于替他回了一口 (默认: 0.05，即 5%)")
            .comment("On trigger the hit is reduced by this fraction of the protected target's max health.")
            .defineInRange("springBloomReturnHealRatio", 0.05, 0.0, 1.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue SPRING_BLOOM_RETURN_COOLDOWN_REFUND_RATIO = BUILDER
            .comment("春花-返触发时返还的剩余法术冷却比例；好感 2 级起生效")
            .comment("Spring Bloom Return refunds this fraction of remaining spell cooldowns from favour level 2.")
            .defineInRange("springBloomReturnCooldownRefundRatio", 0.20, 0.0, 1.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.IntValue FRAGRANT_INGENUITY_BUFF_DURATION = BUILDER
            .comment("馥郁巧思喂食时给主人施加的随机增益时长，单位 tick")
            .comment("Duration in ticks of the random beneficial effect granted when the maid feeds her owner.")
            .defineInRange("fragrantIngenuityBuffDuration", 2400, 200, 120000);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FRAGRANT_INGENUITY_EFFECT_BLACKLIST = BUILDER
            .comment("馥郁巧思随机效果黑名单；支持完整 id 和 regex: 正则，默认与梦云水晶相同")
            .comment("Fragrant Ingenuity effect blacklist; supports ids and regex: patterns, defaults to Dream Crystal's list.")
            .defineListAllowEmpty(
                java.util.List.of("fragrantIngenuityEffectBlacklist"),
                () -> DEFAULT_RANDOM_BENEFICIAL_EFFECT_BLACKLIST,
                obj -> obj instanceof String
            );

    static {
        BUILDER.pop(); // utility
    }
    
    static {
        BUILDER.comment("触发机制相关饰品配置")
               .comment("Trigger mechanism bauble configurations")
               .push("triggers");
    }
    
    private static final ForgeConfigSpec.IntValue SILVER_CERCIS_TRIGGER_COUNT = BUILDER
            .comment("攒够几次命中才反一次真伤，反完清零重新数 (默认: 3)")
            .comment("Hits the maid must land before Silver Cercis fires once.")
            .defineInRange("silverCercisTriggerCount", 3, 1, 10);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue SILVER_CERCIS_COOLDOWN_TICKS = BUILDER
            .comment("两次命中间隔超过此 tick 数时，紫荆银冠立即触发，无需累计命中")
            .comment("If hits are farther apart than this many ticks, Silver Cercis triggers immediately.")
            .defineInRange("silverCercisCooldownTicks", 5, 1, 100);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue WOUND_RIME_BLADE_RECORD_DURATION = BUILDER
            .comment("破愈咒锋每次命中施加的禁疗层数；每次回血消耗一层并被取消")
            .comment("Heal-block charges per Wound Rime Blade hit; each heal consumes one charge and is cancelled.")
            .defineInRange("woundRimeBladeRecordDuration", 15, 5, 100);
    
    static {
        BUILDER.pop(); // triggers
    }
    
    static {
        BUILDER.comment("特殊饰品配置")
               .comment("Special bauble configurations")
               .push("special");
    }
    
    private static final ForgeConfigSpec.DoubleValue CHAOS_BOOK_TRUE_DAMAGE_MIN = BUILDER
            .comment("混沌之书真伤下限；与目标最大生命百分比取较大值")
            .comment("Chaos Book true damage floor; uses the larger of this and the max-health percentage.")
            .defineInRange("chaosBookTrueDamageMin", 5.0, 1.0, 50.0);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.DoubleValue CHAOS_BOOK_TRUE_DAMAGE_PERCENT = BUILDER
            .comment("真伤按目标最大生命算的比例，只有高血量目标才用得着这一条 (默认: 0.01，即 1%)")
            .comment("Max-health percentage used for the true damage above.")
            .defineInRange("chaosBookTrueDamagePercent", 0.01, 0.001, 0.1);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue CHAOS_BOOK_DAMAGE_SPLIT_COUNT = BUILDER
            .comment("混沌之书伤害分段数；单段下限可能使总伤害随段数增加")
            .comment("Chaos Book hit segments; the per-segment floor may increase total damage as this grows.")
            .defineInRange("chaosBookDamageSplitCount", 5, 1, 20);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.DoubleValue CHAOS_BOOK_MIN_SPLIT_DAMAGE = BUILDER
            .comment("每一段的下限，均分结果比它还小的时候就按它算 (默认: 3.0)")
            .comment("Damage floor for a single chunk.")
            .defineInRange("chaosBookMinSplitDamage", 3.0, 0.1, 100.0);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.DoubleValue SOUL_BOOK_DAMAGE_THRESHOLD_PERCENT = BUILDER
            .comment("魂之书把单次受到的伤害拦在「最大生命 × 本值」以内，超出的部分直接不算 (默认: 0.2，即 20%)")
            .comment("Soul Book clamps a single incoming hit to this fraction of the maid's max health.")
            .defineInRange("soulBookDamageThresholdPercent", 0.2, 0.05, 1.0);
    
    static {
        BUILDER.comment("");
    }
    
    private static final ForgeConfigSpec.IntValue SOUL_BOOK_DAMAGE_INTERVAL_THRESHOLD = BUILDER
            .comment("距上一次受伤不到这么多 tick 的话，这一击整个不生效；魂之书真正值钱的是这条 (默认: 10 tick，即 0.5 秒)")
            .comment("A hit arriving sooner than this after the previous one is cancelled outright.")
            .defineInRange("soulBookDamageIntervalThreshold", 10, 1, 100);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> DREAM_CRYSTAL_EFFECT_BLACKLIST = BUILDER
            .comment("梦云水晶随机效果黑名单；支持完整 id 和 regex: 正则")
            .comment("Dream Crystal effect blacklist; supports full ids and regex: patterns.")
            .defineListAllowEmpty(
                java.util.List.of("dreamCrystalEffectBlacklist"),
                () -> DEFAULT_RANDOM_BENEFICIAL_EFFECT_BLACKLIST,
                obj -> obj instanceof String
            );

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.BooleanValue DREAM_CRYSTAL_USE_EFFECT_WHITELIST = BUILDER
            .comment("true: 只抽白名单效果；false: 按黑名单排除")
            .comment("true: roll only whitelisted effects; false: exclude blacklisted effects.")
            .define("dreamCrystalUseEffectWhitelist", true);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> DREAM_CRYSTAL_EFFECT_WHITELIST = BUILDER
            .comment("梦云水晶效果白名单；启用白名单模式时生效，支持完整 id 和 regex: 正则")
            .comment("Dream Crystal effect whitelist; active in whitelist mode, supports ids and regex: patterns.")
            .defineListAllowEmpty(
                java.util.List.of("dreamCrystalEffectWhitelist"),
                    () -> DEFAULT_RANDOM_BENEFICIAL_EFFECT_WHITELIST,
                obj -> obj instanceof String
            );

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.BooleanValue DREAM_CRYSTAL_EXTRA_TRUE_DAMAGE_ENABLED = BUILDER
            .comment("梦云水晶是否在女仆命中时追加等量真伤")
            .comment("Whether Dream Crystal adds true damage equal to a maid's hit.")
            .define("dreamCrystalExtraTrueDamageEnabled", true);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.BooleanValue DREAM_CRYSTAL_SET_NO_AI_ENABLED = BUILDER
            .comment("梦云水晶把目标定住的那 1 秒里是否顺带关掉它的 AI，免得被定住的怪照样换目标、放技能 (默认: true)")
            .comment("Whether the Dream Crystal freeze also disables the target's AI for that second.")
            .define("dreamCrystalSetNoAiEnabled", true);

    static {
        BUILDER.pop(); // special
    }

    static {
        BUILDER.comment("移动相关饰品配置")
               .comment("Movement-related bauble configurations")
               .push("movement");
    }

    private static final ForgeConfigSpec.DoubleValue FLOATING_FOX_LEAF_OWNER_RANGE = BUILDER
            .comment("浮波狐叶赋予主人水面行走的最大距离，单位格")
            .comment("Maximum maid-owner distance in blocks for Floating Fox Leaf water walking.")
            .defineInRange("floatingFoxLeafOwnerRange", 16.0, 4.0, 64.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.DoubleValue MOLTEN_FOX_LEAF_OWNER_RANGE = BUILDER
            .comment("熔岩狐叶同理：主人在这个距离内才能踩着岩浆走 (默认: 16.0 格)")
            .comment("Same for Molten Fox Leaf, with lava instead of water.")
            .defineInRange("moltenFoxLeafOwnerRange", 16.0, 4.0, 64.0);

    static {
        BUILDER.pop(); // movement
        BUILDER.pop(); // baubles
    }

    static {
        BUILDER.comment("归隐之地维度相关配置")
               .comment("Retreat dimension configurations")
               .push("retreat_dimension");
    }
    
    private static final ForgeConfigSpec.BooleanValue ENABLE_PRIVATE_DIMENSIONS = BUILDER
            .comment("true: 每人独立的归隐维度；false: 共用维度，各有一座隐世之境结构")
            .comment("true: private dimension per player; false: shared dimension with one Retreat per player.")
            .define("enablePrivateDimensions", true);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.BooleanValue ENABLE_SHARED_QUOTA_LIMIT = BUILDER
            .comment("共用维度下限制每人搜索一次隐世之境；关闭后可反复搜索")
            .comment("In shared mode, limits each player to one Wind-Seeking Bell search.")
            .define("enableSharedQuotaLimit", true);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.BooleanValue ALLOW_MOB_SPAWNS_IN_RETREAT = BUILDER
            .comment("隐世之境是否允许生物生成，包括结构和刷怪笼生成")
            .comment("Whether mobs may spawn in the Retreat at all, including structure and spawner spawns.")
            .define("allowMobSpawnsInRetreat", true);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.BooleanValue ALLOW_HOSTILE_MOB_SPAWNS_IN_RETREAT = BUILDER
            .comment("允许生物生成时，是否保留敌对生物")
            .comment("Whether hostile mobs are kept when mob spawning is enabled.")
            .define("allowHostileMobSpawnsInRetreat", false);

    private static final ForgeConfigSpec.IntValue RETREAT_RECORD_RETENTION_DAYS = BUILDER
            .comment("无引用的空归隐维度元数据保留天数；0 表示不自动清理")
            .comment("Days to keep unreferenced, empty Retreat metadata; 0 disables cleanup.")
            .defineInRange("retreatRecordRetentionDays", 0, 0, 36500);

    static {
        BUILDER.pop(); // retreat_dimension
        BUILDER.push("compat");
    }

    private static final ForgeConfigSpec.BooleanValue AUTO_INSTALL_TLM_MODEL_PACK = BUILDER
            .comment("启动时安装内置 TLM 模型包并覆盖同名文件；自行维护 tlm_custom_pack 时请关闭")
            .comment("Installs the bundled TLM model pack on launch, overwriting matching files; disable if managed manually.")
            .define("autoInstallTlmModelPack", true);

    static {
        BUILDER.pop(); // compat
        BUILDER.comment("星之魔女酒狐 Boss 战配置")
               .comment("Magical Winefox boss fight configuration")
               .push("winefox");
    }

    private static final ForgeConfigSpec.BooleanValue WINEFOX_CHALLENGE_CONFIG_ENABLED = BUILDER
            .comment("是否允许使用星芒短剑的下一场挑战配置界面；关闭后不会打开界面")
            .comment("Whether the Starglint Dagger challenge configuration screen is available.")
            .define("winefoxChallengeConfigEnabled", true);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_MAID_DAMAGE_SHARE_LIMIT = BUILDER
            .comment("女仆造成的伤害占比超过此值时，不掉落星云核心或解锁特殊交易")
            .comment("Maid damage share above this fraction disables the Nebula Core drop and special trades.")
            .defineInRange("winefoxMaidDamageShareLimit", 0.6, 0.0, 1.0);

    static {
        BUILDER.comment("");
    }

    private static final ForgeConfigSpec.BooleanValue WINEFOX_TRUE_DAMAGE_RESTRICTS_REWARD = BUILDER
            .comment("本场使用真伤后是否限制星云核心掉落和特殊交易")
            .comment("Whether true damage in this fight also restricts the Nebula Core drop and special trades.")
            .define("winefoxTrueDamageRestrictsReward", true);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_MAX_HEALTH = BUILDER
            .comment("酒狐最大生命；修改后对新生成或重新加载的实体生效")
            .comment("Winefox max health; changes apply when a boss spawns or reloads.")
            .defineInRange("winefoxMaxHealth", 600.0, 1.0, 100000.0);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_DAMAGE_MULTIPLIER = BUILDER
            .comment("酒狐承伤倍率，先于女仆和二阶段倍率；0 表示免疫伤害")
            .comment("Winefox damage taken, before maid and phase-two multipliers; 0 prevents damage.")
            .defineInRange("winefoxDamageMultiplier", 1.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_SPELL_POWER_MULTIPLIER = BUILDER
            .comment("酒狐通用及各学派法强倍率；实体进入世界时按当前配置重算")
            .comment("Winefox general and school spell power multiplier, reapplied when the boss enters the world.")
            .defineInRange("winefoxSpellPowerMultiplier", 1.0, 0.0, 1000.0);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_HIT_DAMAGE_CAP_RATIO = BUILDER
            .comment("单次最终伤害上限，占最大生命的比例；包含真伤，0 表示不限伤")
            .comment("Per-hit final damage cap as a fraction of max health, including true damage; 0 disables it.")
            .defineInRange("winefoxHitDamageCapRatio", 0.08, 0.0, 1.0);

    private static final ForgeConfigSpec.IntValue WINEFOX_HIT_INTERVAL_TICKS = BUILDER
            .comment("两次有效受击的最短间隔，单位 tick；包含法术和真伤，0 表示不限制")
            .comment("Minimum ticks between effective hits, including spells and true damage; 0 disables it.")
            .defineInRange("winefoxHitIntervalTicks", 6, 0, 200);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_MAID_DAMAGE_MULTIPLIER = BUILDER
            .comment("女仆及其召唤物对酒狐造成的伤害倍率，叠加在全局承伤倍率上")
            .comment("Damage dealt by maids and their summons to Winefox, on top of her global damage multiplier.")
            .defineInRange("winefoxMaidDamageMultiplier", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_DAMAGE_TO_MAID_MULTIPLIER = BUILDER
            .comment("酒狐对女仆及其召唤物的伤害倍率；正式挑战中仍受最低生命限制")
            .comment("Damage dealt by Winefox to maids and their summons; the duel health floor still applies.")
            .defineInRange("winefoxDamageToMaidMultiplier", 5.0, 0.0, 100.0);


    private static final ForgeConfigSpec.DoubleValue WINEFOX_PHASE_TWO_DAMAGE_MULTIPLIER = BUILDER
            .comment("酒狐二阶段承伤倍率，与其它承伤倍率叠乘")
            .comment("Winefox damage taken in phase two, multiplied with other damage modifiers.")
            .defineInRange("winefoxPhaseTwoDamageMultiplier", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue WINEFOX_DUEL_SURVIVAL_FLOOR = BUILDER
            .comment("正式挑战中玩家及女仆的最低生命；玩家判负，女仆退场")
            .comment("Minimum player and maid health in a formal duel; players lose and maids retire at this value.")
            .defineInRange("winefoxDuelSurvivalFloor", 1.0, 0.0, 1024.0);

    static {
        BUILDER.pop(); // winefox
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // 缓存的配置值
    public static double maxSpellRange;
    public static double meleeRange;
    public static double farRange;
    public static double spellDamageMultiplier;
    public static double coolDownMultiplier;
    public static int meleeAttackInterval;
    public static int farAttackInterval;
    public static java.util.List<String> ironsSpellBlacklist;

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
    public static boolean dreamCrystalSetNoAiEnabled;

    public static boolean enablePrivateDimensions;
    public static boolean enableSharedQuotaLimit;
    public static boolean allowMobSpawnsInRetreat;
    public static boolean allowHostileMobSpawnsInRetreat;
    public static int retreatRecordRetentionDays;

    public static boolean autoInstallTlmModelPack;

    public static double winefoxMaidDamageShareLimit;
    public static boolean winefoxChallengeConfigEnabled;
    public static boolean winefoxTrueDamageRestrictsReward;
    public static double winefoxMaxHealth;
    public static double winefoxDamageMultiplier;
    public static double winefoxSpellPowerMultiplier;
    public static double winefoxHitDamageCapRatio;
    public static int winefoxHitIntervalTicks;
    public static double winefoxMaidDamageMultiplier;
    public static double winefoxDamageToMaidMultiplier;
    public static double winefoxPhaseTwoDamageMultiplier;
    public static double winefoxDuelSurvivalFloor;


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        maxSpellRange = MAX_SPELL_RANGE.get();
        meleeRange = MELEE_RANGE.get();
        spellDamageMultiplier = SPELL_DAMAGE_MULTIPLIER.get();
        coolDownMultiplier = COOLDOWN_MULITIPLIER.get();
        farRange = FAR_RANGE.get();
        meleeAttackInterval = MELEE_ATTACK_INTERVAL.get();
        farAttackInterval = FAR_ATTACK_INTERVAL.get();
        ironsSpellBlacklist = new ArrayList<>(IRONS_SPELL_BLACKLIST.get());
        
        silverCercisTrueDamageMultiplier = SILVER_CERCIS_TRUE_DAMAGE_MULTIPLIER.get();
        springRingMaxDamageBonus = SPRING_RING_MAX_DAMAGE_BONUS.get();
        
        flowCoreHealthRegenRate = FLOW_CORE_HEALTH_REGEN_RATE.get();
        bleedingHeartHealRatio = BLEEDING_HEART_HEAL_RATIO.get();
        
        flowCoreDamageReduction = FLOW_CORE_DAMAGE_REDUCTION.get();
        doubleHeartChainShareRatio = DOUBLE_HEART_CHAIN_SHARE_RATIO.get();
        doubleHeartChainMaxDistance = DOUBLE_HEART_CHAIN_MAX_DISTANCE.get();

        quickChantRingCooldownReduction = QUICK_CHANT_RING_COOLDOWN_REDUCTION.get();
        
        rockCrystalKnockbackResistance = ROCK_CRYSTAL_KNOCKBACK_RESISTANCE.get();
        
        flowCoreTickInterval = FLOW_CORE_TICK_INTERVAL.get();
        
        silverCercisTriggerCount = SILVER_CERCIS_TRIGGER_COUNT.get();
        silverCercisCooldownTicks = SILVER_CERCIS_COOLDOWN_TICKS.get();
        woundRimeBladeRecordTimes = WOUND_RIME_BLADE_RECORD_DURATION.get();
        
        hairpinBeneficialEffectExtension = HAIRPIN_BENEFICIAL_EFFECT_EXTENSION.get();
        hairpinMinExtensionTicks = HAIRPIN_MIN_EXTENSION_TICKS.get();
        fragrantIngenuityFavorabilityGain = FRAGRANT_INGENUITY_FAVORABILITY_GAIN.get();
        fragrantIngenuityBuffDuration = FRAGRANT_INGENUITY_BUFF_DURATION.get();
        fragrantIngenuityEffectBlacklist = new ArrayList<>(FRAGRANT_INGENUITY_EFFECT_BLACKLIST.get());
        springBloomReturnMaxStacks = SPRING_BLOOM_RETURN_MAX_STACKS.get();
        springBloomReturnStackDurationTicks = SPRING_BLOOM_RETURN_STACK_DURATION_TICKS.get();
        springBloomReturnGainCooldownTicks = SPRING_BLOOM_RETURN_GAIN_COOLDOWN_TICKS.get();
        springBloomReturnTriggerCooldownTicks = SPRING_BLOOM_RETURN_TRIGGER_COOLDOWN_TICKS.get();
        springBloomReturnDamageThreshold = SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD.get();
        springBloomReturnDamageThresholdRatio = SPRING_BLOOM_RETURN_DAMAGE_THRESHOLD_RATIO.get();
        springBloomReturnHealRatio = SPRING_BLOOM_RETURN_HEAL_RATIO.get();
        springBloomReturnCooldownRefundRatio = SPRING_BLOOM_RETURN_COOLDOWN_REFUND_RATIO.get();
        
        chaosBookTrueDamageMin = CHAOS_BOOK_TRUE_DAMAGE_MIN.get();
        chaosBookTrueDamagePercent = CHAOS_BOOK_TRUE_DAMAGE_PERCENT.get();
        chaosBookDamageSplitCount = CHAOS_BOOK_DAMAGE_SPLIT_COUNT.get();
        chaosBookMinSplitDamage = CHAOS_BOOK_MIN_SPLIT_DAMAGE.get();
        soulBookDamageThresholdPercent = SOUL_BOOK_DAMAGE_THRESHOLD_PERCENT.get();
        soulBookDamageIntervalThreshold = SOUL_BOOK_DAMAGE_INTERVAL_THRESHOLD.get();

        floatingFoxLeafOwnerRange = FLOATING_FOX_LEAF_OWNER_RANGE.get();
        moltenFoxLeafOwnerRange = MOLTEN_FOX_LEAF_OWNER_RANGE.get();

        dreamCrystalEffectBlacklist = new java.util.ArrayList<>(DREAM_CRYSTAL_EFFECT_BLACKLIST.get());
        dreamCrystalUseEffectWhitelist = DREAM_CRYSTAL_USE_EFFECT_WHITELIST.get();
        dreamCrystalEffectWhitelist = new java.util.ArrayList<>(DREAM_CRYSTAL_EFFECT_WHITELIST.get());
        dreamCrystalExtraTrueDamageEnabled = DREAM_CRYSTAL_EXTRA_TRUE_DAMAGE_ENABLED.get();
        dreamCrystalSetNoAiEnabled = DREAM_CRYSTAL_SET_NO_AI_ENABLED.get();
        DreamCatCrystalBauble.invalidateBeneficialEffectsCache();

        enablePrivateDimensions = ENABLE_PRIVATE_DIMENSIONS.get();
        enableSharedQuotaLimit = ENABLE_SHARED_QUOTA_LIMIT.get();
        allowMobSpawnsInRetreat = ALLOW_MOB_SPAWNS_IN_RETREAT.get();
        allowHostileMobSpawnsInRetreat = ALLOW_HOSTILE_MOB_SPAWNS_IN_RETREAT.get();
        retreatRecordRetentionDays = RETREAT_RECORD_RETENTION_DAYS.get();

        autoInstallTlmModelPack = AUTO_INSTALL_TLM_MODEL_PACK.get();

        winefoxMaidDamageShareLimit = WINEFOX_MAID_DAMAGE_SHARE_LIMIT.get();
        winefoxChallengeConfigEnabled = WINEFOX_CHALLENGE_CONFIG_ENABLED.get();
        winefoxTrueDamageRestrictsReward = WINEFOX_TRUE_DAMAGE_RESTRICTS_REWARD.get();
        winefoxMaxHealth = WINEFOX_MAX_HEALTH.get();
        winefoxDamageMultiplier = WINEFOX_DAMAGE_MULTIPLIER.get();
        winefoxSpellPowerMultiplier = WINEFOX_SPELL_POWER_MULTIPLIER.get();
        winefoxHitDamageCapRatio = WINEFOX_HIT_DAMAGE_CAP_RATIO.get();
        winefoxHitIntervalTicks = WINEFOX_HIT_INTERVAL_TICKS.get();
        winefoxMaidDamageMultiplier = WINEFOX_MAID_DAMAGE_MULTIPLIER.get();
        winefoxDamageToMaidMultiplier = WINEFOX_DAMAGE_TO_MAID_MULTIPLIER.get();
        winefoxPhaseTwoDamageMultiplier = WINEFOX_PHASE_TWO_DAMAGE_MULTIPLIER.get();
        winefoxDuelSurvivalFloor = WINEFOX_DUEL_SURVIVAL_FLOOR.get();

        SpellCombatMeleeTask.setSpellRange((float) maxSpellRange);
        SpellCombatFarTask.setSpellRange((float) maxSpellRange);
        SimplifiedSpellCaster.MELEE_RANGE= (float) meleeRange;
        SimplifiedSpellCaster.FAR_RANGE= (float) farRange;

        Global.resetCommonDamageCalc();

        Global.resetCommonCoolDownCalc();
        DreamCatCrystalBauble.registerCommonCallbacks();
    }


}
