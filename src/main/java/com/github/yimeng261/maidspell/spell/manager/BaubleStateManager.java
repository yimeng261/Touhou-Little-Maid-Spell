package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.LivingEntity;
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

    /**
     * 获取女仆装备的指定饰品ItemStack
     * @param maid 女仆实体
     * @param item 饰品物品
     * @return 饰品ItemStack，如果未装备则返回null
     */
    public static ItemStack getBaubleItem(EntityMaid maid, Item item) {
        // 遍历所有饰品槽位
        for (int i = 0; i < maid.getMaidBauble().getSlots(); i++) {
            ItemStack stack = maid.getMaidBauble().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 检查实体是否为装备了光环的女仆
     * @param caster 施法者实体
     * @return 如果是装备了终末之环或晋升之环的女仆返回true，否则返回false
     */
    public static boolean hasMaidWithHalo(LivingEntity caster) {
        if (!(caster instanceof EntityMaid maid)) {
            return false;
        }
        var haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        var ascensionHalo = MaidSpellItems.getAscensionHalo();

        return (haloOfTheEnd != null && hasBauble(maid, haloOfTheEnd)) ||
               (ascensionHalo != null && hasBauble(maid, ascensionHalo));
    }

    /**
     * 检查实体是否为装备了晋升之环的女仆
     * @param caster 施法者实体
     * @return 如果是装备了晋升之环的女仆返回true，否则返回false
     */
    public static boolean hasMaidWithAscensionHalo(LivingEntity caster) {
        if (!(caster instanceof EntityMaid maid)) {
            return false;
        }
        var ascensionHalo = MaidSpellItems.getAscensionHalo();
        return ascensionHalo != null && hasBauble(maid, ascensionHalo);
    }

    /**
     * 检查实体是否为装备了终末之环的女仆
     * @param caster 施法者实体
     * @return 如果是装备了终末之环的女仆返回true，否则返回false
     */
    public static boolean hasMaidWithHaloOfTheEnd(LivingEntity caster) {
        if (!(caster instanceof EntityMaid maid)) {
            return false;
        }
        var haloOfTheEnd = MaidSpellItems.getHaloOfTheEnd();
        return haloOfTheEnd != null && hasBauble(maid, haloOfTheEnd);
    }

} 