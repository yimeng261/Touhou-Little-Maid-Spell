package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentRenderProviders;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.armor.ExtendedArmorItem;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.function.Consumer;

/**
 * 星之魔女法帽：万法酒狐的法帽，戴在头上时用 GeckoLib 渲染 3D 模型。
 * 另外提供 +200 最大法力值、+15% 法术强度、+10% 法术冷却缩减。
 */
public class StarWitchHatItem extends ExtendedArmorItem {

    /**
     * GeckoLib 装甲骨架模型：穿戴（GeoArmorRenderer）与 boss 佩戴都用这份，
     * 方块坐标必须落在头部那一段，否则 armorHead 对不上。
     */
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/star_witch_hat.geo.json");
    /**
     * 物品栏 / 手持用的模型：同一顶帽子，但整体平移到原点。
     * GeoItemRenderer 的 display 变换是绕模型原点旋转缩放的，装甲那份离原点 33 格远。
     */
    public static final ResourceLocation ITEM_MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "geo/star_witch_hat_item.geo.json");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "textures/item/star_witch_hat.png");
    /** 仅用于物品栏的平面图标；模型贴图仍由 {@link #TEXTURE} 提供。 */
    public static final ResourceLocation GUI_MODEL =
            ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "item/star_witch_hat_gui");

    private static final int DURABILITY = 600;

    public StarWitchHatItem() {
        super(StarWitchArmorMaterial.STAR_WITCH, ArmorItem.Type.HELMET,
                new Item.Properties().durability(DURABILITY).rarity(Rarity.EPIC),
                new AttributeContainer(AttributeRegistry.MAX_MANA, 200.0D, AttributeModifier.Operation.ADD_VALUE),
                new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.10D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** GeckoLib 负责实际贴图，这里只是避免原版去找不存在的护甲层贴图。 */
    @Override
    public @Nullable ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                                      ArmorMaterial.Layer layer, boolean innerModel) {
        return TEXTURE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public GeoArmorRenderer<?> supplyRenderer() {
        return new GeoArmorRenderer<>(new StarEquipmentGeoModel<StarWitchHatItem>(MODEL, TEXTURE));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(StarEquipmentRenderProviders.armor(ITEM_MODEL, TEXTURE, GUI_MODEL, this::supplyRenderer));
    }
}
