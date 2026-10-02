package com.github.yimeng261.maidspell.mixin.iss;

import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 设置同步状态
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03 14:20
 */
@Mixin(value = ClientMagicData.class, remap = false)
public class ClientMagicDataMixin {
    /**
     * 必须在 HEAD 读取同步数据；ISS 会在处理瞬发法术时原地清空同一对象。
     * TAIL 时动画状态只会看到空数据。
     */
    @Inject(method = "handleAbstractCastingMobSyncedData", at = @At(value = "HEAD"))
    private static void afterHandleAbstractCastingMobSyncedData(int entityId, SyncedSpellData syncedSpellData, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Entity entity = level.getEntity(entityId);
            // 万法酒狐不是女仆，自己实现了 CastingAnimateStateAccessor
            if (entity instanceof CastingAnimateStateAccessor animateStateAccessor
                    && entity instanceof LivingEntity caster) {
                MagicCastingAnimateState magicCastingAnimateState = animateStateAccessor.maidspell$getCastingAnimateState();
                if (magicCastingAnimateState != null) {
                    magicCastingAnimateState.updateState(caster, syncedSpellData);
                }
            }
        }
    }
}
