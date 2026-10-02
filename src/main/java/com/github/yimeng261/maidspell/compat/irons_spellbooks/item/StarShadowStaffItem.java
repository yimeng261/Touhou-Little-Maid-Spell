package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentRenderProviders;
import io.redspace.ironsspellbooks.api.item.weapons.ExtendedSwordItem;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import io.redspace.ironsspellbooks.item.weapons.StaffTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * 星影法杖：万法酒狐的法杖，强化末影系法术。
 *
 * <p>继承铁魔法的 {@link StaffItem}，右键施放法术轮盘里当前选中的法术，和铁魔法自己的法杖走同一条代码路径。
 */
public class StarShadowStaffItem extends StaffItem implements GeoItem {

    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/star_shadow_staff.geo.json");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/item/star_shadow_staff.png");
    public static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "animations/star_shadow_staff.animation.json");
    /** 物品栏用的平面图标模型，在客户端登记烘焙。 */
    public static final ResourceLocation GUI_MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "item/star_shadow_staff_gui");

    /**
     * 杖顶魔法石常驻旋转，2 秒一整圈。
     *
     * <p>轨道名必须是 ASCII：GeckoLib 按系统默认字符集读动画文件，中文名在 GBK 环境下会读成乱码。
     */
    private static final RawAnimation GEM_SPIN = RawAnimation.begin().thenLoop("gem_spin");

    private static final int DURABILITY = 1200;
    private static final int ENCHANTMENT_VALUE = 22;

    /** 攻击力 7 + 基础 1 = 8，攻击速度 -3 + 基础 4 = 1。 */
    private static final StaffTier TIER = new StaffTier(7.0F, -3.0F,
            new AttributeContainer(Attributes.ENTITY_INTERACTION_RANGE, 3.0D, AttributeModifier.Operation.ADD_VALUE),
            new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.10D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.10D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new AttributeContainer(AttributeRegistry.ENDER_SPELL_POWER, 0.20D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowStaffItem() {
        super(new Item.Properties().durability(DURABILITY).rarity(Rarity.EPIC)
                .attributes(ExtendedSwordItem.createAttributes(TIER)));
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(Items.AMETHYST_SHARD);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "gem", 0,
                state -> state.setAndContinue(GEM_SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(StarEquipmentRenderProviders.<StarShadowStaffItem>item(MODEL, TEXTURE, ANIMATION, GUI_MODEL));
    }
}
