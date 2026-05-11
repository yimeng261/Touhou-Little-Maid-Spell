package com.github.yimeng261.maidspell.mixin.goety;

import com.Polarice3.Goety.utils.CuriosFinder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;
import java.util.function.Predicate;

@Pseudo
@Mixin(value = CuriosFinder.class, remap = false)
public class CuriosFinderMixin {

    @Inject(method = "findCurio(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void onFindCurioByPredicate(LivingEntity livingEntity, Predicate<ItemStack> filter,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (livingEntity instanceof EntityMaid maid) {
            cir.setReturnValue(findMaidEquippedItem(maid, filter));
        }
    }

    @Inject(method = "findCurio(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void onFindCurioByItem(LivingEntity livingEntity, Item item,
                                          CallbackInfoReturnable<ItemStack> cir) {
        if (livingEntity instanceof EntityMaid maid) {
            cir.setReturnValue(findMaidEquippedItem(maid, stack -> stack.is(item)));
        }
    }

    private static ItemStack findMaidEquippedItem(EntityMaid maid, Predicate<ItemStack> filter) {
        ItemStack bauble = findMaidBauble(maid, filter);
        if (!bauble.isEmpty()) {
            return bauble;
        }
        for (ItemStack stack : maid.getArmorSlots()) {
            if (!stack.isEmpty() && filter.test(stack)) {
                return stack;
            }
        }
        return findMaidCurio(maid, filter);
    }

    private static ItemStack findMaidBauble(EntityMaid maid, Predicate<ItemStack> filter) {
        BaubleItemHandler baubles = maid.getMaidBauble();
        if (baubles == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findMaidCurio(EntityMaid maid, Predicate<ItemStack> filter) {
        Optional<SlotResult> slotResult = CuriosApi.getCuriosInventory(maid)
                .map(inventory -> inventory.findFirstCurio(filter))
                .orElse(Optional.empty());
        return slotResult.map(SlotResult::stack).orElse(ItemStack.EMPTY);
    }
}
