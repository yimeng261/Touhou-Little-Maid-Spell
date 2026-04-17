package com.github.yimeng261.maidspell.block;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.block.custom.PottedScarletZhuhuaBlock;
import com.github.yimeng261.maidspell.block.custom.ScarletZhuhuaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaidSpellBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MaidSpellMod.MOD_ID);

    public static final DeferredBlock<ScarletZhuhuaBlock> SCARLET_ZHUHUA =
            BLOCKS.registerBlock("scarlet_zhuhua", ScarletZhuhuaBlock::new, ScarletZhuhuaBlock.createProperties());

    public static final DeferredBlock<PottedScarletZhuhuaBlock> POTTED_SCARLET_ZHUHUA =
            BLOCKS.registerBlock("potted_scarlet_zhuhua", PottedScarletZhuhuaBlock::new, ScarletZhuhuaBlock.createPottedProperties());

    private MaidSpellBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    public static void registerPottedPlants() {
        ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(SCARLET_ZHUHUA.getId(), POTTED_SCARLET_ZHUHUA);
    }
}
