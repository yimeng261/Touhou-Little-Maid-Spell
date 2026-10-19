package com.github.yimeng261.maidspell;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;

/** Combat-related common configuration stored in combat.toml. */
final class ConfigCombat {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.comment("战斗系统相关配置",
                    "Combat system configurations").push("combat");
    }

    private static final ForgeConfigSpec.DoubleValue MAX_SPELL_RANGE = BUILDER
            .comment("法术战斗任务里女仆的索敌与施法距离，超出这个距离她就不再出手 (默认: 24.0 格)",
                    "How far a maid will pick targets and cast during a spell-combat task, in blocks.")
            .defineInRange("maxSpellRange", 24.0, 8.0, 64.0);
    private static final ForgeConfigSpec.DoubleValue MELEE_RANGE = BUILDER
            .comment("近战任务判定「够得着」的距离，比原版手长一点，女仆才追得上边走边打的目标 (默认: 2.5 格)",
                    "Melee reach used by the melee task, in blocks.")
            .defineInRange("meleeRange", 2.5, 1.0, 5.0);
    private static final ForgeConfigSpec.DoubleValue FAR_RANGE = BUILDER
            .comment("远程任务的开火距离，箭矢/法术从这个距离开始出手 (默认: 8.5 格)",
                    "Firing distance used by the far task, in blocks.")
            .defineInRange("farRange", 8.5, 1.0, 20.0);
    private static final ForgeConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
            .comment("女仆在法术战斗任务下的伤害倍率；只有装着那个任务才吃这条，填 0 等于让她一点输出都没有 (默认: 1.0)",
                    "Damage multiplier for the maid while running the spell-combat task. 0 turns her damage off.")
            .defineInRange("maidDamageMultiplier", 1.0, 0.0, 50.0);
    private static final ForgeConfigSpec.DoubleValue COOLDOWN_MULTIPLIER = BUILDER
            .comment("法术战斗任务中的女仆法术冷却倍率；越小施法越频繁",
                    "Maid spell cooldown multiplier during spell combat; lower values allow more frequent casts.")
            .defineInRange("maidCooldownMultiplier", 1.0, 0.0, 50.0);
    private static final ForgeConfigSpec.IntValue MELEE_ATTACK_INTERVAL = BUILDER
            .comment("近战任务两次出手之间至少隔这么多 tick；调小是贴脸连击，调大输出直接掉一档 (默认: 8 tick，即 0.4 秒)",
                    "Minimum ticks between two attacks in a melee task (default 8 ticks = 0.4 seconds).")
            .defineInRange("meleeAttackInterval", 8, 1, 100);
    private static final ForgeConfigSpec.IntValue FAR_ATTACK_INTERVAL = BUILDER
            .comment("远程任务两次出手之间的间隔，同样按 tick 算 (默认: 5 tick，即 0.25 秒)",
                    "Minimum ticks between two attacks in a far task (default 5 ticks = 0.25 seconds).")
            .defineInRange("farAttackInterval", 5, 1, 100);
    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> IRONS_SPELL_BLACKLIST = BUILDER
            .comment("女仆不施放列表中的法术；可填完整 id 或 regex: 前缀的正则",
                    "Maids will not cast listed spells. Use full ids or regex: patterns.")
            .defineListAllowEmpty(java.util.List.of("spellBlacklist"),
                    () -> java.util.List.of("irons_spellbooks:spectral_hammer"),
                    obj -> obj instanceof String);
    private static final ForgeConfigSpec.BooleanValue POWERFUL_FRIENDLY_FIRE_PROTECTION = BUILDER
            .comment("强力防友伤：阻止绕过普通伤害事件的友方伤害，包括强制写入生命值 (默认: 关闭)",
                    "Powerful friendly-fire protection, including damage that bypasses normal damage events.")
            .define("powerfulFriendlyFireProtection", false);

    static {
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ConfigCombat() {
    }

    static void load() {
        Config.maxSpellRange = MAX_SPELL_RANGE.get();
        Config.meleeRange = MELEE_RANGE.get();
        Config.spellDamageMultiplier = SPELL_DAMAGE_MULTIPLIER.get();
        Config.coolDownMultiplier = COOLDOWN_MULTIPLIER.get();
        Config.farRange = FAR_RANGE.get();
        Config.meleeAttackInterval = MELEE_ATTACK_INTERVAL.get();
        Config.farAttackInterval = FAR_ATTACK_INTERVAL.get();
        Config.ironsSpellBlacklist = new ArrayList<>(IRONS_SPELL_BLACKLIST.get());
        Config.powerfulFriendlyFireProtection = POWERFUL_FRIENDLY_FIRE_PROTECTION.get();
    }
}
