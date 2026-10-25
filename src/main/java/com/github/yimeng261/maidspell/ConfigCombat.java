package com.github.yimeng261.maidspell;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;

/** 战斗相关的通用配置，存放在 combat.toml。 */
final class ConfigCombat {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("战斗系统相关配置",
                    "Combat system configurations").push("combat");
    }

    private static final ModConfigSpec.DoubleValue MAX_SPELL_RANGE = BUILDER
            .comment("法术战斗任务里女仆的索敌与施法距离 (默认: 24.0 格)",
                    "How far a maid will pick targets and cast during a spell-combat task, in blocks.")
            .defineInRange("maxSpellRange", 24.0, 8.0, 64.0);
    private static final ModConfigSpec.DoubleValue MELEE_RANGE = BUILDER
            .comment("近战任务范围(默认: 2.5 格)",
                    "Melee reach used by the melee task, in blocks.")
            .defineInRange("meleeRange", 2.5, 1.0, 5.0);
    private static final ModConfigSpec.DoubleValue FAR_RANGE = BUILDER
            .comment("远程任务的开火距离 (默认: 8.5 格)",
                    "Firing distance used by the far task, in blocks.")
            .defineInRange("farRange", 8.5, 1.0, 20.0);
    private static final ModConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
            .comment("女仆在法术战斗任务下的伤害倍率 (默认: 1.0)",
                    "Damage multiplier for the maid while running the spell-combat task. 0 turns her damage off.")
            .defineInRange("maidDamageMultiplier", 1.0, 0.0, 50.0);
    private static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER = BUILDER
            .comment("法术战斗任务中的女仆法术冷却倍率 (默认: 1.0)",
                    "Maid spell cooldown multiplier during spell combat; lower values allow more frequent casts.")
            .defineInRange("maidCooldownMultiplier", 1.0, 0.0, 50.0);
    private static final ModConfigSpec.IntValue MELEE_ATTACK_INTERVAL = BUILDER
            .comment("近战任务攻击间隔 (默认: 8 tick，即 0.4 秒)",
                    "Minimum ticks between two attacks in a melee task (default 8 ticks = 0.4 seconds).")
            .defineInRange("meleeAttackInterval", 8, 1, 100);
    private static final ModConfigSpec.IntValue FAR_ATTACK_INTERVAL = BUILDER
            .comment("远程任务攻击间隔，同样按 tick 算 (默认: 5 tick，即 0.25 秒)",
                    "Minimum ticks between two attacks in a far task (default 5 ticks = 0.25 seconds).")
            .defineInRange("farAttackInterval", 5, 1, 100);
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> IRONS_SPELL_BLACKLIST = BUILDER
            .comment("女仆不施放列表中的法术；可填完整 id 或 regex: 前缀的正则",
                    "Maids will not cast listed spells. Use full ids or regex: patterns.")
            .defineListAllowEmpty(java.util.List.of("spellBlacklist"),
                    () -> java.util.List.of("irons_spellbooks:spectral_hammer"),
                    obj -> obj instanceof String);
    private static final ModConfigSpec.BooleanValue POWERFUL_FRIENDLY_FIRE_PROTECTION = BUILDER
            .comment("强力防友伤：阻止绕过普通伤害事件的友方伤害，包括强制写入生命值 (默认: 关闭)",
                    "Powerful friendly-fire protection, including damage that bypasses normal damage events.")
            .define("powerfulFriendlyFireProtection", false);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

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
