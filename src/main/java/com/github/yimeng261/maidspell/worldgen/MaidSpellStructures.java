package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.ConditionalJigsawStructure;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MaidSpellStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES
            = DeferredRegister.create(Registries.STRUCTURE_TYPE, MaidSpellMod.MOD_ID);

    // 隐居之地结构（基于拼图系统）
    public static final DeferredHolder<StructureType<?>, StructureType<HiddenRetreatStructure>> HIDDEN_RETREAT
            = STRUCTURE_TYPES.register("hidden_retreat", () -> () -> HiddenRetreatStructure.CODEC);

    // 阴阳祭坛结构（单块结构，需要goety模组）
    // public static final DeferredHolder<StructureType<?>, StructureType<YinYangAltarStructure>> YIN_YANG_ALTAR
    //         = STRUCTURE_TYPES.register("yin_yang_altar", () -> () -> YinYangAltarStructure.CODEC);

    // 妖精女仆咖啡厅结构（单块结构，需要妖怪归家模组）
    // public static final DeferredHolder<StructureType<?>, StructureType<FairyMaidCafeStructure>> FAIRY_MAID_CAFE
    //         = STRUCTURE_TYPES.register("fairy_maid_cafe", () -> () -> FairyMaidCafeStructure.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<ConditionalJigsawStructure>> CONDITIONAL_JIGSAW
             = STRUCTURE_TYPES.register("conditional_jigsaw", () -> () -> ConditionalJigsawStructure.CODEC);
}
