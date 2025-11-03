package com.github.yimeng261.maidspell.spell.manager;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.mojang.logging.LogUtils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 饰品状态管理器
 * 定期检测女仆饰品的变化，特别是法术增强饰品的卸下
 */
public class BaubleStateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 存储每个女仆的饰品状态快照 UUID -> HashSet<Item>
    private static final Map<UUID, HashSet<Item>> maidBaublePrevious = new ConcurrentHashMap<>();
    private static final Map<UUID, HashSet<Item>> maidBaubleCurrent = new ConcurrentHashMap<>();

    /**
     * 更新指定女仆的饰品状态并检测变化
     * @param maid 女仆实体
     */
    public static void updateAndCheckBaubleState(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        
        UUID maidUUID = maid.getUUID();
        HashSet<Item> currentBaubles = getCurrentBaubles(maid);
        HashSet<Item> previousBaubles = getPreviousBaubles(maidUUID);

        maidBaubleCurrent.put(maid.getUUID(), currentBaubles);
        
        List<Item> removedBaubles = previousBaubles.stream().filter(item->!currentBaubles.contains(item)).toList();
        List<Item> addedBaubles = currentBaubles.stream().filter(item->!previousBaubles.contains(item)).toList();
        for (Item removedBauble : removedBaubles) {
            IMaidBauble bauble = BaubleManager.getBauble(new ItemStack(removedBauble));
            if(bauble instanceof IExtendBauble) {
                ((IExtendBauble) bauble).onRemove(maid);
            }
        }
        for (Item addedBauble : addedBaubles) {
            IMaidBauble bauble = BaubleManager.getBauble(new ItemStack(addedBauble));
            if(bauble instanceof IExtendBauble) {
                ((IExtendBauble) bauble).onAdd(maid);
            }
        }

        maidBaublePrevious.put(maidUUID, new HashSet<>(currentBaubles));
    }

    private static HashSet<Item> getPreviousBaubles(UUID maidUUID) {
        return maidBaublePrevious.computeIfAbsent(maidUUID, k -> new HashSet<>());
    }
    
    /**
     * 获取女仆当前的饰品列表
     */
    private static HashSet<Item> getCurrentBaubles(EntityMaid maid) {
        if(maid == null || !maid.isAlive()) {
            return new HashSet<>();
        }

        HashSet<Item> baubles = new HashSet<>();
        BaubleItemHandler handler = maid.getMaidBauble();
        
        // 检查handler是否为null
        if (handler == null) {
            LOGGER.debug("BaubleItemHandler is null for maid {}", maid.getUUID());
            return new HashSet<>();
        }

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                baubles.add(stack.getItem());
            }
        }

        return baubles;
    }

    public static HashSet<Item> getBaubles(EntityMaid maid) {
        if(maid == null || !maid.isAlive()) {
            return new HashSet<>();
        }
        return maidBaubleCurrent.computeIfAbsent(maid.getUUID(), k -> getCurrentBaubles(maid));
    }

    public static boolean hasBauble(EntityMaid maid, ItemStack stack) {
        return getBaubles(maid).contains(stack.getItem());
    }

    public static boolean hasBauble(EntityMaid maid, RegistryObject<Item> item) {
        return getBaubles(maid).contains(item.get());
    }

    public static boolean hasBauble(EntityMaid maid, Item item) {
        return getBaubles(maid).contains(item);
    }

    public static void removeMaidBaubles(EntityMaid maid) {
        maidBaubleCurrent.remove(maid.getUUID());
        maidBaublePrevious.remove(maid.getUUID());
    }

} 