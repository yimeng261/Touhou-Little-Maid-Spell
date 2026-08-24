package com.github.yimeng261.maidspell.item.common;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentClientExtensions;
import com.github.yimeng261.maidspell.entity.StarShadowSpearEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowSpearItem() {
        super(new Properties().durability(250));
    }

    /**
     * 照抄 {@code TridentItem.releaseUsing}，只把扔出去的实体换成星影投枪。
     * 音效仍用三叉戟那一套（投掷 / 返回 / 命中 / 引雷）。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (!(entityLiving instanceof Player player)) {
            return;
        }
        if (this.getUseDuration(stack) - timeLeft < 10) {
            return;
        }

        int riptide = EnchantmentHelper.getRiptide(stack);
        if (riptide > 0 && !player.isInWaterOrRain()) {
            return;
        }

        if (!level.isClientSide) {
            stack.hurtAndBreak(1, player, thrower -> thrower.broadcastBreakEvent(entityLiving.getUsedItemHand()));
            if (riptide == 0) {
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
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (riptide > 0) {
            launchRiptide(level, player, riptide);
        }
    }

    /** 激流那段与原版一致：把玩家自己甩出去，并进入旋转攻击状态。 */
    private static void launchRiptide(Level level, Player player, int riptide) {
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float x = -Mth.sin(yRot * ((float) Math.PI / 180F)) * Mth.cos(xRot * ((float) Math.PI / 180F));
        float y = -Mth.sin(xRot * ((float) Math.PI / 180F));
        float z = Mth.cos(yRot * ((float) Math.PI / 180F)) * Mth.cos(xRot * ((float) Math.PI / 180F));
        float length = Mth.sqrt(x * x + y * y + z * z);
        float push = 3.0F * ((1.0F + (float) riptide) / 4.0F);
        x *= push / length;
        y *= push / length;
        z *= push / length;
        player.push(x, y, z);
        player.startAutoSpinAttack(20);
        if (player.onGround()) {
            player.move(MoverType.SELF, new Vec3(0.0D, 1.1999999F, 0.0D));
        }

        SoundEvent sound;
        if (riptide >= 3) {
            sound = SoundEvents.TRIDENT_RIPTIDE_3;
        } else if (riptide == 2) {
            sound = SoundEvents.TRIDENT_RIPTIDE_2;
        } else {
            sound = SoundEvents.TRIDENT_RIPTIDE_1;
        }
        level.playSound(null, player, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
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
