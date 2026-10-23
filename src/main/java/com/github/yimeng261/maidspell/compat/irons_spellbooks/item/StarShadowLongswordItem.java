package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentRenderProviders;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import io.redspace.ironsspellbooks.api.item.weapons.ExtendedSwordItem;
import io.redspace.ironsspellbooks.api.item.weapons.MagicSwordItem;
import io.redspace.ironsspellbooks.api.registry.SpellDataRegistryHolder;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.ExtendedWeaponTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * 星影长剑：万法酒狐的佩剑，注入了虚空相变法术（法术容器由铁魔法在物品创建时写入）。
 */
public class StarShadowLongswordItem extends MagicSwordItem implements GeoItem {

    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/star_shadow_longsword.geo.json");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/item/star_shadow_longsword.png");
    public static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "animations/star_shadow_longsword.animation.json");
    /** 物品栏用的平面图标模型，在客户端登记烘焙。 */
    public static final ResourceLocation GUI_MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "item/star_shadow_longsword_gui");

    /**
     * 剑身周围三圈光环常驻旋转，2 秒一整圈。
     *
     * <p>轨道名必须是 ASCII：GeckoLib 按系统默认字符集读动画文件，中文名在 GBK 环境下会读成乱码。
     */
    private static final RawAnimation HALO_SPIN = RawAnimation.begin().thenLoop("halo_spin");

    /** 攻击力 11 + 基础 1 = 12，攻击速度 -3 + 基础 4 = 1。 */
    private static final ExtendedWeaponTier TIER = new ExtendedWeaponTier(2000, 11.0F, -3.0F, 22,
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            () -> Ingredient.of(Items.AMETHYST_SHARD),
            new AttributeContainer(Attributes.ENTITY_INTERACTION_RANGE, 3.0D, AttributeModifier.Operation.ADD_VALUE));

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowLongswordItem() {
        super(TIER, new Item.Properties().rarity(Rarity.EPIC).attributes(ExtendedSwordItem.createAttributes(TIER)),
                SpellDataRegistryHolder.of(
                        new SpellDataRegistryHolder(IronsSpellbooksCompatSpells.VOID_PHASE, 1)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "halo", 0,
                state -> state.setAndContinue(HALO_SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(StarEquipmentRenderProviders.<StarShadowLongswordItem>item(MODEL, TEXTURE, ANIMATION, GUI_MODEL));
    }
}
