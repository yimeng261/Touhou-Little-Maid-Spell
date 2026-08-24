package com.github.yimeng261.maidspell.item.common;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentRenderProviders;
import com.github.yimeng261.maidspell.entity.StarShadowSpearEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** 继承三叉戟行为，投掷时改用星影投枪实体与模型。 */
public class StarShadowSpearItem extends TridentItem implements GeoItem {

    /** 手持和飞行共用同一份几何体与贴图。 */
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/star_shadow_spear.geo.json");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/winefox_spear_projectile.png");

    /**
     * 枪尖在 {@link #MODEL} 里距原点 32.37 像素，实体判定点在原点，渲染时沿 +Z（枪柄方向）挪回这段距离，
     * 命中瞬间枪尖才落在目标上。模型重新导出时要跟着改。
     */
    public static final float TIP_TO_ORIGIN = 32.37F / 16.0F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowSpearItem() {
        super(new Properties()
                .durability(250)
                .attributes(TridentItem.createAttributes())
                .component(DataComponents.TOOL, TridentItem.createToolProperties()));
    }

    /**
     * 只接管不带激流的投掷：原版写死了 {@code new ThrownTrident(...)}，这里换成 {@link StarShadowSpearEntity}。
     * 带激流时原样交回 {@link TridentItem}，它本来就不投掷。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (EnchantmentHelper.getTridentSpinAttackStrength(stack, entityLiving) > 0.0F) {
            super.releaseUsing(stack, level, entityLiving, timeLeft);
            return;
        }
        if (!(entityLiving instanceof Player player)) {
            return;
        }
        if (this.getUseDuration(stack, entityLiving) - timeLeft < THROW_THRESHOLD_TIME) {
            return;
        }
        // 与原版 isTooDamagedToUse 一致：只剩最后一点耐久时不投掷
        if (stack.getDamageValue() >= stack.getMaxDamage() - 1) {
            return;
        }

        if (!level.isClientSide) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(entityLiving.getUsedItemHand()));
            StarShadowSpearEntity spear = new StarShadowSpearEntity(level, player, stack);
            spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SHOOT_POWER, 1.0F);
            if (player.hasInfiniteMaterials()) {
                spear.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }

            level.addFreshEntity(spear);
            level.playSound(null, spear, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.hasInfiniteMaterials()) {
                player.getInventory().removeItem(stack);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /** 没有常驻动画，共用的 star_equipment.animation.json 由 GeoModel 兜底。 */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // 不传 guiModel：物品栏里也用 3D 模型，走物品 JSON 里 gui 那一槽的变换。
        consumer.accept(StarEquipmentRenderProviders.<StarShadowSpearItem>item(
                MODEL, TEXTURE, StarEquipmentGeoModel.SHARED_ANIMATION, null));
    }
}
