package com.github.yimeng261.maidspell.compat.irons_spellbooks.item;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.client.model.item.StarEquipmentGeoModel;
import com.github.yimeng261.maidspell.client.renderer.item.StarEquipmentClientExtensions;
import io.redspace.ironsspellbooks.item.armor.ExtendedArmorItem;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.function.Consumer;

/**
 * 星之魔女法帽：万法酒狐的法帽，戴在头上时用 GeckoLib 渲染 3D 模型。
 */
public class StarWitchHatItem extends ExtendedArmorItem {

    /**
     * GeckoLib 装甲骨架模型：穿戴（GeoArmorRenderer）与 boss 佩戴都用这份，
     * 方块坐标必须落在头部那一段，否则 armorHead 对不上。
     */
    public static final ResourceLocation MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "geo/star_witch_hat.geo.json");
    /**
     * 物品栏 / 手持用的模型：同一顶帽子，但整体平移到原点。
     * GeoItemRenderer 的 display 变换是绕模型原点旋转缩放的，
     * 装甲那份离原点 33 格远，摆位和在 Blockbench 里调都不好使。
     */
    public static final ResourceLocation ITEM_MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "geo/star_witch_hat_item.geo.json");
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/item/star_witch_hat.png");

    public StarWitchHatItem() {
        super(StarWitchArmorMaterial.STAR_WITCH, ArmorItem.Type.HELMET,
                new Item.Properties().rarity(Rarity.EPIC), new AttributeContainer[0]);
    }

    /** GeckoLib 负责实际贴图，这里只是避免原版去找不存在的护甲层贴图。 */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return TEXTURE.toString();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public GeoArmorRenderer<?> supplyRenderer() {
        return new GeoArmorRenderer<>(new StarEquipmentGeoModel<StarWitchHatItem>(MODEL, TEXTURE));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(StarEquipmentClientExtensions.armor(ITEM_MODEL, TEXTURE, this::supplyRenderer));
    }
}
