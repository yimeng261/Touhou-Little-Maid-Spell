package com.github.yimeng261.maidspell.item.bauble.transmogNecklace;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;

public enum TransmogHaloStyle {
    DREAM_1(
            "dream_1",
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/maid/halo_deco/dream_halo_dec_1.png"),
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/maid/halo_deco/dream_halo_dec_1_gui.png"),
            "gui.maidspell.transmog_necklace.style.dream_1"
    ),
    DREAM_2(
            "dream_2",
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/maid/halo_deco/dream_halo_dec_2.png"),
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/maid/halo_deco/dream_halo_dec_2_gui.png"),
            "gui.maidspell.transmog_necklace.style.dream_2"
    ),
    DREAM_3(
            "dream_3",
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/maid/halo_deco/dream_halo_dec_3.png"),
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/entity/maid/halo_deco/dream_halo_dec_3_gui.png"),
            "gui.maidspell.transmog_necklace.style.dream_3"
    );

    public static final TransmogHaloStyle DEFAULT = DREAM_1;

    private final String id;
    private final ResourceLocation texture;
    private final ResourceLocation iconTexture;
    private final String translationKey;

    TransmogHaloStyle(String id, ResourceLocation texture, ResourceLocation iconTexture, String translationKey) {
        this.id = id;
        this.texture = texture;
        this.iconTexture = iconTexture;
        this.translationKey = translationKey;
    }

    public String id() {
        return id;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public ResourceLocation iconTexture() {
        return iconTexture;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public int getSerializedIndex() {
        return ordinal();
    }

    public TransmogHaloStyle next() {
        TransmogHaloStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public TransmogHaloStyle previous() {
        TransmogHaloStyle[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public static TransmogHaloStyle byId(String id) {
        return Arrays.stream(values()).filter(style -> style.id.equals(id)).findFirst().orElse(DEFAULT);
    }

    public static TransmogHaloStyle byIndex(int index) {
        TransmogHaloStyle[] values = values();
        if (index < 0 || index >= values.length) {
            return DEFAULT;
        }
        return values[index];
    }
}
