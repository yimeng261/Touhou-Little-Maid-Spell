package com.github.yimeng261.maidspell.block.custom;

import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import com.github.yimeng261.maidspell.block.entity.ScarletZhuhuaBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PottedScarletZhuhuaBlock extends FlowerPotBlock implements EntityBlock {
    private static final MapCodec<FlowerPotBlock> CODEC = simpleCodec(PottedScarletZhuhuaBlock::new);

    public PottedScarletZhuhuaBlock(BlockBehaviour.Properties properties) {
        super(() -> (FlowerPotBlock) Blocks.FLOWER_POT, MaidSpellBlocks.SCARLET_ZHUHUA, properties);
    }

    @Override
    public MapCodec<FlowerPotBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScarletZhuhuaBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : ScarletZhuhuaBlockEntity.createTicker(type);
    }
}
