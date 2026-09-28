package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.CompanionBlackHoleEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.SpellbreakingEchoEntity;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 伴星黑洞只追踪其他施法者的弹射物；破法回响清空原版追踪名单。
 * 在名单更新后过滤，因为同一调用点已有 {@code BlackHoleMixin} 的 Redirect，
 * 再添加 Redirect 会发生注入冲突。其它黑洞不受影响。
 */
@Mixin(value = BlackHole.class, remap = false)
public abstract class CompanionBlackHoleFilterMixin {

    /** require = 0 避免 ISS 方法变更导致启动失败。 */
    @Inject(method = "updateTrackingEntities()V", at = @At("TAIL"), require = 0, remap = false)
    private void maidspell$filterTrackingEntities(CallbackInfo ci) {
        if (!((Object) this instanceof CompanionBlackHoleEntity)
                && !((Object) this instanceof SpellbreakingEchoEntity)) {
            return;
        }
        BlackHoleTrackingAccessor accessor = (BlackHoleTrackingAccessor) (Object) this;
        List<Entity> tracked = accessor.maidspell$getTrackingEntities();
        if (tracked == null || tracked.isEmpty()) {
            return;
        }
        if ((Object) this instanceof SpellbreakingEchoEntity) {
            tracked.clear();
            return;
        }
        CompanionBlackHoleEntity companion = (CompanionBlackHoleEntity) (Object) this;
        Entity caster = companion.getOwner();
        tracked.removeIf(entity -> !maidspell$shouldBePulled(entity, caster));
    }

    private static boolean maidspell$shouldBePulled(Entity entity, @Nullable Entity caster) {
        if (!(entity instanceof Projectile projectile)) {
            return false;
        }
        return projectile.getOwner() != caster;
    }
}
