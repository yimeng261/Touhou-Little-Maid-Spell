package com.github.yimeng261.maidspell;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 女仆法术战斗系统配置类
 * 管理所有可配置的参数和设置
 */
@Mod.EventBusSubscriber(modid = MaidSpellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    
    
    // 战斗相关配置
    private static final ForgeConfigSpec.DoubleValue MAX_SPELL_RANGE = BUILDER
        .comment("最大法术攻击范围 (默认: 24.0)")
        .comment("Maximum spell attack range")
        .defineInRange("maxSpellRange", 24.0, 8.0, 64.0);
        
    private static final ForgeConfigSpec.DoubleValue MELEE_RANGE = BUILDER
        .comment("近战攻击范围 (默认: 2.5)")
        .comment("Melee attack range")
        .defineInRange("meleeRange", 2.5, 1.0, 5.0);
        
    private static final ForgeConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
        .comment("法术伤害倍数 (默认: 1.0)")
        .comment("Spell damage multiplier")
        .defineInRange("spellDamageMultiplier", 1.0, 0, 50.0);
    


    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double maxSpellRange=24;
    public static double meleeRange=2.5;
    public static double spellDamageMultiplier=1;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 加载配置值到缓存变量
        maxSpellRange = MAX_SPELL_RANGE.get();
        meleeRange = MELEE_RANGE.get();
        spellDamageMultiplier = SPELL_DAMAGE_MULTIPLIER.get();
    }

}
