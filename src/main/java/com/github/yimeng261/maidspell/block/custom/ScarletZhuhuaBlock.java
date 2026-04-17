package com.github.yimeng261.maidspell.block.custom;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.block.entity.ScarletZhuhuaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
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

public class ScarletZhuhuaBlock extends BushBlock implements EntityBlock {
    private static final int LIGHT_LEVEL = 10;
    private static final int AURA_RANGE = 3;
    private static final int EFFECT_DURATION_TICKS = 100;

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

    public static void applyAura(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(AURA_RANGE);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, ScarletZhuhuaBlock::shouldAffect)) {
            living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION_TICKS, 1, true, false, true));

            if (!(living instanceof EntityMaid)) {
                living.addEffect(new MobEffectInstance(MobEffects.WITHER, EFFECT_DURATION_TICKS, 0, true, false, true));
            }
        }
    }

    private static boolean shouldAffect(LivingEntity living) {
        return living.isAlive() && !isIronsSpellbooksNpc(living);
    }


    private static boolean isIronsSpellbooksNpc(LivingEntity living) {
        return living instanceof Merchant
            && !((living instanceof Enemy))
            && "irons_spellbooks".equals(living.getType().builtInRegistryHolder().key().location().getNamespace());
    }
}
