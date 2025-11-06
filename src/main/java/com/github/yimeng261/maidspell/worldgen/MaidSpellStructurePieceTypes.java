package com.github.yimeng261.maidspell.worldgen;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.worldgen.structure.SingleTemplatePiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class MaidSpellStructurePieceTypes {
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES = 
        DeferredRegister.create(Registries.STRUCTURE_PIECE, MaidSpellMod.MOD_ID);
    
    public static final RegistryObject<StructurePieceType> SINGLE_TEMPLATE_PIECE = STRUCTURE_PIECE_TYPES.register(
        "single_template_piece",
        () -> SingleTemplatePiece::new
    );
}

