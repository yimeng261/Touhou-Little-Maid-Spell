package com.github.yimeng261.maidspell.compat.travelerstitles.client;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** 不引入硬依赖，把本模组结构的进入事件转给 Traveler's Titles 显示标题。 */
public final class TravelerTitlesStructureClient {
    private static final String TRAVELER_TITLES_MOD_ID = "travelerstitles";
    private static final String TITLE_KEY_PREFIX = "travelerstitles.structure.";
    private static final Map<ResourceLocation, String> TITLE_KEYS = Map.of(
            id("relic_sanctum"), TITLE_KEY_PREFIX + "touhou_little_maid_spell.relic_sanctum",
            id("fallen_sanctum"), TITLE_KEY_PREFIX + "touhou_little_maid_spell.fallen_sanctum",
            id("stellar_endshore"), TITLE_KEY_PREFIX + "touhou_little_maid_spell.stellar_endshore",
            id("starfall_garden"), TITLE_KEY_PREFIX + "touhou_little_maid_spell.starfall_garden",
            id("hidden_retreat"), TITLE_KEY_PREFIX + "touhou_little_maid_spell.hidden_retreat"
    );

    @Nullable
    private static ResourceLocation activeStructure;
    @Nullable
    private static RendererAccess rendererAccess;
    private static boolean rendererUnavailableLogged;

    private TravelerTitlesStructureClient() {
    }

    public static void setStructure(@Nullable ResourceLocation structureId) {
        if (!ModList.get().isLoaded(TRAVELER_TITLES_MOD_ID)) {
            return;
        }

        if (structureId == null) {
            activeStructure = null;
            return;
        }
        if (structureId.equals(activeStructure)) {
            return;
        }
        activeStructure = structureId;

        String titleKey = TITLE_KEYS.get(structureId);
        if (titleKey == null || !Language.getInstance().has(titleKey)) {
            return;
        }

        RendererAccess access = getRendererAccess();
        if (access == null || !access.isEnabled()) {
            return;
        }

        String colorKey = titleKey + ".color";
        String color = Language.getInstance().has(colorKey)
                ? Language.getInstance().getOrDefault(colorKey)
                : "ffffff";
        try {
            access.setColor.invoke(access.renderer, color);
            access.displayTitle.invoke(access.renderer,
                    new Object[]{Component.translatable(titleKey), null});
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!rendererUnavailableLogged) {
                rendererUnavailableLogged = true;
                MaidSpellMod.LOGGER.warn("Unable to display Traveler's Titles structure title", exception);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        activeStructure = null;
        rendererAccess = null;
        rendererUnavailableLogged = false;
    }

    @Nullable
    private static RendererAccess getRendererAccess() {
        if (rendererAccess != null) {
            return rendererAccess;
        }
        try {
            Class<?> commonClass = Class.forName(
                    "com.yungnickyoung.minecraft.travelerstitles.TravelersTitlesCommon"
            );
            Field managerField = commonClass.getField("titleManager");
            Object manager = managerField.get(null);
            Field rendererField = manager.getClass().getField("biomeTitleRenderer");
            Object renderer = rendererField.get(manager);
            Field enabled = renderer.getClass().getField("enabled");
            Method setColor = renderer.getClass().getMethod("setColor", String.class);
            Method displayTitle = renderer.getClass().getMethod(
                    "displayTitle", Component.class, Component.class
            );
            rendererAccess = new RendererAccess(renderer, enabled, setColor, displayTitle);
            return rendererAccess;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!rendererUnavailableLogged) {
                rendererUnavailableLogged = true;
                MaidSpellMod.LOGGER.warn("Traveler's Titles structure title integration is unavailable", exception);
            }
            return null;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, path);
    }

    private record RendererAccess(Object renderer, Field enabled, Method setColor, Method displayTitle) {
        private boolean isEnabled() {
            try {
                return enabled.getBoolean(renderer);
            } catch (IllegalAccessException exception) {
                return false;
            }
        }
    }
}
