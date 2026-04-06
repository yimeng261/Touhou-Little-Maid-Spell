package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.FairyMaidCafeStructure;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenCherryTreeStructure;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
import com.github.yimeng261.maidspell.worldgen.structure.YinYangAltarStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class MaidSpellStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MaidSpellMod.MOD_ID);
    
    // 隐居之地结构（基于拼图系统）
    public static final RegistryObject<StructureType<HiddenRetreatStructure>> HIDDEN_RETREAT = STRUCTURE_TYPES.register(
            "hidden_retreat",
            () -> () -> HiddenRetreatStructure.CODEC
    );
    
    // 阴阳祭坛结构（单块结构，需要goety模组）
    public static final RegistryObject<StructureType<YinYangAltarStructure>> YIN_YANG_ALTAR = STRUCTURE_TYPES.register(
            "yin_yang_altar",
            () -> () -> YinYangAltarStructure.CODEC
    );
    
    // 妖精女仆咖啡厅结构（单块结构，需要妖怪归家模组）
    public static final RegistryObject<StructureType<FairyMaidCafeStructure>> FAIRY_MAID_CAFE = STRUCTURE_TYPES.register(
            "fairy_maid_cafe",
            () -> () -> FairyMaidCafeStructure.CODEC
    );
    
    // 隐世樱花树结构（单块结构，在任意维度樱花林生成）
    public static final RegistryObject<StructureType<HiddenCherryTreeStructure>> HIDDEN_CHERRY_TREE = STRUCTURE_TYPES.register(
            "hidden_cherry_tree",
            () -> () -> HiddenCherryTreeStructure.CODEC
    );
}
