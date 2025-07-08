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
        
    private static final ForgeConfigSpec.DoubleValue OPTIMAL_SPELL_RANGE = BUILDER
        .comment("最佳法术攻击范围 (默认: 12.0)")
        .comment("Optimal spell attack range")
        .defineInRange("optimalSpellRange", 12.0, 4.0, 32.0);
        
    private static final ForgeConfigSpec.DoubleValue SAFE_DISTANCE = BUILDER
        .comment("安全距离，女仆会尝试保持的最小距离 (默认: 8.0)")
        .comment("Safe distance that maids try to maintain")
        .defineInRange("safeDistance", 8.0, 2.0, 20.0);
        
    private static final ForgeConfigSpec.DoubleValue MELEE_RANGE = BUILDER
        .comment("近战攻击范围 (默认: 2.5)")
        .comment("Melee attack range")
        .defineInRange("meleeRange", 2.5, 1.0, 5.0);
    
    // 法术相关配置
    private static final ForgeConfigSpec.IntValue BASE_SPELL_COOLDOWN = BUILDER
        .comment("基础法术冷却时间，tick (默认: 40)")
        .comment("Base spell cooldown in ticks")
        .defineInRange("baseSpellCooldown", 40, 10, 200);
        
    private static final ForgeConfigSpec.IntValue TACTICAL_SWITCH_INTERVAL = BUILDER
        .comment("战术切换间隔，tick (默认: 100)")
        .comment("Tactical switching interval in ticks")
        .defineInRange("tacticalSwitchInterval", 100, 20, 400);
        
    private static final ForgeConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
        .comment("法术伤害倍数 (默认: 1.0)")
        .comment("Spell damage multiplier")
        .defineInRange("spellDamageMultiplier", 1.0, 0.1, 5.0);
    
    // AI相关配置
    private static final ForgeConfigSpec.BooleanValue ENABLE_INTELLIGENT_TARGETING = BUILDER
        .comment("启用智能目标选择 (默认: true)")
        .comment("Enable intelligent targeting")
        .define("enableIntelligentTargeting", true);
        
    private static final ForgeConfigSpec.BooleanValue ENABLE_DYNAMIC_MODE_SWITCHING = BUILDER
        .comment("启用动态模式切换 (默认: true)")
        .comment("Enable dynamic mode switching")
        .define("enableDynamicModeSwitching", true);
        
    private static final ForgeConfigSpec.BooleanValue ENABLE_DISTANCE_MANAGEMENT = BUILDER
        .comment("启用距离管理 (默认: true)")
        .comment("Enable distance management")
        .define("enableDistanceManagement", true);
    
    // 调试配置
    private static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
        .comment("启用调试日志 (默认: false)")
        .comment("Enable debug logging")
        .define("enableDebugLogging", false);
        
    private static final ForgeConfigSpec.BooleanValue ENABLE_PERFORMANCE_MONITORING = BUILDER
        .comment("启用性能监控 (默认: true)")
        .comment("Enable performance monitoring")
        .define("enablePerformanceMonitoring", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // 缓存的配置值
    public static int maxManagedMaids;
    public static long updateInterval;
    public static double maxSpellRange;
    public static double optimalSpellRange;
    public static double safeDistance;
    public static double meleeRange;
    public static int baseSpellCooldown;
    public static int tacticalSwitchInterval;
    public static double spellDamageMultiplier;
    public static boolean enableIntelligentTargeting;
    public static boolean enableDynamicModeSwitching;
    public static boolean enableDistanceManagement;
    public static boolean enableDebugLogging;
    public static boolean enablePerformanceMonitoring;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 加载配置值到缓存变量
        maxSpellRange = MAX_SPELL_RANGE.get();
        optimalSpellRange = OPTIMAL_SPELL_RANGE.get();
        safeDistance = SAFE_DISTANCE.get();
        meleeRange = MELEE_RANGE.get();
        baseSpellCooldown = BASE_SPELL_COOLDOWN.get();
        tacticalSwitchInterval = TACTICAL_SWITCH_INTERVAL.get();
        spellDamageMultiplier = SPELL_DAMAGE_MULTIPLIER.get();
        enableIntelligentTargeting = ENABLE_INTELLIGENT_TARGETING.get();
        enableDynamicModeSwitching = ENABLE_DYNAMIC_MODE_SWITCHING.get();
        enableDistanceManagement = ENABLE_DISTANCE_MANAGEMENT.get();
        enableDebugLogging = ENABLE_DEBUG_LOGGING.get();
        enablePerformanceMonitoring = ENABLE_PERFORMANCE_MONITORING.get();
        
        System.out.println("女仆法术战斗系统配置已加载");
        System.out.println("最大管理女仆数: " + maxManagedMaids);
        System.out.println("最大法术范围: " + maxSpellRange);
        System.out.println("智能目标选择: " + (enableIntelligentTargeting ? "启用" : "禁用"));
        System.out.println("动态模式切换: " + (enableDynamicModeSwitching ? "启用" : "禁用"));
    }
    
    /**
     * 调试日志输出
     */
    public static void debugLog(String message) {
        if (enableDebugLogging) {
            System.out.println("[女仆法术战斗系统] " + message);
        }
    }
    
    /**
     * 性能监控日志
     */
    public static void performanceLog(String message) {
        if (enablePerformanceMonitoring) {
            System.out.println("[性能监控] " + message);
        }
    }
}
