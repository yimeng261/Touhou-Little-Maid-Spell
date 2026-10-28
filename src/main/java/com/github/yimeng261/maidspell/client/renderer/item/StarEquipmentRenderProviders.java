package com.github.yimeng261.maidspell.client.renderer.item;

import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.function.Supplier;

/**
 * 星之魔女系列装备的 GeckoLib 渲染提供者，只在客户端由 {@code createGeoRenderer} 创建。
 */
public final class StarEquipmentRenderProviders {

    private StarEquipmentRenderProviders() {
    }

    /**
     * 物品形态用 GeckoLib 3D 模型；{@code guiModel} 非空时物品栏改画平面图标。
     *
     * <p>{@code guiModel} 指向一份 {@code item/generated} 模型，需要在
     * {@code ModelEvent.RegisterAdditional} 里登记才会被烘焙，物品模型 JSON 里 {@code gui} 那一槽必须是单位变换。
     */
    public static <T extends Item & GeoAnimatable> GeoRenderProvider item(ResourceLocation model,
                                                                          ResourceLocation texture,
                                                                          ResourceLocation animation,
                                                                          @Nullable ResourceLocation guiModel) {
        return new GeoRenderProvider() {
            private StarEquipmentItemRenderer<T> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new StarEquipmentItemRenderer<>(
                            new StarEquipmentGeoModel<>(model, texture, animation), guiModel);
                }
                return renderer;
            }
        };
    }

    /**
     * 护甲：物品形态同 {@link #item}，穿戴时用 {@link GeoArmorRenderer}。
     *
     * <p>护甲骨架模型的方块坐标要落在对应护甲槽那一段，物品模型则平移回原点，所以 {@code itemModel} 单独传。
     */
    public static GeoRenderProvider armor(ResourceLocation itemModel,
                                          ResourceLocation texture,
                                          @Nullable ResourceLocation guiModel,
                                          Supplier<GeoArmorRenderer<?>> armorRendererFactory) {
        GeoRenderProvider itemProvider = item(itemModel, texture, StarEquipmentGeoModel.SHARED_ANIMATION, guiModel);
        return new GeoRenderProvider() {
            private GeoArmorRenderer<?> armorRenderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                return itemProvider.getGeoItemRenderer();
            }

            @Override
            public <E extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable E livingEntity,
                                                                               ItemStack itemStack,
                                                                               @Nullable EquipmentSlot equipmentSlot,
                                                                               @Nullable HumanoidModel<E> original) {
                if (armorRenderer == null) {
                    armorRenderer = armorRendererFactory.get();
                }
                return armorRenderer;
            }
        };
    }
}
