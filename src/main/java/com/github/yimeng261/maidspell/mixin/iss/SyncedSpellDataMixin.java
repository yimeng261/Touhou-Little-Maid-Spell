package com.github.yimeng261.maidspell.mixin.iss;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.data.MaidIronsSpellData;
import io.netty.buffer.Unpooled;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.network.casting.SyncEntityDataPacket;
import io.redspace.ironsspellbooks.setup.Messages;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.Lazy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

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

    @Unique
    private final Lazy<Field> maidSpell$SYNCED_SPELL_DATA_FIELD = Lazy.of(SyncedSpellDataMixin::maidSpell$getSyncedSpellDataField);

    @Unique
    private final Lazy<Boolean> maidSpell$isOldIronsSpellBooks = Lazy.of(SyncedSpellDataMixin::maidSpell$isOldIronsSpellBooks);

    private final Lazy<Constructor<?>> maidSpell$ClientboundSyncEntityDataConstructor = Lazy.of(SyncedSpellDataMixin::maidSpell$ClientboundSyncEntityDataConstructor);

    @Inject(method = "doSync", at = @At("TAIL"))
    public void afterDoSync(CallbackInfo ci) {
        if (!(livingEntity instanceof IMagicEntity) && livingEntity instanceof EntityMaid maid) {
            MaidIronsSpellData spellData = MaidIronsSpellData.get(maid.getUUID());
            if (spellData != null) {
                FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
                byteBuf.writeInt(maid.getId());
                if (maidSpell$isOldIronsSpellBooks.get()) {
                    maidSpell$sendToPlayersTrackingEntityOld(byteBuf, livingEntity);
                } else {
                    SyncedSpellData.write(byteBuf, (SyncedSpellData)(Object)this);
                    PacketDistributor.sendToPlayersTrackingEntity(livingEntity, new SyncEntityDataPacket(byteBuf));
                }
            }
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "removal"})
    private void maidSpell$sendToPlayersTrackingEntityOld(FriendlyByteBuf byteBuf, LivingEntity livingEntity) {
        Field field = maidSpell$SYNCED_SPELL_DATA_FIELD.get();
        try {
            EntityDataSerializer<SyncedSpellData> writter = (EntityDataSerializer<SyncedSpellData>) field.get(null);
            writter.write(byteBuf, (SyncedSpellData) (Object) this);
            Messages.sendToPlayersTrackingEntity(maidSpell$ClientboundSyncEntityDataConstructor.get().newInstance(byteBuf), livingEntity);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private static boolean maidSpell$isOldIronsSpellBooks() {
        try {
            Class.forName("io.redspace.ironsspellbooks.network.ClientboundSyncEntityData", false, null);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @Unique
    private static Constructor<?> maidSpell$ClientboundSyncEntityDataConstructor() {
        try {
            Class<?> clazz = Class.forName("io.redspace.ironsspellbooks.network.ClientboundSyncEntityData", false, null);
            return clazz.getConstructor(FriendlyByteBuf.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private static Field maidSpell$getSyncedSpellDataField() {
        try {
            Field syncedSpellData = SyncedSpellData.class.getDeclaredField("SYNCED_SPELL_DATA");
            syncedSpellData.setAccessible(true);
            return syncedSpellData;
        } catch (Exception ignored) {
            return null;
        }
    }
}
