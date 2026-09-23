package com.github.yimeng261.maidspell.compat.curios;

import com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class DreamCrystalCurioItem extends DreamCatCrystal implements ICurioItem {
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return context.entity() instanceof Player && !context.cosmetic()
            && top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(context.entity()).resolve()
                .map(handler -> handler.findCurios(stack.getItem()).stream().allMatch(result ->
                    result.slotContext().identifier().equals(context.identifier())
                        && result.slotContext().index() == context.index())).orElse(false);
    }
}
