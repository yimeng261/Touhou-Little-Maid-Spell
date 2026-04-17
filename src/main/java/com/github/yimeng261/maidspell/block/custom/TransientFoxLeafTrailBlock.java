package com.github.yimeng261.maidspell.block.custom;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 女仆水上行走时生成的临时荷叶，会在短时间后自动消失，避免污染地形。
 */
public class TransientFoxLeafTrailBlock extends WaterlilyBlock {
    private static final long REFRESH_INTERVAL_TICKS = 5L;
    private final int minLifetimeTicks;
    private final int maxLifetimeTicks;

    public TransientFoxLeafTrailBlock(int minLifetimeTicks, int maxLifetimeTicks) {
        super(BlockBehaviour.Properties.copy(Blocks.LILY_PAD)
            .strength(0.0F)
            .noCollission()
            .noOcclusion()
            .noLootTable());
        this.minLifetimeTicks = minLifetimeTicks;
        this.maxLifetimeTicks = maxLifetimeTicks;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, getLifetime(level.random));
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (maidStandingOnLeaf(level, pos)) {
            level.scheduleTick(pos, this, getLifetime(random));
            return;
        }
        if (state.is(this)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)
            || state.is(Blocks.ICE)
            || state.getBlock() instanceof LiquidBlock;
    }

    private int getLifetime(RandomSource random) {
        if (maxLifetimeTicks <= minLifetimeTicks) {
            return minLifetimeTicks;
        }
        return minLifetimeTicks + random.nextInt(maxLifetimeTicks - minLifetimeTicks + 1);
    }

    public void refreshLifetime(Level level, BlockPos pos) {
        if (!level.isClientSide() && level.getGameTime() % REFRESH_INTERVAL_TICKS == 0L) {
            level.scheduleTick(pos, this, getLifetime(level.random));
        }
    }

    private static boolean maidStandingOnLeaf(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(0.35D, 1.2D, 0.35D).move(0.0D, 1.0D, 0.0D);
        return !level.getEntitiesOfClass(EntityMaid.class, area, maid -> maid.isAlive() && !maid.isPassenger()).isEmpty();
    }
}
