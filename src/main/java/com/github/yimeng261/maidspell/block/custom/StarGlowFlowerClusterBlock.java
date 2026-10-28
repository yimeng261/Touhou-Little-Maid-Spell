package com.github.yimeng261.maidspell.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.joml.Vector3f;

public class StarGlowFlowerClusterBlock extends PinkPetalsBlock {
    private static final int LIGHT_LEVEL = 10;
    private static final Vector3f BLUE_GLOW = new Vector3f(0.50f, 0.92f, 1.0f);
    private static final Vector3f PURPLE_GLOW = new Vector3f(0.86f, 0.58f, 1.0f);

    public StarGlowFlowerClusterBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.PINK_PETALS)
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .lightLevel(state -> LIGHT_LEVEL));
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.END_STONE) || super.mayPlaceOn(state, level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int amount = state.getValue(AMOUNT);
        if (random.nextFloat() > 0.12f + amount * 0.05f) {
            return;
        }

        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double y = pos.getY() + 0.08 + random.nextDouble() * 0.2;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        Vector3f color = random.nextBoolean() ? BLUE_GLOW : PURPLE_GLOW;
        level.addParticle(new DustParticleOptions(color, 0.75f),
            x, y, z,
            0.0, 0.004 + random.nextDouble() * 0.006, 0.0);
    }
}
