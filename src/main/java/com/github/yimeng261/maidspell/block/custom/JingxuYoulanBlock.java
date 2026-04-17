package com.github.yimeng261.maidspell.block.custom;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.block.entity.JingxuYoulanBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

public class JingxuYoulanBlock extends BushBlock implements EntityBlock {
    private static final int LIGHT_LEVEL = 10;
    private static final int AURA_RANGE = 3;

    public JingxuYoulanBlock() {
        super(createProperties());
    }

    public static BlockBehaviour.Properties createProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .noCollission()
            .instabreak()
            .sound(SoundType.GRASS)
            .lightLevel(state -> LIGHT_LEVEL)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.DESTROY);
    }

    public static BlockBehaviour.Properties createPottedProperties() {
        return BlockBehaviour.Properties.copy(Blocks.POTTED_BLUE_ORCHID)
            .lightLevel(state -> LIGHT_LEVEL);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JingxuYoulanBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : JingxuYoulanBlockEntity.createTicker(type);
    }

    public static void applyAura(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(AURA_RANGE);
        for (net.minecraft.world.entity.LivingEntity living : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area, net.minecraft.world.entity.LivingEntity::isAlive)) {
            if (living instanceof EntityMaid maid) {
                removeHarmfulEffects(maid);
                continue;
            }
            living.removeAllEffects();
        }
    }

    private static void removeHarmfulEffects(EntityMaid maid) {
        List<MobEffect> harmfulEffects = maid.getActiveEffects().stream()
            .map(MobEffectInstance::getEffect)
            .filter(effect -> effect.getCategory() == MobEffectCategory.HARMFUL)
            .toList();
        harmfulEffects.forEach(maid::removeEffect);
    }
}
