package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.HiddenRetreatStructure;
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
}
