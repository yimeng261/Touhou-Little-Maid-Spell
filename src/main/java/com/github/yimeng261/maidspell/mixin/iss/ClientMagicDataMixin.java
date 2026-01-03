package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.client.animation.MagicCastingAnimateState;
import com.github.yimeng261.maidspell.client.spell.CastingAnimateStateAccessor;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
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
    @Inject(method = "handleAbstractCastingMobSyncedData", at = @At(value = "TAIL"))
    private static void afterHandleAbstractCastingMobSyncedData(int entityId, SyncedSpellData syncedSpellData, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof CastingAnimateStateAccessor animateStateAccessor) {
                MagicCastingAnimateState magicCastingAnimateState = animateStateAccessor.maidspell$getCastingAnimateState();
                if (magicCastingAnimateState != null && entity instanceof EntityMaid maid) {
                    magicCastingAnimateState.updateState(maid, syncedSpellData);
                }
            }
        }
    }
}
