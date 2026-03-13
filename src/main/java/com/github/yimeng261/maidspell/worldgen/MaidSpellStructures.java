package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import com.github.yimeng261.maidspell.worldgen.structure.LandJigsawStructure;
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
}
