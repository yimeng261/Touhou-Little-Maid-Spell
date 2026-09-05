package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentClientExtensions;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import io.redspace.ironsspellbooks.api.item.weapons.MagicSwordItem;
import io.redspace.ironsspellbooks.api.registry.SpellDataRegistryHolder;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.ExtendedWeaponTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
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
 * 星影长剑：万法酒狐的佩剑，注入了虚空相变法术。
 */
public class StarShadowLongswordItem extends MagicSwordItem implements GeoItem {

    public static final ResourceLocation MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "geo/star_shadow_longsword.geo.json");
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/item/star_shadow_longsword.png");
    public static final ResourceLocation ANIMATION =
            new ResourceLocation(MaidSpellMod.MOD_ID, "animations/star_shadow_longsword.animation.json");
    /** 物品栏用的平面图标模型，在 MaidSpellClientMod 里登记烘焙。 */
    public static final ResourceLocation GUI_MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "item/star_shadow_longsword_gui");

    /**
     * 剑身周围三圈光环常驻旋转，2 秒一整圈，在 Blockbench 里调。
     *
     * <p>轨道名必须是 ASCII：GeckoLib 的 {@code FileLoader.getFileContents} 用
     * {@code Charset.defaultCharset()} 读动画文件，中文名在 GBK 环境下会被读成乱码，
     * 控制器找不到轨道就静静停掉。
     */
    private static final RawAnimation HALO_SPIN = RawAnimation.begin().thenLoop("halo_spin");

    /** 攻击力 11 + 基础 1 = 12，攻击速度 -3 + 基础 4 = 1。 */
    private static final ExtendedWeaponTier TIER = new ExtendedWeaponTier(2000, 11.0F, -3.0F, 22,
            () -> Ingredient.of(Items.AMETHYST_SHARD),
            new AttributeContainer(ForgeMod.ENTITY_REACH, 3.0D, AttributeModifier.Operation.ADDITION));

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarShadowLongswordItem() {
        super(TIER, new Item.Properties().rarity(Rarity.EPIC),
                SpellDataRegistryHolder.of(
                        new SpellDataRegistryHolder(IronsSpellbooksCompatSpells.VOID_PHASE, 1)));
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        initializeSpellContainer(stack);
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !ISpellContainer.isSpellContainer(stack)) {
            initializeSpellContainer(stack);
        }
        super.inventoryTick(stack, level, entity, slot, selected);
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
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(StarEquipmentClientExtensions.<StarShadowLongswordItem>item(MODEL, TEXTURE, ANIMATION, GUI_MODEL));
    }
}
