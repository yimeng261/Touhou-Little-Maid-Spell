package com.github.yimeng261.maidspell.compat.geckolib.entity;

import com.github.yimeng261.maidspell.compat.geckolib.registry.GeckoLibCompatItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** GeckoLib-only spear attack; only called after checking the mod is loaded. */
public final class GeckoLibCompatSpear {

    private GeckoLibCompatSpear() {
    }

    public static void spawn(LivingEntity boss, LivingEntity target) {
        StarShadowSpearEntity spear = new StarShadowSpearEntity(boss.level(), boss,
                new ItemStack(GeckoLibCompatItems.STAR_SHADOW_SPEAR.get()));
        spear.setBossProjectile();
        Vec3 direction = target.getBoundingBox().getCenter().subtract(spear.position());
        spear.shoot(direction.x, direction.y, direction.z, 3.5F, 0.0F);
        boss.level().addFreshEntity(spear);
    }
}
