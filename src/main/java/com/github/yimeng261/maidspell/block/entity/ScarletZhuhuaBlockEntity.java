package com.github.yimeng261.maidspell.block.entity;

import com.github.yimeng261.maidspell.block.custom.ScarletZhuhuaBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ScarletZhuhuaBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 20;

    public ScarletZhuhuaBlockEntity(BlockPos pos, BlockState state) {
        super(MaidSpellBlockEntities.SCARLET_ZHUHUA.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ScarletZhuhuaBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        long gameTime = serverLevel.getGameTime();
        int offset = Math.floorMod(pos.asLong(), TICK_INTERVAL);
        if ((gameTime + offset) % TICK_INTERVAL != 0) {
            return;
        }

        ScarletZhuhuaBlock.applyAura(serverLevel, pos);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityTicker<T> createTicker(BlockEntityType<T> type) {
        return type == MaidSpellBlockEntities.SCARLET_ZHUHUA.get()
            ? (level, pos, state, blockEntity) -> serverTick(level, pos, state, (ScarletZhuhuaBlockEntity) blockEntity)
            : null;
    }
}
