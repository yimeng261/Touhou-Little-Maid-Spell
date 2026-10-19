package com.github.yimeng261.maidspell.compat.goety;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import net.minecraft.world.item.Item;

/**
 * 无法获得的隐藏法杖，聚晶存取代理到女仆的聚晶包。
 */
public final class VirtualGoetyWandItem extends Item implements IWand {
    public VirtualGoetyWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 虚拟法杖不提供任何学派加成：聚晶决定法术，但不代表有对应学派的法杖。
     */
    @Override
    public SpellType getSpellType() {
        return SpellType.NONE;
    }
}
