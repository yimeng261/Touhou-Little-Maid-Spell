package com.github.yimeng261.maidspell.client.model.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * 星之魔女系列装备用的 GeoModel，提供模型、贴图与动画文件。
 */
public class StarEquipmentGeoModel<T extends GeoAnimatable> extends GeoModel<T> {

    /**
     * 没有自己动画的装备共用这份。铁魔法的 {@code ExtendedArmorItem} 会注册一个循环播放 idle 的控制器，
     * 所以这个文件必须存在且含有 idle，否则渲染物品时会抛 GeckoLibException。
     */
    public static final ResourceLocation SHARED_ANIMATION =
            new ResourceLocation(MaidSpellMod.MOD_ID, "animations/star_equipment.animation.json");

    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public StarEquipmentGeoModel(ResourceLocation model, ResourceLocation texture) {
        this(model, texture, SHARED_ANIMATION);
    }

    /** 有自己动画的装备（例如星影长剑的光环旋转）走这个构造器。 */
    public StarEquipmentGeoModel(ResourceLocation model, ResourceLocation texture,
                                 ResourceLocation animation) {
        this.model = model;
        this.texture = texture;
        this.animation = animation;
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animation;
    }
}
