package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentClientExtensions;
import io.redspace.ironsspellbooks.render.StaffArmPose;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.CastingItem;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import io.redspace.ironsspellbooks.item.weapons.StaffTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * 星影法杖：万法酒狐的法杖，强化末影系法术。
 *
 * <p>继承铁魔法的 {@link StaffItem}（其父类是 {@link CastingItem}），右键施放法术轮盘里
 * 当前选中的法术 —— 和铁魔法自己的法杖走同一条代码路径，不用自己抄一份。
 */
public class StarShadowStaffItem extends StaffItem implements GeoItem {

    public static final ResourceLocation MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "geo/star_shadow_staff.geo.json");
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/item/star_shadow_staff.png");
    public static final ResourceLocation ANIMATION =
            new ResourceLocation(MaidSpellMod.MOD_ID, "animations/star_shadow_staff.animation.json");
    /** 物品栏用的平面图标模型，在 MaidSpellClientMod 里登记烘焙。 */
    public static final ResourceLocation GUI_MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "item/star_shadow_staff_gui");

    /**
     * 杖顶魔法石常驻旋转，2 秒一整圈，在 Blockbench 里调。
     *
     * <p>轨道名必须是 ASCII：GeckoLib 的 {@code FileLoader.getFileContents} 用
     * {@code Charset.defaultCharset()} 读动画文件，中文名在 GBK 环境下会被读成乱码，
     * 控制器找不到轨道就静静停掉。
     */
    private static final RawAnimation GEM_SPIN = RawAnimation.begin().thenLoop("gem_spin");

    private static final int DURABILITY = 1200;
    private static final int ENCHANTMENT_VALUE = 22;

    /**
     * 攻击力 7 + 基础 1 = 8，攻击速度 -3 + 基础 4 = 1。
     *
     * <p>{@link StaffTier} 只装伤害/攻速/附加属性，耐久、附魔能力、修复材料这三项原先由
     * {@code ExtendedWeaponTier} 一并提供，改继承后要在下面逐个补回，数值保持不变。
     */
    private static final StaffTier TIER = new StaffTier(7.0F, -3.0F,
            new AttributeContainer(ForgeMod.ENTITY_REACH, 3.0D, AttributeModifier.Operation.ADDITION),
            new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.10D,
                    AttributeModifier.Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.10D,
                    AttributeModifier.Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.ENDER_SPELL_POWER, 0.20D,
                    AttributeModifier.Operation.MULTIPLY_BASE));

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowStaffItem() {
        super(new Item.Properties().durability(DURABILITY).rarity(Rarity.EPIC), TIER);
    }

    /** {@link StaffItem} 固定给 20，这里保持原来的 22。 */
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ENCHANTMENT_VALUE;
    }

    /** 修复材料原先来自 {@code ExtendedWeaponTier}，改继承后手动保留紫水晶碎片。 */
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

    /** {@code staff} 重载把 GeckoLib 渲染器和铁魔法法杖的持握姿势合在同一份扩展里。 */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(StarEquipmentClientExtensions.<StarShadowStaffItem>itemWithArmPose(
                MODEL, TEXTURE, ANIMATION, GUI_MODEL, StaffArmPose.STAFF_ARM_POS));
    }
}
