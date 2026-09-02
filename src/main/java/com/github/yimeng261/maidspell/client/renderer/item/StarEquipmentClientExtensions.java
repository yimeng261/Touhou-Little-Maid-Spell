package com.github.yimeng261.maidspell.client.renderer.item;

import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 星之魔女系列装备的客户端渲染扩展，仅在客户端加载。
 */
public final class StarEquipmentClientExtensions {

    private StarEquipmentClientExtensions() {
    }

    public static <T extends Item & GeoAnimatable> IClientItemExtensions item(ResourceLocation model,
                                                                              ResourceLocation texture) {
        return item(model, texture, StarEquipmentGeoModel.SHARED_ANIMATION, null);
    }

    /**
     * 物品栏想用平面图标的装备走这个重载。
     *
     * <p>{@code guiModel} 指向一份 {@code item/generated} 模型，它得在
     * {@link com.github.yimeng261.maidspell.client.MaidSpellClientMod} 里登记过才会被烘焙，
     * 同时物品模型 JSON 里 {@code gui} 那一槽必须是单位变换。
     */
    public static <T extends Item & GeoAnimatable> IClientItemExtensions item(ResourceLocation model,
                                                                              ResourceLocation texture,
                                                                              ResourceLocation animation,
                                                                              @Nullable ResourceLocation guiModel) {
        return withArmPose(model, texture, animation, guiModel, null);
    }

    /**
     * 要顺带指定持握姿势的装备走这个 —— 比如法杖，手臂微微上抬并跟着视角俯仰。
     *
     * <p>一个物品只能给一份 {@link IClientItemExtensions}，而铁魔法是在
     * {@code StaffItem.initializeClient} 里单独 accept 一份只带 {@code getArmPose} 的扩展，
     * 所以这里把姿势和渲染器合到同一份里。
     *
     * <p>姿势由调用方传进来，本类不认识任何一个可选模组的类型：
     * 这个文件在核心包下，缺了那个模组照样要能加载。
     */
    public static <T extends Item & GeoAnimatable> IClientItemExtensions itemWithArmPose(
            ResourceLocation model,
            ResourceLocation texture,
            ResourceLocation animation,
            @Nullable ResourceLocation guiModel,
            @Nullable HumanoidModel.ArmPose armPose) {
        return StarEquipmentClientExtensions.<T>withArmPose(model, texture, animation, guiModel, armPose);
    }

    /**
     * 护甲用这个：物品形态还是走上面那套 GeckoLib 物品渲染器，
     * 再补上穿戴时的 {@link GeoArmorRenderer}。
     *
     * <p>护甲那份模型通常和物品那份不是同一个文件 —— 骨架模型的方块坐标得落在
     * 对应护甲槽那一段，物品模型则要平移回原点，所以 {@code itemModel} 单独传。
     */
    public static IClientItemExtensions armor(ResourceLocation itemModel,
                                              ResourceLocation texture,
                                              Supplier<GeoArmorRenderer<?>> armorRendererFactory) {
        IClientItemExtensions itemExtensions = item(itemModel, texture);
        return new IClientItemExtensions() {
            private GeoArmorRenderer<?> armorRenderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return itemExtensions.getCustomRenderer();
            }

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                          EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (armorRenderer == null) {
                    armorRenderer = armorRendererFactory.get();
                }
                armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return armorRenderer;
            }
        };
    }

    private static <T extends Item & GeoAnimatable> IClientItemExtensions withArmPose(ResourceLocation model,
                                                                                      ResourceLocation texture,
                                                                                      ResourceLocation animation,
                                                                                      @Nullable ResourceLocation guiModel,
                                                                                      @Nullable HumanoidModel.ArmPose armPose) {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new StarEquipmentItemRenderer<T>(
                            new StarEquipmentGeoModel<>(model, texture, animation), guiModel);
                }
                return renderer;
            }

            /** 返回 null 就是不干预，交回原版的判定。 */
            @Nullable
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return armPose;
            }
        };
    }
}
