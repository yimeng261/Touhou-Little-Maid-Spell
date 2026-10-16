package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.PalmBlackHoleSpell;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Hides ritual scroll variants while keeping the spell registered for the boss. */
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
        // Run after ISS adds its enabled-spell scrolls; this also removes search visibility.
        List<ItemStack> hidden = new ArrayList<>();
        for (var entry : event.getEntries()) {
            if (isHiddenScroll(entry.getKey())) hidden.add(entry.getKey());
        }
        hidden.forEach(event.getEntries()::remove);
    }
}
