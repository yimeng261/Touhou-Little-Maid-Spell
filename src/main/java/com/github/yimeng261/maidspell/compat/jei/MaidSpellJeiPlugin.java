package com.github.yimeng261.maidspell.compat.jei;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.overlay.HudExclusionAreas;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class MaidSpellJeiPlugin implements IModPlugin {
    @SuppressWarnings("removal")
    private static final ResourceLocation ID = new ResourceLocation(MaidSpellMod.MOD_ID, "hud_exclusions");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        HudExclusionAreas.setProvider(() -> jeiRuntime.getScreenHelper()
                .getGuiExclusionAreas(Minecraft.getInstance().screen)
                .toList());
    }
}
