package com.github.yimeng261.maidspell;

import net.minecraftforge.common.ForgeConfigSpec;

/** Magical Winefox boss configuration stored in boss.toml. */
final class ConfigWinefox {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.comment("星之魔女酒狐 Boss 战配置",
                    "Magical Winefox boss fight configuration").push("winefox");
    }

    private static final ForgeConfigSpec.BooleanValue CHALLENGE_CONFIG_ENABLED = BUILDER
            .comment("是否允许使用星芒短剑的下一场挑战配置界面；关闭后不会打开界面",
                    "Whether the Starglint Dagger challenge configuration screen is available.")
            .define("winefoxChallengeConfigEnabled", true);
    private static final ForgeConfigSpec.DoubleValue MAID_DAMAGE_SHARE_LIMIT = BUILDER
            .comment("女仆造成的伤害占比超过此值时，不掉落星云核心或解锁特殊交易",
                    "Maid damage share above this fraction disables the Nebula Core drop and special trades.")
            .defineInRange("winefoxMaidDamageShareLimit", 0.6, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue TRUE_DAMAGE_RESTRICTS_REWARD = BUILDER
            .comment("本场使用真伤后是否限制星云核心掉落和特殊交易",
                    "Whether true damage in this fight also restricts the Nebula Core drop and special trades.")
            .define("winefoxTrueDamageRestrictsReward", true);
    private static final ForgeConfigSpec.DoubleValue MAX_HEALTH = BUILDER
            .comment("酒狐最大生命；修改后对新生成或重新加载的实体生效",
                    "Winefox max health; changes apply when a boss spawns or reloads.")
            .defineInRange("winefoxMaxHealth", 600.0, 1.0, 100000.0);
    private static final ForgeConfigSpec.DoubleValue ARMOR = BUILDER
            .comment("酒狐基础护甲；生成、读档或挑战结束时应用",
                    "Winefox base armor, applied on spawn, reload or challenge reset.")
            .defineInRange("winefoxArmor", 12.0, 0.0, 30.0);
    private static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER = BUILDER
            .comment("酒狐承伤倍率，先于女仆和二阶段倍率；0 表示免疫伤害",
                    "Winefox damage taken, before maid and phase-two multipliers; 0 prevents damage.")
            .defineInRange("winefoxDamageMultiplier", 1.0, 0.0, 100.0);
    private static final ForgeConfigSpec.DoubleValue SPELL_POWER_MULTIPLIER = BUILDER
            .comment("酒狐通用及各学派法强倍率；实体进入世界时按当前配置重算",
                    "Winefox general and school spell power multiplier, reapplied when the boss enters the world.")
            .defineInRange("winefoxSpellPowerMultiplier", 1.0, 0.0, 1000.0);
    private static final ForgeConfigSpec.DoubleValue HIT_DAMAGE_CAP_RATIO = BUILDER
            .comment("单次最终伤害上限，占最大生命的比例；包含真伤，0 表示不限伤",
                    "Per-hit final damage cap as a fraction of max health, including true damage; 0 disables it.")
            .defineInRange("winefoxHitDamageCapRatio", 0.08, 0.0, 1.0);
    private static final ForgeConfigSpec.IntValue HIT_INTERVAL_TICKS = BUILDER
            .comment("两次有效受击的最短间隔，单位 tick；包含法术和真伤，0 表示不限制",
                    "Minimum ticks between effective hits, including spells and true damage; 0 disables it.")
            .defineInRange("winefoxHitIntervalTicks", 6, 0, 200);
    private static final ForgeConfigSpec.DoubleValue MAID_DAMAGE_MULTIPLIER = BUILDER
            .comment("女仆及其召唤物对酒狐造成的伤害倍率，叠加在全局承伤倍率上",
                    "Damage dealt by maids and their summons to Winefox, on top of her global damage multiplier.")
            .defineInRange("winefoxMaidDamageMultiplier", 0.5, 0.0, 1.0);
    private static final ForgeConfigSpec.DoubleValue DAMAGE_TO_MAID_MULTIPLIER = BUILDER
            .comment("酒狐对女仆及其召唤物的伤害倍率；正式挑战中仍受最低生命限制",
                    "Damage dealt by Winefox to maids and their summons; the duel health floor still applies.")
            .defineInRange("winefoxDamageToMaidMultiplier", 5.0, 0.0, 100.0);
    private static final ForgeConfigSpec.DoubleValue PHASE_TWO_DAMAGE_MULTIPLIER = BUILDER
            .comment("酒狐二阶段承伤倍率，与其它承伤倍率叠乘",
                    "Winefox damage taken in phase two, multiplied with other damage modifiers.")
            .defineInRange("winefoxPhaseTwoDamageMultiplier", 0.8, 0.0, 1.0);
    private static final ForgeConfigSpec.BooleanValue DAMAGE_DEBUG_LOGGING = BUILDER
            .comment("开启后逐次记录酒狐受伤、减伤与拦截结果到日志；排查结束后建议关闭",
                    "Log Winefox incoming damage, reductions and blocked hits at INFO level for diagnostics.")
            .define("winefoxDamageDebugLogging", false);
    private static final ForgeConfigSpec.BooleanValue ADAPTIVE_DAMAGE_ENABLED = BUILDER
            .comment("是否启用星之魔女二阶段自适应减伤",
                    "Whether adaptive damage reduction is enabled in phase two.")
            .define("winefoxAdaptiveDamageEnabled", true);
    private static final ForgeConfigSpec.DoubleValue ADAPTIVE_TARGET_DPS_RATIO = BUILDER
            .comment("自适应减伤目标 DPS，占酒狐最大生命的比例（每秒）",
                    "Target DPS as a fraction of Winefox max health per second.")
            .defineInRange("winefoxAdaptiveTargetDpsRatio", 0.045, 0.0001, 10.0);
    private static final ForgeConfigSpec.IntValue ADAPTIVE_WINDOW_TICKS = BUILDER
            .comment("自适应减伤统计窗口，单位 tick",
                    "Sliding window used by adaptive damage reduction, in ticks.")
            .defineInRange("winefoxAdaptiveWindowTicks", 100, 20, 1200);
    private static final ForgeConfigSpec.DoubleValue ADAPTIVE_ACTIVATION_RATIO = BUILDER
            .comment("尝试伤害达到目标 DPS 的此倍数后开始减伤",
                    "Pre-cap reduction starts at this multiple of the target DPS.")
            .defineInRange("winefoxAdaptiveActivationRatio", 1.2, 1.0, 100.0);
    private static final ForgeConfigSpec.DoubleValue ADAPTIVE_START_MULTIPLIER = BUILDER
            .comment("自适应减伤刚触发时的承伤倍率",
                    "Damage multiplier when adaptive reduction first activates.")
            .defineInRange("winefoxAdaptiveStartMultiplier", 0.9, 0.0, 1.0);
    private static final ForgeConfigSpec.DoubleValue ADAPTIVE_MIN_MULTIPLIER = BUILDER
            .comment("自适应减伤的最低承伤倍率；极端输出保护",
                    "Minimum multiplier for pre-cap adaptive reduction.")
            .defineInRange("winefoxAdaptiveMinMultiplier", 0.05, 0.0, 1.0);
    private static final ForgeConfigSpec.DoubleValue ADAPTIVE_SATURATION_RATIO = BUILDER
            .comment("尝试伤害达到目标 DPS 的此倍数时降至最低倍率",
                    "Pressure ratio at which pre-cap reduction reaches its minimum.")
            .defineInRange("winefoxAdaptiveSaturationRatio", 4.0, 1.2, 1000.0);
    private static final ForgeConfigSpec.DoubleValue ADAPTIVE_FINAL_BUDGET_MULTIPLIER = BUILDER
            .comment("限伤后的最终 DPS 预算相对目标 DPS 的倍数",
                    "Post-cap effective DPS budget as a multiple of the target DPS.")
            .defineInRange("winefoxAdaptiveFinalBudgetMultiplier", 2.0, 1.0, 100.0);
    private static final ForgeConfigSpec.DoubleValue DUEL_SURVIVAL_FLOOR = BUILDER
            .comment("正式挑战中玩家及女仆的最低生命；玩家判负，女仆退场",
                    "Minimum player and maid health in a formal duel; players lose and maids retire at this value.")
            .defineInRange("winefoxDuelSurvivalFloor", 1.0, 0.0, 1024.0);
    private static final ForgeConfigSpec.IntValue INVISIBILITY_SEARCH_TICKS = BUILDER
            .comment("酒狐专注搜索隐身玩家的时长，单位 tick；玩家持续隐身时刷新",
                    "Ticks spent searching only for the hidden player, refreshed while invisible.")
            .defineInRange("winefoxInvisibilitySearchTicks", 200, 1, 1200);

    static {
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ConfigWinefox() {
    }

    static void load() {
        Config.winefoxMaidDamageShareLimit = MAID_DAMAGE_SHARE_LIMIT.get();
        Config.winefoxChallengeConfigEnabled = CHALLENGE_CONFIG_ENABLED.get();
        Config.winefoxTrueDamageRestrictsReward = TRUE_DAMAGE_RESTRICTS_REWARD.get();
        Config.winefoxMaxHealth = MAX_HEALTH.get();
        Config.winefoxArmor = ARMOR.get();
        Config.winefoxDamageMultiplier = DAMAGE_MULTIPLIER.get();
        Config.winefoxSpellPowerMultiplier = SPELL_POWER_MULTIPLIER.get();
        Config.winefoxHitDamageCapRatio = HIT_DAMAGE_CAP_RATIO.get();
        Config.winefoxHitIntervalTicks = HIT_INTERVAL_TICKS.get();
        Config.winefoxMaidDamageMultiplier = MAID_DAMAGE_MULTIPLIER.get();
        Config.winefoxDamageToMaidMultiplier = DAMAGE_TO_MAID_MULTIPLIER.get();
        Config.winefoxPhaseTwoDamageMultiplier = PHASE_TWO_DAMAGE_MULTIPLIER.get();
        Config.winefoxDamageDebugLogging = DAMAGE_DEBUG_LOGGING.get();
        Config.winefoxAdaptiveDamageEnabled = ADAPTIVE_DAMAGE_ENABLED.get();
        Config.winefoxAdaptiveTargetDpsRatio = ADAPTIVE_TARGET_DPS_RATIO.get();
        Config.winefoxAdaptiveWindowTicks = ADAPTIVE_WINDOW_TICKS.get();
        Config.winefoxAdaptiveActivationRatio = ADAPTIVE_ACTIVATION_RATIO.get();
        Config.winefoxAdaptiveStartMultiplier = ADAPTIVE_START_MULTIPLIER.get();
        Config.winefoxAdaptiveMinMultiplier = ADAPTIVE_MIN_MULTIPLIER.get();
        Config.winefoxAdaptiveSaturationRatio = ADAPTIVE_SATURATION_RATIO.get();
        Config.winefoxAdaptiveFinalBudgetMultiplier = ADAPTIVE_FINAL_BUDGET_MULTIPLIER.get();
        Config.winefoxDuelSurvivalFloor = DUEL_SURVIVAL_FLOOR.get();
        Config.winefoxInvisibilitySearchTicks = INVISIBILITY_SEARCH_TICKS.get();
    }
}
