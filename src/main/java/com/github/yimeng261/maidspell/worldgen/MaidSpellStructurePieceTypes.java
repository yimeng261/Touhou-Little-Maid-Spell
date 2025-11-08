package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MaidSpellStructurePieceTypes {
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_PIECE, MaidSpellMod.MOD_ID);
}

