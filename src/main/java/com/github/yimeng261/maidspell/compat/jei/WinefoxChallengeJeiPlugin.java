package com.github.yimeng261.maidspell.compat.jei;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.gui.WinefoxChallengeConfigScreen;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.client.WinefoxSpellSelectionClient;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** Accepts JEI scroll ingredients directly into the advanced spell slots. */
@JeiPlugin
public final class WinefoxChallengeJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = new ResourceLocation(MaidSpellMod.MOD_ID, "winefox_challenge");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(WinefoxChallengeConfigScreen.class,
                new IGuiContainerHandler<>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(WinefoxChallengeConfigScreen screen) {
                        return List.of(screen.jeiExclusionArea());
                    }
                });
        registration.addGhostIngredientHandler(WinefoxChallengeConfigScreen.class,
                new IGhostIngredientHandler<>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(WinefoxChallengeConfigScreen screen,
                                                               ITypedIngredient<I> ingredient, boolean doStart) {
                        if (!screen.isAdvanced() || !ModList.get().isLoaded("irons_spellbooks")) {
                            return List.of();
                        }
                        ItemStack scroll = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                        if (WinefoxSpellSelectionClient.fromScroll(scroll) == null) {
                            return List.of();
                        }
                        List<Target<I>> targets = new ArrayList<>();
                        List<Rect2i> areas = screen.spellAreas();
                        for (int index = 0; index < areas.size(); index++) {
                            int slot = index;
                            targets.add(new Target<>() {
                                @Override
                                public Rect2i getArea() {
                                    return areas.get(slot);
                                }

                                @Override
                                public void accept(I value) {
                                    if (value instanceof ItemStack stack) {
                                        screen.acceptScroll(slot, stack);
                                    }
                                }
                            });
                        }
                        return targets;
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }
}
