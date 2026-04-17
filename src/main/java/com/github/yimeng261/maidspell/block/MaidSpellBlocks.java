package com.github.yimeng261.maidspell.block;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.block.custom.JingxuYoulanBlock;
import com.github.yimeng261.maidspell.block.custom.PottedJingxuYoulanBlock;
import com.github.yimeng261.maidspell.block.custom.PottedScarletZhuhuaBlock;
import com.github.yimeng261.maidspell.block.custom.PottedYueLinglanBlock;
import com.github.yimeng261.maidspell.block.custom.ScarletZhuhuaBlock;
import com.github.yimeng261.maidspell.block.custom.TransientFoxLeafTrailBlock;
import com.github.yimeng261.maidspell.block.custom.YueLinglanBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

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

    public static final RegistryObject<Block> YUE_LINGLAN =
        BLOCKS.register("yue_linglan", YueLinglanBlock::new);

    public static final RegistryObject<Block> POTTED_YUE_LINGLAN =
        BLOCKS.register("potted_yue_linglan",
            () -> new PottedYueLinglanBlock(
                () -> (FlowerPotBlock) Blocks.FLOWER_POT,
                YUE_LINGLAN));

    public static final RegistryObject<Block> JINGXU_YOULAN =
        BLOCKS.register("jingxu_youlan", JingxuYoulanBlock::new);

    public static final RegistryObject<Block> POTTED_JINGXU_YOULAN =
        BLOCKS.register("potted_jingxu_youlan",
            () -> new PottedJingxuYoulanBlock(
                () -> (FlowerPotBlock) Blocks.FLOWER_POT,
                JINGXU_YOULAN));

    public static final RegistryObject<Block> FLOATING_FOX_LEAF_TRAIL =
        BLOCKS.register("floating_fox_leaf_trail",
            () -> new TransientFoxLeafTrailBlock(10, 18));

    public static final RegistryObject<Block> FLOATING_FOX_LEAF_TRAIL_BIG =
        BLOCKS.register("floating_fox_leaf_trail_big",
            () -> new TransientFoxLeafTrailBlock(12, 20));

    public static final RegistryObject<Block> FLOATING_FOX_LEAF_TRAIL_SMALL =
        BLOCKS.register("floating_fox_leaf_trail_small",
            () -> new TransientFoxLeafTrailBlock(8, 16));

    public static final RegistryObject<Block> MOLTEN_FOX_LEAF_TRAIL =
        BLOCKS.register("molten_fox_leaf_trail",
            () -> new TransientFoxLeafTrailBlock(10, 18));

    public static final RegistryObject<Block> MOLTEN_FOX_LEAF_TRAIL_BIG =
        BLOCKS.register("molten_fox_leaf_trail_big",
            () -> new TransientFoxLeafTrailBlock(12, 20));

    public static final RegistryObject<Block> MOLTEN_FOX_LEAF_TRAIL_SMALL =
        BLOCKS.register("molten_fox_leaf_trail_small",
            () -> new TransientFoxLeafTrailBlock(8, 16));

    private static final List<RegistryObject<Block>> FLOATING_FOX_LEAF_TRAILS = List.of(
        FLOATING_FOX_LEAF_TRAIL,
        FLOATING_FOX_LEAF_TRAIL_BIG,
        FLOATING_FOX_LEAF_TRAIL_SMALL
    );

    private static final List<RegistryObject<Block>> MOLTEN_FOX_LEAF_TRAILS = List.of(
        MOLTEN_FOX_LEAF_TRAIL,
        MOLTEN_FOX_LEAF_TRAIL_BIG,
        MOLTEN_FOX_LEAF_TRAIL_SMALL
    );

    private MaidSpellBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    public static void registerPottedPlants() {
        ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(SCARLET_ZHUHUA.getId(), POTTED_SCARLET_ZHUHUA);
        ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(YUE_LINGLAN.getId(), POTTED_YUE_LINGLAN);
        ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(JINGXU_YOULAN.getId(), POTTED_JINGXU_YOULAN);
    }

    public static BlockState getRandomFloatingFoxLeafTrail(RandomSource random) {
        return FLOATING_FOX_LEAF_TRAILS.get(random.nextInt(FLOATING_FOX_LEAF_TRAILS.size())).get().defaultBlockState();
    }

    public static boolean isFloatingFoxLeafTrail(BlockState state) {
        return FLOATING_FOX_LEAF_TRAILS.stream().anyMatch(entry -> state.is(entry.get()));
    }

    public static BlockState getRandomMoltenFoxLeafTrail(RandomSource random) {
        return MOLTEN_FOX_LEAF_TRAILS.get(random.nextInt(MOLTEN_FOX_LEAF_TRAILS.size())).get().defaultBlockState();
    }

    public static boolean isMoltenFoxLeafTrail(BlockState state) {
        return MOLTEN_FOX_LEAF_TRAILS.stream().anyMatch(entry -> state.is(entry.get()));
    }
}
