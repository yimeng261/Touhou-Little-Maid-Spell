package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.renderer.entity;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** 分开绘制朝外的圆盘表面和做了背面剔除的核心，以保持底面光照正确。 */
final class SupernovaBlackHoleRenderType extends RenderType {
    static final RenderType DISK = createSurface("maidspell_supernova_black_hole_disk");
    static final RenderType CORE = createSurface("maidspell_supernova_black_hole_core");
    static final RenderType GLOW = create("maidspell_supernova_black_hole_glow",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/companion_black_hole/supernova.png"), false, false))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setCullState(CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private static RenderType createSurface(String name) {
        return create(name,
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/companion_black_hole/supernova.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true));
    }

    private SupernovaBlackHoleRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                                         int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                         Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
