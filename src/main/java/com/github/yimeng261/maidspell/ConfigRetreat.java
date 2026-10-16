package com.github.yimeng261.maidspell;

import net.minecraftforge.common.ForgeConfigSpec;

/** Retreat dimension configuration stored in retreat.toml. */
final class ConfigRetreat {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.comment("归隐之地维度相关配置",
                    "Retreat dimension configurations").push("retreat_dimension");
    }

    private static final ForgeConfigSpec.BooleanValue ENABLE_PRIVATE_DIMENSIONS = BUILDER
            .comment("true: 每人独立的归隐维度；false: 共用维度，各有一座隐世之境结构",
                    "true: private dimension per player; false: shared dimension with one Retreat per player.")
            .define("enablePrivateDimensions", true);
    private static final ForgeConfigSpec.BooleanValue ENABLE_SHARED_QUOTA_LIMIT = BUILDER
            .comment("共用维度下限制每人搜索一次隐世之境；关闭后可反复搜索",
                    "In shared mode, limits each player to one Wind-Seeking Bell search.")
            .define("enableSharedQuotaLimit", true);
    private static final ForgeConfigSpec.BooleanValue ALLOW_MOB_SPAWNS_IN_RETREAT = BUILDER
            .comment("隐世之境是否允许生物生成，包括结构和刷怪笼生成",
                    "Whether mobs may spawn in the Retreat at all, including structure and spawner spawns.")
            .define("allowMobSpawnsInRetreat", true);
    private static final ForgeConfigSpec.BooleanValue ALLOW_HOSTILE_MOB_SPAWNS_IN_RETREAT = BUILDER
            .comment("允许生物生成时，是否保留敌对生物",
                    "Whether hostile mobs are kept when mob spawning is enabled.")
            .define("allowHostileMobSpawnsInRetreat", false);
    private static final ForgeConfigSpec.IntValue RETREAT_RECORD_RETENTION_DAYS = BUILDER
            .comment("无引用的空归隐维度元数据保留天数；0 表示不自动清理",
                    "Days to keep unreferenced, empty Retreat metadata; 0 disables cleanup.")
            .defineInRange("retreatRecordRetentionDays", 0, 0, 36500);

    static {
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ConfigRetreat() {
    }

    static void load() {
        Config.enablePrivateDimensions = ENABLE_PRIVATE_DIMENSIONS.get();
        Config.enableSharedQuotaLimit = ENABLE_SHARED_QUOTA_LIMIT.get();
        Config.allowMobSpawnsInRetreat = ALLOW_MOB_SPAWNS_IN_RETREAT.get();
        Config.allowHostileMobSpawnsInRetreat = ALLOW_HOSTILE_MOB_SPAWNS_IN_RETREAT.get();
        Config.retreatRecordRetentionDays = RETREAT_RECORD_RETENTION_DAYS.get();
    }
}
