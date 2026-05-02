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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class MaidSpellBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MaidSpellMod.MOD_ID);

    public static final DeferredBlock<ScarletZhuhuaBlock> SCARLET_ZHUHUA =
            BLOCKS.registerBlock("scarlet_zhuhua", ScarletZhuhuaBlock::new, ScarletZhuhuaBlock.createProperties());

    public static final DeferredBlock<PottedScarletZhuhuaBlock> POTTED_SCARLET_ZHUHUA =
            BLOCKS.registerBlock("potted_scarlet_zhuhua", PottedScarletZhuhuaBlock::new, ScarletZhuhuaBlock.createPottedProperties());

    public static final DeferredBlock<YueLinglanBlock> YUE_LINGLAN =
            BLOCKS.registerBlock("yue_linglan", YueLinglanBlock::new, YueLinglanBlock.createProperties());

    public static final DeferredBlock<PottedYueLinglanBlock> POTTED_YUE_LINGLAN =
            BLOCKS.registerBlock("potted_yue_linglan", PottedYueLinglanBlock::new, YueLinglanBlock.createPottedProperties());

    public static final DeferredBlock<JingxuYoulanBlock> JINGXU_YOULAN =
            BLOCKS.registerBlock("jingxu_youlan", JingxuYoulanBlock::new, JingxuYoulanBlock.createProperties());

    public static final DeferredBlock<PottedJingxuYoulanBlock> POTTED_JINGXU_YOULAN =
            BLOCKS.registerBlock("potted_jingxu_youlan", PottedJingxuYoulanBlock::new, JingxuYoulanBlock.createPottedProperties());

    public static final DeferredBlock<TransientFoxLeafTrailBlock> FLOATING_FOX_LEAF_TRAIL =
            BLOCKS.registerBlock("floating_fox_leaf_trail",
                    props -> new TransientFoxLeafTrailBlock(10, 18, props),
                    TransientFoxLeafTrailBlock.createProperties());

    public static final DeferredBlock<TransientFoxLeafTrailBlock> FLOATING_FOX_LEAF_TRAIL_BIG =
            BLOCKS.registerBlock("floating_fox_leaf_trail_big",
                    props -> new TransientFoxLeafTrailBlock(12, 20, props),
                    TransientFoxLeafTrailBlock.createProperties());

    public static final DeferredBlock<TransientFoxLeafTrailBlock> FLOATING_FOX_LEAF_TRAIL_SMALL =
            BLOCKS.registerBlock("floating_fox_leaf_trail_small",
                    props -> new TransientFoxLeafTrailBlock(8, 16, props),
                    TransientFoxLeafTrailBlock.createProperties());

    public static final DeferredBlock<TransientFoxLeafTrailBlock> MOLTEN_FOX_LEAF_TRAIL =
            BLOCKS.registerBlock("molten_fox_leaf_trail",
                    props -> new TransientFoxLeafTrailBlock(10, 18, props),
                    TransientFoxLeafTrailBlock.createProperties());

    public static final DeferredBlock<TransientFoxLeafTrailBlock> MOLTEN_FOX_LEAF_TRAIL_BIG =
            BLOCKS.registerBlock("molten_fox_leaf_trail_big",
                    props -> new TransientFoxLeafTrailBlock(12, 20, props),
                    TransientFoxLeafTrailBlock.createProperties());

    public static final DeferredBlock<TransientFoxLeafTrailBlock> MOLTEN_FOX_LEAF_TRAIL_SMALL =
            BLOCKS.registerBlock("molten_fox_leaf_trail_small",
                    props -> new TransientFoxLeafTrailBlock(8, 16, props),
                    TransientFoxLeafTrailBlock.createProperties());

    private static final List<DeferredBlock<TransientFoxLeafTrailBlock>> FLOATING_FOX_LEAF_TRAILS = List.of(
            FLOATING_FOX_LEAF_TRAIL,
            FLOATING_FOX_LEAF_TRAIL_BIG,
            FLOATING_FOX_LEAF_TRAIL_SMALL
    );

    private static final List<DeferredBlock<TransientFoxLeafTrailBlock>> MOLTEN_FOX_LEAF_TRAILS = List.of(
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
