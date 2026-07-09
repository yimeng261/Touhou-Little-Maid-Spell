package com.github.yimeng261.maidspell.mixin.mna;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.mna.ManaAndArtificeChannelHelper;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mna.entities.sorcery.base.ChanneledSpellEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChanneledSpellEntity.class, remap = false)
public abstract class ChanneledSpellEntityMixin extends Entity {
    protected ChanneledSpellEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    public abstract LivingEntity getCaster();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = true)
    private void maidspell$discardStoppedMaidChannel(CallbackInfo ci) {
        LivingEntity caster = getCaster();
        if (!level().isClientSide && caster instanceof EntityMaid maid
                && !ManaAndArtificeChannelHelper.isManagedMaidChannel(maid)) {
            remove(RemovalReason.DISCARDED);
            ci.cancel();
        }
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getUseItemRemainingTicks()I",
                    remap = true
            ),
            remap = true
    )
    private int maidspell$keepManagedMaidChannelAlive(int original) {
        LivingEntity caster = getCaster();
        if (caster instanceof EntityMaid maid
                && (level().isClientSide || ManaAndArtificeChannelHelper.isManagedMaidChannel(maid))) {
            return Math.max(original, 1);
        }
        return original;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getUseItem()Lnet/minecraft/world/item/ItemStack;",
                    remap = true
            ),
            remap = true
    )
    private ItemStack maidspell$useManagedMaidChannelItem(LivingEntity caster, Operation<ItemStack> original) {
        if (caster instanceof EntityMaid maid) {
            if (ManaAndArtificeChannelHelper.isManagedMaidChannel(maid)) {
                ItemStack stack = ManaAndArtificeChannelHelper.getManagedChannelItem(maid);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            if (level().isClientSide) {
                ItemStack stack = original.call(caster);
                return stack.isEmpty() ? Items.STICK.getDefaultInstance() : stack;
            }
        }
        return original.call(caster);
    }
}
