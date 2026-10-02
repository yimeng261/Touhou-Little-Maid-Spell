package com.github.yimeng261.maidspell.compat.irons_spellbooks.client;

import com.github.yimeng261.maidspell.winefox.WinefoxSpellChoice;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.WinefoxBossSpells;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Client-only Iron's Spellbooks lookups for the challenge editor. */
public final class WinefoxSpellSelectionClient {
    private WinefoxSpellSelectionClient() {
    }

    public static WinefoxSpellChoice fromScroll(ItemStack stack) {
        if (stack == null || !stack.is(ItemRegistry.SCROLL.get()) || !ISpellContainer.isSpellContainer(stack)) {
            return null;
        }
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) {
            return null;
        }
        for (SpellSlot slot : container.getActiveSpells()) {
            SpellData data = slot.spellData();
            if (data != null && data.getSpell() != SpellRegistry.none()) {
                return WinefoxSpellChoice.valid(data.getSpell().getSpellId(), data.getLevel());
            }
        }
        return null;
    }

    public static ResourceLocation icon(WinefoxSpellChoice choice) {
        AbstractSpell spell = SpellRegistry.getSpell(ResourceLocation.parse(choice.id()));
        return spell == SpellRegistry.none() ? null : spell.getSpellIconResource();
    }

    public static List<WinefoxSpellChoice> defaultChoices(boolean secondPhase) {
        return WinefoxBossSpells.defaultChoices(secondPhase);
    }
}
