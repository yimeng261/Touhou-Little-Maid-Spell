package com.github.yimeng261.maidspell.mixin;


import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class,remap = false)
public class EntityMixin {

    /**
     * 拦截女仆的save方法，防止数据被容器模组复制
     */
    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Z",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onSave(CompoundTag pCompound, CallbackInfoReturnable<Boolean> cir) {
        try {
            if ((Object) this instanceof EntityMaid maid) {
                // 检查女仆是否装备了锚定核心饰品
                if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                    Global.LOGGER.debug("Maid {} does not have anchor_core, allowing save", maid.getUUID());
                    return;
                }

                // 记录调用栈用于调试
                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                for(StackTraceElement stackTraceElement : stackTrace) {
                    String className = stackTraceElement.getClassName();
                    //Global.LOGGER.debug("[MaidSpell] Save className: {}", className);
                    if(!maidSpell$classValid(className)) {
                        cir.setReturnValue(false);
                        Global.LOGGER.debug("[MaidSpell] Illegal Save called for {} (anchor_core protection), className: {}", maid, className);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid save source", e);
        }
    }

    /**
     * 拦截女仆的saveWithoutId方法，防止被超维权杖等物品保存
     */
    @Inject(method = "saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("HEAD"),
            cancellable = true, remap = true)
    public void onSaveWithoutId(CompoundTag pCompound, CallbackInfoReturnable<CompoundTag> cir) {
        try {
            if ((Object) this instanceof EntityMaid maid) {
                // 检查女仆是否装备了锚定核心饰品
                if (!BaubleStateManager.hasBauble(maid, MaidSpellItems.ANCHOR_CORE)) {
                    Global.LOGGER.debug("Maid {} does not have anchor_core, allowing saveWithoutId", maid.getUUID());
                    return;
                }

                // 记录调用栈用于调试
                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                for(StackTraceElement stackTraceElement : stackTrace) {
                    String className = stackTraceElement.getClassName();
                    //Global.LOGGER.debug("[MaidSpell] SaveWithoutId className: {}", className);

                    if(!maidSpell$classValid(className)) {
                        cir.setReturnValue(new CompoundTag()); // 返回空标签
                        Global.LOGGER.warn("[MaidSpell] Illegal SaveWithoutId called for {} by {} (anchor_core protection)",
                                maid.getUUID(), className);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Global.LOGGER.error("Failed to check maid saveWithoutId source", e);
        }
    }

    /**
     * 注入 fireImmune()，当女仆装备不洁圣冠时返回 true
     * 与下界生物相同机制，从源头阻止着火
     */
    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true, remap = true)
    private void onFireImmune(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EntityMaid maid) {
            var unholyHat = MaidSpellItems.getUnholyHat();
            if (unholyHat != null && BaubleStateManager.hasBauble(maid, unholyHat)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private static boolean maidSpell$classValid(String className) {
        return className.startsWith("net.minecraft") ||
                className.startsWith("net.neoforged") ||
                className.startsWith("java") ||
                className.startsWith("it.unimi.dsi") ||
                className.startsWith("com.github.tartaricacid") ||
                className.startsWith("com.github.yimeng261") ||
                className.startsWith("com.google") ||
                className.startsWith("com.mojang") ||
                className.startsWith("io.redspace.ironsspellbooks") ||
                className.startsWith("top.theillusivec4.curios") ||
                className.contains("backup") ||
                className.contains("maid") ||
                className.contains("c2me");
    }

}
