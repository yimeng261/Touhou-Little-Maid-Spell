package com.github.yimeng261.maidspell.mixin.goety;

import com.Polarice3.Goety.utils.CuriosFinder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.compat.curios.CuriosCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Goety 的 CuriosFinder 只查玩家的 Curios 槽位，女仆的帽子、长袍等装在女仆饰品栏里。
 * 目标是女仆时依次查女仆饰品栏、护甲栏、Curios 槽位。
 * hasCurio 及 hasXxxRobe/hasXxxCrown 等判断都经由这两个 findCurio，拦截这两处即可覆盖。
 */
@Pseudo
@Mixin(value = CuriosFinder.class)
public class CuriosFinderMixin {

    @Inject(method = "findCurio(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void onFindCurioByPredicate(LivingEntity livingEntity, Predicate<ItemStack> filter,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (livingEntity instanceof EntityMaid maid) {
            cir.setReturnValue(maidspell$findMaidEquippedItem(maid, filter));
        }
    }

    @Inject(method = "findCurio(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void onFindCurioByItem(LivingEntity livingEntity, Item item,
                                          CallbackInfoReturnable<ItemStack> cir) {
        if (livingEntity instanceof EntityMaid maid) {
            cir.setReturnValue(maidspell$findMaidEquippedItem(maid, stack -> stack.is(item)));
        }
    }

    private static ItemStack maidspell$findMaidEquippedItem(EntityMaid maid, Predicate<ItemStack> filter) {
        BaubleItemHandler baubles = maid.getMaidBauble();
        if (baubles != null) {
            for (int slot = 0; slot < baubles.getSlots(); slot++) {
                ItemStack stack = baubles.getStackInSlot(slot);
                if (!stack.isEmpty() && filter.test(stack)) {
                    return stack;
                }
            }
        }
        for (ItemStack stack : maid.getArmorSlots()) {
            if (!stack.isEmpty() && filter.test(stack)) {
                return stack;
            }
        }
        return CuriosCompat.findFirstCurio(maid, filter);
    }
}
