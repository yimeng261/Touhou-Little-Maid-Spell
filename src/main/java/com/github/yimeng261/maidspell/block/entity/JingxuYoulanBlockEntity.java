package com.github.yimeng261.maidspell.block.entity;

import com.github.yimeng261.maidspell.block.custom.JingxuYoulanBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class JingxuYoulanBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 20;

    public JingxuYoulanBlockEntity(BlockPos pos, BlockState state) {
        super(MaidSpellBlockEntities.JINGXU_YOULAN.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, JingxuYoulanBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        long gameTime = serverLevel.getGameTime();
        int offset = Math.floorMod(pos.asLong(), TICK_INTERVAL);
        if ((gameTime + offset) % TICK_INTERVAL != 0) {
            return;
        }

        JingxuYoulanBlock.applyAura(serverLevel, pos);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityTicker<T> createTicker(BlockEntityType<T> type) {
        return type == MaidSpellBlockEntities.JINGXU_YOULAN.get()
            ? (level, pos, state, blockEntity) -> serverTick(level, pos, state, (JingxuYoulanBlockEntity) blockEntity)
            : null;
    }
}
