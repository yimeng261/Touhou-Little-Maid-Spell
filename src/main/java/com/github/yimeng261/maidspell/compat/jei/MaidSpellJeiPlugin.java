package com.github.yimeng261.maidspell.compat.jei;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.overlay.HudExclusionAreas;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.event.PalmBlackHoleScrollVisibility;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class MaidSpellJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "hud_exclusions");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        HudExclusionAreas.setProvider(() -> jeiRuntime.getScreenHelper()
                .getGuiExclusionAreas(Minecraft.getInstance().screen)
                .toList());
        if (IronsSpellbooksCompat.isLoaded()) {
            var ingredients = jeiRuntime.getIngredientManager();
            var hidden = ingredients.getAllIngredients(VanillaTypes.ITEM_STACK).stream()
                    .filter(PalmBlackHoleScrollVisibility::isHiddenScroll)
                    .toList();
            if (!hidden.isEmpty()) ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }
}
