package com.github.yimeng261.maidspell;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 归隐之地维度的通用配置，存放在 retreat.toml。 */
final class ConfigRetreat {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("归隐之地维度相关配置",
                    "Retreat dimension configurations").push("retreat_dimension");
    }

    private static final ModConfigSpec.BooleanValue ENABLE_PRIVATE_DIMENSIONS = BUILDER
            .comment("true: 每人独立的归隐维度；false: 共用维度，各有一座隐世之境结构",
                    "true: private dimension per player; false: shared dimension with one Retreat per player.")
            .define("enablePrivateDimensions", true);
    private static final ModConfigSpec.BooleanValue ENABLE_SHARED_QUOTA_LIMIT = BUILDER
            .comment("共用维度下限制每人搜索一次隐世之境；关闭后可反复搜索",
                    "In shared mode, limits each player to one Wind-Seeking Bell search.")
            .define("enableSharedQuotaLimit", true);
    private static final ModConfigSpec.BooleanValue DISABLE_HOSTILE_MOB_SPAWNING = BUILDER
            .comment("true: 归隐之地不会自然生成敌对生物；false: 正常生成 (默认: false)",
                    "true: no hostile mobs spawn naturally in retreat dimensions; false: they spawn normally.")
            .define("disableHostileMobSpawning", false);
    private static final ModConfigSpec.BooleanValue ALLOW_ALL_STRUCTURES = BUILDER
            .comment("true: 放行所有结构，忽略白名单；false: 仅允许白名单中的结构生成 (默认: false)",
                    "true: allow every structure and ignore the whitelist; false: only whitelisted structures generate.")
            .define("allowAllStructures", false);
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_STRUCTURES = BUILDER
            .comment("允许在归隐之地生成的结构白名单，格式 [\"modid:structure_name\", ...]；仅在 allowAllStructures 为 false 时生效",
                    "Whitelist of structures allowed in retreat dimensions, as [\"modid:structure_name\", ...]; only used when allowAllStructures is false.")
            .defineListAllowEmpty(
                    List.of("allowedStructures"),
                    () -> List.of(
                            MaidSpellMod.MOD_ID + ":hidden_retreat",
                            MaidSpellMod.MOD_ID + ":hidden_cherry_tree"
                    ),
                    obj -> obj instanceof String);
    private static final ModConfigSpec.BooleanValue ALLOW_MOB_SPAWNS_IN_RETREAT = BUILDER
            .comment("隐世之境是否允许生物生成，包括结构和刷怪笼生成",
                    "Whether mobs may spawn in the Retreat at all, including structure and spawner spawns.")
            .define("allowMobSpawnsInRetreat", true);
    private static final ModConfigSpec.BooleanValue ALLOW_HOSTILE_MOB_SPAWNS_IN_RETREAT = BUILDER
            .comment("允许生物生成时，是否保留敌对生物",
                    "Whether hostile mobs are kept when mob spawning is enabled.")
            .define("allowHostileMobSpawnsInRetreat", false);
    private static final ModConfigSpec.IntValue RETREAT_RECORD_RETENTION_DAYS = BUILDER
            .comment("无引用的空归隐维度元数据保留天数；0 表示不自动清理",
                    "Days to keep unreferenced, empty Retreat metadata; 0 disables cleanup.")
            .defineInRange("retreatRecordRetentionDays", 0, 0, 36500);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ConfigRetreat() {
    }

    static void load() {
        Config.enablePrivateDimensions = ENABLE_PRIVATE_DIMENSIONS.get();
        Config.enableSharedQuotaLimit = ENABLE_SHARED_QUOTA_LIMIT.get();
        Config.disableHostileMobSpawning = DISABLE_HOSTILE_MOB_SPAWNING.get();
        Config.allowAllStructures = ALLOW_ALL_STRUCTURES.get();
        Set<ResourceLocation> structures = new HashSet<>();
        for (String id : ALLOWED_STRUCTURES.get()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null) {
                structures.add(location);
            }
        }
        Config.allowedStructures = structures;
        Config.allowMobSpawnsInRetreat = ALLOW_MOB_SPAWNS_IN_RETREAT.get();
        Config.allowHostileMobSpawnsInRetreat = ALLOW_HOSTILE_MOB_SPAWNS_IN_RETREAT.get();
        Config.retreatRecordRetentionDays = RETREAT_RECORD_RETENTION_DAYS.get();
    }
}
