package com.github.yimeng261.maidspell.block.custom;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.block.MaidSpellBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

public class ScarletZhuhuaBlock extends BushBlock {
    private static final int LIGHT_LEVEL = 10;
    private static final int AURA_RANGE = 3;
    private static final double AURA_RANGE_SQR = AURA_RANGE * AURA_RANGE;
    private static final int EFFECT_DURATION_TICKS = 60;

    public ScarletZhuhuaBlock() {
        super(createProperties());
    }

    public static BlockBehaviour.Properties createProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .noCollission()
            .instabreak()
            .sound(SoundType.GRASS)
            .lightLevel(state -> LIGHT_LEVEL)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.DESTROY);
    }

    public static BlockBehaviour.Properties createPottedProperties() {
        return BlockBehaviour.Properties.copy(Blocks.POTTED_POPPY)
            .lightLevel(state -> LIGHT_LEVEL);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.CRIMSON_NYLIUM) || super.mayPlaceOn(state, level, pos);
    }

    public static void applyAuraIfNeeded(LivingEntity living) {
        if (!living.isAlive() || isIronsSpellbooksNpc(living) || !hasScarletZhuhuaNearby(living)) {
            return;
        }

        living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION_TICKS, 1, true, false, true));

        if (!(living instanceof EntityMaid)) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, EFFECT_DURATION_TICKS, 0, true, false, true));
        }
    }

    private static boolean hasScarletZhuhuaNearby(LivingEntity living) {
        Level level = living.level();
        BlockPos origin = living.blockPosition();
        Vec3 position = living.position();

        for (BlockPos checkPos : BlockPos.betweenClosed(
            origin.offset(-AURA_RANGE, -AURA_RANGE, -AURA_RANGE),
            origin.offset(AURA_RANGE, AURA_RANGE, AURA_RANGE))) {
            BlockState state = level.getBlockState(checkPos);
            if (!state.is(MaidSpellBlocks.SCARLET_ZHUHUA.get()) && !state.is(MaidSpellBlocks.POTTED_SCARLET_ZHUHUA.get())) {
                continue;
            }

            if (checkPos.getCenter().distanceToSqr(position) <= AURA_RANGE_SQR) {
                return true;
            }
        }

        return false;
    }

    private static boolean isIronsSpellbooksNpc(LivingEntity living) {
        return living instanceof Merchant
            && !((living instanceof Enemy))
            && "irons_spellbooks".equals(living.getType().builtInRegistryHolder().key().location().getNamespace());
    }
}
