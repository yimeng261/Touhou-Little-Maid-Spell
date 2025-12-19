package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

import org.slf4j.Logger;


/**
 * 饰品状态管理器
 * 定期检测女仆饰品的变化，特别是法术增强饰品的卸下
 */
public class BaubleStateManager {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean hasBauble(EntityMaid maid, ItemStack stack) {
        return maid.getMaidBauble().containsItem(stack.getItem());
    }

    public static boolean hasBauble(EntityMaid maid, RegistryObject<Item> item) {
        return maid.getMaidBauble().containsItem(item.get());
    }

    public static boolean hasBauble(EntityMaid maid, Item item) {
        return maid.getMaidBauble().containsItem(item);
    }

} 