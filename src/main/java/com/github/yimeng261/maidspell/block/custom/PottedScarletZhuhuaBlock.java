package com.github.yimeng261.maidspell.block.custom;

import com.github.yimeng261.maidspell.block.entity.ScarletZhuhuaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class PottedScarletZhuhuaBlock extends FlowerPotBlock implements EntityBlock {
    public PottedScarletZhuhuaBlock(Supplier<FlowerPotBlock> emptyPot, Supplier<? extends Block> plant) {
        super(emptyPot, plant, ScarletZhuhuaBlock.createPottedProperties());
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
