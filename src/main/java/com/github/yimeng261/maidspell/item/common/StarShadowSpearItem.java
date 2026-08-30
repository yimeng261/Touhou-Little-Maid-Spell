package com.github.yimeng261.maidspell.item.common;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentClientExtensions;
import com.github.yimeng261.maidspell.entity.StarShadowSpearEntity;
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
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * 星影投枪：万法酒狐的投枪，数值与行为都跟着原版三叉戟走。
 *
 * <p>直接继承 {@link TridentItem}，所以攻击力 +8、攻击速度 -2.9、耐久掉落、
 * 蓄力投掷、激流全都是现成的；附魔面也一并拿到 ——
 * {@code EnchantmentCategory.TRIDENT.canEnchant} 判的就是 {@code instanceof TridentItem}，
 * 忠诚 / 激流 / 引雷 / 穿刺因此都能上。
 *
 * <p>只重写了 {@code releaseUsing}：原版那份写死了 {@code new ThrownTrident(...)}，
 * 扔出去会渲染成三叉戟，这里换成 {@link StarShadowSpearEntity}，飞行中用的是同一份枪身模型。
 *
 * <p>没有 lang 条目、也没进创造模式物品栏，都是有意的。
 */
public class StarShadowSpearItem extends TridentItem implements GeoItem {

    /** 手持和飞行共用同一份几何体与贴图。 */
    public static final ResourceLocation MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "geo/star_shadow_spear.geo.json");
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/winefox_spear_projectile.png");

    /**
     * 枪尖在 {@link #MODEL} 里距原点 32.37 像素。原版三叉戟只有 4 像素，差别看不出来；
     * 这把枪有两格多，不往回挪的话命中瞬间枪尖会捅到目标后面两格去 ——
     * 判定点在实体坐标上，而枪尖在它前方两格。往 +Z（枪柄方向）挪回来对齐。
     *
     * <p>放在模型旁边而不是各渲染器里：这是对这一份 {@code .geo.json} 的测量，
     * 在 Blockbench 里重新导出就得跟着改，两处各存一份必然有一处漏掉。
     */
    public static final float TIP_TO_ORIGIN = 32.37F / 16.0F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowSpearItem() {
        super(new Properties().durability(250));
    }

    /**
     * 只接管 riptide == 0 那一支：原版那份写死了 {@code new ThrownTrident(...)}，
     * 这里换成 {@link StarShadowSpearEntity}。音效仍用三叉戟那一套（投掷 / 返回 / 命中 / 引雷）。
     *
     * <p>带激流的那一支原样交回 {@link TridentItem}：它在 {@code j == 0} 上就把投掷整段跳过了，
     * 剩下的时长判定、{@code isInWaterOrRain}、{@code hurtAndBreak}、{@code awardStat}
     * 和那段甩人的向量数学一字不差，抄一遍只会跟着原版偷偷漂。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (EnchantmentHelper.getRiptide(stack) > 0) {
            super.releaseUsing(stack, level, entityLiving, timeLeft);
            return;
        }
        if (!(entityLiving instanceof Player player)) {
            return;
        }
        if (this.getUseDuration(stack) - timeLeft < 10) {
            return;
        }

        if (!level.isClientSide) {
            stack.hurtAndBreak(1, player, thrower -> thrower.broadcastBreakEvent(entityLiving.getUsedItemHand()));
            StarShadowSpearEntity spear = new StarShadowSpearEntity(level, player, stack);
            spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.5F, 1.0F);
            if (player.getAbilities().instabuild) {
                spear.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }

            level.addFreshEntity(spear);
            level.playSound(null, spear, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
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
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        // 不传 guiModel：物品栏里也用 3D 模型，走物品 JSON 里 gui 那一槽的变换。
        consumer.accept(StarEquipmentClientExtensions.<StarShadowSpearItem>item(MODEL, TEXTURE));
    }
}
