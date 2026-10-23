package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.PalmBlackHoleSpell;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.List;

/** 隐藏掌心黑洞的卷轴变体，法术本身仍保持注册，供首领使用。 */
public final class PalmBlackHoleScrollVisibility {
    private PalmBlackHoleScrollVisibility() {
    }

    public static boolean isHiddenScroll(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IScroll) || !ISpellContainer.isSpellContainer(stack)) {
            return false;
        }
        ISpellContainer container = ISpellContainer.get(stack);
        return container != null && container.getActiveSpells().stream()
                .anyMatch(slot -> PalmBlackHoleSpell.SPELL_ID.equals(slot.spellData().getSpell().getSpellResource()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        // 在铁魔法加入已启用法术的卷轴之后再移除，搜索栏里也就看不到。
        List<ItemStack> hidden = new ArrayList<>();
        for (ItemStack stack : event.getParentEntries()) {
            if (isHiddenScroll(stack)) hidden.add(stack);
        }
        for (ItemStack stack : event.getSearchEntries()) {
            if (isHiddenScroll(stack) && !hidden.contains(stack)) hidden.add(stack);
        }
        hidden.forEach(stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
}
