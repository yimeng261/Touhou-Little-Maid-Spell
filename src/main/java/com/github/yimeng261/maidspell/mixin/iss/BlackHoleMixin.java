package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 从黑洞追踪名单中排除观察者、创造玩家和友方弹射物。
 * 过滤 {@code updateTrackingEntities()} 的查询结果，兼容不同 ISS 版本。
 * {@code Level.getEntities} 是原版方法，Redirect 必须显式启用 remap。
 */
@Mixin(value = BlackHole.class, remap = false)
public abstract class BlackHoleMixin {
    /** getEntities 返回新列表，可直接过滤。 */
    @Redirect(
            method = "updateTrackingEntities()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
                    remap = true
            ),
            remap = true
    )
    private List<Entity> maidspell$skipInvulnerableToPull(Level level, @Nullable Entity excluded,
                                                          AABB area) {
        List<Entity> targets = level.getEntities(excluded, area);
        BlackHole blackHole = (BlackHole) (Object) this;
        Entity caster = blackHole.getOwner();
        targets.removeIf(entity -> this.maidspell$isImmuneToBlackHole(entity, caster));
        return targets;
    }

    /** 友方判定覆盖队伍与主人链，仅对弹射物生效。 */
    private boolean maidspell$isImmuneToBlackHole(Entity entity, @Nullable Entity caster) {
        if (entity.isSpectator()) {
            return true;
        }
        if (entity instanceof Player player && player.isCreative()) {
            return true;
        }
        if (!(entity instanceof Projectile) || caster == null) {
            return false;
        }
        return MaidSpellAllyResolver.areFriendly(caster, entity)
                || MaidSpellAllyResolver.isOwnedBy(entity, caster.getClass());
    }
}
