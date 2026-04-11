package com.github.yimeng261.maidspell.block;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.block.custom.PottedScarletZhuhuaBlock;
import com.github.yimeng261.maidspell.block.custom.ScarletZhuhuaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MaidSpellBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, MaidSpellMod.MOD_ID);

    public static final RegistryObject<Block> SCARLET_ZHUHUA =
        BLOCKS.register("scarlet_zhuhua", ScarletZhuhuaBlock::new);

    public static final RegistryObject<Block> POTTED_SCARLET_ZHUHUA =
        BLOCKS.register("potted_scarlet_zhuhua",
            () -> new PottedScarletZhuhuaBlock(
                () -> (FlowerPotBlock) Blocks.FLOWER_POT,
                SCARLET_ZHUHUA));

    private MaidSpellBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    public static void registerPottedPlants() {
        ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(SCARLET_ZHUHUA.getId(), POTTED_SCARLET_ZHUHUA);
    }
}
