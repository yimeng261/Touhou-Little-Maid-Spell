package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.FallenSanctumStructure;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import com.github.yimeng261.maidspell.worldgen.structure.LandJigsawStructure;
import com.github.yimeng261.maidspell.worldgen.structure.RelicSanctumStructure;
import com.github.yimeng261.maidspell.worldgen.structure.StarwatchTowerStructure;
import com.github.yimeng261.maidspell.worldgen.structure.StellarEndshoreStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MaidSpellStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES
            = DeferredRegister.create(Registries.STRUCTURE_TYPE, MaidSpellMod.MOD_ID);

    // 隐世之境结构（基于拼图系统，带特殊逻辑：去重、队列、配额）
    public static final DeferredHolder<StructureType<?>, StructureType<HiddenRetreatStructure>> HIDDEN_RETREAT
            = STRUCTURE_TYPES.register("hidden_retreat", () -> () -> HiddenRetreatStructure.CODEC);

    // 通用 Jigsaw 结构，支持 avoid_water 字段
    public static final DeferredHolder<StructureType<?>, StructureType<LandJigsawStructure>> LAND_JIGSAW
            = STRUCTURE_TYPES.register("land_jigsaw", () -> () -> LandJigsawStructure.CODEC);

    // 圣遗礼拜堂结构（基于拼图系统，带地形平整度检测）
    public static final DeferredHolder<StructureType<?>, StructureType<RelicSanctumStructure>> RELIC_SANCTUM
            = STRUCTURE_TYPES.register("relic_sanctum", () -> () -> RelicSanctumStructure.CODEC);

    // 堕天圣堂结构（基于拼图系统，下界绯红森林地表生成）
    public static final DeferredHolder<StructureType<?>, StructureType<FallenSanctumStructure>> FALLEN_SANCTUM
            = STRUCTURE_TYPES.register("fallen_sanctum", () -> () -> FallenSanctumStructure.CODEC);

    // 星途终岸结构（基于拼图系统，悬在末地外岛上空的固定高度）
    public static final DeferredHolder<StructureType<?>, StructureType<StellarEndshoreStructure>> STELLAR_ENDSHORE
            = STRUCTURE_TYPES.register("stellar_endshore", () -> () -> StellarEndshoreStructure.CODEC);

    // 观星塔结构（基于拼图系统，末地外岛地表，落点判定同末地城）
    public static final DeferredHolder<StructureType<?>, StructureType<StarwatchTowerStructure>> STARWATCH_TOWER
            = STRUCTURE_TYPES.register("starwatch_tower", () -> () -> StarwatchTowerStructure.CODEC);
}
