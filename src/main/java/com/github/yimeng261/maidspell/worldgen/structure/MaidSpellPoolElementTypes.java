package com.github.yimeng261.maidspell.worldgen.structure;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MaidSpellPoolElementTypes {
    public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENT_TYPES =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_POOL_ELEMENT, MaidSpellMod.MOD_ID);

    public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<NoTerrainPoolElement>> NO_TERRAIN =
            POOL_ELEMENT_TYPES.register("no_terrain_pool_element",
                    () -> () -> NoTerrainPoolElement.CODEC);
}
