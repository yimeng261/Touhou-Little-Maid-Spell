package com.github.yimeng261.maidspell.client.renderer.item;

import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import io.redspace.ironsspellbooks.render.StaffArmPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.core.animatable.GeoAnimatable;

import javax.annotation.Nullable;

/**
 * 星之魔女系列装备的客户端渲染扩展，仅在客户端加载。
 */
public final class StarEquipmentClientExtensions {

    private StarEquipmentClientExtensions() {
    }

    public static <T extends Item & GeoAnimatable> IClientItemExtensions item(ResourceLocation model,
                                                                              ResourceLocation texture) {
        return item(model, texture, StarEquipmentGeoModel.SHARED_ANIMATION);
    }

    /** 带自己动画文件的装备用这个重载，控制器要播的轨道得在这份文件里。 */
    public static <T extends Item & GeoAnimatable> IClientItemExtensions item(ResourceLocation model,
                                                                              ResourceLocation texture,
                                                                              ResourceLocation animation) {
        return item(model, texture, animation, null);
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
     * 法杖用这个：除了 GeckoLib 渲染器，再接上铁魔法法杖那套持握姿势 —— 手臂微微上抬，
     * 并跟着视角俯仰。
     *
     * <p>铁魔法是在 {@code StaffItem.initializeClient} 里单独 accept 一份只带
     * {@code getArmPose} 的扩展；一个物品只能给一份 {@link IClientItemExtensions}，
     * 所以这里把姿势和渲染器合到同一份里。
     */
    public static <T extends Item & GeoAnimatable> IClientItemExtensions staff(ResourceLocation model,
                                                                               ResourceLocation texture,
                                                                               ResourceLocation animation,
                                                                               @Nullable ResourceLocation guiModel) {
        return withArmPose(model, texture, animation, guiModel, StaffArmPose.STAFF_ARM_POS);
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
