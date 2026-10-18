package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.MultiExclusionSpreadPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class MaidSpellStructurePlacements {
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, MaidSpellMod.MOD_ID);

    // 随机分散放置，可同时避开多个结构集
    public static final RegistryObject<StructurePlacementType<MultiExclusionSpreadPlacement>> MULTI_EXCLUSION_SPREAD =
            STRUCTURE_PLACEMENTS.register("multi_exclusion_spread", () -> () -> MultiExclusionSpreadPlacement.CODEC);
}
