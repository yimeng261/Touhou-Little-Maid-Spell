package com.github.yimeng261.maidspell.entity;

import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 扔出去的星影投枪。
 *
 * <p>整套飞行逻辑（忠诚回收、引雷、穿刺加伤、拾取、落地）都从 {@link ThrownTrident} 继承，
 * 只是换了一个自己的 {@code EntityType}，这样客户端才会用星影投枪的模型渲染，
 * 而不是原版三叉戟那套。
 */
public class StarShadowSpearEntity extends ThrownTrident implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowSpearEntity(EntityType<? extends ThrownTrident> entityType, Level level) {
        super(entityType, level);
        // 父类默认塞的是原版三叉戟，换成自己人，免得 /summon 出来的枪被捡起来变成三叉戟。
        this.tridentItem = new ItemStack(MaidSpellItems.STAR_SHADOW_SPEAR.get());
    }

    /**
     * 原版 {@code ThrownTrident(Level, LivingEntity, ItemStack)} 把 {@code EntityType.TRIDENT}
     * 写死在里面了，用不上，只能照着 {@code AbstractArrow(EntityType, LivingEntity, Level)}
     * 的构造链把定位、主人、拾取方式补齐。
     */
    public StarShadowSpearEntity(Level level, LivingEntity shooter, ItemStack stack) {
        this(MaidSpellEntities.STAR_SHADOW_SPEAR.get(), level);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        this.setOwner(shooter);
        if (shooter instanceof Player) {
            this.pickup = Pickup.ALLOWED;
        }
        this.tridentItem = stack.copy();
        this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(stack));
        this.entityData.set(ID_FOIL, stack.hasFoil());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
