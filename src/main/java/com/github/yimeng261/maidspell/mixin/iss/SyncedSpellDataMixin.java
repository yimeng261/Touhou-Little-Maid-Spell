package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;
import io.netty.buffer.Unpooled;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.network.ClientboundSyncEntityData;
import io.redspace.ironsspellbooks.setup.Messages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让自定义实体也能同步施法状态
 *
 * @author Gardel &lt;gardel741@outlook.com&gt;
 * @since 2026-01-03 15:06
 */
@Mixin(value = SyncedSpellData.class, remap = false)
public abstract class SyncedSpellDataMixin {
    @Shadow
    private LivingEntity livingEntity;

    @Inject(method = "doSync", at = @At("TAIL"))
    public void afterDoSync(CallbackInfo ci) {
        if (!(livingEntity instanceof IMagicEntity) && livingEntity instanceof EntityMaid maid) {
            MaidIronsSpellData spellData = MaidIronsSpellData.get(maid.getUUID());
            if (spellData != null) {
                FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
                byteBuf.writeInt(maid.getId());
                SyncedSpellData.SYNCED_SPELL_DATA.write(byteBuf, (SyncedSpellData)(Object)this);
                Messages.sendToPlayersTrackingEntity(new ClientboundSyncEntityData(byteBuf), livingEntity);
            }
        }
    }
}
